import { isOtherAccount, toRecoveryCode, type Credentials } from '../lib/recoveryCode'
import { AppError, configureAuth } from './client'
import { api } from './endpoints'
import type { AuthResponse } from './types'

/**
 * Same idea as the Android app: no login form. Each browser gets a hidden account on first visit,
 * stored in localStorage. The recovery code (lib/recoveryCode.ts) is these same credentials, so it is
 * the way back to the account after site data is cleared or on a new phone.
 */
const CREDENTIALS_KEY = 'liftlog.device'
const TOKEN_KEY = 'liftlog.token'
/** One-time "welcome back" after a restore, read by the layout after the reload. */
export const RESTORED_KEY = 'liftlog.restored'
/** One-time "new code created" when a pending new code turns out to have reached the server after all. */
export const CODE_CHANGED_KEY = 'liftlog.codeChanged'
/** Per device: 'saved' once the code was copied or shared, or 'snooze:YYYY-MM-DD'. */
export const NUDGE_KEY = 'liftlog.recoveryNudge'

interface DeviceCredentials extends Credentials {
  /** Set only between sending a new code and the server confirming it; see regenerateRecoveryCode. */
  pendingPassword?: string
}

function randomPassword(): string {
  const secret = new Uint8Array(32)
  crypto.getRandomValues(secret)
  // base64url of 256 random bits: 43 characters, well under BCrypt's 72-byte limit
  return btoa(String.fromCharCode(...secret)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export function generateCredentials(): Credentials {
  return { email: `device-${crypto.randomUUID()}@liftlog.device`, password: randomPassword() }
}

function readCredentials(): DeviceCredentials | null {
  try {
    const raw = localStorage.getItem(CREDENTIALS_KEY)
    return raw ? (JSON.parse(raw) as DeviceCredentials) : null
  } catch {
    return null
  }
}

/**
 * The account this tab is showing. Kept in memory because a storage event fires after localStorage
 * already holds the new value, so reading storage then would compare the new account with itself.
 */
let currentEmail: string | null = readCredentials()?.email ?? null

function writeCredentials(credentials: DeviceCredentials) {
  localStorage.setItem(CREDENTIALS_KEY, JSON.stringify(credentials))
  currentEmail = credentials.email
}

function quietly(action: () => void) {
  try {
    action()
  } catch {
    // Storage can be unavailable (private mode); these keys only drive reminders
  }
}

let token: string | null = localStorage.getItem(TOKEN_KEY)
const listeners = new Set<() => void>()

function setToken(value: string | null) {
  token = value
  if (value) localStorage.setItem(TOKEN_KEY, value)
  else localStorage.removeItem(TOKEN_KEY)
  listeners.forEach((listener) => listener())
}

export const session = {
  getToken: () => token,
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}

configureAuth({
  getToken: () => token,
  // Only the token that was actually rejected is dropped: a late 401 for a request sent before a new
  // code was made must not sign the fresh token out
  onUnauthorized: (rejected) => {
    if (rejected === token) setToken(null)
  },
})

/** Another tab switched or removed this browser's account; this tab must reload rather than show the old one. */
export function isAccountChangeElsewhere(key: string | null, newValue: string | null): boolean {
  return key === CREDENTIALS_KEY && isOtherAccount(currentEmail, newValue)
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (isAccountChangeElsewhere(event.key, event.newValue)) window.location.reload()
  })
}

export function hasDeviceCredentials(): boolean {
  return readCredentials() !== null
}

/**
 * True only when the code is exactly what this device already uses. The same account with a different
 * password is that account's newer code (for example on a device that was signed out) and must be accepted.
 */
export function isThisDevicesCode(credentials: Credentials): boolean {
  const stored = readCredentials()
  return stored !== null && stored.email === credentials.email && stored.password === credentials.password
}

/** The code for the confirmed password, never a pending one the server may not have accepted yet. */
export function currentRecoveryCode(): string | null {
  const credentials = readCredentials()
  return credentials ? toRecoveryCode(credentials) : null
}

export function hasPendingPassword(): boolean {
  return readCredentials()?.pendingPassword != null
}

let inFlight: Promise<void> | null = null

/** Signs in with this browser's account, creating it on first visit. Concurrent callers share one attempt. */
export function signInDevice(): Promise<void> {
  inFlight ??= doSignIn().finally(() => {
    inFlight = null
  })
  return inFlight
}

async function doSignIn() {
  let credentials = readCredentials()
  if (!credentials) {
    credentials = generateCredentials()
    // Stored before calling the server, so a retry after a lost response reuses the same account
    writeCredentials(credentials)
    setToken((await api.register(credentials.email, credentials.password)).token)
    return
  }
  try {
    setToken((await api.login(credentials.email, credentials.password)).token)
    return
  } catch (error) {
    if (!(error instanceof AppError && error.kind === 'unauthorized')) throw error
  }
  // A new code may have reached the server even though its answer never reached us
  if (credentials.pendingPassword) {
    try {
      const auth = await api.login(credentials.email, credentials.pendingPassword)
      writeCredentials({ email: credentials.email, password: credentials.pendingPassword })
      // The code really changed, so the one the user saved no longer works: tell them (after the gate
      // remounts the app) and bring the "save your code" reminder back
      quietly(() => localStorage.removeItem(NUDGE_KEY))
      quietly(() => sessionStorage.setItem(CODE_CHANGED_KEY, '1'))
      setToken(auth.token)
      return
    } catch (error) {
      if (!(error instanceof AppError && error.kind === 'unauthorized')) throw error
    }
  }
  try {
    // The server no longer knows this account (database reset) or a registration never completed
    setToken((await api.register(credentials.email, credentials.password)).token)
  } catch (error) {
    // The account exists but refuses every password this device has: its code was replaced elsewhere
    if (error instanceof AppError && error.kind === 'conflict') throw new AppError('signedOut')
    throw error
  }
}

let regenerating: Promise<string> | null = null

/**
 * "New code": changes the password, which signs every other device out. The new password is stored as
 * pending first and reused on retry, so a lost response can never leave this device with the wrong one.
 */
export function regenerateRecoveryCode(): Promise<string> {
  regenerating ??= doRegenerate().finally(() => {
    regenerating = null
  })
  return regenerating
}

async function doRegenerate(): Promise<string> {
  const credentials = readCredentials()
  if (!credentials) throw new AppError('unauthorized')
  const pending = credentials.pendingPassword ?? randomPassword()
  writeCredentials({ ...credentials, pendingPassword: pending })
  const auth = await api.changePassword(credentials.password, pending)
  const confirmed = { email: credentials.email, password: pending }
  writeCredentials(confirmed)
  setToken(auth.token)
  return toRecoveryCode(confirmed) ?? ''
}

/** Checks a code with the server without changing anything here. Never registers, so a typo cannot create an account. */
export function verifyRecoveryCode(credentials: Credentials): Promise<AuthResponse> {
  return api.login(credentials.email, credentials.password)
}

/**
 * Switches this device to the verified account. Written without notifying listeners, so the old
 * account's screens never render with the new token; the caller reloads the page right after.
 */
export function commitRestore(credentials: Credentials, auth: AuthResponse) {
  writeCredentials({ email: credentials.email, password: credentials.password })
  localStorage.setItem(TOKEN_KEY, auth.token)
  sessionStorage.setItem(RESTORED_KEY, auth.user.displayName ?? '')
}

/** Only offered after this device's credentials stopped working: starts a brand-new, empty account. */
export function startFresh(): Promise<void> {
  localStorage.removeItem(CREDENTIALS_KEY)
  currentEmail = null
  return signInDevice()
}
