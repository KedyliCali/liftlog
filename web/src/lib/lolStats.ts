import type { LolMatch, LolQueueStats, LolRankPoint, LolRoleStats, LolStreak } from '../api/types'
import { queueKey, rankLabel, rankLadder } from './lol'

// Pure helpers for the detailed League stats: table sorting, labels, grouping and chart data.

export type SortDirection = 'asc' | 'desc'

export interface SortState<K extends string = string> {
  key: K
  direction: SortDirection
}

/**
 * Sorted copy of the rows. Missing values (older matches lack the detailed numbers) always go last, whichever
 * the direction, so reversing a column never puts a row of dashes on top. Equal values keep their order.
 */
export function sortRows<T>(rows: readonly T[], value: (row: T) => number | string | null | undefined, direction: SortDirection): T[] {
  const sign = direction === 'asc' ? 1 : -1
  return rows
    .map((row, index) => ({ row, index, v: value(row) }))
    .sort((a, b) => {
      const aMissing = a.v == null || (typeof a.v === 'number' && Number.isNaN(a.v))
      const bMissing = b.v == null || (typeof b.v === 'number' && Number.isNaN(b.v))
      if (aMissing || bMissing) return aMissing === bMissing ? a.index - b.index : aMissing ? 1 : -1
      const order = typeof a.v === 'string' && typeof b.v === 'string' ? a.v.localeCompare(b.v) : (a.v as number) - (b.v as number)
      return order * sign || a.index - b.index
    })
    .map(({ row }) => row)
}

/** A tap on a header: the same column reverses, another column starts in its own natural direction. */
export function nextSort<K extends string>(current: SortState<K>, key: K, firstDirection: SortDirection): SortState<K> {
  if (current.key === key) return { key, direction: current.direction === 'asc' ? 'desc' : 'asc' }
  return { key, direction: firstDirection }
}

export type MultikillKey = 'double' | 'triple' | 'quadra' | 'penta'

/** The largest multikill as a badge key; nothing below a double kill. */
export function multikillKey(largestMultiKill: number | null | undefined): MultikillKey | null {
  if (largestMultiKill == null || largestMultiKill < 2) return null
  if (largestMultiKill === 2) return 'double'
  if (largestMultiKill === 3) return 'triple'
  if (largestMultiKill === 4) return 'quadra'
  return 'penta'
}

export type PositionKey = 'top' | 'jungle' | 'middle' | 'bottom' | 'support'

const POSITION_KEYS: Record<string, PositionKey> = {
  TOP: 'top',
  JUNGLE: 'jungle',
  MIDDLE: 'middle',
  MID: 'middle',
  BOTTOM: 'bottom',
  ADC: 'bottom',
  UTILITY: 'support',
  SUPPORT: 'support',
}
const LANE_ORDER: PositionKey[] = ['top', 'jungle', 'middle', 'bottom', 'support']

export function positionKey(position: string | null | undefined): PositionKey | null {
  return position ? (POSITION_KEYS[position.toUpperCase()] ?? null) : null
}

export interface RoleRow {
  key: PositionKey
  games: number
  wins: number
  winRate: number
  /** Games as a share of the most played role, for the bar length */
  share: number
}

/**
 * One row per lane in map order (top to support). Games without a lane (ARAM, Arena) are left out; a lane the
 * server sent twice under two spellings is merged.
 */
export function roleRows(roles: readonly LolRoleStats[] | null | undefined): RoleRow[] {
  const byLane = new Map<PositionKey, { games: number; wins: number }>()
  for (const role of roles ?? []) {
    const key = positionKey(role.position)
    if (!key || role.games <= 0) continue
    const sum = byLane.get(key) ?? { games: 0, wins: 0 }
    byLane.set(key, { games: sum.games + role.games, wins: sum.wins + role.wins })
  }
  const most = Math.max(0, ...[...byLane.values()].map((lane) => lane.games))
  return LANE_ORDER.filter((key) => byLane.has(key)).map((key) => {
    const { games, wins } = byLane.get(key)!
    return { key, games, wins, winRate: wins / games, share: games / most }
  })
}

export interface QueueRow {
  key: string
  games: number
  wins: number
  winRate: number
  share: number
}

/** Queues by their label (normal draft and blind are both "Normal"), most played first. */
export function queueRows(queues: readonly LolQueueStats[] | null | undefined): QueueRow[] {
  const byKey = new Map<string, { games: number; wins: number }>()
  for (const queue of queues ?? []) {
    if (queue.games <= 0) continue
    const key = queueKey(queue.queueId)
    const sum = byKey.get(key) ?? { games: 0, wins: 0 }
    byKey.set(key, { games: sum.games + queue.games, wins: sum.wins + queue.wins })
  }
  const most = Math.max(0, ...[...byKey.values()].map((queue) => queue.games))
  return sortRows(
    [...byKey.entries()].map(([key, { games, wins }]) => ({ key, games, wins, winRate: wins / games, share: games / most })),
    (row) => row.games,
    'desc',
  )
}

/** 0..1 → whole percent; null stays null so the cell can show a dash. */
export function toPercent(fraction: number | null | undefined): number | null {
  return fraction == null || Number.isNaN(fraction) ? null : Math.round(fraction * 100)
}

export function winPercent(wins: number, games: number): number | null {
  return games > 0 ? Math.round((wins / games) * 100) : null
}

/** One decimal for per-minute numbers and KDA; whole numbers for big ones like damage per minute. */
export function roundTo(value: number, decimals: number): number {
  const factor = 10 ** decimals
  return Math.round(value * factor) / factor
}

export function perMinute(value: number | null | undefined, durationS: number): number | null {
  return value == null || durationS <= 0 ? null : value / (durationS / 60)
}

/**
 * The current run of wins or losses. Older servers send no streak, so it is counted from the form strip
 * (newest first), which holds the same games.
 */
export function currentStreak(streak: LolStreak | null | undefined, form: readonly boolean[]): LolStreak | null {
  if (streak) return streak.count > 0 ? streak : null
  if (form.length === 0) return null
  let count = 0
  while (count < form.length && form[count] === form[0]) count += 1
  return { win: form[0], count }
}

/** Always seven slots (six items and the trinket), so the icons line up from row to row; 0 is empty. */
export function itemSlots(items: readonly number[] | null | undefined): number[] | null {
  if (!items || items.length === 0) return null
  return Array.from({ length: 7 }, (_, i) => items[i] ?? 0)
}

export interface RankChartPoint {
  /** Position on the LP ladder (see rankLadder) */
  value: number
  /** "Gold II 45 LP" */
  label: string
  recordedAt: string
}

/** Rank snapshots as chart points, oldest first; snapshots with an unknown tier are skipped. */
export function rankChartPoints(history: readonly LolRankPoint[] | null | undefined): RankChartPoint[] {
  const points: RankChartPoint[] = []
  for (const point of history ?? []) {
    const value = rankLadder(point)
    if (value == null) continue
    points.push({ value, label: `${rankLabel({ ...point, wins: 0, losses: 0 })} ${point.lp} LP`, recordedAt: point.recordedAt })
  }
  return points.sort((a, b) => a.recordedAt.localeCompare(b.recordedAt))
}

/** The match a record points at, if it is among the matches on the page. */
export function findMatch(matches: readonly LolMatch[], matchId: string | null | undefined): LolMatch | null {
  return matchId ? (matches.find((match) => match.matchId === matchId) ?? null) : null
}
