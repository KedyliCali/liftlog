import type { LolMatchParticipant, LolStatsQueue, LolStatsWindow, LolTimeline, LolTimelineSnapshot, LolTrendPoint, LolWinCount } from '../api/types'
import { positionKey, type PositionKey } from './lolStats'

// Pure helpers for the tabbed League player page and the match detail page: URL choices, chart data,
// Data Dragon parsing and the lane comparisons.

export const PLAYER_TABS = ['overview', 'champions', 'duos', 'opponents', 'build', 'matches'] as const
export type PlayerTab = (typeof PLAYER_TABS)[number]

/** The tab named in the URL (?tab=), or the overview for anything else. */
export function playerTab(value: string | null): PlayerTab {
  return (PLAYER_TABS as readonly string[]).includes(value ?? '') ? (value as PlayerTab) : 'overview'
}

/** The queue chips; OTHER exists on the server but has no chip, as it mixes modes with nothing in common. */
export const QUEUE_CHIPS: readonly LolStatsQueue[] = ['ALL', 'SOLO', 'FLEX', 'ARAM']
export const WINDOW_CHIPS: readonly LolStatsWindow[] = [20, 50, 100]

export function statsQueue(value: string | null): LolStatsQueue {
  return (QUEUE_CHIPS as readonly string[]).includes(value ?? '') ? (value as LolStatsQueue) : 'ALL'
}

export function statsWindow(value: string | null): LolStatsWindow {
  const n = Number(value)
  return (WINDOW_CHIPS as readonly number[]).includes(n) ? (n as LolStatsWindow) : 20
}

/** Minutes east of UTC (Turkey is 180), the opposite sign of getTimezoneOffset; never -0. */
export function tzOffsetMinutes(date: Date = new Date()): number {
  return -date.getTimezoneOffset() || 0
}

/** The match page, carrying the player along so their row stands out there. */
export const matchPath = (matchId: string, puuid: string) => `/game/match/${encodeURIComponent(matchId)}?player=${encodeURIComponent(puuid)}`

const QUEUE_IDS: Partial<Record<LolStatsQueue, number>> = { SOLO: 420, FLEX: 440, ARAM: 450 }

/** The same split the server makes, for filtering the match list on the phone. */
export function inQueue(queueId: number, queue: LolStatsQueue): boolean {
  if (queue === 'ALL') return true
  if (queue === 'OTHER') return !Object.values(QUEUE_IDS).includes(queueId)
  return QUEUE_IDS[queue] === queueId
}

export interface WinRateRow {
  key: string
  games: number
  wins: number
  /** Games as a share of the busiest row, for the bar length */
  share: number
}

/** Bars for sides, durations, hours or weekdays, in the server's order; rows without games are left out. */
export function winRateRows<T extends LolWinCount>(rows: readonly T[] | null | undefined, key: (row: T) => string): WinRateRow[] {
  const played = (rows ?? []).filter((row) => row.games > 0)
  const most = Math.max(0, ...played.map((row) => row.games))
  return played.map((row) => ({ key: key(row), games: row.games, wins: row.wins, share: row.games / most }))
}

/** 1 = Monday … 7 = Sunday, in the phone's language. 1 January 2024 was a Monday. */
export function weekdayName(day: number, locale: string): string {
  return new Date(2024, 0, day).toLocaleDateString(locale, { weekday: 'short' })
}

/** "14.20.123.4567" → "14.20", the patch players talk about. */
export function patchOf(gameVersion: string | null | undefined): string | null {
  if (!gameVersion) return null
  const [major, minor] = gameVersion.split('.')
  return minor == null ? major : `${major}.${minor}`
}

/** "4,14" → [4, 14]; anything unreadable is dropped. */
export function spellPair(spells: string): number[] {
  return spells
    .split(',')
    .map((part) => Number(part.trim()))
    .filter((n) => Number.isFinite(n) && n > 0)
}

export type TrendMetric = 'kda' | 'csPerMinute' | 'damagePerMinute' | 'goldPerMinute' | 'visionPerMinute' | 'killParticipation'
export const TREND_METRICS: readonly TrendMetric[] = ['kda', 'csPerMinute', 'damagePerMinute', 'goldPerMinute', 'visionPerMinute', 'killParticipation']

export interface TrendChartPoint {
  index: number
  matchId: string
  endedAt: string
  win: boolean
  value: number
}

/**
 * One metric over the games, oldest first; games without that number are skipped, not drawn as zero.
 * Kill participation arrives as 0..1 and is charted in percent like everywhere else.
 */
export function trendPoints(trend: readonly LolTrendPoint[] | null | undefined, metric: TrendMetric): TrendChartPoint[] {
  const points: TrendChartPoint[] = []
  for (const point of trend ?? []) {
    const raw = point[metric]
    if (raw == null || Number.isNaN(raw)) continue
    const value = metric === 'killParticipation' ? raw * 100 : raw
    points.push({ index: points.length + 1, matchId: point.matchId, endedAt: point.endedAt, win: point.win, value })
  }
  return points
}

export function average(values: readonly number[]): number | null {
  return values.length === 0 ? null : values.reduce((sum, v) => sum + v, 0) / values.length
}

// ---------- Data Dragon ----------

export interface ItemInfo {
  name: string
  /** Potions, wards, elixirs, trinkets: bought every game, so they say nothing about a build */
  consumable: boolean
}

