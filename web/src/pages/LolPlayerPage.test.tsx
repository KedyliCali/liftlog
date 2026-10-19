import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { LolDeepStats, LolGroupDuo, LolMatch, LolPlayerDetail, LolRankPoint } from '../api/types'
import { ToastContext } from '../components/contexts'
import { t } from '../lib/i18n'
import { tzOffsetMinutes } from '../lib/lolDeep'
import { LolPlayerPage } from './LolPlayerPage'

function jsonResponse(status: number, body: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const summonerJson = {
  data: {
    SummonerFlash: { key: '4', image: { full: 'SummonerFlash.png' } },
    SummonerDot: { key: '14', image: { full: 'SummonerDot.png' } },
  },
}

const itemJson = {
  data: {
    '3020': { name: "Sorcerer's Shoes", tags: ['Boots'] },
    '6655': { name: "Luden's Companion", tags: ['SpellDamage'] },
    '2003': { name: 'Health Potion', tags: ['Consumable'] },
    '3364': { name: 'Oracle Lens', tags: ['Trinket'] },
  },
}

const runesJson = [
  {
    id: 8100,
    name: 'Domination',
    icon: 'perk-images/Styles/7200_Domination.png',
    slots: [{ runes: [{ id: 8112, name: 'Electrocute', icon: 'perk-images/Styles/Domination/Electrocute/Electrocute.png' }] }],
  },
]

interface ServerOptions {
  detail: unknown
  history?: LolRankPoint[] | null
  /** null: an older server without the stats endpoint (404) */
  stats?: LolDeepStats | null
  groupDuos?: LolGroupDuo[] | null
}

interface Call {
  method: string
  url: URL
  body: unknown
}

/** Answers the player, rank history, stats, group duos and follow; Data Dragon gets a version and its files. */
function fakeServer({ detail, history = null, stats = null, groupDuos = null }: ServerOptions): Call[] {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init: RequestInit = {}) => {
      if (input.includes('versions.json')) return jsonResponse(200, ['15.1.1'])
      if (input.includes('summoner.json')) return jsonResponse(200, summonerJson)
      if (input.includes('item.json')) return jsonResponse(200, itemJson)
      if (input.includes('runesReforged.json')) return jsonResponse(200, runesJson)
      const url = new URL(input)
      const method = init.method ?? 'GET'
      calls.push({ method, url, body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined })
      const route = `${method} ${url.pathname}`
      if (route === 'GET /api/v1/lol/players/p1') return jsonResponse(200, detail)
      if (route === 'GET /api/v1/lol/players/p1/rank-history') return history ? jsonResponse(200, history) : jsonResponse(404, { status: 404 })
      if (route === 'GET /api/v1/lol/players/p1/stats') {
        if (!stats) return jsonResponse(404, { status: 404 })
        // Echo the filter back, like the server does
        return jsonResponse(200, { ...stats, queue: url.searchParams.get('queue'), games: Number(url.searchParams.get('games')) })
      }
      if (route === 'GET /api/v1/lol/group/duos') return groupDuos ? jsonResponse(200, groupDuos) : new Response(null, { status: 204 })
      if (route === 'POST /api/v1/lol/players') return jsonResponse(201, { puuid: 'd2' })
      if (route === 'GET /api/v1/lol/players') return jsonResponse(200, [])
      return jsonResponse(404, { status: 404 })
    }),
  )
  return calls
}

/** Stand-in for the match page: shows where the app went. */
function WhereAmI() {
  const location = useLocation()
  return <p data-testid="location">{location.pathname + location.search}</p>
}

function renderPlayer(entry = '/game/p1', toast: (t: { message: string }) => void = () => {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      { path: '/game/:puuid', element: <LolPlayerPage /> },
      { path: '/game/match/:matchId', element: <WhereAmI /> },
    ],
    { initialEntries: [entry] },
  )
  render(
    <QueryClientProvider client={client}>
      <ToastContext.Provider value={toast}>
        <RouterProvider router={router} />
      </ToastContext.Provider>
    </QueryClientProvider>,
  )
  return { user: userEvent.setup(), router }
}

/** The shape servers sent before the detailed stats: none of the new fields. */
const oldMatch = (matchId: string, champion: string, win: boolean, queueId = 420): LolMatch => ({
  matchId,
  queueId,
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
      teamId: 100,
    },
    oldMatch('TR1_2', 'Ahri', true, 450),
    { ...oldMatch('TR1_3', 'Zed', false), kills: 14, largestMultiKill: 2 },
  ],
}

