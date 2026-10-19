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

describe('passkey session', () => {
  const EMAIL = 'device-0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d@liftlog.device'
  const buffer = (...values: number[]) => new Uint8Array(values).buffer
  const REQUEST = { ceremonyId: 'ceremony-1', publicKey: { challenge: 'BAUG', rpId: 'localhost', userVerification: 'required' } }
  const CREATION = {
    ceremonyId: 'ceremony-2',
    publicKey: {
      rp: { id: 'localhost', name: 'LiftLog' },
      user: { id: 'AQID', name: EMAIL, displayName: 'LiftLog' },
      challenge: 'BAUG',
      pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
    },
  }
  const assertion = {
    id: 'Bwg',
    rawId: buffer(7, 8),
    type: 'public-key',
    response: { clientDataJSON: buffer(1), authenticatorData: buffer(2), signature: buffer(3), userHandle: buffer(4) },
    getClientExtensionResults: () => ({}),
  }
  const attestation = {
    id: 'Bwg',
    rawId: buffer(7, 8),
    type: 'public-key',
    response: { clientDataJSON: buffer(1), attestationObject: buffer(5), getTransports: () => ['internal'] },
    getClientExtensionResults: () => ({}),
  }
  const call = (mock: ReturnType<typeof vi.fn>, i: number) => mock.mock.calls[i] as unknown as [string, RequestInit]
  const urls = (mock: ReturnType<typeof vi.fn>) => mock.mock.calls.map((c) => String((c as unknown[])[0]))
  const count = (mock: ReturnType<typeof vi.fn>, suffix: string) => urls(mock).filter((u) => u.endsWith(suffix)).length
  const LOGIN_OPTIONS = '/auth/passkey/options'
  const CREATE_OPTIONS = '/profile/passkeys/options'
  /** Lets fetch promises and their .then chains run. */
  const settle = () => new Promise((r) => setTimeout(r, 0))

  // Every watch a test starts is stopped, so no refresh timer or listener outlives it
  const unwatchers: (() => void)[] = []

  function stubCredentials(credentials: { create?: unknown; get?: unknown }) {
    Object.defineProperty(navigator, 'credentials', { value: credentials, configurable: true })
  }

  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.resetModules()
  })
  afterEach(() => {
    unwatchers.splice(0).forEach((unwatch) => unwatch())
    vi.useRealTimers()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    Reflect.deleteProperty(navigator, 'credentials')
    Reflect.deleteProperty(document, 'visibilityState')
  })

  describe('coming back with Face ID', () => {
    it('stores the account with a fresh device secret, marked as not being the recovery code', async () => {
      const get = vi.fn(async () => assertion)
      stubCredentials({ get })
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(200, REQUEST))
        .mockResolvedValueOnce(jsonResponse(200, { ...AUTH, user: { id: 1, email: EMAIL, displayName: 'Behlul' } }))
      vi.stubGlobal('fetch', fetchMock)
      const { restoreWithPasskey, hasPasskeyHere, restoredWithPasskey, currentRecoveryCode, RESTORED_KEY } = await import('./session')

      await expect(restoreWithPasskey()).resolves.toBe('done')

      const options = (get.mock.calls[0] as unknown as [CredentialRequestOptions])[0]
      expect(options.mediation).toBe('optional')
      expect(Array.from(new Uint8Array(options.publicKey!.challenge as ArrayBuffer))).toEqual([4, 5, 6])
      const [url, init] = call(fetchMock, 1)
      expect(url).toMatch(/\/api\/v1\/auth\/passkey$/)
      expect((init.headers as Record<string, string>).Authorization).toBeUndefined()
      const body = JSON.parse(String(init.body))
      expect(body.ceremonyId).toBe('ceremony-1')
      expect(body.credential).toEqual({
        id: 'Bwg',
        rawId: 'Bwg',
        type: 'public-key',
        response: { clientDataJSON: 'AQ', authenticatorData: 'Ag', signature: 'Aw', userHandle: 'BA' },
        clientExtensionResults: {},
      })
      expect(body.deviceSecret).toMatch(/^[A-Za-z0-9_-]{43}$/)
      expect(JSON.parse(localStorage.getItem('liftlog.device')!)).toEqual({ email: EMAIL, password: body.deviceSecret, via: 'passkey' })
      expect(localStorage.getItem('liftlog.token')).toBe('jwt-token')
      expect(sessionStorage.getItem(RESTORED_KEY)).toBe('Behlul')
      expect(hasPasskeyHere()).toBe(true)
      expect(restoredWithPasskey()).toBe(true)
      // The device secret must never be offered as the recovery code
      expect(currentRecoveryCode()).toBeNull()
    })

    it('closing the Face ID sheet over prefetched options is a quiet cancel that stores nothing', async () => {
      stubCredentials({ get: vi.fn().mockRejectedValue(new DOMException('closed', 'NotAllowedError')) })
      const fetchMock = vi.fn(async () => jsonResponse(200, REQUEST))
      vi.stubGlobal('fetch', fetchMock)
      const { restoreWithPasskey, watchPasskeyRequest } = await import('./session')
      unwatchers.push(watchPasskeyRequest())
      await settle()

      await expect(restoreWithPasskey()).resolves.toBe('cancelled')
      // Only options requests: the prefetched one, then a fresh one in the background for the next tap
      await vi.waitFor(() => expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2))
      expect(fetchMock).toHaveBeenCalledTimes(2)
      expect(localStorage.getItem('liftlog.device')).toBeNull()
    })

    it('a refusal after fetching options on the tap asks to tap again; an abort stays quiet', async () => {
      const get = vi
        .fn()
        .mockRejectedValueOnce(new DOMException('no activation', 'NotAllowedError'))
        .mockRejectedValueOnce(new DOMException('aborted', 'AbortError'))
      stubCredentials({ get })
      vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(200, REQUEST)))
      const { restoreWithPasskey } = await import('./session')

      await expect(restoreWithPasskey()).resolves.toBe('tapAgain')
      await settle()
      // Nobody watches here, but the refetch after the refusal is still held for the next tap... which
      // is an abort, and that is quiet whatever the source
      await expect(restoreWithPasskey()).resolves.toBe('cancelled')
    })

    it('an unknown passkey rejects as unauthorized and keeps the old account', async () => {
      localStorage.setItem('liftlog.device', JSON.stringify({ email: EMAIL, password: 'old' }))
      stubCredentials({ get: vi.fn(async () => assertion) })
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(jsonResponse(200, REQUEST)).mockResolvedValue(jsonResponse(401, { status: 401 })))
      const { restoreWithPasskey } = await import('./session')

      await expect(restoreWithPasskey()).rejects.toMatchObject({ kind: 'unauthorized' })
      expect(JSON.parse(localStorage.getItem('liftlog.device')!)).toEqual({ email: EMAIL, password: 'old' })
      expect(localStorage.getItem('liftlog.token')).toBeNull()
    })

    it('a rate-limited server rejects with its own kind', async () => {
      stubCredentials({ get: vi.fn(async () => assertion) })
      vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(429, { status: 429 })))
      const { restoreWithPasskey } = await import('./session')

      await expect(restoreWithPasskey()).rejects.toMatchObject({ kind: 'rateLimited' })
    })
  })

  describe('backing up with Face ID', () => {
    beforeEach(() => {
      localStorage.setItem('liftlog.device', JSON.stringify({ email: EMAIL, password: 'p' }))
      localStorage.setItem('liftlog.token', 'jwt')
    })

    it('registers the answer and remembers it for this account', async () => {
      const create = vi.fn(async () => attestation)
      stubCredentials({ create })
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(200, CREATION))
        .mockResolvedValueOnce(jsonResponse(201, { id: 1, createdAt: '2026-10-19T10:00:00Z' }))
      vi.stubGlobal('fetch', fetchMock)
      const { createPasskey, hasPasskeyHere } = await import('./session')

      expect(hasPasskeyHere()).toBe(false)
      await expect(createPasskey()).resolves.toBe('done')

      const options = (create.mock.calls[0] as unknown as [CredentialCreationOptions])[0]
      expect(Array.from(new Uint8Array(options.publicKey!.user.id as ArrayBuffer))).toEqual([1, 2, 3])
      const [url, init] = call(fetchMock, 1)
      expect(url).toMatch(/\/api\/v1\/profile\/passkeys$/)
      expect((init.headers as Record<string, string>).Authorization).toBe('Bearer jwt')
      expect(JSON.parse(String(init.body))).toEqual({
        ceremonyId: 'ceremony-2',
        credential: {
          id: 'Bwg',
          rawId: 'Bwg',
          type: 'public-key',
          response: { clientDataJSON: 'AQ', attestationObject: 'BQ', transports: ['internal'] },
          clientExtensionResults: {},
        },
      })
      expect(hasPasskeyHere()).toBe(true)
    })

    it('a cancelled backup sends nothing and remembers nothing', async () => {
      stubCredentials({ create: vi.fn().mockRejectedValue(new DOMException('aborted', 'AbortError')) })
      const fetchMock = vi.fn(async () => jsonResponse(200, CREATION))
      vi.stubGlobal('fetch', fetchMock)
      const { createPasskey, hasPasskeyHere } = await import('./session')

      await expect(createPasskey()).resolves.toBe('cancelled')
      await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
      expect(count(fetchMock, CREATE_OPTIONS)).toBe(2)
      expect(hasPasskeyHere()).toBe(false)
    })

    it('a passkey already in this keychain (InvalidStateError) counts as backed up without a POST', async () => {
      stubCredentials({ create: vi.fn().mockRejectedValue(new DOMException('exists', 'InvalidStateError')) })
      const fetchMock = vi.fn(async () => jsonResponse(200, CREATION))
      vi.stubGlobal('fetch', fetchMock)
      const { createPasskey, hasPasskeyHere } = await import('./session')

      await expect(createPasskey()).resolves.toBe('done')
      expect(urls(fetchMock).every((u) => u.endsWith(CREATE_OPTIONS))).toBe(true)
      expect(hasPasskeyHere()).toBe(true)
    })

    it('a lost registration answer is sent once more', async () => {
      stubCredentials({ create: vi.fn(async () => attestation) })
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(200, CREATION))
        .mockRejectedValueOnce(new TypeError('connection lost'))
        .mockResolvedValueOnce(jsonResponse(201, { id: 1, createdAt: '2026-10-19T10:00:00Z' }))
      vi.stubGlobal('fetch', fetchMock)
      const { createPasskey, hasPasskeyHere } = await import('./session')

      await expect(createPasskey()).resolves.toBe('done')
      expect(count(fetchMock, '/profile/passkeys')).toBe(2)
      expect(call(fetchMock, 2)[1].body).toBe(call(fetchMock, 1)[1].body)
      expect(hasPasskeyHere()).toBe(true)
    })

    it('never remembers a passkey the server did not take', async () => {
      stubCredentials({ create: vi.fn(async () => attestation) })
      const routes = [
        jsonResponse(400, { status: 400, message: 'Expired ceremony' }),
        // Lost twice: retried once, then given up
        new TypeError('lost'),
      ]
      for (const failure of routes) {
        vi.resetModules()
        const fetchMock = vi.fn(async (url: string) => {
          if (url.endsWith(CREATE_OPTIONS)) return jsonResponse(200, CREATION)
          if (failure instanceof Response) return failure.clone()
          throw failure
        })
        vi.stubGlobal('fetch', fetchMock)
        const { createPasskey, hasPasskeyHere } = await import('./session')

        await expect(createPasskey()).rejects.toMatchObject({ kind: failure instanceof Response ? 'validation' : 'network' })
        expect(hasPasskeyHere()).toBe(false)
      }
    })

    it('a new code clears the passkey flag and the Face ID marker', async () => {
      localStorage.setItem('liftlog.device', JSON.stringify({ email: EMAIL, password: 'device-secret', via: 'passkey' }))
      localStorage.setItem('liftlog.passkey', EMAIL)
      const fetchMock = vi.fn(async () => jsonResponse(200, AUTH))
      vi.stubGlobal('fetch', fetchMock)
      const { regenerateRecoveryCode, hasPasskeyHere, restoredWithPasskey, currentRecoveryCode } = await import('./session')
      expect(currentRecoveryCode()).toBeNull()

      const code = await regenerateRecoveryCode()

      // The device secret is accepted as the current password
      expect(JSON.parse(String(call(fetchMock, 0)[1].body)).currentPassword).toBe('device-secret')
      expect(hasPasskeyHere()).toBe(false)
      expect(restoredWithPasskey()).toBe(false)
      expect(currentRecoveryCode()).toBe(code)
      expect(JSON.parse(localStorage.getItem('liftlog.device')!)).not.toHaveProperty('via')
    })
  })

  describe('prefetched options', () => {
    /** Each options request gets its own ceremony id, so a test can tell which one a tap used. */
    function server(extra: object = {}) {
      let ceremonies = 0
      const fetchMock = vi.fn(async (url: string) => {
        if (url.endsWith(LOGIN_OPTIONS)) return jsonResponse(200, { ...REQUEST, ...extra, ceremonyId: `ceremony-${++ceremonies}` })
        return jsonResponse(200, { ...AUTH, user: { id: 1, email: EMAIL, displayName: null } })
      })
      vi.stubGlobal('fetch', fetchMock)
      return fetchMock
    }
    const usedCeremony = (mock: ReturnType<typeof vi.fn>) => {
      const post = mock.mock.calls.find((c) => String((c as unknown[])[0]).endsWith('/auth/passkey')) as unknown as [string, RequestInit]
      return JSON.parse(String(post[1].body)).ceremonyId
    }

    it('a tap uses the prefetched options and calls Face ID without another options request', async () => {
      const fetchMock = server()
      const get = vi.fn(async () => assertion)
      stubCredentials({ get })
      const { watchPasskeyRequest, restoreWithPasskey } = await import('./session')

      unwatchers.push(watchPasskeyRequest(), watchPasskeyRequest()) // a second visible button does not ask twice
      await settle()
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(1)

      // Face ID is asked synchronously in the tap: nothing is awaited before navigator.credentials.get
      const tap = restoreWithPasskey()
      expect(get).toHaveBeenCalledTimes(1)
      await expect(tap).resolves.toBe('done')
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(1)
      expect(usedCeremony(fetchMock)).toBe('ceremony-1')
    })

    it('a tap during the prefetch waits for that request instead of sending a second one', async () => {
      let answer: (r: Response) => void = () => {}
      const fetchMock = vi.fn((url: string) =>
        url.endsWith(LOGIN_OPTIONS) && fetchMock.mock.calls.length === 1
          ? new Promise<Response>((resolve) => (answer = resolve))
          : Promise.resolve(jsonResponse(200, { ...REQUEST, ceremonyId: 'ceremony-later' })),
      )
      vi.stubGlobal('fetch', fetchMock)
      const get = vi.fn().mockRejectedValue(new DOMException('no activation', 'NotAllowedError'))
      stubCredentials({ get })
      const { watchPasskeyRequest, restoreWithPasskey } = await import('./session')
      unwatchers.push(watchPasskeyRequest())

      const tap = restoreWithPasskey()
      await settle()
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(1)
      expect(get).not.toHaveBeenCalled()

      answer(jsonResponse(200, REQUEST))
      // Still a wait on the tap, so Safari's refusal reads as "tap again", not as a cancel
      await expect(tap).resolves.toBe('tapAgain')
      expect(get).toHaveBeenCalledTimes(1)
      // The in-flight ceremony was the tap's; the next one comes from the refetch, not from it
      await vi.waitFor(() => expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2))
    })

    it('options older than 4 minutes are fetched again on the tap', async () => {
      const fetchMock = server()
      stubCredentials({ get: vi.fn(async () => assertion) })
      const now = vi.spyOn(Date, 'now').mockReturnValue(1_000_000)
      const { watchPasskeyRequest, restoreWithPasskey } = await import('./session')

      const unwatch = watchPasskeyRequest()
      await settle()
      unwatch() // no refresh timer: this test is about the tap's own check
      now.mockReturnValue(1_000_000 + 4 * 60_000)

      await expect(restoreWithPasskey()).resolves.toBe('done')
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2)
      expect(usedCeremony(fetchMock)).toBe('ceremony-2')
    })

    it('a prefetched ceremony is used once; the next tap gets the background refetch', async () => {
      const fetchMock = server()
      const get = vi.fn().mockRejectedValueOnce(new DOMException('closed', 'NotAllowedError')).mockResolvedValue(assertion)
      stubCredentials({ get })
      const { watchPasskeyRequest, restoreWithPasskey } = await import('./session')
      unwatchers.push(watchPasskeyRequest())
      await settle()

      await expect(restoreWithPasskey()).resolves.toBe('cancelled')
      await vi.waitFor(() => expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2))
      await settle()

      const tap = restoreWithPasskey()
      expect(get).toHaveBeenCalledTimes(2)
      await expect(tap).resolves.toBe('done')
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2)
      expect(usedCeremony(fetchMock)).toBe('ceremony-2')
    })

    it('refreshes held options about 4 minutes after fetching while a button is watching', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
      const fetchMock = server()
      const { watchPasskeyRequest } = await import('./session')

      const unwatch = watchPasskeyRequest()
      await vi.advanceTimersByTimeAsync(0)
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(1)

      await vi.advanceTimersByTimeAsync(4 * 60_000 - 1000)
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(1)
      await vi.advanceTimersByTimeAsync(1000)
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2)

      unwatch()
      await vi.advanceTimersByTimeAsync(20 * 60_000)
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2)
    })

    it("uses the server's expiry minus a minute when it comes sooner", async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
      vi.setSystemTime(new Date('2026-10-19T10:00:00Z'))
      const fetchMock = server({ expiresAt: '2026-10-19T10:01:30Z' })
      const { watchPasskeyRequest } = await import('./session')

      unwatchers.push(watchPasskeyRequest())
      await vi.advanceTimersByTimeAsync(0)
      await vi.advanceTimersByTimeAsync(29_000)
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(1)
      await vi.advanceTimersByTimeAsync(1000)
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2)
    })

    it('refreshes stale options when the app comes back to the foreground', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
      const fetchMock = server()
      const { watchPasskeyRequest } = await import('./session')
      unwatchers.push(watchPasskeyRequest())
      await vi.advanceTimersByTimeAsync(0)

      // iOS froze the page: the clock moved on, the refresh timer never ran
      vi.setSystemTime(Date.now() + 10 * 60_000)
      Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
      document.dispatchEvent(new Event('visibilitychange'))
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(1)

      Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
      document.dispatchEvent(new Event('visibilitychange'))
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2)
      await vi.advanceTimersByTimeAsync(0)

      // Fresh again, so another return (pageshow from the back-forward cache) asks for nothing
      window.dispatchEvent(new Event('pageshow'))
      expect(count(fetchMock, LOGIN_OPTIONS)).toBe(2)
    })
  })

  it("another account's passkey flag does not count for this one", async () => {
    localStorage.setItem('liftlog.device', JSON.stringify({ email: EMAIL, password: 'p' }))
    localStorage.setItem('liftlog.passkey', 'device-other@liftlog.device')
    const { hasPasskeyHere, rememberPasskey } = await import('./session')

    expect(hasPasskeyHere()).toBe(false)
    rememberPasskey(true)
    expect(hasPasskeyHere()).toBe(true)
    rememberPasskey(false)
    expect(hasPasskeyHere()).toBe(false)
  })
})
