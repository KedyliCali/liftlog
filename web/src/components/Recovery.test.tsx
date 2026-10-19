import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { t } from '../lib/i18n'

const MINE = { email: 'device-11111111-1111-4111-8111-111111111111@liftlog.device', password: 'Mine-0123456789abcdefghijklmnopqrstuvwxyzAB' }
const OTHER_CODE = '22222222222242228222222222222222.Other-123456789abcdefghijklmnopqrstuvwxyzAB'
const MINE_CODE = '11111111111141118111111111111111.Mine-0123456789abcdefghijklmnopqrstuvwxyzAB'
const AUTH = { token: 'other-token', expiresAt: '2026-11-18T00:00:00Z', user: { id: 2, email: 'x', displayName: 'Ali' } }

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function renderForm(needsConfirm: boolean) {
  const { RestoreForm } = await import('./Recovery')
  const onRestored = vi.fn()
  render(<RestoreForm needsConfirm={needsConfirm} onRestored={onRestored} />)
  return { onRestored, user: userEvent.setup() }
}

describe('RestoreForm', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    localStorage.setItem('liftlog.device', JSON.stringify(MINE))
    vi.resetModules()
  })
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('rejects text that is not a code without asking the server', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const { user } = await renderForm(false)

    await user.type(screen.getByLabelText(t.restorePlaceholder), 'hello')
    await user.click(screen.getByTestId('restore-submit'))

    expect(screen.getByText(t.restoreInvalid)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("notices this device's own code without asking the server", async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const { user } = await renderForm(true)

    await user.type(screen.getByLabelText(t.restorePlaceholder), MINE_CODE)
    await user.click(screen.getByTestId('restore-submit'))

    expect(screen.getByText(t.restoreSameAccount)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('explains an unknown code and changes nothing', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(401, { status: 401 })))
    const { user, onRestored } = await renderForm(false)

    await user.type(screen.getByLabelText(t.restorePlaceholder), OTHER_CODE)
    await user.click(screen.getByTestId('restore-submit'))

    expect(await screen.findByText(t.restoreNotRecognized)).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem('liftlog.device')!)).toEqual(MINE)
    expect(onRestored).not.toHaveBeenCalled()
  })

  it('names the target log first when this device already has one', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(200, AUTH)))
    const { user, onRestored } = await renderForm(true)

    await user.type(screen.getByLabelText(t.restorePlaceholder), OTHER_CODE)
    await user.click(screen.getByTestId('restore-submit'))

    expect(await screen.findByText(t.restoreConfirmTitle('Ali'))).toBeInTheDocument()
    await user.click(screen.getByText(t.cancel))
    expect(JSON.parse(localStorage.getItem('liftlog.device')!)).toEqual(MINE)
    expect(onRestored).not.toHaveBeenCalled()

    await user.click(screen.getByTestId('restore-submit'))
    await user.click(await screen.findByTestId('switch-log'))
    expect(JSON.parse(localStorage.getItem('liftlog.device')!).email).toBe('device-22222222-2222-4222-8222-222222222222@liftlog.device')
    expect(localStorage.getItem('liftlog.token')).toBe('other-token')
    expect(onRestored).toHaveBeenCalledTimes(1)
  })

  it('switches at once when nothing on this device can be lost', async () => {
    localStorage.clear()
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(200, AUTH)))
    const { user, onRestored } = await renderForm(false)

    await user.type(screen.getByLabelText(t.restorePlaceholder), `LiftLog kurtarma kodu:\n${OTHER_CODE}`)
    await user.click(screen.getByTestId('restore-submit'))

    await vi.waitFor(() => expect(onRestored).toHaveBeenCalledTimes(1))
    expect(localStorage.getItem('liftlog.token')).toBe('other-token')
  })
})

describe('RestoreForm on a signed-out device', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
  })
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it("accepts this account's newer code: same account, different password", async () => {
    localStorage.setItem('liftlog.device', JSON.stringify(MINE))
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(200, AUTH)))
    const { user, onRestored } = await renderForm(false)
    const newer = MINE_CODE.split('.')[0] + '.Newer-23456789abcdefghijklmnopqrstuvwxyzABC'

    await user.type(screen.getByLabelText(t.restorePlaceholder), newer)
    await user.click(screen.getByTestId('restore-submit'))

    await vi.waitFor(() => expect(onRestored).toHaveBeenCalledTimes(1))
    expect(JSON.parse(localStorage.getItem('liftlog.device')!)).toEqual({ email: MINE.email, password: 'Newer-23456789abcdefghijklmnopqrstuvwxyzABC' })
  })
})

describe('SignedOutScreen', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
  })
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it("calls the device's own stale code unrecognized rather than 'already on this device'", async () => {
    localStorage.setItem('liftlog.device', JSON.stringify(MINE))
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(401, { status: 401 })))
    const { SignedOutScreen } = await import('./Recovery')
    render(<SignedOutScreen onRestored={vi.fn()} onStartFresh={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(t.restorePlaceholder), MINE_CODE)
    await user.click(screen.getByTestId('restore-submit'))

    expect(await screen.findByText(t.restoreNotRecognized)).toBeInTheDocument()
    expect(screen.queryByText(t.restoreSameAccount)).not.toBeInTheDocument()
  })
})
