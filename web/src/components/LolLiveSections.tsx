import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { useNow } from './useNow'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { LolLiveGame, LolLiveParticipant, LolRank } from '../api/types'
import { t } from '../lib/i18n'
import { queueKey, rankLabel, timeAgo } from '../lib/lol'
import { compact } from '../lib/lolFormat'
import { gameClock, liveElapsedSeconds, liveTeams, lpChangeText, masteryRows } from '../lib/lolLive'
import { ChampionIcon, RankBadge, RuneIcon, SpellIcon } from './LolBits'
import { Sheet } from './Sheet'
import { SetListSkeleton } from './Skeletons'
import { ErrorState, MessageState } from './StateViews'
import { ControllerIcon } from './Icons'
import { useChampionKeys, useRuneData } from './useDdragonData'
import { useSummonerSpells } from './useSummonerSpells'

// Live games, mastery, flex ranks and LP changes on the League pages.

/** "+18 LP" in green or "−21 LP" in red; nothing when the change is not known. */
export function LpChip({ change }: { change: number | null | undefined }) {
  if (change == null) return null
  const text = lpChangeText(change)
  return (
    <span className={change > 0 ? 'lp-chip up' : 'lp-chip down'} aria-label={t.lpChangeLabel(text)} data-testid="lp-chip">
      {text}
    </span>
  )
}

/** One queue's rank with its short name in front: "SOLO Gold II 45 LP". */
export function QueueRank({ label, rank }: { label: string; rank: LolRank | null | undefined }) {
  return (
    <span className="queue-rank">
      <span className="queue-rank-label">{label}</span>
      <RankBadge rank={rank ?? null} />
    </span>
  )
}

/** Solo and flex side by side; flex is left out for an older server that does not send it. */
export function SoloFlexRanks({ rank, flexRank }: { rank: LolRank | null; flexRank?: LolRank | null }) {
  return (
    <span className="solo-flex">
      <QueueRank label={t.soloQueueShort} rank={rank} />
      {flexRank !== undefined && <QueueRank label={t.flexQueueShort} rank={flexRank} />}
    </span>
  )
}

/** The hero's "Live 12:34" tag, the clock ticking on; a tap opens the live game. */
export function LiveBadge({ game, onOpen }: { game: LolLiveGame; onOpen: () => void }) {
  const now = useNow()
  const clock = gameClock(liveElapsedSeconds(game, now))
  return (
    <button type="button" className="live-badge" onClick={onOpen} aria-label={t.liveBadgeLabel(clock)} data-testid="live-badge">
      <span className="live-dot" aria-hidden />
      {t.liveBadge}
      <span className="num">{clock}</span>
    </button>
  )
}

/** "Şu an oyunda" next to a group member, with the champion they are on. */
export function InGameTag({ name, championId, version, championKeys }: {
  name: string
  championId: number | null
  version: string | null
  championKeys: Record<number, string> | null
}) {
  const champion = championId != null ? championKeys?.[championId] : undefined
  return (
    <span className="in-game-tag" aria-label={t.inGameNowLabel(name)} data-testid="in-game-tag">
      <span className="live-dot" aria-hidden />
      {champion && <ChampionIcon version={version} champion={champion} size={18} />}
      {t.inGameNow}
    </span>
  )
}

/** Both teams of the live game: champion, spells, keystone, name and rank (when the server knew it). */
export function LiveGameSheet({ game, version, viewer, onClose }: {
  game: LolLiveGame
  version: string | null
  /** The page's player, marked in the list */
  viewer: string
  onClose: () => void
}) {
  const now = useNow()
  const championKeys = useChampionKeys(version)
  const spells = useSummonerSpells(version)
  const runes = useRuneData(version)
  const { blue, red } = liveTeams(game.participants)
  const queue = game.queueId != null ? t.queues[queueKey(game.queueId)] : game.gameMode
  return (
    <Sheet title={t.liveGameTitle} onClose={onClose}>
      <div className="live-game" data-testid="live-game">
        <p className="live-game-meta">
          <span className="live-dot" aria-hidden />
          <span>{queue}</span>
          <span className="num">{t.liveSince(gameClock(liveElapsedSeconds(game, now)))}</span>
        </p>
        {[
          { teamId: 100, players: blue },
          { teamId: 200, players: red },
        ].map(({ teamId, players }) => (
          <section key={teamId} className={teamId === 100 ? 'live-team blue' : 'live-team red'} aria-label={t.teamNames[teamId]}>
            <header>
              <h3>{t.teamNames[teamId]}</h3>
              <span className="live-bans" aria-label={t.bansLabel}>
                {game.bans
                  .filter((ban) => ban.teamId === teamId && ban.championId > 0)
                  .map((ban, i) => {
                    const champion = championKeys?.[ban.championId]
                    return champion ? <ChampionIcon key={i} version={version} champion={champion} size={20} /> : null
                  })}
              </span>
            </header>
            <ul>
              {players.map((p, i) => (
                <LiveRow key={p.puuid ?? i} participant={p} version={version} championKeys={championKeys} spells={spells} runes={runes} me={p.puuid === viewer} />
              ))}
            </ul>
          </section>
        ))}
      </div>
    </Sheet>
  )
}

