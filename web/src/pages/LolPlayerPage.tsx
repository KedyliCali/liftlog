import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { LolMatch, LolPlayerDetail } from '../api/types'
import { useToast } from '../components/contexts'
import { CountUp } from '../components/CountUp'
import { ChampionIcon, LolAvatar, RankBadge } from '../components/LolBits'
import { useDdragonVersion } from '../components/useDdragonVersion'
import { SetListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { ControllerIcon } from '../components/Icons'
import { errorMessage, formatKg } from '../lib/format'
import { t } from '../lib/i18n'
import { formatDuration, kdaRatio, queueKey, seasonWinRate, timeAgo } from '../lib/lol'

export function LolPlayerPage() {
  const puuid = useParams().puuid ?? ''
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [refreshing, setRefreshing] = useState(false)
  const query = useQuery({ queryKey: queryKeys.lolPlayer(puuid), queryFn: () => api.lolPlayer(puuid) })
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
        <PlayerContent data={data!} version={version} />
      )}
    </div>
  )
}

function PlayerContent({ data, version }: { data: LolPlayerDetail; version: string | null }) {
  const { player, summary, matches } = data
  const updated = player.refreshedAt ? timeAgo(player.refreshedAt) : null
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
      </section>

      {summary.games === 0 ? (
        <MessageState icon={<ControllerIcon size={32} />} title={t.lolNoMatches} />
      ) : (
        <>
          <div className="lol-stats">
            <Stat label={t.winRateLabel} value={<CountUp value={Math.round((summary.wins / summary.games) * 100)} format={(n) => `%${n}`} />} sub={t.gamesCount(summary.wins, summary.games)} />
            <Stat label="KDA" value={<CountUp value={summary.kda} format={(n) => formatKg(Math.round(n * 100) / 100)} />} sub={`${formatKg(summary.avgKills)} / ${formatKg(summary.avgDeaths)} / ${formatKg(summary.avgAssists)}`} />
            <Stat label={t.csPerMin} value={<CountUp value={summary.csPerMinute} format={(n) => formatKg(Math.round(n * 10) / 10)} />} />
          </div>

          <div className="lol-form" aria-label={t.recentForm}>
            {summary.form.map((win, i) => (
              <span key={i} className={win ? 'win' : 'loss'}>
                {win ? t.winShort : t.lossShort}
              </span>
            ))}
          </div>

          <h2 className="section-title">{t.topChampions}</h2>
          <div className="lol-champions">
            {summary.champions.map((c) => (
              <div key={c.champion} className="lol-champion">
                <ChampionIcon version={version} champion={c.champion} size={48} />
                <strong>{c.champion}</strong>
                <span className="muted num">
                  {t.gamesCount(c.wins, c.games)} · {formatKg(c.kda)} KDA
                </span>
              </div>
            ))}
          </div>

          <h2 className="section-title">{t.recentMatches}</h2>
          <div className="lol-matches">
            {matches.map((m, i) => (
              <MatchRow key={m.matchId} match={m} version={version} index={i} />
            ))}
          </div>
        </>
      )}
    </>
  )
}

function Stat({ label, value, sub }: { label: string; value: ReactNode; sub?: string }) {
  return (
    <div className="lol-stat">
      <small>{label}</small>
      <strong>{value}</strong>
      {sub && <span className="muted num">{sub}</span>}
    </div>
  )
}

function MatchRow({ match, version, index }: { match: LolMatch; version: string | null; index: number }) {
  const ago = timeAgo(match.endedAt)
  const kda = kdaRatio(match.kills, match.deaths, match.assists)
  return (
    <div className={match.win ? 'lol-match win' : 'lol-match loss'} style={{ animationDelay: `${Math.min(index, 8) * 30}ms` }}>
      <ChampionIcon version={version} champion={match.champion} />
      <div className="lol-match-main">
        <strong className="num">
          {match.kills} / <span className="deaths">{match.deaths}</span> / {match.assists}
        </strong>
        <span className="muted">
          {t.queues[queueKey(match.queueId)]} · {formatDuration(match.durationS)}
        </span>
      </div>
      <div className="lol-match-side num">
        <span>{formatKg(Math.round(kda * 10) / 10)} KDA</span>
        <span className="muted">
          {match.cs} CS · {t.ago(ago.value, ago.unit)}
        </span>
      </div>
    </div>
  )
}
