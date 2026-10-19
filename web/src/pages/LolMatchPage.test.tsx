import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { LolMatchDetail, LolMatchParticipant, LolTimeline } from '../api/types'
import { ToastContext } from '../components/contexts'
import { t } from '../lib/i18n'
import { LolMatchPage } from './LolMatchPage'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const championJson = { data: { Ahri: { id: 'Ahri', key: '103' }, MonkeyKing: { id: 'MonkeyKing', key: '62' } } }

type Answer = Response | Promise<Response>

/** The match and its timeline; each answer can be held back with a promise. Every API path is recorded. */
function fakeServer(match: () => Answer, timeline: () => Answer): string[] {
  const paths: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string) => {
      if (input.includes('versions.json')) return jsonResponse(200, ['15.1.1'])
      if (input.includes('champion.json')) return jsonResponse(200, championJson)
      if (input.includes('ddragon')) return jsonResponse(404, {})
      const path = new URL(input).pathname
      paths.push(path)
      if (path === '/api/v1/lol/matches/TR1_9') return match()
      if (path === '/api/v1/lol/matches/TR1_9/timeline') return timeline()
      return jsonResponse(404, { status: 404 })
    }),
  )
  return paths
}

function renderMatch(entry = '/game/match/TR1_9?player=me') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: '/game/match/:matchId', element: <LolMatchPage /> }], { initialEntries: [entry] })
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={() => {}}>
        <RouterProvider router={router} />
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

const POSITIONS = ['TOP', 'JUNGLE', 'MIDDLE', 'BOTTOM', 'UTILITY']

function participant(participantId: number): LolMatchParticipant {
  const blue = participantId <= 5
  const position = POSITIONS[(participantId - 1) % 5]
  return {
    participantId,
    puuid: participantId === 3 ? 'me' : `p${participantId}`,
    riotName: `Player${participantId}`,
    riotTag: 'TR1',
    teamId: blue ? 100 : 200,
    champion: participantId === 3 ? 'Ahri' : `Champ${participantId}`,
    champLevel: 14,
    position,
    kills: participantId,
    deaths: 2,
    assists: 5,
    cs: 150 + participantId,
    gold: 10000 + participantId * 100,
    damage: 10000 + participantId * 1000,
    damageTaken: 15000,
    damageObjectives: 4000,
    damageBuildings: 2000,
    healing: 3000,
    shielding: 500,
    ccTime: 12,
    timeDead: 60,
    visionScore: 20,
    wardsPlaced: 8,
    wardsKilled: 2,
    controlWards: 1,
    doubleKills: 1,
    tripleKills: 0,
    quadraKills: 0,
    pentaKills: 0,
    firstBlood: participantId === 3,
    soloKills: 1,
    killParticipation: 0.5,
    damageShare: 0.2,
    items: [3020, 0, 0, 0, 0, 0, 3364],
    spells: [4, 14],
    keystone: 8112,
    primaryStyle: 8100,
    secondaryStyle: 8300,
    win: blue,
    tracked: participantId === 7,
  }
}

const detail: LolMatchDetail = {
  matchId: 'TR1_9',
  queueId: 420,
  gameMode: 'CLASSIC',
  gameVersion: '14.20.123.4567',
  endedAt: '2026-10-18T10:00:00',
  durationS: 1834,
  teams: [
    { teamId: 100, win: true, kills: 30, towers: 9, inhibitors: 2, dragons: 3, barons: 1, heralds: 1, grubs: 4, atakhan: 0, firstBlood: true, firstTower: true, firstDragon: false, firstBaron: true, bans: [62, -1, 103] },
    { teamId: 200, win: false, kills: 18, towers: 3, inhibitors: 0, dragons: 1, barons: 0, heralds: 0, grubs: 2, atakhan: 0, firstBlood: false, firstTower: false, firstDragon: true, firstBaron: false, bans: [] },
  ],
  // Shuffled, to check the lane order on screen
  participants: [5, 1, 3, 2, 4, 10, 6, 7, 8, 9].map(participant),
  timelineAvailable: false,
}

const timeline: LolTimeline = {
  matchId: 'TR1_9',
  minutes: [
    { minute: 0, blueGold: 2500, redGold: 2500 },
    { minute: 1, blueGold: 3200, redGold: 3000 },
    { minute: 2, blueGold: 4000, redGold: 4400 },
  ],
  participants: [
    { participantId: 3, at10: { gold: 4200, cs: 90, xp: 5000, level: 8 }, at15: { gold: 6100, cs: 130, xp: 8000, level: 11 }, gold: [] },
    { participantId: 8, at10: { gold: 3700, cs: 84, xp: 4800, level: 8 }, at15: { gold: 6400, cs: 128, xp: 8100, level: 11 }, gold: [] },
  ],
}

const teamRows = (team: number) =>
  within(screen.getByRole('table', { name: t.teamNames[team] }))
    .getAllByRole('row')
    .slice(1)