function LiveRow({ participant: p, version, championKeys, spells, runes, me }: {
  participant: LolLiveParticipant
  version: string | null
  championKeys: Record<number, string> | null
  spells: Record<number, string> | null
  runes: ReturnType<typeof useRuneData>
  me: boolean
}) {
  const champion = championKeys?.[p.championId] ?? String(p.championId)
  const name = p.riotName ?? t.playerColumn
  return (
    <li className={me ? 'live-row me' : 'live-row'}>
      <ChampionIcon version={version} champion={champion} size={34} />
      <span className="live-row-loadout">
        {p.spells.slice(0, 2).map((key, i) => (
          <SpellIcon key={i} version={version} spellKey={key} spells={spells} />
        ))}
      </span>
      <RuneIcon runeId={p.keystone} runes={runes} size={20} />
      <span className="live-row-name">
        {p.tracked && p.puuid ? (
          <Link to={`/game/${encodeURIComponent(p.puuid)}`}>{name}</Link>
        ) : (
          <span>{name}</span>
        )}
        {p.riotTag && <small>#{p.riotTag}</small>}
      </span>
      {p.rank ? <span className="live-row-rank">{rankLabel(p.rank)}</span> : <span className="live-row-rank muted">–</span>}
    </li>
  )
}

/** The Mastery tab: score, total points and the top champions with their level, points and when last played. */
export function MasteryTab({ puuid, version }: { puuid: string; version: string | null }) {
  const mastery = useQuery({ queryKey: queryKeys.lolMastery(puuid), queryFn: () => api.lolMastery(puuid) })
  const championKeys = useChampionKeys(version)
  if (mastery.isPending) return <SetListSkeleton />
  if (mastery.isError) return <ErrorState error={mastery.error} onRetry={() => mastery.refetch()} />
  const rows = masteryRows(mastery.data.champions)
  if (rows.length === 0) return <MessageState icon={<ControllerIcon size={32} />} title={t.masteryEmptyTitle} body={t.masteryEmptyBody} />
  return (
    <>
      <div className="mastery-totals">
        <div className="lol-tile">
          <small>{t.masteryScore}</small>
          <strong className="num" data-testid="mastery-score">{mastery.data.score ?? '–'}</strong>
        </div>
        <div className="lol-tile">
          <small>{t.masteryTotalPoints}</small>
          <strong className="num">{mastery.data.totalPoints != null ? compact(mastery.data.totalPoints) : '–'}</strong>
        </div>
      </div>
      <h2 className="section-title">{t.masteryTitle}</h2>
      <ol className="mastery-list" aria-label={t.masteryTitle}>
        {rows.map((row, index) => {
          const champion = championKeys?.[row.championId] ?? String(row.championId)
          const ago = row.lastPlayed ? timeAgo(row.lastPlayed) : null
          return (
            <li key={row.championId} className="mastery-row" style={{ animationDelay: `${Math.min(index, 8) * 30}ms` }} data-testid="mastery-row">
              <span className="mastery-champ">
                <ChampionIcon version={version} champion={champion} size={40} />
                <span className="mastery-level num">{t.masteryLevel(row.level)}</span>
              </span>
              <span className="mastery-main">
                <strong>{champion}</strong>
                <span className="mastery-bar" aria-hidden>
                  <span style={{ width: `${Math.max(4, row.share * 100)}%` }} />
                </span>
              </span>
              <span className="mastery-side num">
                <span>{t.masteryPoints(compact(row.points))}</span>
                {ago && <small className="muted">{t.masteryLastPlayed(ago.value, ago.unit)}</small>}
              </span>
            </li>
          )
        })}
      </ol>
    </>
  )
}
