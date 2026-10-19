import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useParams } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Exercise, Routine } from '../api/types'
import { ConfirmProvider } from '../components/Confirm'
import { ToastContext } from '../components/contexts'
import { t } from '../lib/i18n'
import { todayIso } from '../lib/nutrition'
import { fakeServer, jsonResponse } from '../test/fakeServer'
import { RoutinesSheet } from './RoutinesSheet'

const catalog: Exercise[] = [
  { id: 1, name: 'Bench Press', muscleGroup: 'Chest', custom: false, bodyweight: false },
  { id: 2, name: 'Barbell Row', muscleGroup: 'Back', custom: false, bodyweight: false },
  { id: 3, name: 'Pull-up', muscleGroup: 'Back', custom: false, bodyweight: true },
  { id: 4, name: 'Overhead Press', muscleGroup: 'Shoulders', custom: false, bodyweight: false },
  { id: 7, name: 'Lateral Raise', muscleGroup: 'Shoulders', custom: false, bodyweight: false },
  { id: 8, name: 'Triceps Pushdown', muscleGroup: 'Arms', custom: false, bodyweight: false },
]

const push: Routine = {
  id: 5,
  name: 'Push A',
  exercises: [
    { exerciseId: 1, name: 'Bench Press', bodyweight: false, sets: 3, targetReps: 8 },
    { exerciseId: 2, name: 'Barbell Row', bodyweight: false, sets: 3, targetReps: 10 },
  ],
}

function renderSheet(routines: Routine[]) {
  let current = routines
  const calls = fakeServer({
    'GET /api/v1/routines': () => jsonResponse(200, current),
    'GET /api/v1/exercises': () => jsonResponse(200, catalog),
    'POST /api/v1/routines': (body) => jsonResponse(201, { id: 6, ...(body as object) }),
    'POST /api/v1/routines/6/start': () => jsonResponse(201, { id: 42, workoutDate: todayIso(), notes: 'Push', totalVolumeKg: 0, sets: [], routineId: 6 }),
    'PUT /api/v1/routines/5': (body) => jsonResponse(200, { id: 5, ...(body as object) }),
    'DELETE /api/v1/routines/5': () => {
      current = []
      return jsonResponse(204, undefined)
    },
  })
  const toast = vi.fn()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = vi.fn()
  const router = createMemoryRouter(
    [
      { path: '/', element: <RoutinesSheet onClose={onClose} /> },
      { path: '/workouts/:id', element: <OpenedWorkout /> },
    ],
    { initialEntries: ['/'] },
  )
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={toast}>
        <ConfirmProvider>
          <RouterProvider router={router} />
        </ConfirmProvider>
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
  return { calls, toast, onClose, user: userEvent.setup() }
}

function OpenedWorkout() {
  return <p>opened workout {useParams().id}</p>
}

