import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { LolChampionCount, LolDeepChampion, LolDeepStats, LolDuo, LolGroupDuo, LolMatchup, LolWinCount } from '../api/types'
import { formatKg } from '../lib/format'
import { locale, t, termLang } from '../lib/i18n'
import { average, buildItems, signedNumber, spellPair, TREND_METRICS, trendPoints, weekdayName, winRateRows, type ItemInfo, type RuneInfo, type TrendMetric, type WinRateRow } from '../lib/lolDeep'
import { DASH, decimal, percentText, shortDate } from '../lib/lolFormat'
import { roundTo, toPercent, winPercent } from '../lib/lolStats'
import { CountUp } from './CountUp'
import { ChampionIcon, ItemIcon, RuneIcon, SpellIcon } from './LolBits'
import { BarRow } from './LolPlayerSections'
import { SortableTable, type SortableColumn } from './SortableTable'

// The sections of the tabbed League player page that read the filtered deep stats (GET /players/{puuid}/stats):
// grouped stat tiles, the trend chart, win-rate bars and every table. Each copes with an empty list by
// showing a short "not enough games" line instead of an empty table.

const whole = (n: number | null | undefined) => (n == null ? DASH : formatKg(Math.round(n)))
const pct = (fraction: number | null | undefined) => percentText(toPercent(fraction))
const signed = (n: number | null | undefined) => (n == null ? DASH : signedNumber(Math.round(n), (v) => formatKg(v)))

/** Games, W–L and win rate: the three columns every count table shares. */
function winColumns<T extends LolWinCount>(): SortableColumn<T>[] {
  return [
    { key: 'games', label: t.gamesColumn, sortValue: (r) => r.games, render: (r) => r.games },
    { key: 'winLoss', label: t.winLossColumn, sortValue: (r) => r.wins, render: (r) => `${r.wins}–${r.games - r.wins}` },
    {
      key: 'winRate',
      label: t.winRateColumn,
      sortValue: (r) => winPercent(r.wins, r.games),
      render: (r) => {
        const rate = winPercent(r.wins, r.games)
        return <span className={rate == null ? undefined : rate >= 50 ? 'wr good' : 'wr bad'}>{percentText(rate)}</span>
      },
    },
  ]
}

const championColumn = <T extends { champion: string }>(version: string | null): SortableColumn<T> => ({
  key: 'champion',
  label: t.championColumn,
  sortValue: (r) => r.champion,
  firstDirection: 'asc',
  render: (r) => (
    <span className="table-champion">
      <ChampionIcon version={version} champion={r.champion} size={28} />
      <span>{r.champion}</span>
    </span>
  ),
})

/** A titled table, or one muted line when the list is empty. */
function TableSection({ title, empty, children }: { title: string; empty: boolean; children: ReactNode }) {
  return (
    <>
      <h2 className="section-title" lang={termLang(title)}>{title}</h2>
      {empty ? <p className="lol-empty-line muted">{t.notEnoughGames}</p> : children}
    </>
  )
}

/** "Teammate stats cover 34 of 50 games": only while some games still lack both teams. */
export function FullDataNote({ stats }: { stats: LolDeepStats }) {
  if (stats.gamesWithFullData >= stats.gamesFound) return null
  return (
    <p className="lol-data-note" role="note">
      {t.fullDataNote(stats.gamesWithFullData, stats.gamesFound)}
    </p>
  )
}

// ---------- Overview ----------

interface StatTile {
  key: string
  label: string
  value: ReactNode
  caption?: string
  /** Takes two grid cells: the K / D / A line does not fit in one on a phone */
  wide?: boolean
}

function tile(key: string, label: string, raw: number | null | undefined, format: (n: number) => string, caption?: string): StatTile | null {
  if (raw == null || Number.isNaN(raw)) return null
  return { key, label, value: <CountUp value={raw} format={format} />, caption }
}

