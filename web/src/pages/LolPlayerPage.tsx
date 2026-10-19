import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { LolPlayerDetail, LolRankPoint } from '../api/types'
import { useToast } from '../components/contexts'
import { LolAvatar, RankBadge } from '../components/LolBits'
import { ChampionsTable, MatchRow, QueueBars, RankChart, RecordList, RoleBars, StatTiles, StreakChip } from '../components/LolPlayerSections'
import { useDdragonVersion } from '../components/useDdragonVersion'
import { useSummonerSpells } from '../components/useSummonerSpells'
import { SetListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { ControllerIcon } from '../components/Icons'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { seasonWinRate, timeAgo } from '../lib/lol'
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
      // The follow list and the group board show this player's rank too
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.lolPlayers }),
        queryClient.invalidateQueries({ queryKey: queryKeys.lolGroup }),
        queryClient.invalidateQueries({ queryKey: queryKeys.lolRankHistory(puuid) }),
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
        <PlayerContent data={data!} rankHistory={rankHistory.data} version={version} />
      )}
    </div>
  )
}

function PlayerContent({ data, rankHistory, version }: { data: LolPlayerDetail; rankHistory: LolRankPoint[] | undefined; version: string | null }) {
  const { player, summary, matches } = data
  const spells = useSummonerSpells(version)
  const [expandedMatch, setExpandedMatch] = useState<string | null>(null)
  // A record asks for its match: once the row has opened (and grown), it is scrolled into view
  const scrollTarget = useRef<string | null>(null)
  const updated = player.refreshedAt ? timeAgo(player.refreshedAt) : null
  const streak = currentStreak(summary.streak, summary.form)

  // jsdom has no scrollIntoView
  const scrollToMatch = (matchId: string) =>
    document.getElementById(`match-${matchId}`)?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })

  useEffect(() => {
    if (!expandedMatch || scrollTarget.current !== expandedMatch) return
    scrollTarget.current = null
    scrollToMatch(expandedMatch)
  }, [expandedMatch])

  const showMatch = (matchId: string) => {
    // Already open: nothing re-renders, so scroll right away
    if (matchId === expandedMatch) return scrollToMatch(matchId)
    scrollTarget.current = matchId
    setExpandedMatch(matchId)
  }

  return (
    <>
      <section className="lol-hero">
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
          <RankBadge rank={player.rank} />
          {player.rank && (
            <span className="muted num lol-season">
              {t.seasonRecord(player.rank.wins, player.rank.losses, Math.round(seasonWinRate(player.rank) * 100))}
            </span>
          )}
        </div>
        {summary.games > 0 && <StreakChip streak={streak} />}
      </section>

      <RankChart history={rankHistory} />

      {summary.games === 0 ? (
        <MessageState icon={<ControllerIcon size={32} />} title={t.lolNoMatches} />
      ) : (
        <>
          <StatTiles summary={summary} />

          <div className="lol-form" aria-label={t.recentForm}>
            {summary.form.map((win, i) => (
              <span key={i} className={win ? 'win' : 'loss'}>
                {win ? t.winShort : t.lossShort}
              </span>
            ))}
          </div>

          <RoleBars roles={summary.roles} />
          <ChampionsTable champions={summary.champions} version={version} />
          <QueueBars queues={summary.queues} />
          <RecordList records={summary.records} matches={matches} version={version} onShowMatch={showMatch} />

          <h2 className="section-title">{t.recentMatches}</h2>
          <div className="lol-matches">
            {matches.map((m, i) => (
              <MatchRow
                key={m.matchId}
                match={m}
                version={version}
                spells={spells}
                index={i}
                expanded={expandedMatch === m.matchId}
                onToggle={() => setExpandedMatch((open) => (open === m.matchId ? null : m.matchId))}
              />
            ))}
          </div>
        </>
      )}
    </>
  )
}
