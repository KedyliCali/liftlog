import type { LolChampionMastery, LolGroupLiveMember, LolLiveGame, LolLiveParticipant, LolRankPoint } from '../api/types'
import { rankLadder } from './lol'

// Live games, mastery and LP changes: the small pure pieces behind the League player page and group board.

/**
 * Seconds the game has been running at `now`. Riot's start time is the truth once the game has started; while it
 * is still loading (start time 0) the length Riot reported is carried on from when it was asked.
 */
export function liveElapsedSeconds(game: Pick<LolLiveGame, 'gameStartTime' | 'gameLengthS' | 'fetchedAt'>, now: number): number {
  const seconds = game.gameStartTime > 0 ? (now - game.gameStartTime) / 1000 : game.gameLengthS + (now - game.fetchedAt) / 1000
  return Math.max(0, Math.floor(seconds))
}

/** "12:05", or "1:02:05" past an hour. */
export function gameClock(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds))
  const hours = Math.floor(s / 3600)
  const minutes = Math.floor((s % 3600) / 60)
  const rest = String(s % 60).padStart(2, '0')
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${rest}` : `${minutes}:${rest}`
}

/** "+18 LP" and "−21 LP", with a real minus sign so the two line up in a list. */
export function lpChangeText(change: number): string {
  return change > 0 ? `+${change} LP` : `−${Math.abs(change)} LP`
}

/** Blue (100) and red (200) players in Riot's order. */
export function liveTeams(participants: readonly LolLiveParticipant[]): { blue: LolLiveParticipant[]; red: LolLiveParticipant[] } {
  return { blue: participants.filter((p) => p.teamId === 100), red: participants.filter((p) => p.teamId !== 100) }
}

/** A member of the group board who is in a game right now, by puuid. */
export function inGameByPuuid(members: readonly LolGroupLiveMember[] | null | undefined): Map<string, LolGroupLiveMember> {
  const map = new Map<string, LolGroupLiveMember>()
  for (const member of members ?? []) if (member.inGame) map.set(member.puuid, member)
  return map
}

/** Mastery champions most points first, each with its share of the top one (for the bar under it). */
export function masteryRows(champions: readonly LolChampionMastery[]): (LolChampionMastery & { share: number })[] {
  const sorted = [...champions].sort((a, b) => b.points - a.points)
  const top = sorted[0]?.points ?? 0
  return sorted.map((c) => ({ ...c, share: top > 0 ? c.points / top : 0 }))
}

/** The peak only matters when it is above where the player stands now. */
export function peakAboveCurrent(peak: LolRankPoint | null | undefined, current: { tier: string; division: string; lp: number } | null): boolean {
  if (!peak) return false
  if (!current) return true
  const peakValue = rankLadder(peak)
  const currentValue = rankLadder(current)
  return peakValue != null && (currentValue == null || peakValue > currentValue)
}
