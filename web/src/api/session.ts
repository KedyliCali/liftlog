import { isOtherAccount, toRecoveryCode, type Credentials } from '../lib/recoveryCode'
import { credentialToJson, isUserCancel, toCreationOptions, toRequestOptions } from '../lib/webauthn'
import { AppError, configureAuth } from './client'
import { api } from './endpoints'
import type { AuthResponse, PasskeyCeremony } from './types'

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
  /**
   * 'passkey' when this device came back with Face ID: its password is then a device secret, not the
   * account's real password, so it must never be shown as the recovery code. "New code" clears it.
   */
  via?: 'passkey'
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

/**
 * The code for the confirmed password, never a pending one the server may not have accepted yet. Null on a
 * device that came back with Face ID: its device secret only opens the log until the next "New code".
 */
export function currentRecoveryCode(): string | null {
  const credentials = readCredentials()
  return credentials && credentials.via !== 'passkey' ? toRecoveryCode(credentials) : null
}

export function restoredWithPasskey(): boolean {
  return readCredentials()?.via === 'passkey'
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
      rememberPasskey(false)
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
  // A real password now, so a passkey-restored device loses its marker and shows a normal code
  const confirmed = { email: credentials.email, password: pending }
  writeCredentials(confirmed)
  // The server deletes every passkey with the old code, so the backup must be offered again
  rememberPasskey(false)
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
export function commitRestore(credentials: Credentials, auth: AuthResponse, via?: 'passkey') {
  writeCredentials({ email: credentials.email, password: credentials.password, ...(via && { via }) })
  localStorage.setItem(TOKEN_KEY, auth.token)
  sessionStorage.setItem(RESTORED_KEY, auth.user.displayName ?? '')
}

/**
 * The email of the account this device made (or came back with) a passkey for. Lets the reminder banner
 * decide without a request; Profile asks the server and corrects it. Keyed by email so switching to
 * another log does not inherit the old log's backup.
 */
const PASSKEY_KEY = 'liftlog.passkey'

export function hasPasskeyHere(): boolean {
  try {
    const email = localStorage.getItem(PASSKEY_KEY)
    return email !== null && email === readCredentials()?.email
  } catch {
    return false
  }
}

/** Profile calls this with what the server says, so a passkey made on another device counts too. */
export function rememberPasskey(exists: boolean, email: string | null = readCredentials()?.email ?? null) {
  quietly(() => {
    if (exists && email) localStorage.setItem(PASSKEY_KEY, email)
    else localStorage.removeItem(PASSKEY_KEY)
  })
}

/**
 * Safari only opens Face ID while the tap's user activation is still fresh, so a slow options request
 * between the tap and navigator.credentials (a sleeping server) can make it refuse. Each screen with a
 * Face ID button therefore watches a prefetch: options are fetched when the button shows, refreshed shortly
 * before they go stale (timer, and on returning to the app, since iOS freezes timers in the background),
 * and the tap uses them at once. Each ceremony is handed out once, since the server deletes it after use.
 */
const PREFETCH_MAX_AGE_MS = 4 * 60_000
/** Kept from the server's own expiry, so a slow clock or a long request never hands out a dead ceremony. */
const EXPIRY_MARGIN_MS = 60_000

/** How a tap got its options: 'ready' (prefetched, Face ID opened inside the tap) or 'onTheSpot'. */
type Taken<Options> = { ceremony: PasskeyCeremony<Options>; source: 'ready' | 'onTheSpot' }

function ceremonyPrefetch<Options>(fetchOptions: () => Promise<PasskeyCeremony<Options>>) {
  let ready: { ceremony: PasskeyCeremony<Options>; staleAt: number } | null = null
  let inFlight: { promise: Promise<PasskeyCeremony<Options>>; claimed: boolean } | null = null
  let watchers = 0
  let timer: ReturnType<typeof setTimeout> | undefined

  const fresh = () => ready !== null && Date.now() < ready.staleAt

  const staleAt = (ceremony: PasskeyCeremony<Options>, askedAt: number) => {
    // Aged from the moment of asking: the server's ceremony clock starts no earlier than that
    const byClock = askedAt + PREFETCH_MAX_AGE_MS
    const expires = ceremony.expiresAt ? Date.parse(ceremony.expiresAt) : NaN
    return Number.isNaN(expires) ? byClock : Math.min(byClock, expires - EXPIRY_MARGIN_MS)
  }

  const prefetch = () => {
    if (fresh() || inFlight) return
    const askedAt = Date.now()
    const entry = { promise: fetchOptions(), claimed: false }
    inFlight = entry
    entry.promise
      .then((ceremony) => {
        // A tap that arrived meanwhile took this ceremony; it must not be handed out a second time
        if (entry.claimed) return
        ready = { ceremony, staleAt: staleAt(ceremony, askedAt) }
        clearTimeout(timer)
        timer = setTimeout(() => {
          if (watchers > 0) prefetch()
        }, Math.max(0, ready.staleAt - Date.now()))
      })
      .catch(() => {})
      .finally(() => {
        if (inFlight === entry) inFlight = null
      })
  }

  return {
    prefetch,
    /** A visible button: keeps options fresh until the returned function is called (on unmount). */
    watch() {
      watchers++
      prefetch()
      return () => {
        watchers--
        if (watchers === 0) clearTimeout(timer)
      }
    },
    /** Back in the foreground (timers may not have run): refresh stale options for visible buttons. */
    wake() {
      if (watchers > 0) prefetch()
    },
    /**
     * Fresh options are returned synchronously, so nothing is awaited before navigator.credentials. A tap
     * during a prefetch waits for that request instead of sending a second one.
     */
    take(): PasskeyCeremony<Options> | null {
      const ceremony = fresh() ? ready!.ceremony : null
      ready = null
      return ceremony
    },
    fetchNow(): Promise<PasskeyCeremony<Options>> {
      if (inFlight && !inFlight.claimed) {
        inFlight.claimed = true
        return inFlight.promise
      }
      return fetchOptions()
    },
  }
}

/**
 * Held options come back as a plain value, not a promise: even an already-resolved await would put a
 * microtask between the tap and navigator.credentials.
 */
function takeCeremony<Options>(cache: ReturnType<typeof ceremonyPrefetch<Options>>): Taken<Options> | Promise<Taken<Options>> {
  const ceremony = cache.take()
  if (ceremony) return { ceremony, source: 'ready' }
  return cache.fetchNow().then((fetched) => ({ ceremony: fetched, source: 'onTheSpot' as const }))
}

const creationPrefetch = ceremonyPrefetch(api.passkeyCreationOptions)
const requestPrefetch = ceremonyPrefetch(api.passkeyRequestOptions)

if (typeof window !== 'undefined') {
  const wake = () => {
    if (document.visibilityState !== 'visible') return
    creationPrefetch.wake()
    requestPrefetch.wake()
  }
  document.addEventListener('visibilitychange', wake)
  // pageshow covers a page restored from the back-forward cache
  window.addEventListener('pageshow', wake)
}

/** For a visible "Back up with Face ID" button; returns the cleanup for unmount. */
export const watchPasskeyCreation = () => creationPrefetch.watch()
/** For a visible "Come back with Face ID" button; returns the cleanup for unmount. */
export const watchPasskeyRequest = () => requestPrefetch.watch()

/**
 * 'cancelled': the user closed the Face ID sheet, so the caller says nothing. 'tapAgain': the options had
 * to be fetched on the tap and Safari then refused, most likely because that wait used up the tap's user
 * activation; the background prefetch is ready by then, so a second tap works.
 */
export type PasskeyResult = 'done' | 'cancelled' | 'tapAgain'

function refusal(error: unknown, source: Taken<unknown>['source']): PasskeyResult | null {
  if (!isUserCancel(error)) return null
  return source === 'onTheSpot' && (error as { name?: string }).name === 'NotAllowedError' ? 'tapAgain' : 'cancelled'
}

/** Anything but success leaves the button on screen, so it gets a fresh ceremony for the next tap. */
async function refreshUnlessDone(cache: { prefetch: () => void }, attempt: () => Promise<PasskeyResult>) {
  const result = await attempt().catch((error: unknown) => {
    cache.prefetch()
    throw error
  })
  if (result !== 'done') cache.prefetch()
  return result
}

/**
 * "Back up with Face ID": the server starts a registration, the phone makes a key pair in its keychain,
 * and the server keeps the public half. Other failures reject (AppError from the server, or the
 * browser's DOMException). The local flag is set only once the server has the passkey.
 */
export function createPasskey(): Promise<PasskeyResult> {
  return refreshUnlessDone(creationPrefetch, async () => {
    const taken = takeCeremony(creationPrefetch)
    const { ceremony, source } = taken instanceof Promise ? await taken : taken
    let credential: PublicKeyCredential
    try {
      credential = (await navigator.credentials.create({ publicKey: toCreationOptions(ceremony.publicKey) })) as PublicKeyCredential
    } catch (error) {
      const refused = refusal(error, source)
      if (refused) return refused
      // excludeCredentials matched: this keychain already holds a passkey the server knows for this account
      if ((error as { name?: string }).name === 'InvalidStateError') {
        rememberPasskey(true)
        return 'done'
      }
      throw error
    }
    const answer = credentialToJson(credential)
    try {
      await api.registerPasskey(ceremony.ceremonyId, answer)
    } catch (error) {
      // The key pair already exists in the keychain; one lost answer should not throw it away
      if (!(error instanceof AppError && error.kind === 'network')) throw error
      await api.registerPasskey(ceremony.ceremonyId, answer)
    }
    rememberPasskey(true)
    return 'done'
  })
}

/**
 * "Come back with Face ID": the phone offers its LiftLog passkey, the server recognizes the account and
 * keeps a fresh random secret as this device's own password. From then on this device signs in silently
 * with {email, deviceSecret}, exactly like after a code restore; the caller reloads.
 */
export function restoreWithPasskey(): Promise<PasskeyResult> {
  return refreshUnlessDone(requestPrefetch, async () => {
    const taken = takeCeremony(requestPrefetch)
    const { ceremony, source } = taken instanceof Promise ? await taken : taken
    let credential: PublicKeyCredential
    try {
      credential = (await navigator.credentials.get({ publicKey: toRequestOptions(ceremony.publicKey), mediation: 'optional' })) as PublicKeyCredential
    } catch (error) {
      const refused = refusal(error, source)
      if (refused) return refused
      throw error
    }
    const deviceSecret = randomPassword()
    // A 401 here (unknown or deleted passkey) rejects as AppError 'unauthorized'; nothing was stored yet
    const auth = await api.signInWithPasskey(ceremony.ceremonyId, credentialToJson(credential), deviceSecret)
    // The device secret is not the account's password, so it is marked and never shown as a recovery code
    commitRestore({ email: auth.user.email, password: deviceSecret }, auth, 'passkey')
    rememberPasskey(true, auth.user.email)
    return 'done'
  })
}

/** Only offered after this device's credentials stopped working: starts a brand-new, empty account. */
export function startFresh(): Promise<void> {
  localStorage.removeItem(CREDENTIALS_KEY)
  currentEmail = null
  return signInDevice()
}
