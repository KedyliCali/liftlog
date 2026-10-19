import { describe, expect, it } from 'vitest'
import type { LolPlayer, LolRank } from '../api/types'
import { formatDuration, isValidRiotId, kdaRatio, ladderAxis, ladderLabel, parseSummonerSpells, queueKey, rankLabel, rankLadder, rankScore, sortForLeaderboard, timeAgo } from './lol'

const rank = (tier: string, division: string, lp: number): LolRank => ({ tier, division, lp, wins: 10, losses: 10 })

const player = (gameName: string, r: LolRank | null, recentWins = 5, recentGames = 10): LolPlayer => ({
  puuid: gameName,
  gameName,
  tagLine: 'TR1',
  platform: 'TR1',
  profileIconId: 1,
  summonerLevel: 100,
  rank: r,
  recentGames,
  recentWins,
  refreshedAt: null,
})

describe('rankScore', () => {
  it('orders by tier, then division, then LP', () => {
    expect(rankScore(rank('GOLD', 'I', 0))).toBeGreaterThan(rankScore(rank('GOLD', 'II', 99)))
    expect(rankScore(rank('PLATINUM', 'IV', 0))).toBeGreaterThan(rankScore(rank('GOLD', 'I', 99)))
    expect(rankScore(rank('GOLD', 'II', 50))).toBeGreaterThan(rankScore(rank('GOLD', 'II', 10)))
  })

  it('puts apex tiers by LP and unranked last', () => {
    expect(rankScore(rank('GRANDMASTER', 'I', 0))).toBeGreaterThan(rankScore(rank('MASTER', 'I', 400)))
    expect(rankScore(null)).toBeLessThan(rankScore(rank('IRON', 'IV', 0)))
  })
})

describe('sortForLeaderboard', () => {
  it('breaks rank ties with recent form', () => {
    const sorted = sortForLeaderboard([
      player('Unranked', null),
      player('Gold', rank('GOLD', 'II', 40), 4),
      player('Gold hot', rank('GOLD', 'II', 40), 8),
      player('Plat', rank('PLATINUM', 'IV', 0)),
    ])
    expect(sorted.map((p) => p.gameName)).toEqual(['Plat', 'Gold hot', 'Gold', 'Unranked'])
  })
})

describe('labels and validation', () => {
  it('formats ranks like the game does', () => {
    expect(rankLabel(rank('GOLD', 'II', 0))).toBe('Gold II')
    expect(rankLabel(rank('MASTER', 'I', 0))).toBe('Master')
  })

  it('accepts Riot IDs with spaces and Turkish letters', () => {
    expect(isValidRiotId('Behlül K#TR1')).toBe(true)
    expect(isValidRiotId('ab#TR1')).toBe(false)
    expect(isValidRiotId('NoTag')).toBe(false)
    expect(isValidRiotId('Name#TOOLONG')).toBe(false)
  })

  it('maps queues and computes KDA safely', () => {
    expect(queueKey(420)).toBe('solo')
    expect(queueKey(9999)).toBe('other')
    expect(kdaRatio(5, 0, 7)).toBe(12)
    expect(formatDuration(1865)).toBe('31:05')
  })
})

describe('timeAgo', () => {
  const now = new Date('2026-10-18T12:00:00Z')
  it('reads server UTC times without a zone suffix', () => {
    expect(timeAgo('2026-10-18T11:45:00', now)).toEqual({ value: 15, unit: 'm' })
    expect(timeAgo('2026-10-18T06:00:00', now)).toEqual({ value: 6, unit: 'h' })
    expect(timeAgo('2026-10-14T12:00:00', now)).toEqual({ value: 4, unit: 'd' })
  })
})

describe('rankLadder', () => {
  const ranks = [
    rank('IRON', 'IV', 0),
    rank('SILVER', 'I', 99),
    rank('GOLD', 'IV', 0),
    rank('GOLD', 'II', 45),
    rank('GOLD', 'I', 0),
    rank('DIAMOND', 'I', 99),
    rank('MASTER', 'I', 0),
    rank('MASTER', 'I', 350),
  ]

  it('orders ranks the same way as rankScore', () => {
    const byLadder = [...ranks].sort((a, b) => rankLadder(a)! - rankLadder(b)!)
    const byScore = [...ranks].sort((a, b) => rankScore(a) - rankScore(b))
    expect(byLadder).toEqual(byScore)
  })

  it('makes a division 100 LP tall, with no gap between divisions', () => {
    expect(rankLadder(rank('GOLD', 'I', 0))! - rankLadder(rank('GOLD', 'II', 99))!).toBe(1)
    expect(rankLadder(rank('IRON', 'IV', 0))).toBe(0)
    expect(rankLadder(rank('UNKNOWN', 'I', 0))).toBeNull()
  })

  it('labels axis lines with the division that starts there', () => {
    expect(ladderLabel(rankLadder(rank('GOLD', 'II', 0))!)).toBe('Gold II')
    expect(ladderLabel(rankLadder(rank('MASTER', 'I', 0))!)).toBe('Master')
    expect(ladderLabel(rankLadder(rank('MASTER', 'I', 200))!)).toBe('200 LP')
  })

  it('puts the axis on whole divisions with at most six lines', () => {
    const gold2 = rankLadder(rank('GOLD', 'II', 45))!
    const gold1 = rankLadder(rank('GOLD', 'I', 30))!
    const near = ladderAxis([gold2, gold1])
    expect(near.domain).toEqual([rankLadder(rank('GOLD', 'II', 0)), rankLadder(rank('PLATINUM', 'IV', 0))])
    expect(near.ticks.map(ladderLabel)).toEqual(['Gold II', 'Gold I', 'Platinum IV'])

    const wide = ladderAxis([rankLadder(rank('IRON', 'IV', 10))!, rankLadder(rank('DIAMOND', 'I', 50))!])
    expect(wide.ticks.length).toBeLessThanOrEqual(6)
    expect(wide.ticks.every((tick) => tick % 100 === 0)).toBe(true)

    // One point exactly on a boundary still gets a range to draw in
    expect(ladderAxis([1200, 1200]).domain).toEqual([1200, 1300])
  })
})

describe('parseSummonerSpells', () => {
  it('maps the numeric key to the icon file', () => {
    const json = { data: { SummonerFlash: { key: '4', image: { full: 'SummonerFlash.png' } }, SummonerDot: { key: '14', image: { full: 'SummonerDot.png' } } } }
    expect(parseSummonerSpells(json)).toEqual({ 4: 'SummonerFlash.png', 14: 'SummonerDot.png' })
  })

  it('gives an empty map for anything unexpected', () => {
    expect(parseSummonerSpells(['15.1.1'])).toEqual({})
    expect(parseSummonerSpells(null)).toEqual({})
    expect(parseSummonerSpells({ data: { Broken: { key: 'x' } } })).toEqual({})
  })
})
