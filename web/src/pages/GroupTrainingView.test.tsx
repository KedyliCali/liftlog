import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/endpoints'
import type { GroupTraining, GroupTrainingMember, LolGroup } from '../api/types'
import { ShareTrainingToggle } from '../components/ShareTraining'
import { ToastContext } from '../components/contexts'
import { formatKg } from '../lib/format'
import { t } from '../lib/i18n'
import { GamePage } from './GamePage'

function jsonResponse(status: number, body: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

interface Call {
  method: string
  path: string
  search: string
  body: unknown
}

/** Answers by "METHOD /path" and records the query string too, since the window travels in it. */
function fakeServer(routes: Record<string, (call: Call) => Response>): Call[] {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      if (url.includes('ddragon')) return jsonResponse(200, ['15.1.1'])
      const parsed = new URL(url)
      const call = {
        method: init.method ?? 'GET',
        path: parsed.pathname,
        search: parsed.search,
        body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
      }
      calls.push(call)
      return routes[`${call.method} ${call.path}`]?.(call) ?? jsonResponse(404, { status: 404 })
    }),
  )
  return calls
}

function renderWithClient(element: React.ReactNode, path = '/game?view=sport') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: '/game', element }], { initialEntries: [path] })
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={() => {}}>
        <RouterProvider router={router} />
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
  return { user: userEvent.setup(), router }
}

const group: LolGroup = {
  name: 'Gym Bros',
  inviteCode: 'K7QXM2PFD9',
  maxMembers: 20,
  members: [
    { userId: 1, displayName: 'Ali', you: true, owner: true, joinedAt: '2026-10-19T12:00:00', player: null },
    { userId: 2, displayName: 'Ece', you: false, owner: false, joinedAt: '2026-10-19T12:00:00', player: null },
    { userId: 3, displayName: 'Can', you: false, owner: false, joinedAt: '2026-10-19T12:00:00', player: null },
  ],
}

const numbers = (userId: number, name: string, workouts: number, volume: number, bench: number | null, flags: Partial<GroupTrainingMember> = {}): GroupTrainingMember => ({
  userId,
  displayName: name,
  you: false,
  shares: true,
  workouts,
  totalVolumeKg: volume,
  sets: workouts * 10,
  weekStreak: workouts,
  best: [
    { exercise: 'Bench Press', estimated1rmKg: bench },
    { exercise: 'Squat', estimated1rmKg: null },
    { exercise: 'Deadlift', estimated1rmKg: null },
    { exercise: 'Overhead Press', estimated1rmKg: null },
  ],
  ...flags,
})

const training = (meShares: boolean): GroupTraining => ({
  days: 7,
  from: '2026-10-13',
  to: '2026-10-19',
  members: [
    numbers(1, 'Ali', 2, 5000, 100, { you: true, shares: meShares }),
    numbers(2, 'Ece', 4, 3000, 120),
    { displayName: 'Can', shares: false },
  ],
})