describe('League match page', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows the result, objectives, bans and both scoreboards in lane order', async () => {
    fakeServer(
      () => jsonResponse(200, detail),
      () => jsonResponse(200, timeline),
    )
    const user = renderMatch()

    const header = await screen.findByTestId('match-header')
    // Told from the visiting player's side
    expect(header).toHaveTextContent(t.victory)
    expect(header).toHaveTextContent(t.patchLabel('14.20'))
    expect(header).toHaveTextContent('30:34')

    const strip = screen.getByRole('region', { name: t.objectivesTitle })
    expect(within(strip).getByText(t.objectiveNames.towers).parentElement).toHaveTextContent(`9${t.objectiveNames.towers}3`)
    // No Atakhan on either side: no row for it
    expect(within(strip).queryByText(t.objectiveNames.atakhan)).toBeNull()
    expect(within(strip).getByText(t.firstObjectives.firstDragon)).toHaveClass('red')
    // Bans arrive as numeric keys; champion.json turns them into pictures, and -1 (no ban) is skipped
    const bans = within(strip).getByLabelText(`${t.bansLabel}: ${t.teamNames[100]}`)
    await waitFor(() => expect(bans.querySelectorAll('img')).toHaveLength(2))
    expect(bans.querySelector('img')).toHaveAttribute('src', 'https://ddragon.leagueoflegends.com/cdn/15.1.1/img/champion/MonkeyKing.png')

    const blue = teamRows(100)
    const red = teamRows(200)
    expect(blue).toHaveLength(5)
    expect(red).toHaveLength(5)
    // Top to support, whatever order the server sent
    expect(blue.map((row) => within(row).getByRole('rowheader').textContent)).toEqual(['Champ1', 'Champ2', 'Ahri', 'Champ4', 'Champ5'].map((name) => expect.stringContaining(name)))
    expect(blue[2]).toHaveClass('you')
    // Followed players link to their page
    expect(within(red[1]).getByRole('link', { name: 'Player7' })).toHaveAttribute('href', '/game/p7')
    expect(within(blue[0]).queryByRole('link')).toBeNull()

    // Details reveal the remaining numbers
    const table = screen.getByRole('table', { name: t.teamNames[100] })
    expect(within(table).queryByRole('columnheader', { name: t.healingColumn })).toBeNull()
    await user.click(screen.getByRole('button', { name: t.scoreboardDetails }))
    for (const label of [t.damageTakenColumn, t.healingColumn, t.shieldingColumn, t.ccColumn, t.timeDeadColumn, t.controlWardsColumn, t.multikillsColumn, t.soloKillsColumn]) {
      expect(within(table).getByRole('columnheader', { name: label })).toBeInTheDocument()
    }
    expect(within(teamRows(100)[2]).getByText('✓')).toBeInTheDocument()
  })

  it('loads the timeline only after the match, then draws the gold lead and the lane differences', async () => {
    let releaseMatch: () => void = () => {}
    const paths = fakeServer(
      () => new Promise<Response>((resolve) => (releaseMatch = () => resolve(jsonResponse(200, detail)))),
      () => jsonResponse(200, timeline),
    )
    renderMatch()

    await waitFor(() => expect(paths).toContain('/api/v1/lol/matches/TR1_9'))
    expect(paths).not.toContain('/api/v1/lol/matches/TR1_9/timeline')

    releaseMatch()
    expect(await screen.findByTestId('gold-lead-chart')).toBeInTheDocument()
    expect(paths).toContain('/api/v1/lol/matches/TR1_9/timeline')

    const lanes = within(screen.getByRole('table', { name: t.laneDiffTitle }))
      .getAllByRole('row')
      .slice(1)
    expect(lanes).toHaveLength(5)
    const mid = lanes[2]
    expect(mid).toHaveTextContent(t.positions.middle)
    // Only mid has both players' frames (the others show dashes): blue Ahri 500 gold up at 10, 300 down at 15
    expect(within(mid).getByText('+500')).toHaveClass('blue')
    expect(within(mid).getByText('−300')).toHaveClass('red')
    expect(within(mid).getByText('+6')).toBeInTheDocument()
  })

  it('explains a busy Riot and retries the timeline', async () => {
    let busy = true
    const paths = fakeServer(
      () => jsonResponse(200, detail),
      () => (busy ? jsonResponse(503, { status: 503, fieldErrors: { riot: 'UNAVAILABLE' } }) : jsonResponse(200, timeline)),
    )
    const user = renderMatch()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(t.timelineBusy)
    // The scoreboard is there all along
    expect(screen.getByRole('table', { name: t.teamNames[100] })).toBeInTheDocument()

    busy = false
    await user.click(within(alert).getByRole('button', { name: t.retry }))
    expect(await screen.findByTestId('gold-lead-chart')).toBeInTheDocument()
    expect(paths.filter((path) => path.endsWith('/timeline'))).toHaveLength(2)
  })

  it('says when Riot no longer has the timeline, without a retry', async () => {
    fakeServer(
      () => jsonResponse(200, detail),
      () => jsonResponse(404, { status: 404 }),
    )
    renderMatch()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(t.timelineGone)
    expect(within(alert).queryByRole('button')).toBeNull()
  })

  it('explains a match that is not stored in full yet', async () => {
    const paths = fakeServer(
      () => jsonResponse(404, { status: 404 }),
      () => jsonResponse(200, timeline),
    )
    renderMatch('/game/match/TR1_9')

    expect(await screen.findByText(t.matchNotStoredTitle)).toBeInTheDocument()
    expect(paths).not.toContain('/api/v1/lol/matches/TR1_9/timeline')
  })

  it('names the winning team when opened without a player', async () => {
    fakeServer(
      () => jsonResponse(200, detail),
      () => jsonResponse(200, timeline),
    )
    renderMatch('/game/match/TR1_9')

    expect(await screen.findByTestId('match-header')).toHaveTextContent(t.teamWon(t.teamNames[100]))
    expect(teamRows(100).some((row) => row.classList.contains('you'))).toBe(false)
  })
})
