import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { LolChampionStats, LolMatch, LolRankPoint, LolRecord, LolRecords, LolStreak, LolSummary } from '../api/types'
import { formatKg } from '../lib/format'
import { t } from '../lib/i18n'
import { formatDuration, kdaRatio, ladderAxis, ladderLabel, queueKey, timeAgo } from '../lib/lol'
import { findMatch, itemSlots, multikillKey, queueRows, rankChartPoints, roleRows, roundTo, toPercent, winPercent } from '../lib/lolStats'
import { compact, DASH, decimal, percentText, shortDate } from '../lib/lolFormat'
import { CountUp } from './CountUp'
import { ChampionIcon, ItemIcon, SpellIcon } from './LolBits'
import { LpChip } from './LolLiveSections'
import { SortableTable, type SortableColumn } from './SortableTable'

// The sections of the League player page below the hero, top to bottom. Each one copes with older answers
// from the server that lack the detailed numbers: it shows what there is, or nothing.


export function StreakChip({ streak }: { streak: LolStreak | null }) {
  // A single game is not a streak worth a chip
  if (!streak || streak.count < 2) return null
  return (
    <span className={streak.win ? 'streak-chip win' : 'streak-chip loss'}>
      <span className="num">{streak.count}</span>
      {streak.win ? t.streakWins(streak.count) : t.streakLosses(streak.count)}
    </span>
  )
}

const axisStyle = { fill: 'var(--text-muted)', fontSize: 11 }

