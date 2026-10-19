// The recovery code is this device's own account written as one string: the UUID from the device email
// (without dashes), a dot, and the 256-bit device password. Restoring is therefore an ordinary login, so
// the server stores nothing new. These helpers are pure so every format rule is unit-tested.

export interface Credentials {
  email: string
  password: string
}

const DEVICE_EMAIL = /^device-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})@liftlog\.device$/
const DEVICE_PASSWORD = /^[A-Za-z0-9_-]{43}$/
// '.' never occurs in base64url, so it splits the two halves unambiguously. No lookbehind, for older Safari.
const CODE_IN_TEXT = /(?:^|[^0-9A-Za-z_-])([0-9a-fA-F]{32})\.([A-Za-z0-9_-]{43})(?![A-Za-z0-9_-])/
const WHOLE_CODE = /^([0-9a-fA-F]{32})\.([A-Za-z0-9_-]{43})$/

/** Null for accounts that are not device accounts (demo, test accounts), which have no code. */
export function toRecoveryCode(credentials: Credentials): string | null {
  const email = DEVICE_EMAIL.exec(credentials.email)
  if (!email || !DEVICE_PASSWORD.test(credentials.password)) return null
  return `${email[1].replace(/-/g, '')}.${credentials.password}`
}

/**
 * Accepts the code as pasted from anywhere: inside the shared message, with spaces around it, broken
 * across lines, or with an auto-capitalized first letter. The password half is case-sensitive and never
 * changed; only the hex half is lowercased.
 */
export function parseRecoveryCode(input: string): Credentials | null {
  const match = CODE_IN_TEXT.exec(input) ?? WHOLE_CODE.exec(input.replace(/\s+/g, ''))
  if (!match) return null
  const hex = match[1].toLowerCase()
  const uuid = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  return { email: `device-${uuid}@liftlog.device`, password: match[2] }
}

/** A labeled message for Notes or Messages; parseRecoveryCode finds the code inside it again. */
export function shareText(label: string, code: string): string {
  return `${label}\n${code}`
}

/** Stored per device: 'saved' once the user copied or shared the code, or 'snooze:YYYY-MM-DD'. */
export function shouldNudge(state: string | null, today: string): boolean {
  if (state === 'saved') return false
  if (state?.startsWith('snooze:')) return today >= state.slice('snooze:'.length)
  return true
}

/** The date `days` after an ISO date, for the "remind me later" snooze. */
export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  const date = new Date(Date.UTC(y, m - 1, d + days))
  return date.toISOString().slice(0, 10)
}

/** Another tab switched or removed this browser's account (not just changed its password). */
export function isOtherAccount(currentEmail: string | null, newStorageValue: string | null): boolean {
  if (newStorageValue === null) return true
  try {
    return (JSON.parse(newStorageValue) as Partial<Credentials>).email !== currentEmail
  } catch {
    return true
  }
}
