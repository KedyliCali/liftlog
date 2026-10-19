import { describe, expect, it } from 'vitest'
import type { LolGroupMember, LolPlayer, LolRank } from '../api/types'
import { extractInviteCode, formatInviteCode, inviteMessage, normalizeInviteCode, oldestRefresh, splitGroupBoard } from './lolGroup'

const gold = (lp: number): LolRank => ({ tier: 'GOLD', division: 'II', lp, wins: 10, losses: 10 })

const player = (puuid: string, rank: LolRank | null, refreshedAt: string | null = null): LolPlayer => ({
  puuid,
  gameName: puuid,
  tagLine: 'TR1',
  platform: 'TR1',
  profileIconId: 1,
  summonerLevel: 100,
  rank,
  recentGames: 10,
  recentWins: 5,
  refreshedAt,
})

const member = (userId: number, linked: LolPlayer | null, flags: Partial<LolGroupMember> = {}): LolGroupMember => ({
  userId,
  displayName: `User ${userId}`,
  you: false,
  owner: false,
  joinedAt: `2026-10-19T12:0${userId}:00`,
  player: linked,
  ...flags,
})

describe('invite codes', () => {
  it('normalizes case, dashes and spaces', () => {
    expect(normalizeInviteCode('k7qxm-2pfd9')).toBe('K7QXM2PFD9')
    expect(normalizeInviteCode(' K7QXM 2PFD9 ')).toBe('K7QXM2PFD9')
  })

  it('rejects look-alikes and wrong lengths', () => {
    expect(normalizeInviteCode('K7QXM-2PFD0')).toBeNull()
    expect(normalizeInviteCode('K7QXO2PFD9')).toBeNull()
    expect(normalizeInviteCode('K7QXI2PFD9')).toBeNull()
    expect(normalizeInviteCode('K7QXM2PFD')).toBeNull()
    expect(normalizeInviteCode('K7QXM2PFD9A')).toBeNull()
  })

  it('finds the code in the whole shared message, not in the name or the link', () => {
    const message = inviteMessage('ABCDE FGHJK', 'K7QXM2PFD9', 'https://liftlog-web.onrender.com')
    expect(extractInviteCode(message)).toBe('K7QXM2PFD9')
    expect(extractInviteCode('kod: k7qxm 2pfd9')).toBe('K7QXM2PFD9')
    expect(extractInviteCode('K7QXM-2PFD9')).toBe('K7QXM2PFD9')
    expect(extractInviteCode('see you at 8')).toBeNull()
    expect(extractInviteCode('')).toBeNull()
  })

  it('formats with a dash and puts the code and link in the message', () => {
    expect(formatInviteCode('K7QXM2PFD9')).toBe('K7QXM-2PFD9')
    const message = inviteMessage('Duo Queue', 'K7QXM2PFD9', 'https://example.test')
    expect(message).toContain('K7QXM-2PFD9')
    expect(message).toContain('https://example.test')
    expect(message).toContain('Duo Queue')
  })
})

describe('splitGroupBoard', () => {
  it('ranks linked members and lists the rest in join order', () => {
    const members = [
      member(1, player('low', gold(10)), { owner: true }),
      member(2, null),
      member(3, player('high', gold(90)), { you: true }),
      member(4, null),
    ]

    const { ranked, unlinked } = splitGroupBoard(members)

    expect(ranked.map((m) => m.userId)).toEqual([3, 1])
    expect(ranked[0].you).toBe(true)
    expect(ranked[1].owner).toBe(true)
    expect(unlinked.map((m) => m.userId)).toEqual([2, 4])
  })
})

describe('oldestRefresh', () => {
  it('ignores players that were never refreshed', () => {
    expect(oldestRefresh([player('a', null, '2026-10-19T12:10:00'), null, player('b', null, '2026-10-19T12:00:00'), player('c', null)]))
      .toBe('2026-10-19T12:00:00')
    expect(oldestRefresh([player('c', null), null])).toBeNull()
  })
})