const win = (games: number, wins: number) => ({ games, wins })

const deepStats: LolDeepStats = {
  queue: 'ALL',
  games: 20,
  gamesFound: 3,
  gamesWithFullData: 2,
  wins: 2,
  overview: {
    kda: 5.33,
    avgKills: 7,
    avgDeaths: 3,
    avgAssists: 9,
    killParticipation: 0.56,
    damageShare: 0.27,
    csPerMinute: 7,
    goldPerMinute: 401,
    damagePerMinute: 812,
    damageTakenPerMinute: 640,
    visionPerMinute: 0.92,
    avgWardsPlaced: 9.3,
    avgWardsKilled: 2.1,
    avgControlWards: 1.7,
    avgSoloKills: 1.3,
    avgCcTime: 14.2,
    deadShare: 0.12,
    damagePerGold: 1.9,
    objectiveDamagePerMinute: 210,
    firstBloodRate: 0.33,
    multiKills: { double: 4, triple: 1, quadra: 0, penta: 1 },
    avgGameMinutes: 30.5,
  },
  champions: [
    {
      champion: 'Ahri',
      games: 2,
      wins: 2,
      kills: 8,
      deaths: 2,
      assists: 10,
      kda: 9,
      csPerMinute: 7.4,
      damagePerMinute: 900,
      goldPerMinute: 420,
      visionPerMinute: 1.1,
      killParticipation: 0.6,
      damageShare: 0.3,
      pentas: 1,
      lastPlayed: '2026-10-18T10:00:00',
    },
    {
      champion: 'Zed',
      games: 1,
      wins: 0,
      kills: 14,
      deaths: 6,
      assists: 2,
      kda: 2.67,
      csPerMinute: 6.2,
      damagePerMinute: 650,
      goldPerMinute: 380,
      visionPerMinute: 0.5,
      killParticipation: 0.4,
      damageShare: 0.25,
      pentas: 0,
      lastPlayed: '2026-10-17T10:00:00',
    },
  ],
  roles: [{ position: 'MIDDLE', games: 3, wins: 2, kda: 5.3, csPerMinute: 7, damagePerMinute: 812, visionPerMinute: 0.9 }],
  duos: [
    { puuid: 'd1', riotName: 'Buddy', riotTag: 'TR1', games: 3, wins: 2, kda: 4.2, myKda: 5.1, champions: ['Thresh', 'Nautilus'], tracked: true },
    { puuid: 'd2', riotName: 'Stranger', riotTag: 'EUW', games: 2, wins: 0, kda: 1.5, myKda: 2.2, champions: ['Lulu'], tracked: false },
  ],
  matchups: [{ champion: 'Syndra', games: 2, wins: 1, kda: 3.1, avgGoldDiff: 450.4, avgCsDiff: -12.2 }],
  enemyChampions: [{ champion: 'Jinx', games: 2, wins: 1 }],
  allyChampions: [{ champion: 'LeeSin', games: 2, wins: 2 }],
  sides: [
    { teamId: 100, games: 2, wins: 2 },
    { teamId: 200, games: 1, wins: 0 },
  ],
  durations: [{ label: '25-30', games: 3, wins: 2 }],
  hours: [{ label: '18-24', games: 3, wins: 2 }],
  weekdays: [{ day: 1, games: 3, wins: 2 }],
  objectives: {
    avgTeamDragons: 2.5,
    avgTeamBarons: 0.5,
    avgTeamTowers: 6,
    avgTeamGrubs: 3,
    avgTeamHeralds: 1,
    firstBlood: win(1, 1),
    firstTower: win(2, 2),
    firstDragon: win(1, 0),
    firstBaron: win(0, 0),
  },
  items: [
    { itemId: 2003, games: 3, wins: 2 },
    { itemId: 3020, games: 3, wins: 2 },
    { itemId: 6655, games: 2, wins: 2 },
    { itemId: 3364, games: 3, wins: 2 },
  ],
  keystones: [{ keystone: 8112, primaryStyle: 8100, games: 3, wins: 2 }],
  spells: [{ spells: '4,14', games: 3, wins: 2 }],
  patches: [
    { patch: '14.9', games: 1, wins: 0 },
    { patch: '14.10', games: 2, wins: 2 },
  ],
  trend: [
    { matchId: 'TR1_3', endedAt: '2026-10-17T10:00:00', win: false, kda: 2.67, csPerMinute: 6.2, damagePerMinute: 650, goldPerMinute: 380, visionPerMinute: 0.5, killParticipation: 0.4 },
    { matchId: 'TR1_2', endedAt: '2026-10-18T09:00:00', win: true, kda: 6, csPerMinute: 7, damagePerMinute: 800, goldPerMinute: 400, visionPerMinute: 1, killParticipation: null },
    { matchId: 'TR1_1', endedAt: '2026-10-18T10:00:00', win: true, kda: 12, csPerMinute: 7.4, damagePerMinute: 900, goldPerMinute: 420, visionPerMinute: 1.1, killParticipation: 0.6 },
  ],
}