/** Solo/duo rank over time on one LP ladder; hidden until there are two snapshots to draw a line between. */
export function RankChart({ history }: { history: LolRankPoint[] | undefined }) {
  const points = rankChartPoints(history)
  if (points.length < 2) return null
  const axis = ladderAxis(points.map((point) => point.value))
  return (
    <section className="chart-card lol-lp-card" data-testid="lp-chart">
      <h2>{t.lpChartTitle}</h2>
      <p className="muted">{t.lpChartSubtitle}</p>
      <div style={{ height: 210 }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={points} margin={{ top: 8, right: 10, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="var(--outline-variant)" strokeDasharray="4 4" vertical={false} />
            <XAxis dataKey="recordedAt" tickFormatter={shortDate} tick={axisStyle} tickLine={false} axisLine={false} minTickGap={24} />
            <YAxis domain={axis.domain} ticks={axis.ticks} tickFormatter={ladderLabel} tick={axisStyle} tickLine={false} axisLine={false} width={74} />
            <Tooltip
              cursor={{ stroke: 'var(--outline)' }}
              content={({ active, payload }) => {
                const point = active ? (payload?.[0]?.payload as (typeof points)[number] | undefined) : undefined
                if (!point) return null
                return (
                  <div className="lp-tooltip">
                    <strong>{point.label}</strong>
                    <span className="muted">{shortDate(point.recordedAt)}</span>
                  </div>
                )
              }}
            />
            <Line type="linear" dataKey="value" stroke="var(--primary)" strokeWidth={3} dot={{ r: 3, fill: 'var(--primary)' }} activeDot={{ r: 6 }} isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}

interface Tile {
  key: string
  label: string
  value: ReactNode
}

/** The averages as tiles; a number the server did not send (or no match had) gets no tile at all. */
export function StatTiles({ summary }: { summary: LolSummary }) {
  const tiles: Tile[] = []
  const add = (key: string, label: string, raw: number | null | undefined, format: (n: number) => string) => {
    if (raw == null || Number.isNaN(raw)) return
    tiles.push({ key, label, value: <CountUp value={raw} format={format} /> })
  }
  add('winRate', t.statWinRate, winPercent(summary.wins, summary.games), (n) => t.percent(Math.round(n)))
  add('kda', t.statKda, summary.kda, (n) => formatKg(roundTo(n, 2)))
  add('cs', t.statCsPerMin, summary.csPerMinute, (n) => formatKg(roundTo(n, 1)))
  add('kp', t.statKillParticipation, toPercent(summary.killParticipation), (n) => t.percent(Math.round(n)))
  add('damage', t.statDamagePerMin, summary.damagePerMinute, (n) => formatKg(Math.round(n)))
  add('gold', t.statGoldPerMin, summary.goldPerMinute, (n) => formatKg(Math.round(n)))
  add('vision', t.statVisionPerMin, summary.visionPerMinute, (n) => formatKg(roundTo(n, 2)))
  add('damageShare', t.statDamageShare, toPercent(summary.damageShare), (n) => t.percent(Math.round(n)))

  return (
    <div className={tiles.length > 3 ? 'lol-tiles four' : 'lol-tiles'}>
      {tiles.map((tile) => (
        <div key={tile.key} className="lol-tile" data-testid={`tile-${tile.key}`}>
          <small>{tile.label}</small>
          <strong>{tile.value}</strong>
          <span className="lol-tile-caption">{t.lastGames(summary.games)}</span>
        </div>
      ))}
    </div>
  )
}

export function BarRow({ label, share, games, wins }: { label: string; share: number; games: number; wins: number }) {
  const winRate = winPercent(wins, games) ?? 0
  return (
    <li className="lol-bar-row">
      <span className="lol-bar-label">{label}</span>
      <span className="lol-bar-track" aria-hidden>
        <span className="lol-bar-fill" style={{ width: `${Math.max(6, share * 100)}%` }}>
          <span className="lol-bar-wins" style={{ width: `${winRate}%` }} />
        </span>
      </span>
      <span className="lol-bar-side num">
        {t.gamesCount(wins, games)}
        <small className={winRate >= 50 ? 'good' : 'bad'}>{t.percent(winRate)}</small>
      </span>
    </li>
  )
}

export function RoleBars({ roles }: { roles: LolSummary['roles'] }) {
  const rows = roleRows(roles)
  if (rows.length === 0) return null
  return (
    <>
      <h2 className="section-title">{t.rolesTitle}</h2>
      <ul className="lol-bars" aria-label={t.rolesTitle}>
        {rows.map((row) => (
          <BarRow key={row.key} label={t.positions[row.key]} share={row.share} games={row.games} wins={row.wins} />
        ))}
      </ul>
    </>
  )
}

export function QueueBars({ queues }: { queues: LolSummary['queues'] }) {
  const rows = queueRows(queues)
  if (rows.length === 0) return null
  return (
    <>
      <h2 className="section-title">{t.queuesTitle}</h2>
      <ul className="lol-bars" aria-label={t.queuesTitle}>
        {rows.map((row) => (
          <BarRow key={row.key} label={t.queues[row.key]} share={row.share} games={row.games} wins={row.wins} />
        ))}
      </ul>
    </>
  )
}

export function ChampionsTable({ champions, version }: { champions: LolChampionStats[]; version: string | null }) {
  if (champions.length === 0) return null
  const columns: SortableColumn<LolChampionStats>[] = [
    {
      key: 'champion',
      label: t.championColumn,
      sortValue: (c) => c.champion,
      firstDirection: 'asc',
      render: (c) => (
        <span className="table-champion">
          <ChampionIcon version={version} champion={c.champion} size={28} />
          <span>{c.champion}</span>
        </span>
      ),
    },
    { key: 'games', label: t.gamesColumn, sortValue: (c) => c.games, render: (c) => c.games },
    { key: 'winLoss', label: t.winLossColumn, sortValue: (c) => c.wins, render: (c) => `${c.wins}–${c.games - c.wins}` },
    { key: 'winRate', label: t.winRateColumn, sortValue: (c) => winPercent(c.wins, c.games), render: (c) => percentText(winPercent(c.wins, c.games)) },
    { key: 'kda', label: t.statKda, sortValue: (c) => c.kda, render: (c) => decimal(c.kda, 2) },
    { key: 'cs', label: t.statCsPerMin, sortValue: (c) => c.csPerMinute, render: (c) => decimal(c.csPerMinute) },
    { key: 'damage', label: t.damagePerMinColumn, sortValue: (c) => c.damagePerMinute, render: (c) => (c.damagePerMinute == null ? DASH : formatKg(Math.round(c.damagePerMinute))) },
  ]
  return (
    <>
      <h2 className="section-title">{t.championsTitle}</h2>
      <SortableTable label={t.championsTitle} rows={champions} columns={columns} rowKey={(c) => c.champion} initialSort={{ key: 'games', direction: 'desc' }} />
    </>
  )
}

const RECORD_KEYS = ['mostKills', 'bestKda', 'mostDamage', 'mostCs', 'longestGame', 'largestMultiKill'] as const

function recordValue(key: (typeof RECORD_KEYS)[number], record: LolRecord): string {
  switch (key) {
    case 'bestKda':
      return formatKg(roundTo(record.value, 2))
    case 'mostDamage':
      return formatKg(Math.round(record.value))
    case 'longestGame':
      return formatDuration(Math.round(record.value))
    case 'largestMultiKill': {
      const badge = multikillKey(record.value)
      return badge ? t.multikills[badge] : String(record.value)
    }
    default:
      return String(Math.round(record.value))
  }
}

/** Best single games; a tap opens that match's page, when the match is still among the stored ones. */
export function RecordList({ records, matches, version, onShowMatch }: {
  records: LolRecords | null | undefined
  matches: LolMatch[]
  version: string | null
  onShowMatch: (matchId: string) => void
}) {
  const entries = RECORD_KEYS.flatMap((key) => {
    const record = records?.[key]
    return record ? [{ key, record, match: findMatch(matches, record.matchId) }] : []
  })
  if (entries.length === 0) return null
  return (
    <>
      <h2 className="section-title">{t.recordsTitle}</h2>
      <ul className="lol-records">
        {entries.map(({ key, record, match }) => {
          const content = (
            <>
              {match ? <ChampionIcon version={version} champion={match.champion} size={32} /> : <span className="record-icon-blank" aria-hidden />}
              <span className="lol-record-name">{t.recordNames[key]}</span>
              <strong className="lol-record-value num">{recordValue(key, record)}</strong>
            </>
          )
          return (
            <li key={key}>
              {match ? (
                <button type="button" className="lol-record" onClick={() => onShowMatch(match.matchId)} aria-label={`${t.recordNames[key]}: ${recordValue(key, record)}. ${t.showMatch}`}>
                  {content}
                </button>
              ) : (
                <div className="lol-record">{content}</div>
              )}
            </li>
          )
        })}
      </ul>
    </>
  )
}

/** One stored game: the result as a colored edge, items and spells; a tap opens the full match page. */
export function MatchRow({ match, version, spells, index, to }: {
  match: LolMatch
  version: string | null
  spells: Record<number, string> | null
  index: number
  /** The match page, which carries the player along so their row stands out */
  to: string
}) {
  const ago = timeAgo(match.endedAt)
  const kda = kdaRatio(match.kills, match.deaths, match.assists)
  const items = itemSlots(match.items)
  const badge = multikillKey(match.largestMultiKill)
  const kp = toPercent(match.killParticipation)
  const extras: { key: string; label: string; value: string }[] = []
  if (kp != null) extras.push({ key: 'kp', label: 'KP', value: t.percent(kp) })
  if (match.damage != null) extras.push({ key: 'damage', label: t.detailDamage, value: compact(match.damage) })
  if (match.gold != null) extras.push({ key: 'gold', label: t.detailGold, value: compact(match.gold) })
  if (match.vision != null) extras.push({ key: 'vision', label: t.visionShort, value: String(match.vision) })

  return (
    <Link
      to={to}
      viewTransition
      id={`match-${match.matchId}`}
      className={match.win ? 'lol-match win' : 'lol-match loss'}
      style={{ animationDelay: `${Math.min(index, 8) * 30}ms` }}
      data-testid="match-row"
    >
      <span className="lol-match-head">
        <span className="lol-match-champ">
          <ChampionIcon version={version} champion={match.champion} />
          {match.champLevel != null && <span className="lol-match-level num">{match.champLevel}</span>}
        </span>
        {match.summonerSpells && match.summonerSpells.length > 0 && (
          <span className="lol-match-spells" aria-label={t.summonerSpellsLabel}>
            {match.summonerSpells.slice(0, 2).map((key, i) => (
              <SpellIcon key={i} version={version} spellKey={key} spells={spells} />
            ))}
          </span>
        )}
        <span className="lol-match-main">
          <strong className="num">
            {match.kills} / <span className="deaths">{match.deaths}</span> / {match.assists}
          </strong>
          <span className="muted">
            {t.queues[queueKey(match.queueId)]} · {formatDuration(match.durationS)}
          </span>
        </span>
        <span className="lol-match-side num">
          <span>{formatKg(roundTo(kda, 1))} KDA</span>
          <span className="muted">
            {match.cs} CS · {t.ago(ago.value, ago.unit)}
          </span>
        </span>
        {badge && <span className={`multikill-badge ${badge}`}>{t.multikills[badge]}</span>}
        <LpChip change={match.lpChange} />
      </span>

      {(items || extras.length > 0) && (
        <span className="lol-match-extra">
          {items && (
            <span className="lol-match-items" aria-label={t.itemsLabel}>
              {items.map((itemId, i) => (
                <ItemIcon key={i} version={version} itemId={itemId} />
              ))}
            </span>
          )}
          {extras.length > 0 && (
            <span className="lol-match-numbers num">
              {extras.map((extra) => (
                <span key={extra.key}>
                  <small>{extra.label}</small> {extra.value}
                </span>
              ))}
            </span>
          )}
        </span>
      )}
    </Link>
  )
}
