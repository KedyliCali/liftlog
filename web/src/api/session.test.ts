import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const AUTH = { token: 'jwt-token', expiresAt: '2026-11-16T00:00:00Z', user: { id: 1, email: 'x', displayName: null } }

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('device session', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules() // session.ts reads localStorage when it is imported
  })
  afterEach(() => vi.unstubAllGlobals())

  it('generates a strong random password and a unique email', async () => {
    const { generateCredentials } = await import('./session')
    const a = generateCredentials()
    const b = generateCredentials()
    expect(a.password).toMatch(/^[A-Za-z0-9_-]{43}$/)
    expect(a.email).toMatch(/^device-[0-9a-f-]{36}@liftlog\.device$/)
    expect(a.email).not.toBe(b.email)
  })

  it('registers on first visit and logs in afterwards with the same account', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(201, AUTH))
    vi.stubGlobal('fetch', fetchMock)
    let module = await import('./session')
    await module.signInDevice()
    expect(module.session.getToken()).toBe('jwt-token')
    const firstCall = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(firstCall[0]).toMatch(/\/api\/v1\/auth\/register$/)
    const registered = JSON.parse(String(firstCall[1].body))

    vi.resetModules()
    module = await import('./session')
    await module.signInDevice()
    const secondCall = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    expect(secondCall[0]).toMatch(/\/api\/v1\/auth\/login$/)
    expect(JSON.parse(String(secondCall[1].body))).toEqual(registered)
  })

  it('recreates the account when the server no longer knows it', async () => {
    localStorage.setItem('liftlog.device', JSON.stringify({ email: 'device-old@liftlog.device', password: 'p' }))
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, message: 'Invalid email or password' }))
      .mockResolvedValueOnce(jsonResponse(201, AUTH))
    vi.stubGlobal('fetch', fetchMock)
    const { signInDevice, session } = await import('./session')

    await signInDevice()

    expect((fetchMock.mock.calls[1] as [string])[0]).toMatch(/register$/)
    expect(session.getToken()).toBe('jwt-token')
  })

  it('a 401 on an authenticated request drops the token so the app signs in again', async () => {
    localStorage.setItem('liftlog.token', 'expired')
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(401, { status: 401 })))
    const { session } = await import('./session')
    const { api } = await import('./endpoints')

    await expect(api.workouts(0)).rejects.toMatchObject({ kind: 'unauthorized' })
    expect(session.getToken()).toBeNull()
  })
})
