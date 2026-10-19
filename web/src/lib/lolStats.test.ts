import { describe, expect, it } from 'vitest'
import type { LolMatch } from '../api/types'
import {
  currentStreak,
  findMatch,
  itemSlots,
  multikillKey,
  nextSort,
  perMinute,
  positionKey,
  queueRows,
  rankChartPoints,
  roleRows,
  sortRows,
  toPercent,
  winPercent,
} from './lolStats'

describe('sortRows', () => {
  const rows = [
    { name: 'Ahri', kda: 3.2 },
    { name: 'Zed', kda: null },
    { name: 'Lux', kda: 5.1 },
    { name: 'Jinx', kda: 3.2 },
  ]

  it('sorts numbers both ways and keeps missing values last either way', () => {
    expect(sortRows(rows, (r) => r.kda, 'desc').map((r) => r.name)).toEqual(['Lux', 'Ahri', 'Jinx', 'Zed'])
    expect(sortRows(rows, (r) => r.kda, 'asc').map((r) => r.name)).toEqual(['Ahri', 'Jinx', 'Lux', 'Zed'])
  })

  it('sorts text alphabetically and leaves the input alone', () => {
    expect(sortRows(rows, (r) => r.name, 'asc').map((r) => r.name)).toEqual(['Ahri', 'Jinx', 'Lux', 'Zed'])
    expect(rows[0].name).toBe('Ahri')
    expect(rows[1].name).toBe('Zed')
  })
})

describe('nextSort', () => {
  it('reverses the same column and starts a new column in its own direction', () => {
    expect(nextSort({ key: 'games', direction: 'desc' }, 'games', 'desc')).toEqual({ key: 'games', direction: 'asc' })
    expect(nextSort({ key: 'games', direction: 'asc' }, 'games', 'desc')).toEqual({ key: 'games', direction: 'desc' })
    expect(nextSort({ key: 'games', direction: 'asc' }, 'champion', 'asc')).toEqual({ key: 'champion', direction: 'asc' })
  })
})

describe('multikillKey', () => {
  it('names double to penta and nothing below', () => {
    expect([null, undefined, 0, 1].map(multikillKey)).toEqual([null, null, null, null])
    expect([2, 3, 4, 5].map(multikillKey)).toEqual(['double', 'triple', 'quadra', 'penta'])
    // Riot caps it at five, but a larger number is still a penta
    expect(multikillKey(6)).toBe('penta')
  })
})

describe('roles and queues', () => {
  it('maps Riot positions and leaves unknown ones out', () => {
    expect(positionKey('UTILITY')).toBe('support')
    expect(positionKey('MIDDLE')).toBe('middle')
    expect(positionKey('')).toBeNull()
    expect(positionKey(null)).toBeNull()
    expect(positionKey('Invalid')).toBeNull()
  })

  it('lists lanes in map order, merges spellings and measures bars against the most played', () => {
    const rows = roleRows([
      { position: 'UTILITY', games: 4, wins: 1 },
      { position: 'TOP', games: 8, wins: 6 },
      { position: null, games: 5, wins: 2 },
      { position: 'SUPPORT', games: 2, wins: 1 },
    ])
    expect(rows.map((r) => r.key)).toEqual(['top', 'support'])
    expect(rows[0]).toEqual({ key: 'top', games: 8, wins: 6, winRate: 0.75, share: 1 })
    expect(rows[1]).toMatchObject({ games: 6, wins: 2, share: 0.75 })
    expect(roleRows(undefined)).toEqual([])
  })

  it('merges normal draft and blind, most played first', () => {
    const rows = queueRows([
      { queueId: 420, games: 3, wins: 2 },
      { queueId: 400, games: 2, wins: 1 },
      { queueId: 430, games: 2, wins: 2 },
    ])
    expect(rows.map((r) => [r.key, r.games, r.wins])).toEqual([
      ['normal', 4, 3],
      ['solo', 3, 2],
    ])
    expect(queueRows(null)).toEqual([])
  })
})

describe('numbers', () => {
  it('turns fractions into whole percents and keeps missing values missing', () => {
    expect(toPercent(0.536)).toBe(54)
    expect(toPercent(1)).toBe(100)
    expect(toPercent(null)).toBeNull()
    expect(toPercent(undefined)).toBeNull()
    expect(winPercent(2, 3)).toBe(67)
    expect(winPercent(0, 0)).toBeNull()
  })

  it('works out per-minute values', () => {
    expect(perMinute(180, 1800)).toBe(6)
    expect(perMinute(null, 1800)).toBeNull()
    expect(perMinute(10, 0)).toBeNull()
  })
})

describe('currentStreak', () => {
  it('prefers the server streak and counts it from the form strip for older servers', () => {
    expect(currentStreak({ win: false, count: 2 }, [true, true, true])).toEqual({ win: false, count: 2 })
    expect(currentStreak(undefined, [true, true, true, false, true])).toEqual({ win: true, count: 3 })
    expect(currentStreak(null, [false, true])).toEqual({ win: false, count: 1 })
    expect(currentStreak(undefined, [])).toBeNull()
  })
})

describe('itemSlots', () => {
  it('always gives seven slots', () => {
    expect(itemSlots([3006, 0, 6672])).toEqual([3006, 0, 6672, 0, 0, 0, 0])
    expect(itemSlots(null)).toBeNull()
    expect(itemSlots([])).toBeNull()
  })
})

describe('rankChartPoints', () => {
  it('labels each snapshot, orders them oldest first and skips unknown tiers', () => {
    const points = rankChartPoints([
      { recordedAt: '2026-10-18T10:00:00', tier: 'GOLD', division: 'I', lp: 0 },
      { recordedAt: '2026-10-17T10:00:00', tier: 'GOLD', division: 'II', lp: 45 },
      { recordedAt: '2026-10-16T10:00:00', tier: 'NONE', division: 'I', lp: 0 },
    ])
    expect(points.map((p) => p.label)).toEqual(['Gold II 45 LP', 'Gold I 0 LP'])
    // A promotion is a step up by the LP it took, not a jump
    expect(points[1].value - points[0].value).toBe(55)
    expect(rankChartPoints(undefined)).toEqual([])
  })
})

describe('findMatch', () => {
  const match = { matchId: 'TR1_1' } as LolMatch
  it('finds the match a record points at, if it is on the page', () => {
    expect(findMatch([match], 'TR1_1')).toBe(match)
    expect(findMatch([match], 'TR1_2')).toBeNull()
    expect(findMatch([match], null)).toBeNull()
  })
})
