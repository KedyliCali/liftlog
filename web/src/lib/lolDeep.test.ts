import { describe, expect, it } from 'vitest'
import type { LolMatchParticipant, LolTimeline } from '../api/types'
import {
  buildItems,
  goldLeadPoints,
  inQueue,
  laneDiffs,
  parseChampionKeys,
  parseItems,
  parseRunes,
  patchOf,
  playerTab,
  signedNumber,
  spellPair,
  splitTeams,
  statsQueue,
  statsWindow,
  trendPoints,
  tzOffsetMinutes,
  weekdayName,
  winRateRows,
  zeroOffset,
} from './lolDeep'

const player = (participantId: number, teamId: number, position: string | null, champion = `C${participantId}`) =>
  ({ participantId, teamId, position, champion }) as LolMatchParticipant

describe('URL choices', () => {
  it('falls back to the overview, all queues and 20 games for anything unknown', () => {
    expect(playerTab('duos')).toBe('duos')
    expect(playerTab('nope')).toBe('overview')
    expect(playerTab(null)).toBe('overview')
    expect(statsQueue('FLEX')).toBe('FLEX')
    expect(statsQueue('flex')).toBe('ALL')
    expect(statsWindow('100')).toBe(100)
    expect(statsWindow('30')).toBe(20)
    expect(statsWindow(null)).toBe(20)
  })

  it('turns the browser offset into minutes east of UTC', () => {
    expect(tzOffsetMinutes({ getTimezoneOffset: () => -180 } as Date)).toBe(180)
    expect(tzOffsetMinutes({ getTimezoneOffset: () => 300 } as Date)).toBe(-300)
    expect(Object.is(tzOffsetMinutes({ getTimezoneOffset: () => 0 } as Date), 0)).toBe(true)
  })

  it('splits queues like the server', () => {
    expect(inQueue(420, 'ALL')).toBe(true)
    expect(inQueue(420, 'SOLO')).toBe(true)
    expect(inQueue(440, 'SOLO')).toBe(false)
    expect(inQueue(450, 'ARAM')).toBe(true)
    expect(inQueue(400, 'OTHER')).toBe(true)
    expect(inQueue(440, 'OTHER')).toBe(false)
  })
})

describe('chart and bar data', () => {
  it('measures bars against the busiest row and leaves empty rows out', () => {
    const rows = winRateRows(
      [
        { label: '<25', games: 4, wins: 3 },
        { label: '25-30', games: 0, wins: 0 },
        { label: '30-35', games: 8, wins: 2 },
      ],
      (row) => row.label,
    )
    expect(rows).toEqual([
      { key: '<25', games: 4, wins: 3, share: 0.5 },
      { key: '30-35', games: 8, wins: 2, share: 1 },
    ])
    expect(winRateRows(null, () => '')).toEqual([])
  })

  it('names weekdays from Monday', () => {
    expect(weekdayName(1, 'en-US')).toBe('Mon')
    expect(weekdayName(7, 'en-US')).toBe('Sun')
  })

  it('skips games without the metric and charts kill participation in percent', () => {
    const base = { endedAt: '2026-10-18T10:00:00', kda: 3, csPerMinute: 7, damagePerMinute: 800, goldPerMinute: 400, visionPerMinute: 1 }
    const points = trendPoints(
      [
        { ...base, matchId: 'a', win: true, killParticipation: 0.5 },
        { ...base, matchId: 'b', win: false, killParticipation: null },
        { ...base, matchId: 'c', win: true, killParticipation: 0.25 },
      ],
      'killParticipation',
    )
    expect(points.map((p) => [p.index, p.matchId, p.value])).toEqual([
      [1, 'a', 50],
      [2, 'c', 25],
    ])
    expect(trendPoints(undefined, 'kda')).toEqual([])
  })

  it('reads the patch and the spell pair', () => {
    expect(patchOf('14.20.123.4567')).toBe('14.20')
    expect(patchOf('15')).toBe('15')
    expect(patchOf(null)).toBeNull()
    expect(spellPair('4,14')).toEqual([4, 14])
    expect(spellPair('4,x')).toEqual([4])
  })

  it('signs differences', () => {
    expect(signedNumber(120)).toBe('+120')
    expect(signedNumber(-45)).toBe('−45')
    expect(signedNumber(0)).toBe('0')
  })
})