/** item.json → id to name and whether it is a consumable or trinket. Anything unexpected gives an empty map. */
export function parseItems(json: unknown): Record<number, ItemInfo> {
  const map: Record<number, ItemInfo> = {}
  const data = (json as { data?: unknown } | null)?.data
  if (!data || typeof data !== 'object') return map
  for (const [id, item] of Object.entries(data as Record<string, { name?: unknown; tags?: unknown; consumed?: unknown }>)) {
    const key = Number(id)
    if (!Number.isFinite(key) || !item) continue
    const tags = Array.isArray(item.tags) ? item.tags : []
    map[key] = {
      name: typeof item.name === 'string' ? item.name : id,
      consumable: item.consumed === true || tags.includes('Consumable') || tags.includes('Trinket'),
    }
  }
  return map
}

/** Drops consumables and trinkets once item.json is known; until then (or without it) every item stays. */
export function buildItems<T extends { itemId: number }>(items: readonly T[], info: Record<number, ItemInfo> | null): T[] {
  return items.filter((item) => item.itemId > 0 && !info?.[item.itemId]?.consumable)
}

export interface RuneInfo {
  name: string
  /** Path under ddragon's /cdn/img/ */
  icon: string
}

interface RuneEntry {
  id?: unknown
  name?: unknown
  icon?: unknown
  slots?: { runes?: RuneEntry[] }[]
}

/** runesReforged.json → id to name and icon, for both the trees (styles) and every rune in them. */
export function parseRunes(json: unknown): Record<number, RuneInfo> {
  const map: Record<number, RuneInfo> = {}
  if (!Array.isArray(json)) return map
  const add = (entry: RuneEntry | null | undefined) => {
    if (entry && typeof entry.id === 'number' && typeof entry.icon === 'string') {
      map[entry.id] = { name: typeof entry.name === 'string' ? entry.name : String(entry.id), icon: entry.icon }
    }
  }
  for (const style of json as RuneEntry[]) {
    add(style)
    for (const slot of style?.slots ?? []) for (const rune of slot?.runes ?? []) add(rune)
  }
  return map
}

/** champion.json → numeric key (bans carry only that) to the champion id used for icons ("MonkeyKing"). */
export function parseChampionKeys(json: unknown): Record<number, string> {
  const map: Record<number, string> = {}
  const data = (json as { data?: unknown } | null)?.data
  if (!data || typeof data !== 'object') return map
  for (const champion of Object.values(data as Record<string, { id?: unknown; key?: unknown }>)) {
    const key = Number(champion?.key)
    if (Number.isFinite(key) && typeof champion?.id === 'string') map[key] = champion.id
  }
  return map
}

// ---------- Match detail ----------

const LANE_ORDER: PositionKey[] = ['top', 'jungle', 'middle', 'bottom', 'support']
const laneIndex = (p: Pick<LolMatchParticipant, 'position'>) => {
  const key = positionKey(p.position)
  return key ? LANE_ORDER.indexOf(key) : LANE_ORDER.length
}

/** Blue (100) and red (200) players, each in lane order (top to support) like the in-game scoreboard. */
export function splitTeams<T extends Pick<LolMatchParticipant, 'teamId' | 'position' | 'participantId'>>(participants: readonly T[]): { blue: T[]; red: T[] } {
  const ordered = [...participants].sort((a, b) => laneIndex(a) - laneIndex(b) || a.participantId - b.participantId)
  return { blue: ordered.filter((p) => p.teamId === 100), red: ordered.filter((p) => p.teamId !== 100) }
}

export interface GoldLeadPoint {
  minute: number
  /** Blue team gold minus red; above zero blue is ahead */
  lead: number
}

export function goldLeadPoints(timeline: LolTimeline | null | undefined): GoldLeadPoint[] {
  return [...(timeline?.minutes ?? [])].sort((a, b) => a.minute - b.minute).map((m) => ({ minute: m.minute, lead: m.blueGold - m.redGold }))
}

/**
 * Where zero sits from the top of the gold-lead chart, 0..1, so one gradient can paint the blue lead above the
 * line and the red lead below it. All above zero is 1, all below is 0.
 */
export function zeroOffset(values: readonly number[]): number {
  if (values.length === 0) return 0.5
  const max = Math.max(...values)
  const min = Math.min(...values)
  if (max <= 0) return 0
  if (min >= 0) return 1
  return max / (max - min)
}

export interface LaneDiff {
  lane: PositionKey
  blue: LolMatchParticipant
  red: LolMatchParticipant
  /** Blue − red; null when the game had no snapshot at that minute (it ended earlier) */
  at10: LolTimelineSnapshot | null
  at15: LolTimelineSnapshot | null
}

const diff = (a: LolTimelineSnapshot | null | undefined, b: LolTimelineSnapshot | null | undefined): LolTimelineSnapshot | null =>
  a && b ? { gold: a.gold - b.gold, cs: a.cs - b.cs, xp: a.xp - b.xp, level: a.level - b.level } : null

/** Each lane where both teams had a player in that position: the blue one against the red one at 10 and 15 minutes. */
export function laneDiffs(participants: readonly LolMatchParticipant[], timeline: LolTimeline | null | undefined): LaneDiff[] {
  if (!timeline) return []
  const frames = new Map(timeline.participants.map((p) => [p.participantId, p]))
  const { blue, red } = splitTeams(participants)
  const rows: LaneDiff[] = []
  for (const lane of LANE_ORDER) {
    const b = blue.find((p) => positionKey(p.position) === lane)
    const r = red.find((p) => positionKey(p.position) === lane)
    if (!b || !r) continue
    const bf = frames.get(b.participantId)
    const rf = frames.get(r.participantId)
    rows.push({ lane, blue: b, red: r, at10: diff(bf?.at10, rf?.at10), at15: diff(bf?.at15, rf?.at15) })
  }
  return rows
}

/** "+1.2k", "−340", "0": a difference with its sign, the large ones shortened. */
export function signedNumber(value: number, format: (n: number) => string = String): string {
  if (value === 0) return '0'
  return `${value > 0 ? '+' : '−'}${format(Math.abs(value))}`
}
