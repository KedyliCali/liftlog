import { describe, expect, it } from 'vitest'
import { gameClock, inGameByPuuid, liveElapsedSeconds, liveTeams, lpChangeText, masteryRows, peakAboveCurrent } from './lolLive'
import type { LolLiveParticipant } from '../api/types'

describe('liveElapsedSeconds', () => {
  it('counts from the start time once the game has started', () => {
    expect(liveElapsedSeconds({ gameStartTime: 1_000_000, gameLengthS: 5, fetchedAt: 1_500_000 }, 1_000_000 + 754_900)).toBe(754)
  })

  it('carries the reported length on while the game is loading', () => {
    expect(liveElapsedSeconds({ gameStartTime: 0, gameLengthS: 30, fetchedAt: 2_000_000 }, 2_010_000)).toBe(40)
  })

  it('never goes below zero when the phone clock is behind', () => {
    expect(liveElapsedSeconds({ gameStartTime: 5_000, gameLengthS: 0, fetchedAt: 5_000 }, 1_000)).toBe(0)
  })
})

describe('gameClock', () => {
  it('reads like the in-game clock', () => {
    expect(gameClock(0)).toBe('0:00')
    expect(gameClock(754)).toBe('12:34')
    expect(gameClock(3725)).toBe('1:02:05')
  })
})

describe('lpChangeText', () => {
  it('signs gains and losses with a real minus', () => {
    expect(lpChangeText(18)).toBe('+18 LP')
    expect(lpChangeText(-21)).toBe('−21 LP')
  })
})

describe('masteryRows', () => {
  it('sorts by points and gives each a share of the top champion', () => {
    const rows = masteryRows([
      { championId: 1, level: 5, points: 50_000, lastPlayed: null },
      { championId: 2, level: 20, points: 200_000, lastPlayed: null },
    ])
    expect(rows.map((r) => r.championId)).toEqual([2, 1])
    expect(rows.map((r) => r.share)).toEqual([1, 0.25])
    expect(masteryRows([])).toEqual([])
  })
})

describe('peakAboveCurrent', () => {
  const peak = { recordedAt: '2026-03-01T10:00:00', tier: 'PLATINUM', division: 'IV', lp: 20 }

  it('shows the peak only when it is higher than the current rank', () => {
    expect(peakAboveCurrent(peak, { tier: 'GOLD', division: 'I', lp: 80 })).toBe(true)
    expect(peakAboveCurrent(peak, { tier: 'PLATINUM', division: 'IV', lp: 20 })).toBe(false)
    expect(peakAboveCurrent(peak, { tier: 'PLATINUM', division: 'III', lp: 0 })).toBe(false)
    // Unranked now (decayed out, or a new season of placements): the peak is worth showing
    expect(peakAboveCurrent(peak, null)).toBe(true)
    expect(peakAboveCurrent(null, null)).toBe(false)
  })
})

describe('liveTeams and inGameByPuuid', () => {
  it('splits players by side', () => {
    const player = (teamId: number, puuid: string) => ({ teamId, puuid }) as LolLiveParticipant
    const { blue, red } = liveTeams([player(100, 'a'), player(200, 'b'), player(100, 'c')])
    expect(blue.map((p) => p.puuid)).toEqual(['a', 'c'])
    expect(red.map((p) => p.puuid)).toEqual(['b'])
  })

  it('keeps only members who are in a game', () => {
    const base = { userId: 1, queueId: null, gameMode: null, gameStartTime: null, gameLengthS: null, fetchedAt: null, championId: null }
    const map = inGameByPuuid([
      { ...base, puuid: 'a', inGame: true },
      { ...base, puuid: 'b', inGame: false },
      { ...base, puuid: 'c', inGame: null },
    ])
    expect([...map.keys()]).toEqual(['a'])
    expect(inGameByPuuid(null).size).toBe(0)
  })
})
