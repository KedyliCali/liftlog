import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Exercise, Routine } from '../api/types'
import { ToastContext } from '../components/contexts'
import { t } from '../lib/i18n'
import { fakeServer, jsonResponse } from '../test/fakeServer'
import { RoutinesSheet } from './RoutinesSheet'

const catalog: Exercise[] = [
  { id: 1, name: 'Bench Press', muscleGroup: 'Chest', custom: false, bodyweight: false },
  { id: 2, name: 'Barbell Row', muscleGroup: 'Back', custom: false, bodyweight: false },
  { id: 3, name: 'Pull-up', muscleGroup: 'Back', custom: false, bodyweight: true },
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
    'PUT /api/v1/routines/5': (body) => jsonResponse(200, { id: 5, ...(body as object) }),
    'DELETE /api/v1/routines/5': () => {
      current = []
      return jsonResponse(204, undefined)
    },
  })
  const toast = vi.fn()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={toast}>
        <RoutinesSheet onClose={() => {}} />
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
  return { calls, toast, user: userEvent.setup() }
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
    await user.click(screen.getByTestId('save-routine'))
    await waitFor(() => expect(calls.find((c) => c.method === 'PUT')).toBeDefined())
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ name: 'Push B', exercises: [{ exerciseId: 1, sets: 3, targetReps: 8 }] })
  })

  it('deletes a routine only after confirming', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const { calls, toast, user } = renderSheet([push])
    const remove = await screen.findByRole('button', { name: `${t.delete}: Push A` })

    await user.click(remove)
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)

    await user.click(remove)
    await waitFor(() => expect(toast).toHaveBeenCalledWith({ message: t.routineDeleted }))
    expect(confirm).toHaveBeenCalledWith(t.deleteRoutineConfirm('Push A'))
    expect(await screen.findByText(t.routinesEmpty)).toBeInTheDocument()
  })
})
