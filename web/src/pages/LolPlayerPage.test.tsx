import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { LolMatch, LolPlayerDetail, LolRankPoint } from '../api/types'
import { ToastContext } from '../components/contexts'
import { t } from '../lib/i18n'
import { LolPlayerPage } from './LolPlayerPage'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const summonerJson = {
  data: {
    SummonerFlash: { key: '4', image: { full: 'SummonerFlash.png' } },
    SummonerDot: { key: '14', image: { full: 'SummonerDot.png' } },
  },
}

/** Answers the player detail and rank history; Data Dragon gets a version and the spell list. */
function fakeServer(detail: unknown, history: LolRankPoint[] | null) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      if (url.includes('versions.json')) return jsonResponse(200, ['15.1.1'])
      if (url.includes('summoner.json')) return jsonResponse(200, summonerJson)
      const path = new URL(url).pathname
      if (path === '/api/v1/lol/players/p1') return jsonResponse(200, detail)
      if (path === '/api/v1/lol/players/p1/rank-history') return history ? jsonResponse(200, history) : jsonResponse(404, { status: 404 })
      return jsonResponse(404, { status: 404 })
    }),
  )
}

function renderPlayer() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: '/game/:puuid', element: <LolPlayerPage /> }], { initialEntries: ['/game/p1'] })
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={() => {}}>
        <RouterProvider router={router} />
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

/** The shape servers sent before the detailed stats: none of the new fields. */
const oldMatch = (matchId: string, champion: string, win: boolean): LolMatch => ({
  matchId,
  queueId: 420,
  endedAt: '2026-10-18T10:00:00',
  durationS: 1800,
  champion,
  position: 'MIDDLE',
  kills: 7,
  deaths: 3,
  assists: 9,
  cs: 210,
  damage: 24000,
  win,
})

const oldDetail: LolPlayerDetail = {
  player: {
    puuid: 'p1',
    gameName: 'Behlul',
    tagLine: 'TR1',
    platform: 'TR1',
    profileIconId: 1,
    summonerLevel: 300,
    rank: { tier: 'GOLD', division: 'II', lp: 45, wins: 30, losses: 25 },
    recentGames: 3,
    recentWins: 2,
    refreshedAt: '2026-10-19T10:00:00',
  },
  summary: {
    games: 3,
    wins: 2,
    avgKills: 7,
    avgDeaths: 3,
    avgAssists: 9,
    kda: 5.33,
    csPerMinute: 7,
    champions: [
      { champion: 'Ahri', games: 2, wins: 2, kda: 6.1 },
      { champion: 'Zed', games: 1, wins: 0, kda: 2.5 },
    ],
    form: [true, true, false],
  },
  matches: [oldMatch('TR1_1', 'Ahri', true), oldMatch('TR1_2', 'Ahri', true), oldMatch('TR1_3', 'Zed', false)],
}

const newDetail: LolPlayerDetail = {
  ...oldDetail,
  summary: {
    ...oldDetail.summary,
    killParticipation: 0.56,
    damagePerMinute: 812.4,
    goldPerMinute: 401.2,
    visionPerMinute: 0.92,
    damageShare: 0.27,
    streak: { win: true, count: 2 },
    roles: [
      { position: 'MIDDLE', games: 2, wins: 2 },
      { position: 'TOP', games: 1, wins: 0 },
    ],
    queues: [{ queueId: 420, games: 3, wins: 2 }],
    champions: [
      { champion: 'Ahri', games: 2, wins: 2, kda: 6.1, csPerMinute: 7.4, damagePerMinute: 900 },
      { champion: 'Zed', games: 1, wins: 0, kda: 2.5, csPerMinute: 6.2, damagePerMinute: 650 },
      { champion: 'Lux', games: 1, wins: 1, kda: 9.8, csPerMinute: null, damagePerMinute: null },
    ],
    records: { mostKills: { matchId: 'TR1_3', value: 14 }, longestGame: { matchId: 'TR1_GONE', value: 2400 } },
  },
  matches: [
    {
      ...oldMatch('TR1_1', 'Ahri', true),
      gold: 12345,
      vision: 24,
      champLevel: 16,
      damageTaken: 18000,
      killParticipation: 0.6,
      damageShare: 0.31,
      largestMultiKill: 5,
      wardsPlaced: 11,
      items: [3020, 0, 6655, 3089, 0, 0, 3364],
      summonerSpells: [4, 14],
      gameMode: 'CLASSIC',
    },
    oldMatch('TR1_2', 'Ahri', true),
    { ...oldMatch('TR1_3', 'Zed', false), kills: 14, largestMultiKill: 2 },
  ],
}

