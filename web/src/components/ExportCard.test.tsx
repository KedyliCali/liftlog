import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { configureAuth } from '../api/client'
import { t } from '../lib/i18n'
import { saveFile } from '../lib/download'
import { ToastContext } from './contexts'
import { ExportCard } from './ExportCard'

vi.mock('../lib/download', () => ({ saveFile: vi.fn() }))
const saveFileMock = vi.mocked(saveFile)

function fileResponse(body: string, filename: string | null) {
  const headers: Record<string, string> = { 'Content-Type': 'text/csv;charset=UTF-8' }
  if (filename) headers['Content-Disposition'] = `attachment; filename="${filename}"`
  return new Response(body, { status: 200, headers })
}

function renderCard() {
  const toast = vi.fn()
  render(
    <ToastContext.Provider value={toast}>
      <ExportCard />
    </ToastContext.Provider>,
  )
  return { toast, user: userEvent.setup() }
}

describe('ExportCard', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    configureAuth({ getToken: () => 'my-token', onUnauthorized: () => {} })
    fetchMock = vi.fn(async (url: string) => fileResponse('﻿Tarih\r\n', `liftlog-${url.split('/').pop()}`))
    vi.stubGlobal('fetch', fetchMock)
    saveFileMock.mockReset()
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    configureAuth({ getToken: () => null, onUnauthorized: () => {} })
  })

  it('offers the three downloads', () => {
    renderCard()
    expect(screen.getByRole('heading', { name: t.exportTitle })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: t.exportLabel(t.exportWorkouts, 'CSV') })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: t.exportLabel(t.exportNutrition, 'CSV') })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: t.exportLabel(t.exportAll, 'ZIP') })).toBeInTheDocument()
  })

  it('fetches the ZIP with the token and saves it under the server name', async () => {
    saveFileMock.mockResolvedValue('downloaded')
    const { toast, user } = renderCard()

    await user.click(screen.getByTestId('export-all'))

    await waitFor(() => expect(saveFileMock).toHaveBeenCalledOnce())
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toMatch(/\/api\/v1\/export\/all\.zip$/)
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer my-token')
    expect(saveFileMock.mock.calls[0][1]).toBe('liftlog-all.zip')
    expect(toast).toHaveBeenCalledWith({ message: t.exportStarted })
  })

  it('names the file itself when the server sends no name', async () => {
    fetchMock.mockImplementation(async () => fileResponse('x', null))
    saveFileMock.mockResolvedValue('shared')
    const { toast, user } = renderCard()

    await user.click(screen.getByTestId('export-nutrition'))

    await waitFor(() => expect(saveFileMock).toHaveBeenCalledOnce())
    expect(saveFileMock.mock.calls[0][1]).toMatch(/^liftlog-beslenme-\d{4}-\d{2}-\d{2}\.csv$/)
    // The share sheet is its own feedback
    expect(toast).not.toHaveBeenCalled()
  })

  it('shows a save button when Safari wants a fresh tap, and that tap saves the same file', async () => {
    saveFileMock.mockResolvedValueOnce('needsTap').mockResolvedValueOnce('shared')
    const { user } = renderCard()

    await user.click(screen.getByTestId('export-workouts'))
    const saveAgain = await screen.findByTestId('export-save-ready')
    expect(screen.getByRole('status')).toHaveTextContent(t.exportReady('liftlog-workouts.csv'))

    await user.click(saveAgain)

    await waitFor(() => expect(screen.queryByTestId('export-save-ready')).not.toBeInTheDocument())
    expect(saveFileMock).toHaveBeenCalledTimes(2)
    expect(saveFileMock.mock.calls[1][0]).toBe(saveFileMock.mock.calls[0][0])
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('explains a failed download', async () => {
    fetchMock.mockImplementation(async () => new Response(JSON.stringify({ status: 500 }), { status: 500 }))
    const { toast, user } = renderCard()

    await user.click(screen.getByTestId('export-workouts'))

    await waitFor(() => expect(toast).toHaveBeenCalledWith({ message: t.errorServer }))
    expect(saveFileMock).not.toHaveBeenCalled()
    expect(screen.getByTestId('export-workouts')).toBeEnabled()
  })
})
