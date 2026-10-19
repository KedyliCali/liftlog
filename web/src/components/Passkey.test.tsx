import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { t } from '../lib/i18n'
import { ToastContext, type ToastOptions } from './contexts'
import { BackupCard, PasskeyRestoreButton } from './Passkey'
import { RecoveryCodeCard } from './RecoveryCard'
import { RecoveryNudge } from './RecoveryNudge'
import { WelcomeScreen } from './Recovery'

const DEVICE = { email: 'device-11111111-1111-4111-8111-111111111111@liftlog.device', password: 'Mine-0123456789abcdefghijklmnopqrstuvwxyzAB' }
const CREATION = {
  ceremonyId: 'c1',
  publicKey: { rp: { name: 'LiftLog' }, user: { id: 'AQID', name: 'x', displayName: 'x' }, challenge: 'BAUG', pubKeyCredParams: [] },
}
const ATTESTATION = {
  id: 'Bwg',
  rawId: new Uint8Array([7, 8]).buffer,
  type: 'public-key',
  response: { clientDataJSON: new Uint8Array([1]).buffer, attestationObject: new Uint8Array([5]).buffer },
  getClientExtensionResults: () => ({}),
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** Answers "METHOD /path" from the given routes; anything else is a 404. */
function fakeServer(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn(async (url: string, init: RequestInit = {}) => {
    const key = `${init.method ?? 'GET'} ${new URL(url).pathname}`
    return routes[key]?.() ?? jsonResponse(404, { status: 404 })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function supportPasskeys(credentials: { create?: unknown; get?: unknown } = {}) {
  vi.stubGlobal('PublicKeyCredential', class {})
  Object.defineProperty(navigator, 'credentials', { value: { create: vi.fn(), get: vi.fn(), ...credentials }, configurable: true })
}

function renderWith(ui: ReactNode, toast: (options: ToastOptions) => void = () => {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={toast}>
        <MemoryRouter>{ui}</MemoryRouter>
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  localStorage.setItem('liftlog.device', JSON.stringify(DEVICE))
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  Reflect.deleteProperty(navigator, 'credentials')
  window.location.hash = ''
})

describe('Profile backup card', () => {
  it('offers Face ID backup while the account has no passkey, and switches to Backed up after it', async () => {
    let passkeys: unknown[] = []
    const create = vi.fn(async () => ATTESTATION)
    supportPasskeys({ create })
    fakeServer({
      'GET /api/v1/profile/passkeys': () => jsonResponse(200, passkeys),
      'POST /api/v1/profile/passkeys/options': () => jsonResponse(200, CREATION),
      'POST /api/v1/profile/passkeys': () => {
        passkeys = [{ id: 1, createdAt: '2026-10-19T10:00:00Z', lastUsedAt: null }]
        return jsonResponse(201, passkeys[0])
      },
    })
    const toast = vi.fn()
    const user = renderWith(<BackupCard />, toast)

    expect(screen.getByText(t.backupExplain)).toBeInTheDocument()
    await user.click(screen.getByTestId('backup-create'))

    expect(await screen.findByTestId('backup-done')).toBeInTheDocument()
    expect(create).toHaveBeenCalledTimes(1)
    expect(toast).toHaveBeenCalledWith({ message: t.backupCreated })
    expect(screen.getByText(t.backupDoneHint)).toBeInTheDocument()
    expect(localStorage.getItem('liftlog.passkey')).toBe(DEVICE.email)
  })

  it('shows the backup date from the server and corrects a stale local flag', async () => {
    supportPasskeys()
    fakeServer({
      'GET /api/v1/profile/passkeys': () => jsonResponse(200, [{ id: 1, createdAt: '2026-10-19T10:00:00Z', lastUsedAt: null }]),
    })
    renderWith(<BackupCard />)

    const date = new Date('2026-10-19T10:00:00Z').toLocaleDateString(navigator.language, { day: 'numeric', month: 'short' }).replace('.', '')
    expect(await screen.findByText(t.backupDone(date))).toBeInTheDocument()
    expect(screen.queryByTestId('backup-create')).not.toBeInTheDocument()
    // A passkey made on another device now counts on this one too
    expect(localStorage.getItem('liftlog.passkey')).toBe(DEVICE.email)
  })

  it('offers to create the passkey again on this device once backed up', async () => {
    const create = vi.fn().mockRejectedValue(new DOMException('exists', 'InvalidStateError'))
    supportPasskeys({ create })
    fakeServer({
      'GET /api/v1/profile/passkeys': () => jsonResponse(200, [{ id: 1, createdAt: '2026-10-19T10:00:00Z', lastUsedAt: null }]),
      'POST /api/v1/profile/passkeys/options': () => jsonResponse(200, CREATION),
    })
    const toast = vi.fn()
    const user = renderWith(<BackupCard />, toast)

    await user.click(await screen.findByText(t.backupCreateAgain))

    await vi.waitFor(() => expect(toast).toHaveBeenCalledWith({ message: t.backupCreated }))
    expect(create).toHaveBeenCalledTimes(1)
  })

  it('says nothing when the Face ID sheet is closed', async () => {
    supportPasskeys({ create: vi.fn().mockRejectedValue(new DOMException('closed', 'NotAllowedError')) })
    fakeServer({
      'GET /api/v1/profile/passkeys': () => jsonResponse(200, []),
      'POST /api/v1/profile/passkeys/options': () => jsonResponse(200, CREATION),
    })
    const toast = vi.fn()
    const user = renderWith(<BackupCard />, toast)

    await user.click(screen.getByTestId('backup-create'))

    await vi.waitFor(() => expect(screen.getByTestId('backup-create')).not.toBeDisabled())
    expect(toast).not.toHaveBeenCalled()
    expect(screen.getByText(t.backupExplain)).toBeInTheDocument()
  })
})

describe('Recovery code under the backup', () => {
  it('starts folded into one row and opens to the full card', async () => {
    const user = renderWith(<RecoveryCodeCard collapsible />)

    expect(screen.getByText(t.recoveryBackupOfBackup)).toBeInTheDocument()
    expect(screen.queryByTestId('recovery-code')).not.toBeInTheDocument()
    await user.click(screen.getByTestId('recovery-fold'))
    expect(screen.getByTestId('recovery-code')).toBeInTheDocument()
    expect(screen.getByText(t.newCode)).toBeInTheDocument()
  })

  it('opens at once when arriving with #recovery', () => {
    window.location.hash = '#recovery'
    // jsdom has no scrolling; the card still asks for it
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    renderWith(<RecoveryCodeCard collapsible />)

    expect(screen.getByTestId('recovery-code')).toBeInTheDocument()
    expect(scrollIntoView).toHaveBeenCalled()
    Reflect.deleteProperty(Element.prototype, 'scrollIntoView')
  })
})

describe('Recovery code on a device that came back with Face ID', () => {
  const VIA_PASSKEY = { email: DEVICE.email, password: 'Secret-0123456789abcdefghijklmnopqrstuvwxyz', via: 'passkey' }

  it('explains where the code is instead of showing the device secret, and New code makes a real one', async () => {
    localStorage.setItem('liftlog.device', JSON.stringify(VIA_PASSKEY))
    localStorage.setItem('liftlog.passkey', DEVICE.email)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const fetchMock = fakeServer({
      'PUT /api/v1/profile/password': () => jsonResponse(200, { token: 't', expiresAt: '2026-11-18T00:00:00Z', user: { id: 1, email: DEVICE.email, displayName: null } }),
    })
    const user = renderWith(<RecoveryCodeCard collapsible />)

    await user.click(screen.getByTestId('recovery-fold'))
    expect(screen.getByText(t.recoveryViaPasskey)).toBeInTheDocument()
    expect(screen.queryByTestId('recovery-code')).not.toBeInTheDocument()
    expect(screen.queryByText(t.copyCode)).not.toBeInTheDocument()
    expect(screen.queryByText(VIA_PASSKEY.password, { exact: false })).not.toBeInTheDocument()

    await user.click(screen.getByTestId('new-code'))

    expect(await screen.findByTestId('recovery-code')).toHaveTextContent(/^[0-9a-f]{32}\./)
    expect(screen.queryByText(t.recoveryViaPasskey)).not.toBeInTheDocument()
    const body = JSON.parse(String((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body))
    expect(body.currentPassword).toBe(VIA_PASSKEY.password)
    // The server dropped the passkeys with the old code: the backup is offered again
    expect(localStorage.getItem('liftlog.passkey')).toBeNull()
    vi.restoreAllMocks()
  })
})

describe('Workouts reminder banner', () => {
  it('offers the one-tap Face ID backup when there is no passkey and runs it directly', async () => {
    const create = vi.fn(async () => ATTESTATION)
    supportPasskeys({ create })
    fakeServer({
      'POST /api/v1/profile/passkeys/options': () => jsonResponse(200, CREATION),
      'POST /api/v1/profile/passkeys': () => jsonResponse(201, { id: 1, createdAt: '2026-10-19T10:00:00Z' }),
    })
    const toast = vi.fn()
    const user = renderWith(<RecoveryNudge />, toast)

    expect(screen.getByText(t.passkeyNudgeText)).toBeInTheDocument()
    await user.click(screen.getByTestId('nudge-passkey'))

    await vi.waitFor(() => expect(screen.queryByText(t.passkeyNudgeText)).not.toBeInTheDocument())
    expect(create).toHaveBeenCalledTimes(1)
    expect(toast).toHaveBeenCalledWith({ message: t.backupCreated })
  })

  it('never shows once this account has a passkey', () => {
    supportPasskeys()
    localStorage.setItem('liftlog.passkey', DEVICE.email)
    renderWith(<RecoveryNudge />)

    expect(screen.queryByRole('note')).not.toBeInTheDocument()
  })

  it('Later hides it for 30 days', async () => {
    supportPasskeys()
    const user = renderWith(<RecoveryNudge />)

    await user.click(screen.getByText(t.nudgeLater))

    expect(screen.queryByRole('note')).not.toBeInTheDocument()
    const until = localStorage.getItem('liftlog.recoveryNudge')!.slice('snooze:'.length)
    const days = Math.round((Date.parse(until) - Date.parse(new Date().toISOString().slice(0, 10))) / 86_400_000)
    expect(days).toBe(30)
  })

  it('keeps the code reminder where passkeys are not supported', () => {
    renderWith(<RecoveryNudge />)

    expect(screen.getByText(t.nudgeText)).toBeInTheDocument()
    expect(screen.getByText(t.nudgeSave)).toBeInTheDocument()
  })
})

describe('Coming back with Face ID', () => {
  it('the welcome screen offers it only where passkeys work', () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 200 })))
    renderWith(<WelcomeScreen onStart={vi.fn()} onRestored={vi.fn()} />)
    expect(screen.queryByTestId('passkey-restore')).not.toBeInTheDocument()
    cleanup()

    supportPasskeys()
    renderWith(<WelcomeScreen onStart={vi.fn()} onRestored={vi.fn()} />)
    expect(screen.getByText(t.passkeyRestore)).toBeInTheDocument()
    expect(screen.getByText(t.haveRecoveryCode)).toBeInTheDocument()
  })

  it('the welcome screen fetches the Face ID options only after its server wake-up ping', async () => {
    supportPasskeys()
    let wake: (r: Response) => void = () => {}
    const paths: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        const path = new URL(url).pathname
        paths.push(path)
        if (path === '/api/v1/health') return new Promise<Response>((resolve) => (wake = resolve))
        return Promise.resolve(jsonResponse(200, { ceremonyId: 'c', publicKey: { challenge: 'BAUG' } }))
      }),
    )
    renderWith(<WelcomeScreen onStart={vi.fn()} onRestored={vi.fn()} />)

    await new Promise((r) => setTimeout(r, 20))
    expect(paths).toEqual(['/api/v1/health'])
    wake(new Response(null, { status: 200 }))
    await vi.waitFor(() => expect(paths).toEqual(['/api/v1/health', '/api/v1/auth/passkey/options']))
  })

  it('names an unknown passkey and does not reload', async () => {
    supportPasskeys({
      get: vi.fn(async () => ({
        id: 'Bwg',
        rawId: new Uint8Array([7, 8]).buffer,
        type: 'public-key',
        response: { clientDataJSON: new Uint8Array([1]).buffer, authenticatorData: new Uint8Array([2]).buffer, signature: new Uint8Array([3]).buffer, userHandle: null },
        getClientExtensionResults: () => ({}),
      })),
    })
    fakeServer({
      'POST /api/v1/auth/passkey/options': () => jsonResponse(200, { ceremonyId: 'c', publicKey: { challenge: 'BAUG' } }),
      'POST /api/v1/auth/passkey': () => jsonResponse(401, { status: 401 }),
    })
    const onRestored = vi.fn()
    const user = renderWith(<PasskeyRestoreButton onRestored={onRestored} />)

    await user.click(screen.getByTestId('passkey-restore'))

    expect(await screen.findByText(t.passkeyNotRecognized)).toBeInTheDocument()
    expect(onRestored).not.toHaveBeenCalled()
  })

  it('asks to tap again when Safari refused after options were fetched on the tap', async () => {
    // Anything another test left prefetched is too old by now, so this tap fetches its own options
    const realNow = Date.now()
    vi.spyOn(Date, 'now').mockReturnValue(realNow + 60 * 60_000)
    supportPasskeys({ get: vi.fn().mockRejectedValue(new DOMException('no activation', 'NotAllowedError')) })
    fakeServer({ 'POST /api/v1/auth/passkey/options': () => jsonResponse(200, { ceremonyId: 'c', publicKey: { challenge: 'BAUG' } }) })
    const onRestored = vi.fn()
    const user = renderWith(<PasskeyRestoreButton onRestored={onRestored} prefetch={false} />)

    await user.click(screen.getByTestId('passkey-restore'))

    expect(await screen.findByText(t.passkeyTapAgain)).toBeInTheDocument()
    expect(onRestored).not.toHaveBeenCalled()
    vi.restoreAllMocks()
  })

  it('says when the server is rate-limiting', async () => {
    supportPasskeys({ get: vi.fn() })
    fakeServer({ 'POST /api/v1/auth/passkey/options': () => jsonResponse(429, { status: 429 }) })
    const realNow = Date.now()
    vi.spyOn(Date, 'now').mockReturnValue(realNow + 2 * 60 * 60_000)
    const user = renderWith(<PasskeyRestoreButton onRestored={vi.fn()} prefetch={false} />)

    await user.click(screen.getByTestId('passkey-restore'))

    expect(await screen.findByText(t.errorRateLimited)).toBeInTheDocument()
    vi.restoreAllMocks()
  })

  it('a cancel stays quiet', async () => {
    supportPasskeys({ get: vi.fn().mockRejectedValue(new DOMException('closed', 'NotAllowedError')) })
    fakeServer({ 'POST /api/v1/auth/passkey/options': () => jsonResponse(200, { ceremonyId: 'c', publicKey: { challenge: 'BAUG' } }) })
    const onRestored = vi.fn()
    const user = renderWith(<PasskeyRestoreButton onRestored={onRestored} />)

    await user.click(screen.getByTestId('passkey-restore'))

    await vi.waitFor(() => expect(screen.getByTestId('passkey-restore')).not.toBeDisabled())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(onRestored).not.toHaveBeenCalled()
  })
})
