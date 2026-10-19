import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PlanItem, WorkoutDetail, WorkoutSet } from '../api/types'
import { RestTimerContext, ToastContext, type ToastOptions } from '../components/contexts'
import { SyncStatus } from '../components/SyncStatus'
import { t } from '../lib/i18n'
import { outbox } from '../lib/outbox'
import { fakeServer, jsonResponse, type Handler } from '../test/fakeServer'
import { WorkoutDetailPage } from './WorkoutDetailPage'

const bench: PlanItem = {
  exerciseId: 1,
  name: 'Bench Press',
  bodyweight: false,
  plannedSets: 3,
  loggedSets: 0,
  targetReps: 8,
  suggestedWeightKg: 80,
  lastWeightKg: 80,
  lastReps: [8, 8, 8],
  suggestion: 'SAME',
  increaseKg: null,
}

/**
 * A routine workout on a server that can lose the connection: while `offline` is true every request
 * fails like fetch does without signal. `postSet` replaces the normal "store and answer" behaviour.
 */
function gymServer() {
  const state = { offline: false, postSet: null as Handler | null }
  const sets: WorkoutSet[] = []
  const storeSet = (body: unknown) => {
    const input = body as { exerciseId: number; reps: number; weightKg: number; rpe: number | null }
    const setOrder = sets.reduce((max, s) => Math.max(max, s.setOrder), 0) + 1
    const saved: WorkoutSet = { id: 100 + sets.length, exerciseName: bench.name, setOrder, newPersonalRecord: false, bodyweight: false, ...input }
    sets.push(saved)
    return jsonResponse(201, saved)
  }
  const reachable = (handler: Handler): Handler => (body) => {
    if (state.offline) throw new TypeError('Failed to fetch')
    return handler(body)
  }
  const detail = (): WorkoutDetail => ({ id: 7, workoutDate: '2026-10-19', notes: 'Push day', totalVolumeKg: 0, sets: [...sets], routineId: 3 })
  const calls = fakeServer({
    'GET /api/v1/workouts/7': reachable(() => jsonResponse(200, detail())),
    'GET /api/v1/workouts/7/plan': reachable(() => jsonResponse(200, [{ ...bench, loggedSets: sets.length }])),
    'GET /api/v1/profile': reachable(() => jsonResponse(200, { id: 1, displayName: null, heightCm: null, bodyWeightKg: 80, avatarVersion: null })),
    'GET /api/v1/exercises': reachable(() => jsonResponse(200, [])),
    'POST /api/v1/workouts/7/sets': reachable((body) => (state.postSet ? state.postSet(body) : storeSet(body))),
  })
  const posts = () => calls.filter((c) => c.method === 'POST')
  return { state, sets, storeSet, posts }
}

function renderPage(toast: (o: ToastOptions) => void = () => {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      {
        path: '/workouts/:id',
        element: (
          <>
            <SyncStatus />
            <WorkoutDetailPage />
          </>
        ),
      },
    ],
    { initialEntries: ['/workouts/7'] },
  )
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={toast}>
        <RestTimerContext.Provider value={{ start: () => {} }}>
          <RouterProvider router={router} />
        </RestTimerContext.Provider>
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

let online = true
const setOnline = (value: boolean) => {
  online = value
  act(() => void window.dispatchEvent(new Event(value ? 'online' : 'offline')))
}

beforeEach(() => {
  online = true
  vi.spyOn(navigator, 'onLine', 'get').mockImplementation(() => online)
  vi.stubGlobal('AudioContext', class {
    state = 'running'
    resume() {
      return Promise.resolve()
    }
  })
})

