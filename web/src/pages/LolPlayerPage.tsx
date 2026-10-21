import { keepPreviousData, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { LolDeepStats, LolDuo, LolPlayerDetail, LolRankPoint, LolStatsQueue, LolStatsWindow } from '../api/types'
import { useToast } from '../components/contexts'
import { LolAvatar } from '../components/LolBits'
import { LiveBadge, LiveGameSheet, MasteryTab, SoloFlexRanks } from '../components/LolLiveSections'
import {
  ChampionCountTable,
  DeepChampionsTable,
  DuoTable,
  FullDataNote,
  GroupDuosTable,
  ItemsTable,
  KeystonesTable,
  MatchupsTable,
  ObjectivesSection,
  PatchesTable,
  SplitBars,
  SpellsTable,
  StatGroups,
  TrendChart,
} from '../components/LolDeepSections'
import { ChampionsTable, MatchRow, QueueBars, RankChart, RecordList, RoleBars, StatTiles, StreakChip } from '../components/LolPlayerSections'
import { useItemData, useRuneData } from '../components/useDdragonData'
import { useDdragonVersion } from '../components/useDdragonVersion'
import { useSummonerSpells } from '../components/useSummonerSpells'
import { SetListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { ControllerIcon } from '../components/Icons'
import { errorMessage } from '../lib/format'
import { t, termLang } from '../lib/i18n'
import { rankLabel, seasonWinRate, timeAgo } from '../lib/lol'
import { peakAboveCurrent } from '../lib/lolLive'
import { inQueue, matchPath, PLAYER_TABS, playerTab, QUEUE_CHIPS, statsQueue, statsWindow, tzOffsetMinutes, WINDOW_CHIPS, type PlayerTab } from '../lib/lolDeep'
import { currentStreak } from '../lib/lolStats'

export function LolPlayerPage() {
  const puuid = useParams().puuid ?? ''
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [refreshing, setRefreshing] = useState(false)
  const query = useQuery({ queryKey: queryKeys.lolPlayer(puuid), queryFn: () => api.lolPlayer(puuid) })
  // Only the chart needs it; an older server answers 404 and the chart simply stays hidden
  const rankHistory = useQuery({ queryKey: queryKeys.lolRankHistory(puuid), queryFn: () => api.lolRankHistory(puuid), retry: false })
  const version = useDdragonVersion()

  const refresh = async () => {
    setRefreshing(true)
    try {
      const fresh = await api.refreshLolPlayer(puuid)
      queryClient.setQueryData(queryKeys.lolPlayer(puuid), fresh)
      // The follow list and the group board show this player's rank too; every stats filter counts the new games
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.lolPlayers }),
        queryClient.invalidateQueries({ queryKey: queryKeys.lolGroup }),
        queryClient.invalidateQueries({ queryKey: queryKeys.lolRankHistory(puuid) }),
        queryClient.invalidateQueries({ queryKey: [...queryKeys.lolPlayer(puuid), 'stats'] }),
        queryClient.invalidateQueries({ queryKey: queryKeys.lolMastery(puuid) }),
      ])
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      setRefreshing(false)
    }
  }

  const data = query.data
  return (
    <div className="page">
      <div className="top-bar">
        <button className="icon-btn" onClick={() => navigate(-1)} aria-label={t.back}>
          ←
        </button>
        <h1>{data ? data.player.gameName : t.gameTitle}</h1>
        {data && (
          <button className={refreshing ? 'icon-btn spinning' : 'icon-btn'} onClick={refresh} disabled={refreshing} aria-label={t.refresh}>
            ↻
          </button>
        )}
      </div>

      {query.isPending ? (
        <SetListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : (
        <PlayerContent puuid={puuid} data={data!} rankHistory={rankHistory.data} version={version} />
      )}
    </div>
  )
}

/**
 * Hero on top, then the tabs and the filter (both sticky), then the chosen tab. The tab and the filter live in
 * the URL (?tab=&queue=&games=), so coming back from a match page shows the same place.
 */
