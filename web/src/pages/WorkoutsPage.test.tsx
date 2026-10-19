import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useParams } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Routine } from '../api/types'
import { t } from '../lib/i18n'
import { todayIso } from '../lib/nutrition'
import { fakeServer, jsonResponse } from '../test/fakeServer'
import { WorkoutsPage } from './WorkoutsPage'

const push: Routine = {
  id: 5,
  name: 'Push A',
  exercises: [
    { exerciseId: 1, name: 'Bench', bodyweight: false, sets: 3, targetReps: 8 },
    { exerciseId: 2, name: 'Dips', bodyweight: true, sets: 3, targetReps: 10 },
  ],
}

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true }

function OpenedWorkout() {
  return <p>opened workout {useParams().id}</p>
}

function renderWorkouts() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      { path: '/', element: <WorkoutsPage /> },
      { path: '/workouts/:id', element: <OpenedWorkout /> },
    ],
    { initialEntries: ['/'] },
  )
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      observe() {}
      disconnect() {}
    },
  )
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('routine strip', () => {
  it('starts a routine with one tap and opens the new workout', async () => {
    const calls = fakeServer({
      'GET /api/v1/workouts': () => jsonResponse(200, emptyPage),
      'GET /api/v1/routines': () => jsonResponse(200, [push]),
      'POST /api/v1/routines/5/start': () => jsonResponse(201, { id: 42, workoutDate: '2026-10-19', notes: 'Push A', totalVolumeKg: 0, sets: [], routineId: 5 }),
    })
    const user = renderWorkouts()

    const strip = await screen.findByRole('region', { name: t.startWithRoutine })
    await user.click(within(strip).getByRole('button', { name: /Push A/ }))

    expect(await screen.findByText('opened workout 42')).toBeInTheDocument()
    expect(calls.filter((c) => c.path === '/api/v1/routines/5/start')).toEqual([{ method: 'POST', path: '/api/v1/routines/5/start', body: { workoutDate: todayIso() } }])
  })

  it('stays hidden without routines, and on servers that have no routines yet', async () => {
    fakeServer({ 'GET /api/v1/workouts': () => jsonResponse(200, emptyPage) })
    renderWorkouts()
    expect(await screen.findByText(t.workoutsEmptyTitle)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('region', { name: t.startWithRoutine })).not.toBeInTheDocument())
    expect(screen.queryByTestId('manage-routines')).not.toBeInTheDocument()
  })

  it('opens the routines sheet from the header', async () => {
    fakeServer({
      'GET /api/v1/workouts': () => jsonResponse(200, emptyPage),
      'GET /api/v1/routines': () => jsonResponse(200, []),
    })
    const user = renderWorkouts()
    await user.click(await screen.findByTestId('manage-routines'))
    expect(screen.getByRole('dialog', { name: t.routines })).toBeInTheDocument()
    expect(screen.getByText(t.routinesEmpty)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: t.startWithRoutine })).not.toBeInTheDocument()
  })

  it('lists routines first in the new workout sheet and starts them on the chosen date', async () => {
    const calls = fakeServer({
      'GET /api/v1/workouts': () => jsonResponse(200, emptyPage),
      'GET /api/v1/routines': () => jsonResponse(200, [push]),
      'POST /api/v1/routines/5/start': () => jsonResponse(201, { id: 43, workoutDate: '2026-10-18', notes: 'Push A', totalVolumeKg: 0, sets: [], routineId: 5 }),
    })
    const user = renderWorkouts()
    await screen.findByRole('region', { name: t.startWithRoutine })
    await user.click(screen.getByTestId('fab-new-workout'))

    const sheet = screen.getByRole('dialog', { name: t.newWorkout })
    const buttons = within(sheet).getAllByRole('button')
    expect(buttons[0]).toHaveTextContent('Bench 3×8 · Dips 3×10')

    fireEvent.change(within(sheet).getByLabelText(t.date), { target: { value: '2026-10-18' } })
    await user.click(buttons[0])
    expect(await screen.findByText('opened workout 43')).toBeInTheDocument()
    expect(calls.find((c) => c.path === '/api/v1/routines/5/start')?.body).toEqual({ workoutDate: '2026-10-18' })
  })
})
