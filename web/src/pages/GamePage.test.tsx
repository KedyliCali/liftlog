import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { LolGroup, LolGroupMember, LolPlayer } from '../api/types'
import { t } from '../lib/i18n'
import { inviteMessage } from '../lib/lolGroup'
import { GamePage } from './GamePage'

function jsonResponse(status: number, body: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

type Handler = (body: unknown) => Response
interface Call {
  method: string
  path: string
  body: unknown
}

/** Answers API calls by "METHOD /path"; everything it was asked is recorded in order. */
function fakeServer(routes: Record<string, Handler>): Call[] {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      if (url.includes('ddragon')) return jsonResponse(200, ['15.1.1'])
      const method = init.method ?? 'GET'
      const path = new URL(url).pathname
      const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined
      calls.push({ method, path, body })
      return routes[`${method} ${path}`]?.(body) ?? jsonResponse(404, { status: 404 })
    }),
  )
  return calls
}

function renderGame(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: '/game', element: <GamePage /> }], { initialEntries: [path] })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

const player = (puuid: string, tier: string): LolPlayer => ({
  puuid,
  gameName: puuid,
  tagLine: 'TR1',
  platform: 'TR1',
  profileIconId: null,
  summonerLevel: 100,
  rank: { tier, division: 'II', lp: 40, wins: 10, losses: 10 },
  recentGames: 10,
  recentWins: 6,
  refreshedAt: '2026-10-19T12:00:00',
})

const member = (userId: number, displayName: string | null, linked: LolPlayer | null, flags: Partial<LolGroupMember> = {}): LolGroupMember => ({
  userId,
  displayName,
  you: false,
  owner: false,
  joinedAt: '2026-10-19T12:00:00',
  player: linked,
  ...flags,
})

const group = (members: LolGroupMember[]): LolGroup => ({ name: 'Duo Queue', inviteCode: 'K7QXM2PFD9', maxMembers: 20, members })

describe('League tab', () => {
  beforeEach(() => {
    // jsdom has no matchMedia; the join sheet asks it whether the app runs from the home screen
    vi.stubGlobal('matchMedia', () => ({ matches: false }))
  })
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('starts on the follow list without a group and offers to create or join one', async () => {
    fakeServer({
      'GET /api/v1/lol/group': () => jsonResponse(204, undefined),
      'GET /api/v1/lol/players': () => jsonResponse(200, []),
    })
    const user = renderGame('/game')

    expect(await screen.findByText(t.lolEmptyTitle)).toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: t.lolViewGroup }))

    expect(await screen.findByText(t.groupEmptyTitle)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: t.createGroup })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: t.joinGroup })).toBeInTheDocument()
  })

  it('joins with the whole pasted message, then asks for the Riot account', async () => {
    const joined = group([member(1, 'Behlul', player('owner', 'GOLD'), { owner: true }), member(2, null, null, { you: true })])
    let inGroup = false
    const calls = fakeServer({
      'GET /api/v1/lol/group': () => (inGroup ? jsonResponse(200, joined) : jsonResponse(204, undefined)),
      'POST /api/v1/lol/group/join': () => {
        inGroup = true
        return jsonResponse(200, joined)
      },
    })
    const user = renderGame('/game?view=group')

    await user.click(await screen.findByRole('button', { name: t.joinGroup }))
    await user.click(screen.getByLabelText(t.inviteCodeLabel))
    await user.paste(inviteMessage('Duo Queue', 'K7QXM2PFD9', 'https://liftlog.test'))
    expect(screen.getByText(t.joinCodePreview('K7QXM-2PFD9'))).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: t.join }))

    expect(await screen.findByRole('dialog', { name: t.linkAccountTitle })).toBeInTheDocument()
    expect(calls).toContainEqual({ method: 'POST', path: '/api/v1/lol/group/join', body: { code: 'K7QXM2PFD9' } })
    // The follow list is never fetched while the group view is shown
    expect(calls.some((call) => call.path === '/api/v1/lol/players')).toBe(false)
  })

  it('shows the shared board with my row marked and who has not linked yet', async () => {
    fakeServer({
      'GET /api/v1/lol/group': () =>
        jsonResponse(200, group([
          member(1, 'Behlul', player('gold-player', 'GOLD'), { owner: true, you: true }),
          member(2, 'Ece', player('plat-player', 'PLATINUM')),
          member(3, 'Ali', null),
        ])),
    })
    renderGame('/game')

    expect(await screen.findByText('Duo Queue')).toBeInTheDocument()
    expect(screen.getByText('K7QXM-2PFD9')).toBeInTheDocument()
    const rows = screen.getAllByRole('link')
    // Platinum outranks Gold, and only my row carries the tag
    expect(within(rows[0]).getByText('Ece')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Behlul')).toBeInTheDocument()
    expect(within(rows[1]).getByText(t.youTag)).toBeInTheDocument()
    expect(within(rows[0]).queryByText(t.youTag)).toBeNull()
    expect(screen.getByText(t.notLinkedYet('Ali'))).toBeInTheDocument()
  })
})