const tableRows = (name: string) =>
  within(screen.getByRole('table', { name }))
    .getAllByRole('row')
    .slice(1)

const championOrder = () =>
  tableRows(t.championsTitle)
    // The name, not the icon (which shows initials until the picture loads)
    .map((row) => within(row).getByRole('rowheader').querySelector('.table-champion > span:last-child')?.textContent)

const statsCalls = (calls: Call[]) => calls.filter((call) => call.url.pathname === '/api/v1/lol/players/p1/stats').map((call) => call.url.search)

describe('League player page, older server (no stats endpoint)', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('still renders an answer without any of the detailed fields', async () => {
    fakeServer({ detail: oldDetail })
    const { user } = renderPlayer()

    // The overview falls back to the averages of the player answer
    expect(await screen.findByTestId('tile-winRate')).toBeInTheDocument()
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

    await user.click(screen.getByRole('tab', { name: t.playerTabs.champions }))
    expect(championOrder()).toEqual(['Ahri', 'Zed'])

    await user.click(screen.getByRole('tab', { name: t.playerTabs.matches }))
    const rows = screen.getAllByTestId('match-row')
    expect(rows).toHaveLength(3)
    expect(within(rows[0]).queryByLabelText(t.itemsLabel)).toBeNull()
    // The damage it does have is still shown
    expect(within(rows[0]).getByText(t.detailDamage)).toBeInTheDocument()
  })

  it('shows tiles, roles, queues and the LP chart from the player answer', async () => {
    fakeServer({
      detail: newDetail,
      history: [
        { recordedAt: '2026-10-17T10:00:00', tier: 'GOLD', division: 'III', lp: 80 },
        { recordedAt: '2026-10-18T10:00:00', tier: 'GOLD', division: 'II', lp: 45 },
      ],
    })
    renderPlayer()

    expect(await screen.findByTestId('lp-chart')).toBeInTheDocument()
    for (const key of ['winRate', 'kda', 'cs', 'kp', 'damage', 'gold', 'vision', 'damageShare']) {
      expect(await screen.findByTestId(`tile-${key}`)).toBeInTheDocument()
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
    fakeServer({ detail: newDetail, history: [{ recordedAt: '2026-10-18T10:00:00', tier: 'GOLD', division: 'II', lp: 45 }] })
    renderPlayer()

    expect(await screen.findByTestId('tile-winRate')).toBeInTheDocument()
    expect(screen.queryByTestId('lp-chart')).toBeNull()
  })

  it('sorts the champions table by the tapped column, and reverses on a second tap', async () => {
    fakeServer({ detail: newDetail })
    const { user } = renderPlayer('/game/p1?tab=champions')

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

  it('shows items, spells, numbers and the multikill badge on a match, and opens the match page on a tap', async () => {
    fakeServer({ detail: newDetail })
    const { user } = renderPlayer('/game/p1?tab=matches')

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

    await user.click(first)
    expect(await screen.findByTestId('location')).toHaveTextContent('/game/match/TR1_1?player=p1')
  })

  it('filters the match list with the queue chips and the window', async () => {
    fakeServer({ detail: newDetail })
    const { user } = renderPlayer('/game/p1?tab=matches')

    expect(await screen.findAllByTestId('match-row')).toHaveLength(3)
    await user.click(within(screen.getByRole('group', { name: t.statsQueueLabel })).getByRole('button', { name: t.statsQueues.ARAM }))
    expect(screen.getAllByTestId('match-row')).toHaveLength(1)
    await user.click(within(screen.getByRole('group', { name: t.statsQueueLabel })).getByRole('button', { name: t.statsQueues.FLEX }))
    expect(screen.queryAllByTestId('match-row')).toHaveLength(0)
    expect(screen.getByText(t.statsNoGames)).toBeInTheDocument()
  })

  it('opens the match page from a record, and shows records of matches no longer stored without a link', async () => {
    fakeServer({ detail: newDetail })
    const { user } = renderPlayer()

    const record = await screen.findByRole('button', { name: new RegExp(t.recordNames.mostKills) })
    expect(screen.getByText(t.recordNames.longestGame)).toBeInTheDocument()
    expect(screen.getByText('40:00')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: new RegExp(t.recordNames.longestGame) })).toBeNull()

    await user.click(record)
    expect(await screen.findByTestId('location')).toHaveTextContent('/game/match/TR1_3?player=p1')
  })
})

