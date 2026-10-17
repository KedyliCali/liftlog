import { AppError, configureAuth } from './client'
import { api } from './endpoints'

/**
 * Same idea as the Android app: no login form. Each browser gets a hidden account on first visit,
 * stored in localStorage. Clearing site data starts a fresh, empty account.
 */
const CREDENTIALS_KEY = 'liftlog.device'
const TOKEN_KEY = 'liftlog.token'

interface DeviceCredentials {
  email: string
  password: string
}

export function generateCredentials(): DeviceCredentials {
  const secret = new Uint8Array(32)
  crypto.getRandomValues(secret)
  // base64url of 256 random bits: 43 characters, well under BCrypt's 72-byte limit
  const password = btoa(String.fromCharCode(...secret)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return { email: `device-${crypto.randomUUID()}@liftlog.device`, password }
}

function readCredentials(): DeviceCredentials | null {
  try {
    const raw = localStorage.getItem(CREDENTIALS_KEY)
    return raw ? (JSON.parse(raw) as DeviceCredentials) : null
  } catch {
    return null
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

configureAuth({ getToken: () => token, onUnauthorized: () => setToken(null) })

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
    localStorage.setItem(CREDENTIALS_KEY, JSON.stringify(credentials))
    setToken((await api.register(credentials.email, credentials.password)).token)
    return
  }
  try {
    setToken((await api.login(credentials.email, credentials.password)).token)
  } catch (error) {
    // The server no longer knows this account (database reset) or a registration never completed
    if (error instanceof AppError && error.kind === 'unauthorized') {
      setToken((await api.register(credentials.email, credentials.password)).token)
    } else {
      throw error
    }
  }
}