describe('Data Dragon', () => {
  it('marks consumables and trinkets so the build table can drop them', () => {
    const info = parseItems({
      data: {
        '2003': { name: 'Health Potion', tags: ['Consumable'] },
        '3340': { name: 'Stealth Ward', tags: ['Trinket'] },
        '2140': { name: 'Elixir of Wrath', consumed: true },
        '3020': { name: "Sorcerer's Shoes", tags: ['Boots'] },
      },
    })
    expect(info[3020]).toEqual({ name: "Sorcerer's Shoes", consumable: false })
    const items = [3020, 2003, 3340, 2140, 0, 9999].map((itemId) => ({ itemId, games: 1, wins: 1 }))
    // An item missing from item.json (newer patch) stays
    expect(buildItems(items, info).map((i) => i.itemId)).toEqual([3020, 9999])
    // Without item.json nothing can be told apart, so everything but empty slots stays
    expect(buildItems(items, null).map((i) => i.itemId)).toEqual([3020, 2003, 3340, 2140, 9999])
    expect(parseItems(null)).toEqual({})
  })

  it('maps rune trees and runes to their icons', () => {
    const runes = parseRunes([
      {
        id: 8100,
        name: 'Domination',
        icon: 'perk-images/Styles/7200_Domination.png',
        slots: [{ runes: [{ id: 8112, name: 'Electrocute', icon: 'perk-images/Styles/Domination/Electrocute/Electrocute.png' }] }],
      },
    ])
    expect(runes[8100].name).toBe('Domination')
    expect(runes[8112].icon).toBe('perk-images/Styles/Domination/Electrocute/Electrocute.png')
    expect(parseRunes({})).toEqual({})
  })

  it('maps numeric champion keys to icon ids', () => {
    expect(parseChampionKeys({ data: { MonkeyKing: { id: 'MonkeyKing', key: '62' }, Ahri: { id: 'Ahri', key: '103' } } })).toEqual({ 62: 'MonkeyKing', 103: 'Ahri' })
    expect(parseChampionKeys('oops')).toEqual({})
  })
})

describe('match detail', () => {
  it('splits the teams and orders each by lane', () => {
    const { blue, red } = splitTeams([player(1, 100, 'UTILITY'), player(2, 100, 'TOP'), player(6, 200, null), player(7, 200, 'MIDDLE')])
    expect(blue.map((p) => p.participantId)).toEqual([2, 1])
    expect(red.map((p) => p.participantId)).toEqual([7, 6])
  })

  it('draws the gold lead from blue minus red, in minute order', () => {
    const timeline: LolTimeline = {
      matchId: 'm',
      minutes: [
        { minute: 1, blueGold: 3000, redGold: 3200 },
        { minute: 0, blueGold: 2500, redGold: 2500 },
      ],
      participants: [],
    }
    expect(goldLeadPoints(timeline)).toEqual([
      { minute: 0, lead: 0 },
      { minute: 1, lead: -200 },
    ])
    expect(zeroOffset([300, -100])).toBe(0.75)
    expect(zeroOffset([1, 2])).toBe(1)
    expect(zeroOffset([-1, -2])).toBe(0)
  })

  it('compares lane opponents at 10 and 15 minutes', () => {
    const participants = [player(1, 100, 'TOP'), player(2, 100, 'MIDDLE'), player(6, 200, 'TOP'), player(7, 200, 'JUNGLE')]
    const snap = (gold: number, cs: number) => ({ gold, cs, xp: gold * 2, level: 7 })
    const timeline: LolTimeline = {
      matchId: 'm',
      minutes: [],
      participants: [
        { participantId: 1, at10: snap(4000, 80), at15: null, gold: [] },
        { participantId: 6, at10: snap(3500, 85), at15: null, gold: [] },
      ],
    }
    const rows = laneDiffs(participants, timeline)
    // Only top has a player on both sides
    expect(rows).toHaveLength(1)
    expect(rows[0].lane).toBe('top')
    expect(rows[0].at10).toEqual({ gold: 500, cs: -5, xp: 1000, level: 0 })
    expect(rows[0].at15).toBeNull()
    expect(laneDiffs(participants, null)).toEqual([])
  })
})