const championOrder = () =>
  within(screen.getByRole('table', { name: t.championsTitle }))
    .getAllByRole('row')
    .slice(1)
    // The name, not the icon (which shows initials until the picture loads)
    .map((row) => within(row).getByRole('rowheader').querySelector('.table-champion > span:last-child')?.textContent)

describe('League player page', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('still renders an answer from an older server without any of the detailed fields', async () => {
    fakeServer(oldDetail, null)
    renderPlayer()

    expect(await screen.findByRole('table', { name: t.championsTitle })).toBeInTheDocument()
    // Only the averages the old answer has get a tile
    expect(screen.getByTestId('tile-winRate')).toBeInTheDocument()
    expect(screen.getByTestId('tile-kda')).toBeInTheDocument()
    expect(screen.getByTestId('tile-cs')).toBeInTheDocument()
    expect(screen.queryByTestId('tile-kp')).toBeNull()
    expect(screen.queryByTestId('tile-damageShare')).toBeNull()
    // The streak is counted from the form strip instead
    expect(screen.getByText(t.streakWins(2))).toBeInTheDocument()
    // Nothing to draw or list
    expect(screen.queryByTestId('lp-chart')).toBeNull()
    expect(screen.queryByText(t.rolesTitle)).toBeNull()
    expect(screen.queryByText(t.recordsTitle)).toBeNull()
    expect(championOrder()).toEqual(['Ahri', 'Zed'])
    const rows = screen.getAllByTestId('match-row')
    expect(rows).toHaveLength(3)
    expect(within(rows[0]).queryByLabelText(t.itemsLabel)).toBeNull()
    // The damage it does have is still shown
    expect(within(rows[0]).getByText(t.detailDamage)).toBeInTheDocument()
  })

  it('shows tiles, roles, queues and the LP chart when the server sends them', async () => {
    fakeServer(newDetail, [
      { recordedAt: '2026-10-17T10:00:00', tier: 'GOLD', division: 'III', lp: 80 },
      { recordedAt: '2026-10-18T10:00:00', tier: 'GOLD', division: 'II', lp: 45 },
    ])
    renderPlayer()

    expect(await screen.findByTestId('lp-chart')).toBeInTheDocument()
    for (const key of ['winRate', 'kda', 'cs', 'kp', 'damage', 'gold', 'vision', 'damageShare']) {
      expect(screen.getByTestId(`tile-${key}`)).toBeInTheDocument()
    }
    const roles = screen.getByRole('list', { name: t.rolesTitle })
    // Map order: top lane before mid
    expect(within(roles).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      expect.stringContaining(t.positions.top),
      expect.stringContaining(t.positions.middle),
    ])
    expect(within(screen.getByRole('list', { name: t.queuesTitle })).getByText(t.queues.solo)).toBeInTheDocument()
  })

  it('hides the LP chart with a single rank snapshot', async () => {
    fakeServer(newDetail, [{ recordedAt: '2026-10-18T10:00:00', tier: 'GOLD', division: 'II', lp: 45 }])
    renderPlayer()

    expect(await screen.findByRole('table', { name: t.championsTitle })).toBeInTheDocument()
    expect(screen.queryByTestId('lp-chart')).toBeNull()
  })

  it('sorts the champions table by the tapped column, and reverses on a second tap', async () => {
    fakeServer(newDetail, null)
    const user = renderPlayer()

    await screen.findByRole('table', { name: t.championsTitle })
    // Most played first; ties keep the server order
    expect(championOrder()).toEqual(['Ahri', 'Zed', 'Lux'])

    const table = screen.getByRole('table', { name: t.championsTitle })
    await user.click(within(table).getByRole('button', { name: t.statKda }))
    expect(championOrder()).toEqual(['Lux', 'Ahri', 'Zed'])
    expect(within(table).getByRole('columnheader', { name: t.statKda })).toHaveAttribute('aria-sort', 'descending')

    await user.click(within(table).getByRole('button', { name: t.statKda }))
    expect(championOrder()).toEqual(['Zed', 'Ahri', 'Lux'])
    expect(within(table).getByRole('columnheader', { name: t.statKda })).toHaveAttribute('aria-sort', 'ascending')

    // Lux has no CS numbers: it stays last in both directions
    await user.click(within(table).getByRole('button', { name: t.statCsPerMin }))
    expect(championOrder()).toEqual(['Ahri', 'Zed', 'Lux'])
    await user.click(within(table).getByRole('button', { name: t.statCsPerMin }))
    expect(championOrder()).toEqual(['Zed', 'Ahri', 'Lux'])

    // Names start A to Z
    await user.click(within(table).getByRole('button', { name: t.championColumn }))
    expect(championOrder()).toEqual(['Ahri', 'Lux', 'Zed'])
  })

  it('shows items, spells, numbers and the multikill badge on a match, and opens it on a tap', async () => {
    fakeServer(newDetail, null)
    const user = renderPlayer()

    const [first, second, third] = await screen.findAllByTestId('match-row')
    const items = within(first).getByLabelText(t.itemsLabel)
    // Seven slots; the three empty ones have no picture
    expect(items.children).toHaveLength(7)
    expect(items.querySelectorAll('img')).toHaveLength(4)
    expect(items.querySelector('img')).toHaveAttribute('src', 'https://ddragon.leagueoflegends.com/cdn/15.1.1/img/item/3020.png')
    // Spell icons need summoner.json first, which maps the numeric keys to pictures
    const spells = within(first).getByLabelText(t.summonerSpellsLabel)
    await waitFor(() => expect(spells.querySelectorAll('img')).toHaveLength(2))
    expect(spells.querySelector('img')).toHaveAttribute('src', 'https://ddragon.leagueoflegends.com/cdn/15.1.1/img/spell/SummonerFlash.png')
    expect(within(first).getByText(t.multikills.penta)).toBeInTheDocument()
    expect(within(first).getByText(t.percent(60))).toBeInTheDocument()
    expect(within(third).getByText(t.multikills.double)).toBeInTheDocument()
    // An older match in the same list has no extras and no badge
    expect(within(second).queryByLabelText(t.itemsLabel)).toBeNull()
    expect(within(second).queryByText(t.multikills.double)).toBeNull()

    const head = within(first).getByRole('button', { expanded: false })
    await user.click(head)
    const details = within(first).getByLabelText(t.matchDetails)
    expect(within(details).getByText(t.detailGold)).toBeInTheDocument()
    expect(within(details).getByText('12,345')).toBeInTheDocument()
    expect(within(details).getByText(t.detailWards)).toBeInTheDocument()
    expect(head).toHaveAttribute('aria-expanded', 'true')

    await user.click(head)
    expect(within(first).queryByLabelText(t.matchDetails)).toBeNull()
  })

  it('opens the match a record points at, and shows records of matches no longer listed without a link', async () => {
    fakeServer(newDetail, null)
    const user = renderPlayer()

    const record = await screen.findByRole('button', { name: new RegExp(t.recordNames.mostKills) })
    await user.click(record)
    const third = screen.getAllByTestId('match-row')[2]
    expect(within(third).getByLabelText(t.matchDetails)).toBeInTheDocument()

    expect(screen.getByText(t.recordNames.longestGame)).toBeInTheDocument()
    expect(screen.getByText('40:00')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: new RegExp(t.recordNames.longestGame) })).toBeNull()
  })
})
