import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useParams } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Exercise, Routine } from '../api/types'
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

describe('templates when there are no routines yet', () => {
  const catalog: Exercise[] = [
    { id: 21, name: 'Pull-up', muscleGroup: 'Back', custom: false, bodyweight: true },
    { id: 22, name: 'Push-up', muscleGroup: 'Chest', custom: false, bodyweight: true },
    { id: 23, name: 'Dip', muscleGroup: 'Chest', custom: false, bodyweight: true },
    { id: 24, name: 'Inverted Row', muscleGroup: 'Back', custom: false, bodyweight: true },
    { id: 25, name: 'Pistol Squat', muscleGroup: 'Legs', custom: false, bodyweight: true },
    { id: 26, name: 'Hanging Leg Raise', muscleGroup: 'Core', custom: false, bodyweight: true },
  ]
  const calisthenicsBody = {
    name: t.templateNames.calisthenics,
    exercises: [
      { exerciseId: 21, sets: 4, targetReps: 6 },
      { exerciseId: 22, sets: 4, targetReps: 15 },
      { exerciseId: 23, sets: 3, targetReps: 10 },
      { exerciseId: 24, sets: 3, targetReps: 12 },
      { exerciseId: 25, sets: 3, targetReps: 6 },
      { exerciseId: 26, sets: 3, targetReps: 12 },
    ],
  }

  function serverWithoutRoutines() {
    let routines: Routine[] = []
    return fakeServer({
      'GET /api/v1/workouts': () => jsonResponse(200, emptyPage),
      'GET /api/v1/routines': () => jsonResponse(200, routines),
      'GET /api/v1/exercises': () => jsonResponse(200, catalog),
      'POST /api/v1/routines': (body) => {
        const input = body as typeof calisthenicsBody
        const created: Routine = {
          id: 9,
          name: input.name,
          exercises: input.exercises.map((e) => ({ ...e, name: catalog.find((c) => c.id === e.exerciseId)!.name, bodyweight: true })),
        }
        routines = [created]
        return jsonResponse(201, created)
      },
      'POST /api/v1/routines/9/start': () => jsonResponse(201, { id: 77, workoutDate: '2026-10-18', notes: 'x', totalVolumeKg: 0, sets: [], routineId: 9 }),
    })
  }

  it('offers templates in place of the routine strip; adding one puts it in the strip', async () => {
    const calls = serverWithoutRoutines()
    const user = renderWorkouts()

    const templates = await screen.findByRole('region', { name: t.readyTemplates })
    await user.click(within(templates).getByRole('button', { name: new RegExp(t.templateNames.calisthenics) }))
    const sheet = screen.getByRole('dialog', { name: t.readyTemplates })
    await user.click(await within(sheet).findByTestId('template-add'))

    await waitFor(() => expect(calls.find((c) => c.method === 'POST')?.body).toEqual(calisthenicsBody))
    const strip = await screen.findByRole('region', { name: t.startWithRoutine })
    expect(within(strip).getByRole('button', { name: new RegExp(t.templateNames.calisthenics) })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: t.readyTemplates })).not.toBeInTheDocument()
  })

  it('offers templates in the new workout sheet and starts one on the chosen date', async () => {
    const calls = serverWithoutRoutines()
    const user = renderWorkouts()
    await screen.findByRole('region', { name: t.readyTemplates })
    await user.click(screen.getByTestId('fab-new-workout'))

    const sheet = screen.getByRole('dialog', { name: t.newWorkout })
    fireEvent.change(within(sheet).getByLabelText(t.date), { target: { value: '2026-10-18' } })
    const offered = within(sheet).getByRole('region', { name: t.readyTemplates })
    await user.click(within(offered).getByRole('button', { name: new RegExp(t.templateNames.calisthenics) }))
    await user.click(await within(sheet).findByTestId('template-add-start'))

    expect(await screen.findByText('opened workout 77')).toBeInTheDocument()
    expect(calls.find((c) => c.method === 'POST' && c.path === '/api/v1/routines')?.body).toEqual(calisthenicsBody)
    expect(calls.find((c) => c.path === '/api/v1/routines/9/start')?.body).toEqual({ workoutDate: '2026-10-18' })
  })

  it('does not offer templates on a server without routines', async () => {
    fakeServer({ 'GET /api/v1/workouts': () => jsonResponse(200, emptyPage) })
    const user = renderWorkouts()
    expect(await screen.findByText(t.workoutsEmptyTitle)).toBeInTheDocument()
    await user.click(screen.getByTestId('fab-new-workout'))
    expect(screen.queryByRole('region', { name: t.readyTemplates })).not.toBeInTheDocument()
  })
})

describe('weekly streak chip', () => {
  it('shows the streak and this week count in the header', async () => {
    fakeServer({
      'GET /api/v1/workouts': () => jsonResponse(200, emptyPage),
      'GET /api/v1/stats/streak': () => jsonResponse(200, { weeks: 4, thisWeekWorkouts: 2, longestWeeks: 6 }),
    })
    renderWorkouts()

    const chip = await screen.findByRole('img', { name: t.streakAria(4, 2) })
    expect(chip).toHaveTextContent('4')
    expect(chip).toHaveTextContent(t.streakLabel)
    expect(chip).toHaveTextContent(t.streakThisWeek(2))
  })

  it('has no tag before the first workout of the week', async () => {
    fakeServer({
      'GET /api/v1/workouts': () => jsonResponse(200, emptyPage),
      'GET /api/v1/stats/streak': () => jsonResponse(200, { weeks: 3, thisWeekWorkouts: 0, longestWeeks: 3 }),
    })
    renderWorkouts()

    const chip = await screen.findByRole('img', { name: t.streakAria(3, 0) })
    expect(chip).not.toHaveTextContent(t.streakThisWeek(0))
  })

  it('stays hidden without a streak or on servers without the endpoint', async () => {
    fakeServer({
      'GET /api/v1/workouts': () => jsonResponse(200, emptyPage),
      'GET /api/v1/stats/streak': () => jsonResponse(200, { weeks: 0, thisWeekWorkouts: 0, longestWeeks: 2 }),
    })
    renderWorkouts()
    expect(await screen.findByText(t.workoutsEmptyTitle)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('img', { name: /./ })).not.toBeInTheDocument())
    cleanup()

    fakeServer({ 'GET /api/v1/workouts': () => jsonResponse(200, emptyPage) })
    renderWorkouts()
    expect(await screen.findByText(t.workoutsEmptyTitle)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('img', { name: /./ })).not.toBeInTheDocument())
  })
})