afterEach(() => {
  cleanup()
  outbox.reset()
  localStorage.clear()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

const tickRow = async (user: ReturnType<typeof userEvent.setup>, number: number) =>
  user.click(within(await screen.findByTestId('plan-card-1')).getByRole('button', { name: t.logPlannedSet(number) }))

describe('logging sets without signal', () => {
  it('shows ticked sets as waiting, then swaps them for the saved ones once back online', async () => {
    const server = gymServer()
    const toast = vi.fn()
    const user = renderPage(toast)
    await screen.findByTestId('plan-card-1')

    server.state.offline = true
    setOnline(false)
    await tickRow(user, 1)
    await tickRow(user, 2)

    // Both sets are on screen at once, marked as waiting, and nothing reached the server
    await waitFor(() => expect(screen.getAllByTestId('pending-set')).toHaveLength(2))
    expect(screen.getAllByText(t.waitingBadge)).toHaveLength(2)
    expect(within(screen.getByTestId('sync-status')).getByText(t.offline)).toBeInTheDocument()
    expect(within(screen.getByTestId('sync-status')).getByText(t.setsWaiting(2))).toBeInTheDocument()
    expect(within(screen.getByTestId('plan-card-1')).getByText(t.setsLeft(1))).toBeInTheDocument()
    expect(server.sets).toHaveLength(0)
    expect(toast).toHaveBeenCalledWith({ message: t.setSavedOffline, durationMs: 4500 })
    // Kept on the phone: survives the tab being closed
    expect(JSON.parse(localStorage.getItem('liftlog.outbox.v1')!)).toHaveLength(2)

    server.state.offline = false
    setOnline(true)

    await waitFor(() => expect(screen.queryByTestId('pending-set')).not.toBeInTheDocument())
    expect(screen.getAllByTestId('saved-set')).toHaveLength(2)
    expect(server.sets.map((s) => [s.setOrder, s.reps, s.weightKg])).toEqual([
      [1, 8, 80],
      [2, 8, 80],
    ])
    expect(screen.queryByTestId('sync-status')).not.toBeInTheDocument()
    expect(outbox.items()).toEqual([])
  })

  it('does not post a set twice when the server saved it but the answer was lost', async () => {
    const server = gymServer()
    const user = renderPage()
    await screen.findByTestId('plan-card-1')

    // The request arrives and is stored, then the connection drops before the answer comes back
    server.state.postSet = (body) => {
      server.storeSet(body)
      throw new TypeError('Failed to fetch')
    }
    await tickRow(user, 1)
    expect(await screen.findByTestId('pending-set')).toBeInTheDocument()
    expect(server.sets).toHaveLength(1)

    server.state.postSet = null
    setOnline(true)

    await waitFor(() => expect(screen.queryByTestId('pending-set')).not.toBeInTheDocument())
    expect(screen.getAllByTestId('saved-set')).toHaveLength(1)
    expect(server.sets).toHaveLength(1)
    expect(server.posts()).toHaveLength(1)
  })

  it('edits a waiting set in the set editor before it is ever sent', async () => {
    const server = gymServer()
    const user = renderPage()
    await screen.findByTestId('plan-card-1')

    server.state.offline = true
    setOnline(false)
    await tickRow(user, 1)
    await user.click(await screen.findByTestId('pending-set'))
    const reps = screen.getByLabelText(t.reps, { selector: 'input' })
    await user.clear(reps)
    await user.type(reps, '6')
    await user.click(screen.getByRole('button', { name: t.save }))

    await waitFor(() => expect(within(screen.getByTestId('pending-set')).getByText('× 6')).toBeInTheDocument())
    expect(outbox.items()).toHaveLength(1)

    server.state.offline = false
    setOnline(true)
    await waitFor(() => expect(screen.queryByTestId('pending-set')).not.toBeInTheDocument())
    // Only the edited values were ever saved: the edit changed the waiting create, not a second request
    expect(server.sets.map((s) => s.reps)).toEqual([6])
    expect(screen.getAllByTestId('saved-set')).toHaveLength(1)
  })

  it('drops a set the server refuses and says so', async () => {
    const server = gymServer()
    const toast = vi.fn()
    const user = renderPage(toast)
    await screen.findByTestId('plan-card-1')

    server.state.offline = true
    setOnline(false)
    await tickRow(user, 1)
    await screen.findByTestId('pending-set')

    server.state.offline = false
    server.state.postSet = () => jsonResponse(400, { status: 400, message: 'Reps too high' })
    setOnline(true)

    await waitFor(() => expect(screen.queryByTestId('pending-set')).not.toBeInTheDocument())
    expect(toast).toHaveBeenCalledWith({ message: t.waitingSetDropped('Reps too high'), durationMs: 6000 })
    expect(outbox.items()).toEqual([])
  })
})