describe('League player page with deep stats', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows every overview group, the trend chart, the win-rate splits and the objectives', async () => {
    fakeServer({ detail: newDetail, stats: deepStats })
    renderPlayer()

    for (const group of ['combat', 'farm', 'vision', 'multikills']) {
      expect(await screen.findByRole('region', { name: t.statGroups[group] })).toBeInTheDocument()
    }
    for (const key of ['winRate', 'kda', 'kdaLine', 'soloKills', 'firstBlood', 'ccTime', 'deadShare', 'damagePerGold', 'gameLength', 'wardsKilled', 'controlWards', 'penta']) {
      expect(screen.getByTestId(`tile-${key}`)).toBeInTheDocument()
    }
    expect(within(screen.getByTestId('tile-kdaLine')).getByText('7 / 3 / 9')).toBeInTheDocument()

    // KDA by default; KP has only two games with a number, still enough to draw
    const trend = screen.getByTestId('trend-chart')
    const metrics = within(trend).getByRole('group', { name: t.trendMetricLabel })
    expect(within(metrics).getByRole('button', { name: t.trendMetrics.kda })).toHaveAttribute('aria-pressed', 'true')
    expect(within(metrics).getByRole('button', { name: t.trendMetrics.killParticipation })).toBeInTheDocument()

    const sides = screen.getByRole('list', { name: t.sidesTitle })
    expect(within(sides).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      expect.stringContaining(t.sideNames[100]),
      expect.stringContaining(t.sideNames[200]),
    ])
    expect(screen.getByRole('list', { name: t.durationsTitle })).toHaveTextContent(t.durationLabel('25-30'))
    expect(screen.getByRole('list', { name: t.weekdaysTitle })).toBeInTheDocument()

    const firsts = tableRows(t.objectivesTitle).map((row) => row.textContent)
    expect(firsts[1]).toContain(t.firstObjectives.firstTower)
    expect(firsts[1]).toContain('2–0')
    expect(screen.getByRole('note')).toHaveTextContent(t.fullDataNote(2, 3))
  })

  it('asks for the chosen queue and window, with the phone time zone, and keeps them in the URL', async () => {
    const calls = fakeServer({ detail: newDetail, stats: deepStats })
    const { user, router } = renderPlayer()

    await screen.findByTestId('tile-kdaLine')
    const tz = tzOffsetMinutes()
    expect(statsCalls(calls)).toEqual([`?queue=ALL&games=20&tzOffsetMinutes=${tz}`])

    await user.click(within(screen.getByRole('group', { name: t.statsQueueLabel })).getByRole('button', { name: t.statsQueues.SOLO }))
    await waitFor(() => expect(statsCalls(calls)).toContain(`?queue=SOLO&games=20&tzOffsetMinutes=${tz}`))
    await user.click(screen.getByRole('button', { name: t.statsWindowChip(50) }))
    await waitFor(() => expect(statsCalls(calls)).toContain(`?queue=SOLO&games=50&tzOffsetMinutes=${tz}`))
    expect(screen.getByRole('button', { name: t.statsWindowChip(50) })).toHaveAttribute('aria-pressed', 'true')
    expect(router.state.location.search).toBe('?queue=SOLO&games=50')
  })

  it('keeps the tab in the URL and shows every champion column', async () => {
    fakeServer({ detail: newDetail, stats: deepStats })
    const { user, router } = renderPlayer('/game/p1?tab=champions')

    const table = await screen.findByRole('table', { name: t.championsTitle })
    expect(screen.getByRole('tab', { name: t.playerTabs.champions })).toHaveAttribute('aria-selected', 'true')
    for (const label of [t.killsColumn, t.deathsColumn, t.assistsColumn, t.goldPerMinColumn, t.visionPerMinColumn, t.kpColumn, t.damageShareColumn, t.pentasColumn, t.lastPlayedColumn]) {
      expect(within(table).getByRole('columnheader', { name: label })).toBeInTheDocument()
    }
    expect(championOrder()).toEqual(['Ahri', 'Zed'])

    await user.click(screen.getByRole('tab', { name: t.playerTabs.opponents }))
    expect(router.state.location.search).toBe('?tab=opponents')
    const matchup = tableRows(t.matchupsTitle)[0]
    expect(matchup).toHaveTextContent('Syndra')
    expect(within(matchup).getByText('+450')).toHaveClass('good')
    expect(within(matchup).getByText('−12')).toHaveClass('bad')
    expect(tableRows(t.enemyChampionsTitle)[0]).toHaveTextContent('Jinx')
    expect(tableRows(t.allyChampionsTitle)[0]).toHaveTextContent('LeeSin')
  })

  it('links followed duos to their page and follows the others with their Riot ID on the same server', async () => {
    const calls = fakeServer({ detail: newDetail, stats: deepStats })
    const toast = vi.fn()
    const { user } = renderPlayer('/game/p1?tab=duos', toast)

    await screen.findByRole('table', { name: t.duosTitle })
    const [buddy, stranger] = tableRows(t.duosTitle)
    expect(within(buddy).getByRole('link', { name: /Buddy/ })).toHaveAttribute('href', '/game/d1')
    expect(within(buddy).queryByRole('button', { name: new RegExp(t.follow) })).toBeNull()
    expect(within(stranger).queryByRole('link')).toBeNull()
    // Not in a group: no group duos
    expect(screen.queryByText(t.groupDuosTitle)).toBeNull()

    await user.click(within(stranger).getByRole('button', { name: `${t.follow}: Stranger` }))
    await waitFor(() => expect(within(tableRows(t.duosTitle)[1]).getByRole('link', { name: /Stranger/ })).toHaveAttribute('href', '/game/d2'))
    expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ riotId: 'Stranger#EUW', platform: 'TR1' })
    expect(toast).toHaveBeenCalledWith({ message: t.duoFollowed('Stranger') })
  })

  it('shows the group duos when in a group', async () => {
    fakeServer({
      detail: newDetail,
      stats: deepStats,
      groupDuos: [{ a: { userId: 1, displayName: 'Behlül' }, b: { userId: 2, displayName: 'Ece' }, games: 5, wins: 4 }],
    })
    renderPlayer('/game/p1?tab=duos')

    await screen.findByRole('table', { name: t.groupDuosTitle })
    const [pair] = tableRows(t.groupDuosTitle)
    expect(pair).toHaveTextContent('Behlül')
    expect(pair).toHaveTextContent('Ece')
    expect(pair).toHaveTextContent('4–1')
  })

  it('lists the build without consumables or trinkets, with rune icons and patches in version order', async () => {
    fakeServer({ detail: newDetail, stats: deepStats })
    renderPlayer('/game/p1?tab=build')

    // Potions and the trinket go once item.json has arrived
    await waitFor(() => expect(tableRows(t.itemsTitle)).toHaveLength(2))
    expect(tableRows(t.itemsTitle).map((row) => row.textContent)).toEqual([expect.stringContaining("Sorcerer's Shoes"), expect.stringContaining("Luden's Companion")])

    const keystone = tableRows(t.keystonesTitle)[0]
    await waitFor(() => expect(within(keystone).getByRole('img', { name: 'Electrocute' })).toHaveAttribute('src', 'https://ddragon.leagueoflegends.com/cdn/img/perk-images/Styles/Domination/Electrocute/Electrocute.png'))
    expect(keystone).toHaveTextContent('Electrocute')

    const spells = tableRows(t.spellsTitle)[0]
    await waitFor(() => expect(spells.querySelectorAll('img')).toHaveLength(2))

    // Newest patch first: 14.10 is newer than 14.9
    expect(tableRows(t.patchesTitle).map((row) => within(row).getByRole('rowheader').textContent)).toEqual(['14.10', '14.9'])
  })

  it('says so when the filter has no games', async () => {
    fakeServer({ detail: newDetail, stats: { ...deepStats, gamesFound: 0, gamesWithFullData: 0, wins: 0 } })
    renderPlayer('/game/p1?tab=champions')

    expect(await screen.findByText(t.statsNoGames)).toBeInTheDocument()
    expect(screen.queryByRole('table')).toBeNull()
  })
})