/** First-column names of the table body, top to bottom. */
const tableOrder = () =>
  within(screen.getByRole('table', { name: t.trainingTableLabel }))
    .getAllByRole('rowheader')
    .map((cell) => cell.textContent)

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('group training view', () => {
  it('sorts by the tapped column and lights the leader of every column', async () => {
    fakeServer({
      'GET /api/v1/lol/group': () => jsonResponse(200, group),
      'GET /api/v1/group/training': () => jsonResponse(200, training(true)),
    })
    const { user } = renderWithClient(<GamePage />)

    // Most workouts first by default
    await waitFor(() => expect(tableOrder()).toEqual(['Ece', `Ali${t.youTag}`]))
    const table = screen.getByRole('table', { name: t.trainingTableLabel })
    expect(within(table).getByText('4')).toHaveClass('leader')
    expect(within(table).getByText(t.kg(formatKg(5000))).className).toContain('leader')
    expect(within(table).getByText(t.kg(formatKg(3000))).className).not.toContain('leader')
    expect(within(table).getByText('120')).toHaveClass('leader')

    await user.click(within(table).getByRole('button', { name: new RegExp(t.volumeColumn) }))
    expect(tableOrder()).toEqual([`Ali${t.youTag}`, 'Ece'])
    // A second tap reverses
    await user.click(within(table).getByRole('button', { name: new RegExp(t.volumeColumn) }))
    expect(tableOrder()).toEqual(['Ece', `Ali${t.youTag}`])

    // The member who did not opt in is a greyed name without numbers
    const notSharing = screen.getByText('Can').closest('li')!
    expect(notSharing).toHaveClass('not-sharing')
    expect(within(notSharing).getByText(t.notSharing)).toBeInTheDocument()
    // Already sharing: no opt-in card
    expect(screen.queryByText(t.shareCardTitle)).not.toBeInTheDocument()
  })

  it('explains what is shared until I opt in, and the switch turns it on', async () => {
    let sharing = false
    const calls = fakeServer({
      'GET /api/v1/lol/group': () => jsonResponse(200, group),
      'GET /api/v1/group/training': () => jsonResponse(200, training(sharing)),
      'PUT /api/v1/profile/share-training': (call) => {
        sharing = (call.body as { enabled: boolean }).enabled
        return jsonResponse(200, { id: 1, displayName: 'Ali', heightCm: null, bodyWeightKg: null, avatarVersion: null, shareTraining: sharing })
      },
    })
    const { user } = renderWithClient(<GamePage />)

    expect(await screen.findByText(t.shareCardTitle)).toBeInTheDocument()
    for (const item of t.shareCardItems) expect(screen.getByText(item)).toBeInTheDocument()
    expect(screen.getByText(t.shareCardNotShared)).toBeInTheDocument()
    expect(screen.getByText(t.onlyYouSeeRow)).toBeInTheDocument()

    await user.click(screen.getByRole('switch', { name: t.shareSwitch }))

    await waitFor(() => expect(screen.queryByText(t.shareCardTitle)).not.toBeInTheDocument())
    expect(calls.find((call) => call.method === 'PUT')?.body).toEqual({ enabled: true })
    expect(screen.queryByText(t.onlyYouSeeRow)).not.toBeInTheDocument()
  })

  it('switches between League and training and asks for the chosen window', async () => {
    const calls = fakeServer({
      'GET /api/v1/lol/group': () => jsonResponse(200, group),
      'GET /api/v1/group/training': () => jsonResponse(200, training(true)),
    })
    const { user, router } = renderWithClient(<GamePage />, '/game?view=group')

    // The League board is the default and never asks for training numbers
    await screen.findByRole('button', { name: t.groupModeLeague, pressed: true })
    expect(calls.some((call) => call.path === '/api/v1/group/training')).toBe(false)

    await user.click(screen.getByRole('button', { name: t.groupModeSport }))
    expect(router.state.location.search).toBe('?view=sport')
    await screen.findByRole('table', { name: t.trainingTableLabel })
    // The Group tab stays selected for both comparisons
    expect(screen.getByRole('tab', { name: t.lolViewGroup })).toHaveAttribute('aria-selected', 'true')

    await user.click(screen.getByRole('button', { name: t.trainingDays(30) }))
    await waitFor(() => expect(calls.some((call) => call.search.includes('days=30'))).toBe(true))
    expect(calls.filter((call) => call.path === '/api/v1/group/training').every((call) => /today=\d{4}-\d{2}-\d{2}/.test(call.search))).toBe(true)

    await user.click(screen.getByRole('button', { name: t.groupModeLeague }))
    expect(router.state.location.search).toBe('?view=group')
  })
})

describe('training API', () => {
  it('maps 204 (not in a group) to null and sends the window and the date', async () => {
    const calls = fakeServer({ 'GET /api/v1/group/training': () => new Response(null, { status: 204 }) })
    await expect(api.groupTraining(30, '2026-10-19')).resolves.toBeNull()
    expect(calls[0].search).toBe('?days=30&today=2026-10-19')
  })

  it('passes the numbers through and leaves out what the server left out', async () => {
    fakeServer({ 'GET /api/v1/group/training': () => jsonResponse(200, training(false)) })
    const result = await api.groupTraining(7, '2026-10-19')
    expect(result?.members[1]).toMatchObject({ userId: 2, workouts: 4, totalVolumeKg: 3000 })
    expect(result?.members[2]).toEqual({ displayName: 'Can', shares: false })
  })
})

describe('profile share switch', () => {
  it('turns sharing off', async () => {
    const calls = fakeServer({
      'PUT /api/v1/profile/share-training': () =>
        jsonResponse(200, { id: 1, displayName: 'Ali', heightCm: null, bodyWeightKg: null, avatarVersion: null, shareTraining: false }),
    })
    const { user } = renderWithClient(<ShareTrainingToggle enabled />, '/game')

    const toggle = screen.getByRole('switch', { name: t.shareSwitch })
    expect(toggle).toBeChecked()
    expect(screen.getByText(t.shareProfileTitle)).toBeInTheDocument()
    await user.click(toggle)
    await waitFor(() => expect(calls).toHaveLength(1))
    expect(calls[0]).toMatchObject({ method: 'PUT', body: { enabled: false } })
  })
})
