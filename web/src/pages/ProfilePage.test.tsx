import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/Confirm'
import { ToastContext } from '../components/contexts'
import { t } from '../lib/i18n'
import { fakeServer, jsonResponse } from '../test/fakeServer'
import { ProfilePage } from './ProfilePage'

const withPhoto = { id: 1, displayName: 'Behlul', heightCm: 180, bodyWeightKg: 80, avatarVersion: 3 }

function renderProfile() {
  // Only the photo matters here; the other cards on the page may fail on routes this server leaves out
  const calls = fakeServer({
    'GET /api/v1/profile': () => jsonResponse(200, withPhoto),
    'GET /api/v1/profile/avatar': () => new Response(new Blob(['jpeg'], { type: 'image/jpeg' }), { status: 200 }),
    'DELETE /api/v1/profile/avatar': () => new Response(null, { status: 204 }),
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={() => {}}>
        <ConfirmProvider>
          <MemoryRouter>
            <ProfilePage />
          </MemoryRouter>
        </ConfirmProvider>
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
  return { calls, user: userEvent.setup() }
}

beforeEach(() => {
  // jsdom has no object URLs (the photo), media queries or layout observer (the weight chart)
  URL.createObjectURL = () => 'blob:photo'
  URL.revokeObjectURL = () => {}
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener() {}, removeEventListener() {} }))
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  Reflect.deleteProperty(URL, 'createObjectURL')
  Reflect.deleteProperty(URL, 'revokeObjectURL')
})

describe('remove photo', () => {
  it('asks first: Cancel keeps the photo, the confirm deletes it', async () => {
    const { calls, user } = renderProfile()
    const deletes = () => calls.filter((c) => c.method === 'DELETE')

    await user.click(await screen.findByRole('button', { name: t.removePhoto }))
    const dialog = screen.getByRole('alertdialog', { name: t.removePhotoTitle })
    await user.click(within(dialog).getByRole('button', { name: t.cancel }))
    expect(deletes()).toEqual([])
    expect(screen.getByRole('button', { name: t.removePhoto })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: t.removePhoto }))
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: t.removePhotoAction }))
    await waitFor(() => expect(deletes()).toEqual([{ method: 'DELETE', path: '/api/v1/profile/avatar', body: undefined }]))
    await waitFor(() => expect(screen.queryByRole('button', { name: t.removePhoto })).toBeNull())
  })
})
