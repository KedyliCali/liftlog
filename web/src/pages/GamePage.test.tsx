import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { LolGroup, LolGroupMember, LolPlayer } from '../api/types'
import { ToastContext, type ToastOptions } from '../components/contexts'
import { t } from '../lib/i18n'
import { inviteMessage } from '../lib/lolGroup'
import { GamePage } from './GamePage'

function jsonResponse(status: number, body: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** A promise lets a test hold an answer back and release it later, like a slow server. */
type Handler = (body: unknown) => Response | Promise<Response>
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

/** Toasts go to the given spy instead of the screen, so a test can check what the user was told. */
function renderGame(path: string, toast: (options: ToastOptions) => void = () => {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: '/game', element: <GamePage /> }], { initialEntries: [path] })
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={toast}>
        <RouterProvider router={router} />
      </ToastContext.Provider>
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

/** The user is in a group of these members until they leave it. */
function serverWithLeave(members: LolGroupMember[]) {
  let inGroup = true
  return fakeServer({
    'GET /api/v1/lol/group': () => (inGroup ? jsonResponse(200, group(members)) : jsonResponse(204, undefined)),
    'DELETE /api/v1/lol/group/me': () => {
      inGroup = false
      return jsonResponse(204, undefined)
    },
  })
}

async function openSettings(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: t.manageGroup }))
  return screen.getByRole('dialog', { name: t.manageGroup })
}

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
    // A screen reader hears the code too, not only what the button does
    expect(screen.getByRole('button', { name: `${t.shareInvite}: K7QXM-2PFD9` })).toBeInTheDocument()
    const rows = screen.getAllByRole('link')
    // Platinum outranks Gold, and only my row carries the tag
    expect(within(rows[0]).getByText('Ece')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Behlul')).toBeInTheDocument()
    expect(within(rows[1]).getByText(t.youTag)).toBeInTheDocument()
    expect(within(rows[0]).queryByText(t.youTag)).toBeNull()
    expect(screen.getByText(t.notLinkedYet('Ali'))).toBeInTheDocument()
  })

  it('leaves as a member and stays on the group view, which offers to create or join again', async () => {
    const confirm = vi.fn(() => true)
    vi.stubGlobal('confirm', confirm)
    const calls = serverWithLeave([member(1, 'Ece', null, { owner: true }), member(2, 'Behlul', null, { you: true })])
    // No ?view=, so the group view is only the default while there is a group
    const user = renderGame('/game')

    const settings = await openSettings(user)
    await user.click(within(settings).getByRole('button', { name: t.leaveGroup }))

    expect(confirm).toHaveBeenCalledWith(`${t.leaveConfirmTitle}\n${t.leaveConfirmMember}`)
    expect(await screen.findByText(t.groupEmptyTitle)).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('tab', { name: t.lolViewGroup })).toHaveAttribute('aria-selected', 'true')
    expect(calls).toContainEqual({ method: 'DELETE', path: '/api/v1/lol/group/me', body: undefined })
    // The follow list never mounted on the way, so it was never fetched
    expect(calls.some((call) => call.path === '/api/v1/lol/players')).toBe(false)
  })

  it('tells the owner who takes the group over before leaving', async () => {
    const confirm = vi.fn(() => true)
    vi.stubGlobal('confirm', confirm)
    serverWithLeave([member(1, 'Behlul', null, { owner: true, you: true }), member(2, 'Ece', null), member(3, 'Ali', null)])
    const user = renderGame('/game?view=group')

    const settings = await openSettings(user)
    await user.click(within(settings).getByRole('button', { name: t.leaveGroup }))

    // Members come in join order, so the next owner is the first other member
    expect(confirm).toHaveBeenCalledWith(`${t.leaveConfirmTitle}\n${t.leaveConfirmOwner('Ece')}`)
    expect(await screen.findByText(t.groupEmptyTitle)).toBeInTheDocument()
  })

  it('keeps the new invite code when a board request from before the change answers late', async () => {
    vi.stubGlobal('confirm', () => true)
    const toast = vi.fn()
    const members = [member(1, 'Behlul', null, { owner: true, you: true })]
    const rotated = { ...group(members), inviteCode: 'H3RTW8NBC4' }
    let boardRequests = 0
    let releaseOldBoard = () => {}
    fakeServer({
      'GET /api/v1/lol/group': () => {
        boardRequests += 1
        if (boardRequests === 1) return jsonResponse(200, group(members))
        // The refetch that opening the settings starts is still running when the owner asks for a new code
        if (boardRequests === 2) return new Promise<Response>((resolve) => (releaseOldBoard = () => resolve(jsonResponse(200, group(members)))))
        return jsonResponse(200, rotated)
      },
      'POST /api/v1/lol/group/invite-code': () => jsonResponse(200, rotated),
    })
    const user = renderGame('/game?view=group', toast)

    const settings = await openSettings(user)
    await user.click(within(settings).getByRole('button', { name: t.newInviteCode }))
    await waitFor(() => expect(toast).toHaveBeenCalledWith({ message: t.inviteCodeChanged }))
    expect(screen.getByText('H3RTW-8NBC4')).toBeInTheDocument()

    await act(async () => {
      releaseOldBoard()
      await new Promise((resolve) => setTimeout(resolve, 50))
    })

    expect(screen.getByText('H3RTW-8NBC4')).toBeInTheDocument()
    expect(screen.queryByText('K7QXM-2PFD9')).toBeNull()
  })

  it('keeps the board and the open settings when the refetch behind them fails', async () => {
    let boardRequests = 0
    fakeServer({
      'GET /api/v1/lol/group': () => {
        boardRequests += 1
        return boardRequests === 1 ? jsonResponse(200, group([member(1, 'Behlul', null, { owner: true, you: true })])) : jsonResponse(500, { status: 500 })
      },
    })
    const user = renderGame('/game?view=group')

    await openSettings(user)
    await act(async () => {
      await waitFor(() => expect(boardRequests).toBe(2))
      await new Promise((resolve) => setTimeout(resolve, 50))
    })

    expect(screen.getByRole('dialog', { name: t.manageGroup })).toBeInTheDocument()
    expect(screen.getByText('Duo Queue')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: t.retry })).toBeNull()
  })

  it('says only the admin can change the code when ownership moved, and shows the group as it is now', async () => {
    vi.stubGlobal('confirm', () => true)
    const toast = vi.fn()
    let ownerNow = true
    const calls = fakeServer({
      'GET /api/v1/lol/group': () =>
        jsonResponse(200, group([member(1, 'Behlul', null, { you: true, owner: ownerNow }), member(2, 'Ece', null, { owner: !ownerNow })])),
      'POST /api/v1/lol/group/invite-code': () => {
        // The group passed to Ece after the settings sheet was drawn
        ownerNow = false
        return jsonResponse(403, { status: 403 })
      },
    })
    const user = renderGame('/game?view=group', toast)

    const settings = await openSettings(user)
    await user.click(within(settings).getByRole('button', { name: t.newInviteCode }))

    await waitFor(() => expect(toast).toHaveBeenCalledWith({ message: t.errorForbidden }))
    await waitFor(() => expect(screen.queryByRole('button', { name: t.newInviteCode })).toBeNull())
    const rotation = calls.findIndex((call) => call.method === 'POST')
    expect(calls.slice(rotation + 1)).toContainEqual({ method: 'GET', path: '/api/v1/lol/group', body: undefined })
  })

  it('closes the link sheet and says so when the owner removed me while it was open', async () => {
    const toast = vi.fn()
    let removed = false
    const calls = fakeServer({
      'GET /api/v1/lol/group': () =>
        removed ? jsonResponse(204, undefined) : jsonResponse(200, group([member(1, 'Ece', null, { owner: true }), member(2, 'Behlul', null, { you: true })])),
      'PUT /api/v1/lol/group/me/account': () => jsonResponse(404, { status: 404 }),
    })
    // No ?view=, so the group view is only the default while there is a group
    const user = renderGame('/game', toast)

    await user.click(await screen.findByRole('button', { name: t.linkAccount }))
    removed = true
    await user.type(screen.getByLabelText(t.riotId), 'Pesimist Fidan#2003')
    await user.click(screen.getByRole('button', { name: t.linkButton }))

    expect(await screen.findByText(t.groupEmptyTitle)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(screen.getByRole('tab', { name: t.lolViewGroup })).toHaveAttribute('aria-selected', 'true')
    expect(toast).toHaveBeenCalledWith({ message: t.removedFromGroup })
    // A removed member is not told that their Riot ID does not exist
    expect(screen.queryByText(t.riotIdNotFound('Pesimist Fidan#2003', 'TR1'))).toBeNull()
    // The view was pinned when the sheet opened, so the follow list never mounted and was never fetched
    expect(calls.some((call) => call.path === '/api/v1/lol/players')).toBe(false)
  })

  it('checks the membership with a request sent after the failed link, not one still running from before it', async () => {
    const toast = vi.fn()
    const before = group([member(1, 'Ece', null, { owner: true }), member(2, 'Behlul', null, { you: true })])
    let boardRequests = 0
    let releaseOldBoard = () => {}
    fakeServer({
      'GET /api/v1/lol/group': () => {
        boardRequests += 1
        if (boardRequests === 1) return jsonResponse(200, before)
        // Opening the settings starts this one; it read the members before the owner removed me
        if (boardRequests === 2) return new Promise<Response>((resolve) => (releaseOldBoard = () => resolve(jsonResponse(200, before))))
        return jsonResponse(204, undefined)
      },
      'PUT /api/v1/lol/group/me/account': () => jsonResponse(404, { status: 404 }),
    })
    const user = renderGame('/game?view=group', toast)

    const settings = await openSettings(user)
    await user.click(within(settings).getByRole('button', { name: t.linkAccount }))
    await user.type(screen.getByLabelText(t.riotId), 'Pesimist Fidan#2003')
    await user.click(screen.getByRole('button', { name: t.linkButton }))

    expect(await screen.findByText(t.groupEmptyTitle)).toBeInTheDocument()
    expect(toast).toHaveBeenCalledWith({ message: t.removedFromGroup })
    expect(screen.queryByText(t.riotIdNotFound('Pesimist Fidan#2003', 'TR1'))).toBeNull()
    expect(boardRequests).toBe(3)

    // The cancelled answer arriving late changes nothing
    await act(async () => {
      releaseOldBoard()
      await new Promise((resolve) => setTimeout(resolve, 50))
    })
    expect(screen.getByText(t.groupEmptyTitle)).toBeInTheDocument()
  })

  it('keeps the link sheet open with the not-found message when I am still in the group', async () => {
    const toast = vi.fn()
    const calls = fakeServer({
      'GET /api/v1/lol/group': () =>
        jsonResponse(200, group([member(1, 'Ece', null, { owner: true }), member(2, 'Behlul', null, { you: true })])),
      // A Riot ID on the wrong server, the usual 404
      'PUT /api/v1/lol/group/me/account': () => jsonResponse(404, { status: 404 }),
    })
    const user = renderGame('/game?view=group', toast)

    await user.click(await screen.findByRole('button', { name: t.linkAccount }))
    await user.type(screen.getByLabelText(t.riotId), 'Pesimist Fidan#2003')
    await user.click(screen.getByRole('button', { name: t.linkButton }))

    expect(await screen.findByText(t.riotIdNotFound('Pesimist Fidan#2003', 'TR1'))).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: t.linkAccountTitle })).toBeInTheDocument()
    expect(toast).not.toHaveBeenCalled()
    // The group was asked for again to tell this apart from a removal
    const link = calls.findIndex((call) => call.method === 'PUT')
    expect(calls.slice(link + 1)).toContainEqual({ method: 'GET', path: '/api/v1/lol/group', body: undefined })
  })

  it('shows the group I am already in when a join says so, instead of asking me to leave a group I cannot see', async () => {
    const toast = vi.fn()
    let inGroup = false
    fakeServer({
      'GET /api/v1/lol/group': () => (inGroup ? jsonResponse(200, group([member(1, 'Behlul', null, { owner: true, you: true })])) : jsonResponse(204, undefined)),
      'POST /api/v1/lol/group/join': () => jsonResponse(409, { status: 409, fieldErrors: { group: 'ALREADY_IN_GROUP' } }),
    })
    const user = renderGame('/game?view=group', toast)

    await user.click(await screen.findByRole('button', { name: t.joinGroup }))
    // A create whose answer was lost on mobile data already put me in a group
    inGroup = true
    await user.type(screen.getByLabelText(t.inviteCodeLabel), 'K7QXM2PFD9')
    await user.click(screen.getByRole('button', { name: t.join }))

    expect(await screen.findByText('Duo Queue')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(toast).toHaveBeenCalledWith({ message: t.groupRules.ALREADY_IN_GROUP })
  })

})