const twoDigits = (n: number) => formatKg(roundTo(n, 2))
const oneDigit = (n: number) => formatKg(roundTo(n, 1))
const percentFormat = (n: number) => t.percent(Math.round(n))
const wholeFormat = (n: number) => formatKg(Math.round(n))

/** Every overview number, in four labelled groups; numbers no game had are left out, and so is an empty group. */
export function StatGroups({ stats }: { stats: LolDeepStats }) {
  const o = stats.overview
  const perGame = t.perGameCaption
  const inGames = t.inGamesCaption(stats.gamesFound)
  const kdaLine =
    o.avgKills == null || o.avgDeaths == null || o.avgAssists == null
      ? null
      : { key: 'kdaLine', label: t.statAvgKda, value: `${oneDigit(o.avgKills)} / ${oneDigit(o.avgDeaths)} / ${oneDigit(o.avgAssists)}`, caption: perGame, wide: true }
  const groups: { key: string; tiles: (StatTile | null)[] }[] = [
    {
      key: 'combat',
      tiles: [
        tile('winRate', t.statWinRate, winPercent(stats.wins, stats.gamesFound), percentFormat, t.gamesCount(stats.wins, stats.gamesFound)),
        tile('kda', t.statKda, o.kda, twoDigits),
        tile('kp', t.statKillParticipation, toPercent(o.killParticipation), percentFormat),
        kdaLine,
        tile('damageShare', t.statDamageShare, toPercent(o.damageShare), percentFormat),
        tile('damage', t.statDamagePerMin, o.damagePerMinute, wholeFormat),
        tile('damageTaken', t.statDamageTakenPerMin, o.damageTakenPerMinute, wholeFormat),
        tile('soloKills', t.statSoloKills, o.avgSoloKills, oneDigit, perGame),
        tile('firstBlood', t.statFirstBlood, toPercent(o.firstBloodRate), percentFormat),
        tile('ccTime', t.statCcTime, o.avgCcTime, (n) => t.secondsShort(oneDigit(n)), perGame),
        tile('deadShare', t.statDeadShare, toPercent(o.deadShare), percentFormat),
      ],
    },
    {
      key: 'farm',
      tiles: [
        tile('cs', t.statCsPerMin, o.csPerMinute, oneDigit),
        tile('gold', t.statGoldPerMin, o.goldPerMinute, wholeFormat),
        tile('damagePerGold', t.statDamagePerGold, o.damagePerGold, twoDigits),
        tile('objectiveDamage', t.statObjectiveDamage, o.objectiveDamagePerMinute, wholeFormat),
        tile('gameLength', t.statGameLength, o.avgGameMinutes, (n) => t.minutesShort(oneDigit(n))),
      ],
    },
    {
      key: 'vision',
      tiles: [
        tile('vision', t.statVisionPerMin, o.visionPerMinute, twoDigits),
        tile('wardsPlaced', t.statWardsPlaced, o.avgWardsPlaced, oneDigit, perGame),
        tile('wardsKilled', t.statWardsKilled, o.avgWardsKilled, oneDigit, perGame),
        tile('controlWards', t.statControlWards, o.avgControlWards, oneDigit, perGame),
      ],
    },
    {
      key: 'multikills',
      tiles: (['double', 'triple', 'quadra', 'penta'] as const).map((kind) => tile(kind, t.multikills[kind], o.multiKills?.[kind], String, inGames)),
    },
  ]

  return (
    <div className="lol-stat-groups">
      {groups.map((group) => {
        const tiles = group.tiles.filter((x): x is StatTile => x != null)
        if (tiles.length === 0) return null
        return (
          <section key={group.key} className={`lol-stat-group ${group.key}`} aria-label={t.statGroups[group.key]}>
            <h3 lang={termLang(t.statGroups[group.key])}>{t.statGroups[group.key]}</h3>
            <div className="lol-tiles four">
              {tiles.map((x) => (
                <div key={x.key} className={x.wide ? 'lol-tile wide' : 'lol-tile'} data-testid={`tile-${x.key}`}>
                  <small lang={termLang(x.label)}>{x.label}</small>
                  <strong>{x.value}</strong>
                  <span className="lol-tile-caption">{x.caption ?? t.lastGames(stats.gamesFound)}</span>
                </div>
              ))}
            </div>
          </section>
        )
      })}
    </div>
  )
}