function PlayerContent({ puuid, data, rankHistory, version }: {
  puuid: string
  data: LolPlayerDetail
  rankHistory: LolRankPoint[] | undefined
  version: string | null
}) {
  const { player, summary } = data
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = playerTab(searchParams.get('tab'))
  const queue = statsQueue(searchParams.get('queue'))
  const games = statsWindow(searchParams.get('games'))
  const stats = useQuery({
    queryKey: queryKeys.lolStats(puuid, queue, games),
    queryFn: () => api.lolStats(puuid, queue, games, tzOffsetMinutes()),
    // A new filter keeps the old numbers on screen (dimmed) instead of flashing a skeleton
    placeholderData: keepPreviousData,
    enabled: summary.games > 0,
  })
  const updated = player.refreshedAt ? timeAgo(player.refreshedAt) : null
  const streak = currentStreak(summary.streak, summary.form)
  // The server caches the answer for a minute, so polling at that pace costs Riot at most one request a minute.
  // Errors (an older server, the budget running low) simply hide the badge
  const liveQuery = useQuery({ queryKey: queryKeys.lolLive(puuid), queryFn: () => api.lolLive(puuid), refetchInterval: 60_000, retry: false })
  const live = liveQuery.data ?? null
  const [liveOpen, setLiveOpen] = useState(false)
  const closeLive = useCallback(() => setLiveOpen(false), [])

  // The tab strip scrolls sideways on a phone: the open tab is brought into view (Matches sits off-screen)
  const tabList = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const list = tabList.current
    const selected = list?.querySelector<HTMLElement>('[aria-selected="true"]')
    if (!list || !selected) return
    list.scrollLeft = Math.max(0, selected.offsetLeft - list.offsetLeft - (list.clientWidth - selected.offsetWidth) / 2)
  }, [tab])

  // Sits right above the sticky bar: a tab chosen further down the page starts at its top, not mid-way
  const barAnchor = useRef<HTMLDivElement>(null)
  const openTab = (key: PlayerTab) => {
    setParam('tab', key, 'overview')
    const top = barAnchor.current?.getBoundingClientRect().top ?? 0
    if (top < 0) window.scrollTo?.({ top: window.scrollY + top })
  }

  const setParam = (key: string, value: string, fallback: string) =>
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current)
        if (value === fallback) params.delete(key)
        else params.set(key, value)
        return params
      },
      { replace: true },
    )

  return (
    <>
      <section className="lol-hero" data-testid="lol-hero">
        <LolAvatar version={version} iconId={player.profileIconId} name={player.gameName} size={72} />
        <div className="lol-hero-text">
          <span className="lol-hero-name">
            {player.gameName}
            <small>#{player.tagLine}</small>
          </span>
          <span className="muted lol-hero-meta">
            {player.platform}
            {player.summonerLevel != null && ` · ${t.level(player.summonerLevel)}`}
            {updated && ` · ${t.updatedAgo(updated.value, updated.unit)}`}
          </span>
          <SoloFlexRanks rank={player.rank} flexRank={player.flexRank} />
          {player.rank && (
            <span className="muted num lol-season">
              {t.seasonRecord(player.rank.wins, player.rank.losses, Math.round(seasonWinRate(player.rank) * 100))}
            </span>
          )}
          {data.seasonPeak && peakAboveCurrent(data.seasonPeak, player.rank) && (
            <span className="lol-peak num" data-testid="season-peak">
              {t.seasonPeak(`${rankLabel({ ...data.seasonPeak, wins: 0, losses: 0 })} ${t.lp(data.seasonPeak.lp)}`)}
            </span>
          )}
        </div>
        {summary.games > 0 && <StreakChip streak={streak} />}
        {live && <LiveBadge game={live} onOpen={() => setLiveOpen(true)} />}
      </section>
      {live && liveOpen && <LiveGameSheet game={live} version={version} viewer={puuid} onClose={closeLive} />}

      {summary.games === 0 ? (
        <>
          <RankChart history={rankHistory} />
          <MessageState icon={<ControllerIcon size={32} />} title={t.lolNoMatches} />
        </>
      ) : (
        <>
          <div ref={barAnchor} aria-hidden />
          <div className="lol-sticky">
            <div ref={tabList} className="chip-row lol-tabs" role="tablist" aria-label={t.playerTabsLabel}>
              {PLAYER_TABS.map((key) => (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  id={`lol-tab-${key}`}
                  aria-selected={key === tab}
                  aria-controls="lol-tab-panel"
                  className={key === tab ? 'lol-tab selected' : 'lol-tab'}
                  lang={termLang(t.playerTabs[key])}
                  onClick={() => openTab(key)}
                >
                  {t.playerTabs[key]}
                </button>
              ))}
            </div>
            {/* Mastery is not about stored games, so the game filter would do nothing there */}
            {tab !== 'mastery' && <FilterBar queue={queue} games={games} onQueue={(q) => setParam('queue', q, 'ALL')} onGames={(g) => setParam('games', String(g), '20')} />}
          </div>

          <div id="lol-tab-panel" role="tabpanel" aria-labelledby={`lol-tab-${tab}`} className={stats.isPlaceholderData ? 'lol-tab-panel stale' : 'lol-tab-panel'}>
            <TabContent tab={tab} puuid={puuid} data={data} stats={stats} queue={queue} games={games} rankHistory={rankHistory} version={version} />
          </div>
        </>
      )}
    </>
  )
}