async function pick(user: ReturnType<typeof userEvent.setup>, name: string | RegExp) {
  await user.click(screen.getByRole('button', { name: `+ ${t.addExercise}` }))
  await user.click(await screen.findByRole('button', { name }))
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('RoutinesSheet', () => {
  it('lists routines with a sets × reps summary', async () => {
    renderSheet([push])
    expect(await screen.findByText('Bench Press 3×8 · Barbell Row 3×10')).toBeInTheDocument()
  })

  it('needs a name and at least one exercise before creating', async () => {
    const { calls, user } = renderSheet([])
    await user.click(await screen.findByTestId('new-routine'))
    await user.click(screen.getByTestId('save-routine'))

    expect(screen.getByText(t.errorRoutineName)).toBeInTheDocument()
    expect(screen.getByText(t.errorRoutineExercises)).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it('creates a routine from picked exercises with stepped sets and reps', async () => {
    const { calls, user } = renderSheet([])
    await user.click(await screen.findByTestId('new-routine'))
    await user.type(screen.getByLabelText(t.routineName), '  Pull day ')
    await pick(user, 'Barbell Row')
    await pick(user, /^Pull-up/)

    const row = screen.getByTestId('routine-exercise-0')
    await user.click(within(row).getByRole('button', { name: t.moreSets }))
    await user.click(within(row).getByRole('button', { name: t.fewerReps }))
    // The second exercise moves above the first
    await user.click(screen.getByRole('button', { name: `${t.moveUp}: Pull-up` }))

    await user.click(screen.getByTestId('save-routine'))
    await waitFor(() => expect(calls.find((c) => c.method === 'POST')).toBeDefined())
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      name: 'Pull day',
      exercises: [
        { exerciseId: 3, sets: 3, targetReps: 10 },
        { exerciseId: 2, sets: 4, targetReps: 9 },
      ],
    })
    // Back on the list afterwards
    expect(await screen.findByTestId('new-routine')).toBeInTheDocument()
  })

  it('does not add an exercise that is already in the routine', async () => {
    const { user } = renderSheet([push])
    await user.click(await screen.findByRole('button', { name: `${t.editRoutine}: Push A` }))
    await pick(user, 'Bench Press')
    expect(screen.getByText(t.errorRoutineDuplicate)).toBeInTheDocument()
    expect(screen.getAllByTestId(/^routine-exercise-/)).toHaveLength(2)
  })

  it('edits an existing routine: rename, remove an exercise, save with PUT', async () => {
    const { calls, user } = renderSheet([push])
    await user.click(await screen.findByRole('button', { name: `${t.editRoutine}: Push A` }))
    const name = screen.getByLabelText(t.routineName)
    expect(name).toHaveValue('Push A')
    await user.clear(name)
    await user.click(screen.getByTestId('save-routine'))
    expect(screen.getByText(t.errorRoutineName)).toBeInTheDocument()

    await user.type(name, 'Push B')
    await user.click(screen.getByRole('button', { name: `${t.remove}: Barbell Row` }))
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: t.remove }))
    await user.click(screen.getByTestId('save-routine'))
    await waitFor(() => expect(calls.find((c) => c.method === 'PUT')).toBeDefined())
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ name: 'Push B', exercises: [{ exerciseId: 1, sets: 3, targetReps: 8 }] })
  })

  it('deletes a routine only after confirming', async () => {
    const { calls, toast, user } = renderSheet([push])
    const remove = await screen.findByRole('button', { name: `${t.delete}: Push A` })

    await user.click(remove)
    const dialog = screen.getByRole('alertdialog', { name: t.confirmDeleteTitle })
    expect(dialog).toHaveTextContent('Push A')
    expect(dialog).toHaveTextContent(t.deleteRoutineConsequence)
    await user.click(within(dialog).getByRole('button', { name: t.cancel }))
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    expect(screen.getByRole('button', { name: `${t.delete}: Push A` })).toBeInTheDocument()

    await user.click(remove)
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: t.delete }))
    await waitFor(() => expect(toast).toHaveBeenCalledWith({ message: t.routineDeleted }))
    expect(calls.filter((c) => c.method === 'DELETE').map((c) => c.path)).toEqual(['/api/v1/routines/5'])
    expect(await screen.findByText(t.routinesEmpty)).toBeInTheDocument()
  })

  it('asks before taking an exercise out of the routine', async () => {
    const { user } = renderSheet([push])
    await user.click(await screen.findByRole('button', { name: `${t.editRoutine}: Push A` }))
    const removeRow = screen.getByRole('button', { name: `${t.remove}: Barbell Row` })

    await user.click(removeRow)
    const dialog = screen.getByRole('alertdialog', { name: t.removeExerciseTitle })
    expect(dialog).toHaveTextContent('Barbell Row')
    await user.keyboard('{Escape}')
    // Escape answered the question only; the routine editor stays open with the exercise
    expect(screen.getByRole('button', { name: `${t.remove}: Barbell Row` })).toBeInTheDocument()

    await user.click(removeRow)
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: t.remove }))
    expect(screen.queryByRole('button', { name: `${t.remove}: Barbell Row` })).toBeNull()
    expect(screen.getByRole('button', { name: `${t.remove}: Bench Press` })).toBeInTheDocument()
  })
  it('adds a ready-made template with the user’s ids, skipping exercises the catalog does not have', async () => {
    const { calls, toast, user } = renderSheet([])
    await user.click(await screen.findByRole('button', { name: t.templateNames.push }))

    // Incline Dumbbell Press is not in this catalog: shown struck out and reported, not sent
    expect(await screen.findByRole('status')).toHaveTextContent(t.templateMissing('Incline Dumbbell Press'))
    await user.click(screen.getByTestId('template-add'))

    await waitFor(() => expect(calls.find((c) => c.method === 'POST')).toBeDefined())
    expect(calls.find((c) => c.method === 'POST')).toEqual({
      method: 'POST',
      path: '/api/v1/routines',
      body: {
        name: t.templateNames.push,
        exercises: [
          { exerciseId: 1, sets: 4, targetReps: 8 },
          { exerciseId: 4, sets: 3, targetReps: 8 },
          { exerciseId: 7, sets: 3, targetReps: 12 },
          { exerciseId: 8, sets: 3, targetReps: 12 },
        ],
      },
    })
    await waitFor(() => expect(toast).toHaveBeenCalledWith({ message: t.templateAdded(t.templateNames.push) }))
    // Back on the list afterwards
    expect(await screen.findByTestId('new-routine')).toBeInTheDocument()
  })

  it('adds a template and starts it on the phone’s date in one tap', async () => {
    const { calls, onClose, user } = renderSheet([])
    await user.click(await screen.findByRole('button', { name: t.templateNames.push }))
    await user.click(await screen.findByTestId('template-add-start'))

    expect(await screen.findByText('opened workout 42')).toBeInTheDocument()
    expect(calls.filter((c) => c.method === 'POST').map((c) => c.path)).toEqual(['/api/v1/routines', '/api/v1/routines/6/start'])
    expect(calls.find((c) => c.path === '/api/v1/routines/6/start')?.body).toEqual({ workoutDate: todayIso() })
    expect(onClose).toHaveBeenCalled()
  })

  it('cannot add a template when none of its exercises exist', async () => {
    const { calls, user } = renderSheet([])
    await user.click(await screen.findByRole('button', { name: t.templateNames.machineStarter }))
    expect(await screen.findByText(t.templateNothingResolved)).toBeInTheDocument()
    expect(screen.getByTestId('template-add')).toBeDisabled()
    expect(screen.getByTestId('template-add-start')).toBeDisabled()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })
})
