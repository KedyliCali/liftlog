import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/Confirm'
import { ToastContext } from '../components/contexts'
import { t } from '../lib/i18n'
import { fakeServer, jsonResponse } from '../test/fakeServer'
import { PREFETCH_ORDER, prefetchRoutePages } from './pageLoaders'
import { pageRoutes } from './pageRoutes'

function renderRoute(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(pageRoutes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={() => {}}>
        <ConfirmProvider>
          <RouterProvider router={router} />
        </ConfirmProvider>
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('lazy page routes', () => {
  it('a lazy page shows its header and skeleton while its chunk loads, then the page itself', async () => {
    fakeServer({ 'GET /api/v1/profile': () => jsonResponse(500, { status: 500 }) })
    renderRoute('/profile')

    // The fallback already carries the page title, so nothing jumps when the real page replaces it
    expect(screen.getByTestId('page-fallback')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: t.profileTitle })).toBeInTheDocument()

    await waitFor(() => expect(screen.queryByTestId('page-fallback')).not.toBeInTheDocument())
    expect(await screen.findByRole('heading', { name: t.profileTitle })).toBeInTheDocument()
  })

  it('the start page is in the entry chunk and renders without a fallback', () => {
    fakeServer({})
    renderRoute('/')

    expect(screen.queryByTestId('page-fallback')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: t.workoutsTitle })).toBeInTheDocument()
  })
})

describe('prefetchRoutePages', () => {
  it('loads the bottom tabs first, one at a time, and keeps going past a failed chunk', async () => {
    const started: string[] = []
    let inFlight = 0
    let mostInFlight = 0
    const loader = (name: string, fail = false) => async () => {
      started.push(name)
      mostInFlight = Math.max(mostInFlight, ++inFlight)
      await new Promise((resolve) => setTimeout(resolve, 1))
      inFlight--
      if (fail) throw new Error('offline')
    }
    const loaders = Object.fromEntries(PREFETCH_ORDER.map((name) => [name, loader(name, name === 'progress')])) as Parameters<
      typeof prefetchRoutePages
    >[0]

    await prefetchRoutePages(loaders)

    expect(started).toEqual(['nutrition', 'progress', 'game', 'profile', 'workoutDetail', 'lolPlayer', 'lolMatch'])
    expect(mostInFlight).toBe(1)
  })
})
