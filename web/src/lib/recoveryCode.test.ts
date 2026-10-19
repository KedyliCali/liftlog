import { describe, expect, it } from 'vitest'
import { generateCredentials } from '../api/session'
import { addDays, isOtherAccount, parseRecoveryCode, shareText, shouldNudge, toRecoveryCode } from './recoveryCode'

const credentials = { email: 'device-0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d@liftlog.device', password: 'Ab-_0123456789abcdefghijklmnopqrstuvwxyzABC' }
const code = '0a1b2c3d4e5f4a6b8c7d9e0f1a2b3c4d.Ab-_0123456789abcdefghijklmnopqrstuvwxyzABC'

describe('toRecoveryCode', () => {
  it('writes the device account as uuid.password', () => {
    expect(toRecoveryCode(credentials)).toBe(code)
    expect(code).toMatch(/^[0-9a-f]{32}\.[A-Za-z0-9_-]{43}$/)
    expect(code).toHaveLength(76)
  })

  it('round-trips freshly generated credentials exactly', () => {
    for (let i = 0; i < 20; i++) {
      const generated = generateCredentials()
      expect(parseRecoveryCode(toRecoveryCode(generated)!)).toEqual(generated)
    }
  })

  it('has no code for accounts that are not device accounts', () => {
    expect(toRecoveryCode({ email: 'demo@liftlog.app', password: credentials.password })).toBeNull()
    expect(toRecoveryCode({ email: credentials.email, password: 'p' })).toBeNull()
  })
})

describe('parseRecoveryCode', () => {
  it('finds the code however it was pasted', () => {
    expect(parseRecoveryCode(code)).toEqual(credentials)
    expect(parseRecoveryCode(`  ${code}\n`)).toEqual(credentials)
    expect(parseRecoveryCode(`${code.slice(0, 20)}\n${code.slice(20, 50)}\n${code.slice(50)}`)).toEqual(credentials)
    expect(parseRecoveryCode(shareText('LiftLog kurtarma kodu:', code))).toEqual(credentials)
    expect(parseRecoveryCode(shareText('LiftLog recovery code:', code))).toEqual(credentials)
    expect(parseRecoveryCode(`Kodum ${code}.`)).toEqual(credentials)
  })

  it('lowercases only the hex half, leaving the password untouched', () => {
    expect(parseRecoveryCode('0A' + code.slice(2))).toEqual(credentials)
    expect(parseRecoveryCode(code)!.password).toBe(credentials.password)
  })

  it('rejects anything that is not a whole code', () => {
    expect(parseRecoveryCode('')).toBeNull()
    expect(parseRecoveryCode('hello')).toBeNull()
    expect(parseRecoveryCode(code.slice(0, -1))).toBeNull()
    expect(parseRecoveryCode(code + 'x')).toBeNull()
    expect(parseRecoveryCode(code.slice(1))).toBeNull()
    expect(parseRecoveryCode(code.replace('.', ''))).toBeNull()
    expect(parseRecoveryCode(code.slice(0, 40) + '+' + code.slice(41))).toBeNull()
  })
})

describe('reminder and tab helpers', () => {
  it('nudges until the code is saved or while a snooze has passed', () => {
    expect(shouldNudge(null, '2026-10-19')).toBe(true)
    expect(shouldNudge('saved', '2026-10-19')).toBe(false)
    expect(shouldNudge('snooze:2026-11-02', '2026-10-19')).toBe(false)
    expect(shouldNudge('snooze:2026-10-19', '2026-10-19')).toBe(true)
    expect(addDays('2026-10-19', 14)).toBe('2026-11-02')
    expect(addDays('2026-12-25', 14)).toBe('2027-01-08')
  })

  it('reloads other tabs only when the account itself changes', () => {
    expect(isOtherAccount('a@x', JSON.stringify({ email: 'a@x', password: 'new' }))).toBe(false)
    expect(isOtherAccount('a@x', JSON.stringify({ email: 'b@x', password: 'p' }))).toBe(true)
    expect(isOtherAccount('a@x', null)).toBe(true)
  })
})