function FilterBar({ queue, games, onQueue, onGames }: {
  queue: LolStatsQueue
  games: LolStatsWindow
  onQueue: (queue: LolStatsQueue) => void
  onGames: (games: LolStatsWindow) => void
}) {
  return (
    <div className="lol-filter">
      <div className="lol-filter-group" role="group" aria-label={t.statsQueueLabel}>
        {QUEUE_CHIPS.map((q) => (
          <button key={q} type="button" aria-pressed={q === queue} className={q === queue ? 'selected' : ''} onClick={() => onQueue(q)}>
            {t.statsQueues[q]}
          </button>
        ))}
      </div>
      <div className="lol-filter-group window" role="group" aria-label={t.statsWindowLabel}>
        {WINDOW_CHIPS.map((n) => (
          <button key={n} type="button" aria-pressed={n === games} aria-label={t.statsWindowChip(n)} className={n === games ? 'selected' : ''} onClick={() => onGames(n)}>
            {n}
          </button>
        ))}
      </div>
    </div>
  )
}

function TabContent({ tab, puuid, data, stats, queue, games, rankHistory, version }: {
  tab: PlayerTab
  puuid: string
  data: LolPlayerDetail
  stats: UseQueryResult<LolDeepStats>
  queue: LolStatsQueue
  games: LolStatsWindow
  rankHistory: LolRankPoint[] | undefined
  version: string | null
}) {
  const navigate = useNavigate()
  const { summary, matches } = data
  // Stored matches, not the stats: the list works on any server and is filtered here the same way
  if (tab === 'matches') return <MatchList puuid={puuid} data={data} queue={queue} games={games} version={version} />
  // Mastery is not about the stored games, so the queue and window filter do not apply to it
  if (tab === 'mastery') return <MasteryTab puuid={puuid} version={version} />

  // An older server has no stats endpoint (404): the overview and champions fall back to the summary
  const fallback =
    stats.isError && (tab === 'overview' || tab === 'champions') ? (
      tab === 'overview' ? (
        <>
          <StatTiles summary={summary} />
          <FormStrip form={summary.form} />
          <RankChart history={rankHistory} />
          <RoleBars roles={summary.roles} />
          <QueueBars queues={summary.queues} />
          <RecordList records={summary.records} matches={matches} version={version} onShowMatch={(id) => navigate(matchPath(id, puuid))} />
        </>
      ) : (
        <ChampionsTable champions={summary.champions} version={version} />
      )
    ) : null
  if (fallback) return fallback
  if (stats.isPending) return <SetListSkeleton />
  if (stats.isError) return <ErrorState error={stats.error} onRetry={() => stats.refetch()} />

  const deep = stats.data
  if (deep.gamesFound === 0) {
    return (
      <>
        {tab === 'overview' && <RankChart history={rankHistory} />}
        <MessageState icon={<ControllerIcon size={32} />} title={t.statsNoGames} body={t.statsNoGamesBody} />
      </>
    )
  }

  switch (tab) {
    case 'overview':
      return (
        <>
          <StatGroups stats={deep} />
          <FormStrip form={summary.form} />
          <RankChart history={rankHistory} />
          <TrendChart trend={deep.trend} />
          <RoleBars roles={deep.roles} />
          {queue === 'ALL' && <QueueBars queues={summary.queues} />}
          <FullDataNote stats={deep} />
          <SplitBars stats={deep} />
          <ObjectivesSection objectives={deep.objectives} />
          <RecordList records={summary.records} matches={matches} version={version} onShowMatch={(id) => navigate(matchPath(id, puuid))} />
        </>
      )
    case 'champions':
      return <DeepChampionsTable champions={deep.champions} version={version} />
    case 'duos':
      return <DuosTab puuid={puuid} data={data} stats={deep} version={version} />
    case 'opponents':
      return (
        <>
          <FullDataNote stats={deep} />
          <MatchupsTable matchups={deep.matchups} version={version} />
          <ChampionCountTable title={t.enemyChampionsTitle} rows={deep.enemyChampions} version={version} />
          <ChampionCountTable title={t.allyChampionsTitle} rows={deep.allyChampions} version={version} />
        </>
      )
    case 'build':
      return <BuildTab stats={deep} version={version} />
  }
}

