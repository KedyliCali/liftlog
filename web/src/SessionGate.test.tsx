import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { t } from './lib/i18n'

const AUTH = { token: 'jwt-token', expiresAt: '2026-11-18T00:00:00Z', user: { id: 1, email: 'x', displayName: null } }
const STORED = { email: 'device-11111111-1111-4111-8111-111111111111@liftlog.device', password: 'Mine-0123456789abcdefghijklmnopqrstuvwxyzAB' }

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

let onRestored = vi.fn()

async function renderGate() {
  const { SessionGate } = await import('./SessionGate')
  onRestored = vi.fn()
  render(
    <SessionGate onRestored={onRestored}>
      <p>app content</p>
    </SessionGate>,
  )
  return userEvent.setup()
}

describe('SessionGate', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
  })
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('welcomes a device that never had an account and only wakes the server', async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await renderGate()

    expect(screen.getByText(t.haveRecoveryCode)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toMatch(/\/api\/v1\/health$/)
  })

  it('creates the account when the user taps Start', async () => {
    const fetchMock = vi.fn(async (url: string) => (String(url).endsWith('/health') ? new Response(null) : jsonResponse(201, AUTH)))
    vi.stubGlobal('fetch', fetchMock)
    const user = await renderGate()

    await user.click(screen.getByTestId('welcome-start'))

    expect(await screen.findByText('app content')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => String(c[0]).endsWith('/auth/register'))).toBe(true)
  })

  it('signs a known device in without the welcome screen', async () => {
    localStorage.setItem('liftlog.device', JSON.stringify(STORED))
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(200, AUTH)))
    await renderGate()

    expect(await screen.findByText('app content')).toBeInTheDocument()
    expect(screen.queryByText(t.haveRecoveryCode)).not.toBeInTheDocument()
  })

  it('shows the signed-out screen when the code was replaced elsewhere, and can start fresh', async () => {
    localStorage.setItem('liftlog.device', JSON.stringify(STORED))
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(401, { status: 401 }))
        .mockResolvedValueOnce(jsonResponse(409, { status: 409 }))
        .mockResolvedValueOnce(jsonResponse(201, AUTH)),
    )
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = await renderGate()

    expect(await screen.findByText(t.signedOutTitle)).toBeInTheDocument()
    await user.click(screen.getByText(t.startFresh))

    // A full reload follows, so no cached data of the old account can show in the new log
    await vi.waitFor(() => expect(onRestored).toHaveBeenCalledTimes(1))
    expect(JSON.parse(localStorage.getItem('liftlog.device')!).email).not.toBe(STORED.email)
  })
})
