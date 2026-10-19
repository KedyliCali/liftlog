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

describe('recovery code session', () => {
  const STORED = {
    email: 'device-0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d@liftlog.device',
    password: 'Ab-_0123456789abcdefghijklmnopqrstuvwxyzABC',
  }
  const store = (value: object) => localStorage.setItem('liftlog.device', JSON.stringify(value))
  const stored = () => JSON.parse(localStorage.getItem('liftlog.device')!)
  const call = (mock: ReturnType<typeof vi.fn>, i: number) => mock.mock.calls[i] as unknown as [string, RequestInit]

  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.resetModules()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('a new code sends the current and a fresh password, then keeps the new one', async () => {
    store(STORED)
    localStorage.setItem('liftlog.token', 'old')
    const fetchMock = vi.fn(async () => jsonResponse(200, AUTH))
    vi.stubGlobal('fetch', fetchMock)
    const { regenerateRecoveryCode, session } = await import('./session')

    const code = await regenerateRecoveryCode()

    const [url, init] = call(fetchMock, 0)
    expect(url).toMatch(/\/api\/v1\/profile\/password$/)
    expect(init.method).toBe('PUT')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer old')
    const body = JSON.parse(String(init.body))
    expect(body.currentPassword).toBe(STORED.password)
    expect(body.newPassword).toMatch(/^[A-Za-z0-9_-]{43}$/)
    expect(stored()).toEqual({ email: STORED.email, password: body.newPassword })
    expect(session.getToken()).toBe('jwt-token')
    expect(code.endsWith('.' + body.newPassword)).toBe(true)
  })

  it('a request that never reached the server keeps the new password pending and reuses it on retry', async () => {
    store(STORED)
    localStorage.setItem('liftlog.token', 'old')
    const fetchMock = vi.fn().mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(jsonResponse(200, AUTH))
    vi.stubGlobal('fetch', fetchMock)
    const { regenerateRecoveryCode, hasPendingPassword } = await import('./session')

    await expect(regenerateRecoveryCode()).rejects.toMatchObject({ kind: 'network' })
    expect(stored().password).toBe(STORED.password)
    expect(hasPendingPassword()).toBe(true)

    await regenerateRecoveryCode()
    const first = JSON.parse(String(call(fetchMock, 0)[1].body)).newPassword
    const second = JSON.parse(String(call(fetchMock, 1)[1].body)).newPassword
    expect(second).toBe(first)
    expect(hasPendingPassword()).toBe(false)
  })

  it('signing in falls back to a pending password the server already accepted', async () => {
    store({ ...STORED, pendingPassword: 'pending-password-that-the-server-took-0000' })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse(200, AUTH))
    vi.stubGlobal('fetch', fetchMock)
    const { signInDevice, session } = await import('./session')

    await signInDevice()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(call(fetchMock, 1)[0]).toMatch(/login$/)
    expect(stored()).toEqual({ email: STORED.email, password: 'pending-password-that-the-server-took-0000' })
    expect(session.getToken()).toBe('jwt-token')
  })

  it('a device whose code was replaced elsewhere is told it was signed out', async () => {
    store(STORED)
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(401, { status: 401 }))
        .mockResolvedValueOnce(jsonResponse(409, { status: 409, message: 'exists' })),
    )
    const { signInDevice } = await import('./session')

    await expect(signInDevice()).rejects.toMatchObject({ kind: 'signedOut' })
    expect(stored()).toEqual(STORED)
  })

  it('checking a code writes nothing and never registers', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(401, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const { verifyRecoveryCode, session } = await import('./session')

    await expect(verifyRecoveryCode(STORED)).rejects.toMatchObject({ kind: 'unauthorized' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('liftlog.device')).toBeNull()
    expect(session.getToken()).toBeNull()
  })

  it('committing a restore stores the account without re-rendering the old one', async () => {
    const { commitRestore, session, RESTORED_KEY } = await import('./session')
    const listener = vi.fn()
    session.subscribe(listener)

    commitRestore(STORED, { ...AUTH, user: { ...AUTH.user, displayName: 'Behlul' } })

    expect(listener).not.toHaveBeenCalled()
    expect(stored()).toEqual(STORED)
    expect(localStorage.getItem('liftlog.token')).toBe('jwt-token')
    expect(sessionStorage.getItem(RESTORED_KEY)).toBe('Behlul')
  })

  it('a late 401 for a replaced token does not sign the new token out', async () => {
    store(STORED)
    localStorage.setItem('liftlog.token', 'old')
    let answerLate: (r: Response) => void = () => {}
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() => new Promise<Response>((resolve) => (answerLate = resolve)))
      .mockResolvedValueOnce(jsonResponse(200, AUTH))
    vi.stubGlobal('fetch', fetchMock)
    const { regenerateRecoveryCode, session } = await import('./session')
    const { api } = await import('./endpoints')

    const slow = api.workouts(0)
    await regenerateRecoveryCode()
    answerLate(jsonResponse(401, { status: 401 }))

    await expect(slow).rejects.toMatchObject({ kind: 'unauthorized' })
    expect(session.getToken()).toBe('jwt-token')
  })

  it('starting fresh creates a brand-new account', async () => {
    store(STORED)
    const fetchMock = vi.fn(async () => jsonResponse(201, AUTH))
    vi.stubGlobal('fetch', fetchMock)
    const { startFresh, hasDeviceCredentials } = await import('./session')

    await startFresh()

    expect(call(fetchMock, 0)[0]).toMatch(/register$/)
    expect(stored().email).not.toBe(STORED.email)
    expect(hasDeviceCredentials()).toBe(true)
  })
})

