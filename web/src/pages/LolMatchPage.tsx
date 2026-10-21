import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { Area, AreaChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { LolMatchDetail, LolMatchParticipant, LolMatchTeam, LolTimeline } from '../api/types'
import { ChampionIcon, ItemIcon, RuneIcon, SpellIcon } from '../components/LolBits'
import { LpChip } from '../components/LolLiveSections'
import { SortableTable, type SortableColumn } from '../components/SortableTable'
import { useChampionKeys, useItemData, useRuneData } from '../components/useDdragonData'
import { useDdragonVersion } from '../components/useDdragonVersion'
import { useSummonerSpells } from '../components/useSummonerSpells'
import { SetListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { ControllerIcon } from '../components/Icons'
import { errorMessage, formatKg } from '../lib/format'
import { locale, t, termLang } from '../lib/i18n'
import { formatDuration, kdaRatio, queueKey } from '../lib/lol'
import { goldLeadPoints, laneDiffs, patchOf, signedNumber, splitTeams, zeroOffset, type ItemInfo, type LaneDiff, type RuneInfo } from '../lib/lolDeep'
import { compact, DASH, percentText, utcDate } from '../lib/lolFormat'
import { itemSlots, roundTo, toPercent } from '../lib/lolStats'

const BLUE = 100

interface Lookups {
  version: string | null
  spells: Record<number, string> | null
  runes: Record<number, RuneInfo> | null
  items: Record<number, ItemInfo> | null
  champions: Record<number, string> | null
}

/**
 * One stored match in full: result, both teams' objectives and bans, a scoreboard per team, and (loaded after
 * the page) Riot's timeline for the gold lead and the 10/15 minute lane comparisons. ?player= marks whose page
 * the visit came from: their row is highlighted and the result is told from their side.
 */
export function LolMatchPage() {
  const matchId = useParams().matchId ?? ''
  const [searchParams] = useSearchParams()
  const viewer = searchParams.get('player')
  const navigate = useNavigate()
  const match = useQuery({ queryKey: queryKeys.lolMatch(matchId), queryFn: () => api.lolMatch(matchId) })
  const timeline = useQuery({
    queryKey: queryKeys.lolTimeline(matchId),
    queryFn: () => api.lolTimeline(matchId),
    // Lazily: after the scoreboard is on screen, so the first paint never waits for Riot
    enabled: match.isSuccess,
    retry: false,
    // A stored timeline never changes
    staleTime: Infinity,
  })

  const notStored = match.error instanceof AppError && match.error.kind === 'notFound'
  return (
    <div className="page lol-match-page">
      <div className="top-bar">
        <button className="icon-btn" onClick={() => navigate(-1)} aria-label={t.back}>
          ←
        </button>
        <h1>{t.matchTitle}</h1>
      </div>
      {match.isPending ? (
        <SetListSkeleton />
      ) : notStored ? (
        <MessageState icon={<ControllerIcon size={32} />} title={t.matchNotStoredTitle} body={t.matchNotStoredBody} />
      ) : match.isError ? (
        <ErrorState error={match.error} onRetry={() => match.refetch()} />
      ) : (
        <MatchContent match={match.data} viewer={viewer} timeline={timeline} />
      )}
    </div>
  )
}

function MatchContent({ match, viewer, timeline }: { match: LolMatchDetail; viewer: string | null; timeline: UseQueryResult<LolTimeline> }) {
  const version = useDdragonVersion()
  const lookups: Lookups = {
    version,
    spells: useSummonerSpells(version),
    runes: useRuneData(version),
    items: useItemData(version),
    champions: useChampionKeys(version),
  }
  const [detailed, setDetailed] = useState(false)
  const { blue, red } = splitTeams(match.participants)
  const teamOf = (id: number) => match.teams.find((team) => team.teamId === id) ?? null
  const me = viewer ? match.participants.find((p) => p.puuid === viewer) : undefined
  const maxDamage = Math.max(1, ...match.participants.map((p) => p.damage))

  return (
    <>
      <MatchHeader match={match} me={me} version={version} />
      <TeamStrip blue={teamOf(BLUE)} red={teamOf(200)} lookups={lookups} />

      <div className="board-toggle scoreboard-toolbar">
        <button type="button" className={detailed ? 'selected' : ''} aria-pressed={detailed} onClick={() => setDetailed((d) => !d)}>
          {t.scoreboardDetails}
        </button>
      </div>
      {[
        { id: BLUE, players: blue },
        { id: 200, players: red },
      ].map(({ id, players }) =>
        players.length === 0 ? null : (
          <section key={id} className={id === BLUE ? 'scoreboard blue' : 'scoreboard red'}>
            <h2 className="section-title scoreboard-title">
              {t.teamNames[id]}
              <span className={teamOf(id)?.win ? 'scoreboard-result win' : 'scoreboard-result loss'}>{teamOf(id)?.win ? t.victory : t.defeat}</span>
            </h2>
            <Scoreboard players={players} teamId={id} detailed={detailed} viewer={viewer} maxDamage={maxDamage} lookups={lookups} />
          </section>
        ),
      )}

      <TimelineSection match={match} timeline={timeline} version={version} />
    </>
  )
}

function MatchHeader({ match, me, version }: { match: LolMatchDetail; me: LolMatchParticipant | undefined; version: string | null }) {
  const winner = match.teams.find((team) => team.win)
  const patch = patchOf(match.gameVersion)
  const date = utcDate(match.endedAt).toLocaleString(locale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
  // From the visiting player's side when known, otherwise which team won
  const result = me ? (me.win ? t.victory : t.defeat) : winner ? t.teamWon(t.teamNames[winner.teamId] ?? '') : null
  const tone = me ? (me.win ? 'win' : 'loss') : winner?.teamId === BLUE ? 'blue' : 'red'
  return (
    <section className={`lol-match-hero ${tone}`} data-testid="match-header">
      {me && <ChampionIcon version={version} champion={me.champion} size={52} />}
      <div className="lol-match-hero-text">
        {result && (
          <span className="lol-match-result-row">
            <strong className="lol-match-result">{result}</strong>
            <LpChip change={me?.lpChange} />
          </span>
        )}
        <span className="muted">
          {t.queues[queueKey(match.queueId)]} · {formatDuration(match.durationS)} · {date}
        </span>
      </div>
      {patch && <span className="lol-patch-tag">{t.patchLabel(patch)}</span>}
    </section>
  )
}

const OBJECTIVES = ['kills', 'towers', 'inhibitors', 'dragons', 'barons', 'heralds', 'grubs', 'atakhan'] as const
const FIRSTS = ['firstBlood', 'firstTower', 'firstDragon', 'firstBaron'] as const

/** Blue on the left, red on the right, one row per objective; the side that took it first gets a marker. */
function TeamStrip({ blue, red, lookups }: { blue: LolMatchTeam | null; red: LolMatchTeam | null; lookups: Lookups }) {
  if (!blue || !red) return null
  // Atakhan only exists on newer patches; a row of zeros says nothing
  const rows = OBJECTIVES.filter((key) => key !== 'atakhan' || blue.atakhan + red.atakhan > 0)
  return (
    <section className="team-strip" aria-label={t.objectivesTitle}>
      <div className="team-strip-head">
        <span className="team-name blue">{t.sideNames[BLUE]}</span>
        <span className="team-name red">{t.sideNames[200]}</span>
      </div>
      <ul className="team-strip-rows">
        {rows.map((key) => {
          const b = blue[key]
          const r = red[key]
          return (
            <li key={key}>
              <strong className={b > r ? 'num ahead' : 'num'}>{b}</strong>
              <span>{t.objectiveNames[key]}</span>
              <strong className={r > b ? 'num ahead' : 'num'}>{r}</strong>
            </li>
          )
        })}
      </ul>
      <div className="team-firsts">
        {FIRSTS.map((key) => {
          const side = blue[key] ? 'blue' : red[key] ? 'red' : null
          return side ? (
            <span key={key} className={`first-tag ${side}`}>
              {t.firstObjectives[key]}
            </span>
          ) : null
        })}
      </div>
      <div className="team-bans">
        {[blue, red].map((team) => (
          <span key={team.teamId} className={team.teamId === BLUE ? 'ban-row blue' : 'ban-row red'} aria-label={`${t.bansLabel}: ${t.teamNames[team.teamId]}`}>
            {team.bans
              .filter((key) => key > 0)
              .map((key, i) => {
                const champion = lookups.champions?.[key]
                return <ChampionIcon key={i} version={champion ? lookups.version : null} champion={champion ?? String(key)} size={24} />
              })}
          </span>
        ))}
      </div>
    </section>
  )
}

const num = (n: number | null | undefined) => (n == null ? DASH : formatKg(n))

function Scoreboard({ players, teamId, detailed, viewer, maxDamage, lookups }: {
  players: LolMatchParticipant[]
  teamId: number
  detailed: boolean
  viewer: string | null
  maxDamage: number
  lookups: Lookups
}) {
  const { version } = lookups
  const columns: SortableColumn<LolMatchParticipant>[] = [
    {
      key: 'champion',
      label: t.championColumn,
      sortValue: (p) => p.champion,
      firstDirection: 'asc',
      render: (p) => (
        <span className="table-champion">
          <span className="lol-match-champ">
            <ChampionIcon version={version} champion={p.champion} size={28} />
            <span className="lol-match-level num">{p.champLevel}</span>
          </span>
          <span>{p.champion}</span>
        </span>
      ),
    },
    {
      key: 'name',
      label: t.nameColumn,
      sortValue: (p) => (p.riotName ?? '').toLocaleLowerCase(),
      firstDirection: 'asc',
      render: (p) =>
        p.tracked ? (
          <Link to={`/game/${encodeURIComponent(p.puuid)}`} viewTransition className="scoreboard-name">
            {p.riotName ?? DASH}
          </Link>
        ) : (
          <span className="scoreboard-name muted">{p.riotName ?? DASH}</span>
        ),
    },
    {
      key: 'kda',
      label: t.kdaColumn,
      sortValue: (p) => kdaRatio(p.kills, p.deaths, p.assists),
      render: (p) => (
        <span className="scoreboard-kda">
          <span>
            {p.kills}/<span className="deaths">{p.deaths}</span>/{p.assists}
          </span>
          <small>{formatKg(roundTo(kdaRatio(p.kills, p.deaths, p.assists), 1))}</small>
        </span>
      ),
    },
    { key: 'cs', label: t.csColumn, sortValue: (p) => p.cs, render: (p) => p.cs },
    { key: 'gold', label: t.goldColumn, sortValue: (p) => p.gold, render: (p) => compact(p.gold) },
    {
      key: 'damage',
      label: t.damageColumn,
      sortValue: (p) => p.damage,
      render: (p) => (
        <span className="damage-cell">
          {compact(p.damage)}
          <span className="damage-bar" aria-hidden>
            <span style={{ width: `${(p.damage / maxDamage) * 100}%` }} />
          </span>
        </span>
      ),
    },
    { key: 'vision', label: t.visionColumn, sortValue: (p) => p.visionScore, render: (p) => p.visionScore },
    {
      key: 'items',
      label: t.itemsLabel,
      sortValue: (p) => p.gold,
      render: (p) => (
        <span className="lol-match-items" aria-label={t.itemsLabel}>
          {(itemSlots(p.items) ?? []).map((itemId, i) => (
            <ItemIcon key={i} version={version} itemId={itemId} name={lookups.items?.[itemId]?.name} />
          ))}
        </span>
      ),
    },
    {
      key: 'loadout',
      label: t.loadoutColumn,
      sortValue: (p) => p.keystone,
      render: (p) => (
        <span className="table-icons">
          {p.spells.slice(0, 2).map((key, i) => (
            <SpellIcon key={i} version={version} spellKey={key} spells={lookups.spells} />
          ))}
          <RuneIcon runeId={p.keystone} runes={lookups.runes} size={20} />
        </span>
      ),
    },
  ]
  if (detailed) {
    const multis = (p: LolMatchParticipant) => [p.doubleKills, p.tripleKills, p.quadraKills, p.pentaKills]
    columns.push(
      { key: 'damageTaken', label: t.damageTakenColumn, sortValue: (p) => p.damageTaken, render: (p) => compact(p.damageTaken) },
      { key: 'damageObjectives', label: t.objectiveDamageColumn, sortValue: (p) => p.damageObjectives, render: (p) => compact(p.damageObjectives) },
      { key: 'damageBuildings', label: t.buildingDamageColumn, sortValue: (p) => p.damageBuildings, render: (p) => compact(p.damageBuildings) },
      { key: 'healing', label: t.healingColumn, sortValue: (p) => p.healing, render: (p) => compact(p.healing) },
      { key: 'shielding', label: t.shieldingColumn, sortValue: (p) => p.shielding, render: (p) => compact(p.shielding) },
      { key: 'ccTime', label: t.ccColumn, sortValue: (p) => p.ccTime, render: (p) => num(p.ccTime) },
      { key: 'timeDead', label: t.timeDeadColumn, sortValue: (p) => p.timeDead, firstDirection: 'asc', render: (p) => num(p.timeDead) },
      { key: 'wardsPlaced', label: t.wardsPlacedColumn, sortValue: (p) => p.wardsPlaced, render: (p) => p.wardsPlaced },
      { key: 'wardsKilled', label: t.wardsKilledColumn, sortValue: (p) => p.wardsKilled, render: (p) => p.wardsKilled },
      { key: 'controlWards', label: t.controlWardsColumn, sortValue: (p) => p.controlWards, render: (p) => p.controlWards },
      {
        key: 'multikills',
        label: t.multikillsColumn,
        // Pentas outrank any number of doubles
        sortValue: (p) => multis(p).reduce((sum, n, i) => sum + n * 100 ** i, 0),
        render: (p) => <span title={`${t.multikills.double} / ${t.multikills.triple} / ${t.multikills.quadra} / ${t.multikills.penta}`}>{multis(p).join('/')}</span>,
      },
      { key: 'firstBlood', label: t.firstBloodColumn, sortValue: (p) => (p.firstBlood ? 1 : 0), render: (p) => (p.firstBlood ? '✓' : DASH) },
      { key: 'soloKills', label: t.soloKillsColumn, sortValue: (p) => p.soloKills, render: (p) => num(p.soloKills) },
      { key: 'kp', label: t.kpColumn, sortValue: (p) => p.killParticipation, render: (p) => percentText(toPercent(p.killParticipation)) },
      { key: 'damageShare', label: t.damageShareColumn, sortValue: (p) => p.damageShare, render: (p) => percentText(toPercent(p.damageShare)) },
    )
  }
  return (
    <SortableTable
      label={t.teamNames[teamId]}
      rows={players}
      columns={columns}
      rowKey={(p) => String(p.participantId)}
      // Lane order (top to support) from splitTeams, like the game's own scoreboard, until a header is tapped
      initialSort={null}
      rowClassName={(p) => (p.puuid === viewer ? 'you' : undefined)}
    />
  )
}

function TimelineSection({ match, timeline, version }: { match: LolMatchDetail; timeline: UseQueryResult<LolTimeline>; version: string | null }) {
  if (timeline.isPending) {
    return (
      <p className="lol-empty-line muted" role="status">
        {t.timelineLoading}
      </p>
    )
  }
  if (timeline.isError) {
    const error = timeline.error
    const gone = error instanceof AppError && error.kind === 'notFound'
    const busy = error instanceof AppError && (error.kind === 'riotUnavailable' || error.kind === 'riotNoKey' || error.kind === 'server' || error.kind === 'rateLimited')
    return (
      <div className="timeline-error" role="alert">
        <p>{gone ? t.timelineGone : busy ? t.timelineBusy : errorMessage(error)}</p>
        {!gone && (
          <button type="button" className="btn btn-tonal" onClick={() => timeline.refetch()}>
            {t.retry}
          </button>
        )}
      </div>
    )
  }
  return (
    <>
      <GoldLeadChart timeline={timeline.data} />
      <LaneDiffTable rows={laneDiffs(match.participants, timeline.data)} version={version} />
    </>
  )
}

const axisStyle = { fill: 'var(--text-muted)', fontSize: 11 }
const goldText = (n: number) => signedNumber(Math.round(n), compact)

/** Blue's lead above the zero line in blue, red's below it in red, from one gradient split at zero. */
function GoldLeadChart({ timeline }: { timeline: LolTimeline }) {
  const points = goldLeadPoints(timeline)
  if (points.length < 2) return null
  const split = zeroOffset(points.map((p) => p.lead))
  return (
    <section className="chart-card gold-lead-card" data-testid="gold-lead-chart">
      <h2>{t.goldGraphTitle}</h2>
      <p className="muted">{t.goldGraphSubtitle}</p>
      <div style={{ height: 190 }}>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={points} margin={{ top: 8, right: 10, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id="gold-lead-fill" x1="0" y1="0" x2="0" y2="1">
                <stop offset={split} stopColor="var(--tertiary)" stopOpacity={0.45} />
                <stop offset={split} stopColor="var(--error)" stopOpacity={0.45} />
              </linearGradient>
              <linearGradient id="gold-lead-stroke" x1="0" y1="0" x2="0" y2="1">
                <stop offset={split} stopColor="var(--tertiary)" />
                <stop offset={split} stopColor="var(--error)" />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="var(--outline-variant)" strokeDasharray="4 4" vertical={false} />
            <XAxis dataKey="minute" tick={axisStyle} tickLine={false} axisLine={false} minTickGap={16} />
            <YAxis tickFormatter={goldText} tick={axisStyle} tickLine={false} axisLine={false} width={48} />
            <ReferenceLine y={0} stroke="var(--outline)" />
            <Tooltip
              cursor={{ stroke: 'var(--outline)' }}
              content={({ active, payload }) => {
                const point = active ? (payload?.[0]?.payload as (typeof points)[number] | undefined) : undefined
                if (!point) return null
                return (
                  <div className={point.lead >= 0 ? 'lp-tooltip blue' : 'lp-tooltip red'}>
                    <strong>{goldText(point.lead)}</strong>
                    <span className="muted">{t.goldLeadTooltip(point.minute)}</span>
                  </div>
                )
              }}
            />
            <Area type="monotone" dataKey="lead" baseValue={0} stroke="url(#gold-lead-stroke)" strokeWidth={2} fill="url(#gold-lead-fill)" isAnimationActive={false} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}

/** Blue minus red: blue-tinted when blue was ahead, red-tinted when red was. */
function TeamDiff({ value }: { value: number | null | undefined }) {
  if (value == null) return <>{DASH}</>
  return <span className={value > 0 ? 'diff blue' : value < 0 ? 'diff red' : 'diff'}>{signedNumber(value, (n) => formatKg(n))}</span>
}

const LANES = ['top', 'jungle', 'middle', 'bottom', 'support']

function LaneDiffTable({ rows, version }: { rows: LaneDiff[]; version: string | null }) {
  if (rows.length === 0) return null
  const columns: SortableColumn<LaneDiff>[] = [
    {
      key: 'lane',
      label: t.laneColumn,
      sortValue: (r) => LANES.indexOf(r.lane),
      firstDirection: 'asc',
      render: (r) => (
        <span className="lane-cell">
          <small>{t.positions[r.lane]}</small>
          <span className="lane-versus">
            <ChampionIcon version={version} champion={r.blue.champion} size={24} />
            <ChampionIcon version={version} champion={r.red.champion} size={24} />
          </span>
        </span>
      ),
    },
    { key: 'gold10', label: t.at10Gold, sortValue: (r) => r.at10?.gold, render: (r) => <TeamDiff value={r.at10?.gold} /> },
    { key: 'cs10', label: t.at10Cs, sortValue: (r) => r.at10?.cs, render: (r) => <TeamDiff value={r.at10?.cs} /> },
    { key: 'gold15', label: t.at15Gold, sortValue: (r) => r.at15?.gold, render: (r) => <TeamDiff value={r.at15?.gold} /> },
    { key: 'cs15', label: t.at15Cs, sortValue: (r) => r.at15?.cs, render: (r) => <TeamDiff value={r.at15?.cs} /> },
  ]
  return (
    <>
      <h2 className="section-title" lang={termLang(t.laneDiffTitle)}>{t.laneDiffTitle}</h2>
      <p className="muted lol-section-caption">{t.laneDiffSubtitle}</p>
      <SortableTable label={t.laneDiffTitle} rows={rows} columns={columns} rowKey={(r) => r.lane} initialSort={{ key: 'lane', direction: 'asc' }} />
    </>
  )
}