function FormStrip({ form }: { form: boolean[] }) {
  return (
    <div className="lol-form" aria-label={t.recentForm}>
      {form.map((win, i) => (
        <span key={i} className={win ? 'win' : 'loss'}>
          {win ? t.winShort : t.lossShort}
        </span>
      ))}
    </div>
  )
}

function DuosTab({ puuid, data, stats, version }: { puuid: string; data: LolPlayerDetail; stats: LolDeepStats; version: string | null }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  // Only shown when the caller is in a group (204 otherwise); an older server's 404 just hides it
  const groupDuos = useQuery({ queryKey: queryKeys.lolGroupDuos, queryFn: api.lolGroupDuos, retry: false })

  const follow = async (duo: LolDuo) => {
    try {
      // Same server as the page's player: teammates always are
      await api.trackLolPlayer(`${duo.riotName}#${duo.riotTag}`, data.player.platform)
      // The row turns into a link right away, without waiting for the stats to load again
      queryClient.setQueriesData<LolDeepStats>({ queryKey: [...queryKeys.lolPlayer(puuid), 'stats'] }, (old) =>
        old ? { ...old, duos: old.duos.map((d) => (d.puuid === duo.puuid ? { ...d, tracked: true } : d)) } : old,
      )
      toast({ message: t.duoFollowed(duo.riotName ?? '') })
      await queryClient.invalidateQueries({ queryKey: queryKeys.lolPlayers })
    } catch (error) {
      toast({ message: errorMessage(error) })
    }
  }

  return (
    <>
      <FullDataNote stats={stats} />
      <DuoTable duos={stats.duos} version={version} playerName={data.player.gameName} onFollow={follow} />
      <GroupDuosTable duos={groupDuos.data} />
    </>
  )
}

function BuildTab({ stats, version }: { stats: LolDeepStats; version: string | null }) {
  const itemData = useItemData(version)
  const runes = useRuneData(version)
  const spellFiles = useSummonerSpells(version)
  return (
    <>
      <FullDataNote stats={stats} />
      <ItemsTable items={stats.items} version={version} itemData={itemData} />
      <KeystonesTable keystones={stats.keystones} runes={runes} />
      <SpellsTable spells={stats.spells} version={version} spellFiles={spellFiles} />
      <PatchesTable patches={stats.patches} />
    </>
  )
}

/** The stored matches (up to 100) under the same filter as the stats; each row opens the match page. */
function MatchList({ puuid, data, queue, games, version }: {
  puuid: string
  data: LolPlayerDetail
  queue: LolStatsQueue
  games: LolStatsWindow
  version: string | null
}): ReactNode {
  const spells = useSummonerSpells(version)
  const shown = data.matches.filter((m) => inQueue(m.queueId, queue)).slice(0, games)
  if (shown.length === 0) return <MessageState icon={<ControllerIcon size={32} />} title={t.statsNoGames} body={t.statsNoGamesBody} />
  return (
    <>
      <h2 className="section-title">{t.recentMatches}</h2>
      <div className="lol-matches">
        {shown.map((m, i) => (
          <MatchRow key={m.matchId} match={m} version={version} spells={spells} index={i} to={matchPath(m.matchId, puuid)} />
        ))}
      </div>
    </>
  )
}