describe('review fixes', () => {
  const P0 = 'Ab-_0123456789abcdefghijklmnopqrstuvwxyzABC'
  const EMAIL = 'device-0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d@liftlog.device'

  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.resetModules()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('a new code that reached the server though its answer was lost is adopted and announced', async () => {
    localStorage.setItem('liftlog.device', JSON.stringify({ email: EMAIL, password: P0 }))
    localStorage.setItem('liftlog.token', 'old')
    localStorage.setItem('liftlog.recoveryNudge', 'saved')
    // The server: the first PUT commits the new password but the answer never arrives
    let serverPassword = P0
    let revoked = false
    const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
      const body = init.body ? JSON.parse(String(init.body)) : {}
      if (url.endsWith('/profile/password')) {
        if (revoked) return jsonResponse(401, { status: 401 })
        serverPassword = body.newPassword
        revoked = true
        throw new TypeError('connection lost')
      }
      if (url.endsWith('/auth/login')) return body.password === serverPassword ? jsonResponse(200, AUTH) : jsonResponse(401, { status: 401 })
      return jsonResponse(500, {})
    })
    vi.stubGlobal('fetch', fetchMock)
    const { regenerateRecoveryCode, signInDevice, session, CODE_CHANGED_KEY } = await import('./session')

    await expect(regenerateRecoveryCode()).rejects.toMatchObject({ kind: 'network' })
    // Following the card's hint and tapping New code again meets the revoked token
    await expect(regenerateRecoveryCode()).rejects.toMatchObject({ kind: 'unauthorized' })
    expect(session.getToken()).toBeNull()

    await signInDevice()

    expect(JSON.parse(localStorage.getItem('liftlog.device')!)).toEqual({ email: EMAIL, password: serverPassword })
    expect(localStorage.getItem('liftlog.recoveryNudge')).toBeNull()
    expect(sessionStorage.getItem(CODE_CHANGED_KEY)).toBe('1')
    expect(session.getToken()).toBe('jwt-token')
  })

  it('notices another tab switching accounts although storage already holds the new value', async () => {
    localStorage.setItem('liftlog.device', JSON.stringify({ email: EMAIL, password: P0 }))
    const { isAccountChangeElsewhere } = await import('./session')
    const other = JSON.stringify({ email: 'device-ffffffff-ffff-4fff-8fff-ffffffffffff@liftlog.device', password: P0 })
    // What the browser does before firing the event in this tab
    localStorage.setItem('liftlog.device', other)

    expect(isAccountChangeElsewhere('liftlog.device', other)).toBe(true)
    expect(isAccountChangeElsewhere('liftlog.device', null)).toBe(true)
    expect(isAccountChangeElsewhere('liftlog.device', JSON.stringify({ email: EMAIL, password: 'rotated' }))).toBe(false)
    expect(isAccountChangeElsewhere('liftlog.token', other)).toBe(false)
  })
})