const axisStyle = { fill: 'var(--text-muted)', fontSize: 11 }

const trendFormat = (metric: TrendMetric, n: number) =>
  metric === 'kda' || metric === 'visionPerMinute' ? twoDigits(n) : metric === 'csPerMinute' ? oneDigit(n) : metric === 'killParticipation' ? percentFormat(n) : wholeFormat(n)

/** One chart, one metric at a time (chips above it): a dot per game, lime for a win, red for a loss. */
export function TrendChart({ trend }: { trend: LolDeepStats['trend'] }) {
  const [metric, setMetric] = useState<TrendMetric>('kda')
  // Only metrics with at least two games to connect get a chip
  const available = TREND_METRICS.filter((m) => trendPoints(trend, m).length >= 2)
  if (available.length === 0) return null
  const shown = available.includes(metric) ? metric : available[0]
  const points = trendPoints(trend, shown)
  const mean = average(points.map((p) => p.value))
  return (
    <section className="chart-card lol-trend-card" data-testid="trend-chart">
      <h2>{t.trendTitle}</h2>
      <p className="muted">{t.trendSubtitle}</p>
      <div className="chip-row lol-metric-chips" role="group" aria-label={t.trendMetricLabel}>
        {available.map((m) => (
          <button key={m} type="button" className={m === shown ? 'filter-chip selected' : 'filter-chip'} aria-pressed={m === shown} lang={termLang(t.trendMetrics[m])} onClick={() => setMetric(m)}>
            {t.trendMetrics[m]}
          </button>
        ))}
      </div>
      {mean != null && <span className="lol-trend-average num">{t.trendAverage(trendFormat(shown, mean))}</span>}
      <div style={{ height: 190 }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={points} margin={{ top: 8, right: 10, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="var(--outline-variant)" strokeDasharray="4 4" vertical={false} />
            <XAxis dataKey="index" tick={axisStyle} tickLine={false} axisLine={false} minTickGap={16} />
            <YAxis tickFormatter={(n: number) => trendFormat(shown, n)} tick={axisStyle} tickLine={false} axisLine={false} width={44} />
            {mean != null && <ReferenceLine y={mean} stroke="var(--outline)" strokeDasharray="6 4" />}
            <Tooltip
              cursor={{ stroke: 'var(--outline)' }}
              content={({ active, payload }) => {
                const point = active ? (payload?.[0]?.payload as (typeof points)[number] | undefined) : undefined
                if (!point) return null
                return (
                  <div className={point.win ? 'lp-tooltip' : 'lp-tooltip loss'}>
                    <strong>{trendFormat(shown, point.value)}</strong>
                    <span className="muted">
                      {t.trendGame(point.index)} · {shortDate(point.endedAt)} · {point.win ? t.winShort : t.lossShort}
                    </span>
                  </div>
                )
              }}
            />
            <Line
              type="monotone"
              dataKey="value"
              stroke="var(--tertiary)"
              strokeWidth={2}
              isAnimationActive={false}
              dot={(props: { cx?: number; cy?: number; index?: number; payload?: { win: boolean } }) => (
                <circle
                  key={props.index}
                  cx={props.cx}
                  cy={props.cy}
                  r={3.5}
                  fill={props.payload?.win ? 'var(--primary)' : 'var(--error)'}
                  stroke="var(--surface)"
                  strokeWidth={1.5}
                />
              )}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}

function WinRateBars({ title, rows, label }: { title: string; rows: WinRateRow[]; label: (key: string) => string }) {
  if (rows.length === 0) return null
  return (
    <div className="lol-split">
      <h3 lang={termLang(title)}>{title}</h3>
      <ul className="lol-bars" aria-label={title}>
        {rows.map((row) => (
          <BarRow key={row.key} label={label(row.key)} share={row.share} games={row.games} wins={row.wins} />
        ))}
      </ul>
    </div>
  )
}

/** Win rate by side, game length, time of day and weekday. */
export function SplitBars({ stats }: { stats: LolDeepStats }) {
  return (
    <div className="lol-splits">
      <WinRateBars title={t.sidesTitle} rows={winRateRows(stats.sides, (r) => String(r.teamId))} label={(key) => t.sideNames[Number(key)] ?? key} />
      <WinRateBars title={t.durationsTitle} rows={winRateRows(stats.durations, (r) => r.label)} label={t.durationLabel} />
      <WinRateBars title={t.hoursTitle} rows={winRateRows(stats.hours, (r) => r.label)} label={(key) => key} />
      <WinRateBars title={t.weekdaysTitle} rows={winRateRows(stats.weekdays, (r) => String(r.day))} label={(key) => weekdayName(Number(key), locale)} />
    </div>
  )
}

const FIRST_KEYS = ['firstBlood', 'firstTower', 'firstDragon', 'firstBaron'] as const
const AVERAGE_KEYS = [
  ['dragons', 'avgTeamDragons'],
  ['barons', 'avgTeamBarons'],
  ['towers', 'avgTeamTowers'],
  ['grubs', 'avgTeamGrubs'],
  ['heralds', 'avgTeamHeralds'],
] as const

/** The team's average objectives, then how often it took each one first and how those games went. */
export function ObjectivesSection({ objectives }: { objectives: LolDeepStats['objectives'] | null | undefined }) {
  if (!objectives) return null
  const averages = AVERAGE_KEYS.filter(([, field]) => objectives[field] != null)
  const firsts = FIRST_KEYS.map((key) => ({ key, ...objectives[key] })).filter((row) => row.games != null)
  if (averages.length === 0 && firsts.length === 0) return null
  const columns: SortableColumn<(typeof firsts)[number]>[] = [
    { key: 'objective', label: t.objectiveColumn, sortValue: (r) => FIRST_KEYS.indexOf(r.key), firstDirection: 'asc', render: (r) => t.firstObjectives[r.key] },
    ...winColumns<(typeof firsts)[number]>(),
  ]
  return (
    <>
      <h2 className="section-title" lang={termLang(t.objectivesTitle)}>{t.objectivesTitle}</h2>
      {averages.length > 0 && (
        <div className="lol-objective-strip">
          {averages.map(([key, field]) => (
            <span key={key} className="lol-objective">
              <strong className="num">{oneDigit(objectives[field]!)}</strong>
              <small lang={termLang(t.objectiveNames[key])}>{t.objectiveNames[key]}</small>
            </span>
          ))}
          <span className="lol-objective-caption muted">{t.teamAverageCaption}</span>
        </div>
      )}
      {firsts.length > 0 && (
        <SortableTable label={t.objectivesTitle} rows={firsts} columns={columns} rowKey={(r) => r.key} initialSort={{ key: 'objective', direction: 'asc' }} />
      )}
    </>
  )
}

// ---------- Champions ----------

export function DeepChampionsTable({ champions, version }: { champions: LolDeepChampion[]; version: string | null }) {
  const columns: SortableColumn<LolDeepChampion>[] = [
    championColumn(version),
    ...winColumns<LolDeepChampion>(),
    { key: 'kills', label: t.killsColumn, sortValue: (c) => c.kills, render: (c) => decimal(c.kills) },
    { key: 'deaths', label: t.deathsColumn, sortValue: (c) => c.deaths, firstDirection: 'asc', render: (c) => decimal(c.deaths) },
    { key: 'assists', label: t.assistsColumn, sortValue: (c) => c.assists, render: (c) => decimal(c.assists) },
    { key: 'kda', label: t.statKda, sortValue: (c) => c.kda, render: (c) => decimal(c.kda, 2) },
    { key: 'cs', label: t.statCsPerMin, sortValue: (c) => c.csPerMinute, render: (c) => decimal(c.csPerMinute) },
    { key: 'damage', label: t.damagePerMinColumn, sortValue: (c) => c.damagePerMinute, render: (c) => whole(c.damagePerMinute) },
    { key: 'gold', label: t.goldPerMinColumn, sortValue: (c) => c.goldPerMinute, render: (c) => whole(c.goldPerMinute) },
    { key: 'vision', label: t.visionPerMinColumn, sortValue: (c) => c.visionPerMinute, render: (c) => decimal(c.visionPerMinute, 2) },
    { key: 'kp', label: t.kpColumn, sortValue: (c) => c.killParticipation, render: (c) => pct(c.killParticipation) },
    { key: 'damageShare', label: t.damageShareColumn, sortValue: (c) => c.damageShare, render: (c) => pct(c.damageShare) },
    { key: 'pentas', label: t.pentasColumn, sortValue: (c) => c.pentas, render: (c) => c.pentas },
    { key: 'lastPlayed', label: t.lastPlayedColumn, sortValue: (c) => c.lastPlayed, render: (c) => shortDate(c.lastPlayed) },
  ]
  return (
    <TableSection title={t.championsTitle} empty={champions.length === 0}>
      <SortableTable label={t.championsTitle} rows={champions} columns={columns} rowKey={(c) => c.champion} initialSort={{ key: 'games', direction: 'desc' }} />
    </TableSection>
  )
}

// ---------- Duo ----------

const duoName = (duo: LolDuo) => duo.riotName ?? t.duoUnknown

/** A followed teammate links to their page; anyone else gets a Follow button, when their Riot ID is known. */
function DuoCell({ duo, onFollow }: { duo: LolDuo; onFollow: (duo: LolDuo) => Promise<void> }) {
  const [busy, setBusy] = useState(false)
  const name = (
    <>
      <span className="table-player-name">{duoName(duo)}</span>
      {duo.riotTag && <small>#{duo.riotTag}</small>}
    </>
  )
  if (duo.tracked) {
    return (
      <Link to={`/game/${encodeURIComponent(duo.puuid)}`} viewTransition className="table-player">
        {name}
      </Link>
    )
  }
  const canFollow = duo.riotName != null && duo.riotTag != null
  return (
    <span className="table-player duo-cell">
      {name}
      {canFollow && (
        <button
          type="button"
          className="duo-follow"
          disabled={busy}
          aria-label={`${t.follow}: ${duoName(duo)}`}
          onClick={async () => {
            setBusy(true)
            try {
              await onFollow(duo)
            } finally {
              setBusy(false)
            }
          }}
        >
          + {t.follow}
        </button>
      )}
    </span>
  )
}

export function DuoTable({ duos, version, playerName, onFollow }: {
  duos: LolDuo[]
  version: string | null
  /** The page's player, for "Behlul's KDA" */
  playerName: string
  onFollow: (duo: LolDuo) => Promise<void>
}) {
  const columns: SortableColumn<LolDuo>[] = [
    { key: 'player', label: t.playerColumn, sortValue: (d) => duoName(d).toLocaleLowerCase(), firstDirection: 'asc', render: (d) => <DuoCell duo={d} onFollow={onFollow} /> },
    ...winColumns<LolDuo>(),
    { key: 'kda', label: t.duoKdaColumn, sortValue: (d) => d.kda, render: (d) => decimal(d.kda, 2) },
    { key: 'myKda', label: t.duoMyKdaColumn(playerName), sortValue: (d) => d.myKda, render: (d) => decimal(d.myKda, 2) },
    {
      key: 'champions',
      label: t.duoChampionsColumn,
      sortValue: (d) => d.champions[0],
      firstDirection: 'asc',
      render: (d) => (
        <span className="table-icons">
          {d.champions.slice(0, 3).map((champion) => (
            <ChampionIcon key={champion} version={version} champion={champion} size={24} />
          ))}
        </span>
      ),
    },
  ]
  return (
    <>
      <h2 className="section-title" lang={termLang(t.duosTitle)}>{t.duosTitle}</h2>
      {duos.length === 0 ? (
        <p className="lol-empty-line muted">{t.duosEmpty}</p>
      ) : (
        <SortableTable label={t.duosTitle} rows={duos} columns={columns} rowKey={(d) => d.puuid} initialSort={{ key: 'games', direction: 'desc' }} />
      )}
    </>
  )
}

const memberName = (member: LolGroupDuo['a']) => member.displayName ?? t.memberFallback

/** Pairs of group members who queued together, most games first. */
export function GroupDuosTable({ duos }: { duos: LolGroupDuo[] | null | undefined }) {
  if (!duos || duos.length === 0) return null
  const columns: SortableColumn<LolGroupDuo>[] = [
    {
      key: 'pair',
      label: t.groupDuoColumn,
      sortValue: (d) => `${memberName(d.a)} ${memberName(d.b)}`.toLocaleLowerCase(),
      firstDirection: 'asc',
      render: (d) => (
        <span className="group-duo">
          <span>{memberName(d.a)}</span>
          <span className="group-duo-plus" aria-hidden>
            +
          </span>
          <span>{memberName(d.b)}</span>
        </span>
      ),
    },
    ...winColumns<LolGroupDuo>(),
  ]
  return (
    <>
      <h2 className="section-title" lang={termLang(t.groupDuosTitle)}>{t.groupDuosTitle}</h2>
      <SortableTable label={t.groupDuosTitle} rows={duos} columns={columns} rowKey={(d) => `${d.a.userId}-${d.b.userId}`} initialSort={{ key: 'games', direction: 'desc' }} />
    </>
  )
}

// ---------- Opponents ----------

export function MatchupsTable({ matchups, version }: { matchups: LolMatchup[]; version: string | null }) {
  const columns: SortableColumn<LolMatchup>[] = [
    championColumn(version),
    ...winColumns<LolMatchup>(),
    { key: 'kda', label: t.statKda, sortValue: (m) => m.kda, render: (m) => decimal(m.kda, 2) },
    { key: 'goldDiff', label: t.goldDiffColumn, sortValue: (m) => m.avgGoldDiff, render: (m) => <DiffValue value={m.avgGoldDiff} /> },
    { key: 'csDiff', label: t.csDiffColumn, sortValue: (m) => m.avgCsDiff, render: (m) => <DiffValue value={m.avgCsDiff} /> },
  ]
  return (
    <TableSection title={t.matchupsTitle} empty={matchups.length === 0}>
      <SortableTable label={t.matchupsTitle} rows={matchups} columns={columns} rowKey={(m) => m.champion} initialSort={{ key: 'games', direction: 'desc' }} />
    </TableSection>
  )
}

/** A difference with its sign, lime when ahead and red when behind. */
export function DiffValue({ value }: { value: number | null | undefined }) {
  if (value == null) return <>{DASH}</>
  const rounded = Math.round(value)
  return <span className={rounded > 0 ? 'diff good' : rounded < 0 ? 'diff bad' : 'diff'}>{signed(rounded)}</span>
}

export function ChampionCountTable({ title, rows, version }: { title: string; rows: LolChampionCount[]; version: string | null }) {
  const columns: SortableColumn<LolChampionCount>[] = [championColumn(version), ...winColumns<LolChampionCount>()]
  return (
    <TableSection title={title} empty={rows.length === 0}>
      <SortableTable label={title} rows={rows} columns={columns} rowKey={(r) => r.champion} initialSort={{ key: 'games', direction: 'desc' }} />
    </TableSection>
  )
}

// ---------- Build ----------

type ItemRow = LolDeepStats['items'][number]

export function ItemsTable({ items, version, itemData }: { items: ItemRow[]; version: string | null; itemData: Record<number, ItemInfo> | null }) {
  const rows = buildItems(items, itemData)
  const nameOf = (r: ItemRow) => itemData?.[r.itemId]?.name ?? `#${r.itemId}`
  const columns: SortableColumn<ItemRow>[] = [
    {
      key: 'item',
      label: t.itemColumn,
      sortValue: (r) => nameOf(r),
      firstDirection: 'asc',
      render: (r) => (
        <span className="table-champion table-item">
          <ItemIcon version={version} itemId={r.itemId} name={nameOf(r)} />
          <span>{nameOf(r)}</span>
        </span>
      ),
    },
    ...winColumns<ItemRow>(),
  ]
  return (
    <TableSection title={t.itemsTitle} empty={rows.length === 0}>
      <SortableTable label={t.itemsTitle} rows={rows} columns={columns} rowKey={(r) => String(r.itemId)} initialSort={{ key: 'games', direction: 'desc' }} />
    </TableSection>
  )
}

type KeystoneRow = LolDeepStats['keystones'][number]

export function KeystonesTable({ keystones, runes }: { keystones: KeystoneRow[]; runes: Record<number, RuneInfo> | null }) {
  const nameOf = (r: KeystoneRow) => runes?.[r.keystone]?.name ?? `#${r.keystone}`
  const columns: SortableColumn<KeystoneRow>[] = [
    {
      key: 'keystone',
      label: t.keystoneColumn,
      sortValue: (r) => nameOf(r),
      firstDirection: 'asc',
      render: (r) => (
        <span className="table-champion table-rune">
          <span className="rune-stack">
            <RuneIcon runeId={r.keystone} runes={runes} size={28} />
            <RuneIcon runeId={r.primaryStyle} runes={runes} size={14} />
          </span>
          <span>{nameOf(r)}</span>
        </span>
      ),
    },
    ...winColumns<KeystoneRow>(),
  ]
  return (
    <TableSection title={t.keystonesTitle} empty={keystones.length === 0}>
      <SortableTable label={t.keystonesTitle} rows={keystones} columns={columns} rowKey={(r) => `${r.keystone}-${r.primaryStyle}`} initialSort={{ key: 'games', direction: 'desc' }} />
    </TableSection>
  )
}

type SpellRow = LolDeepStats['spells'][number]

export function SpellsTable({ spells, version, spellFiles }: { spells: SpellRow[]; version: string | null; spellFiles: Record<number, string> | null }) {
  const columns: SortableColumn<SpellRow>[] = [
    {
      key: 'spells',
      label: t.spellsColumn,
      sortValue: (r) => r.spells,
      firstDirection: 'asc',
      render: (r) => (
        <span className="table-icons" aria-label={r.spells}>
          {spellPair(r.spells).map((key) => (
            <SpellIcon key={key} version={version} spellKey={key} spells={spellFiles} />
          ))}
        </span>
      ),
    },
    ...winColumns<SpellRow>(),
  ]
  return (
    <TableSection title={t.spellsTitle} empty={spells.length === 0}>
      <SortableTable label={t.spellsTitle} rows={spells} columns={columns} rowKey={(r) => r.spells} initialSort={{ key: 'games', direction: 'desc' }} />
    </TableSection>
  )
}

type PatchRow = LolDeepStats['patches'][number]

/** Patch numbers sort as versions: 14.9 comes before 14.10. */
const patchOrder = (patch: string) => patch.split('.').reduce((sum, part) => sum * 1000 + (Number(part) || 0), 0)

export function PatchesTable({ patches }: { patches: PatchRow[] }) {
  const columns: SortableColumn<PatchRow>[] = [
    { key: 'patch', label: t.patchColumn, sortValue: (r) => patchOrder(r.patch), render: (r) => <span className="table-patch">{r.patch}</span> },
    ...winColumns<PatchRow>(),
  ]
  return (
    <TableSection title={t.patchesTitle} empty={patches.length === 0}>
      <SortableTable label={t.patchesTitle} rows={patches} columns={columns} rowKey={(r) => r.patch} initialSort={{ key: 'patch', direction: 'desc' }} />
    </TableSection>
  )
}
