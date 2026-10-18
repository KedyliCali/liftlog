import type { LolPlayer, LolRank } from '../api/types'

// Pure helpers for the League tracker pages.

const TIERS = ['IRON', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'EMERALD', 'DIAMOND', 'MASTER', 'GRANDMASTER', 'CHALLENGER']
const DIVISIONS = ['IV', 'III', 'II', 'I']
/** Master and above have no divisions; LP alone orders them. */
const APEX_FROM = TIERS.indexOf('MASTER')

/**
 * One comparable number per rank: tier, then division, then LP. Tiers are 100 000 apart because Master and
 * above have no divisions and LP keeps climbing past 100. Unranked sorts below Iron IV 0 LP.
 */
export function rankScore(rank: LolRank | null): number {
  if (!rank) return -1
  const tier = TIERS.indexOf(rank.tier)
  if (tier < 0) return -1
  const division = tier >= APEX_FROM ? 0 : Math.max(0, DIVISIONS.indexOf(rank.division))
  return tier * 100_000 + division * 1_000 + rank.lp
}

/** Leaderboard order: rank first, then recent form, then name so ties are stable. */
export function sortForLeaderboard(players: LolPlayer[]): LolPlayer[] {
  return [...players].sort(
    (a, b) =>
      rankScore(b.rank) - rankScore(a.rank) ||
      recentWinRate(b) - recentWinRate(a) ||
      a.gameName.localeCompare(b.gameName),
  )
}

export function recentWinRate(player: Pick<LolPlayer, 'recentGames' | 'recentWins'>): number {
  return player.recentGames === 0 ? 0 : player.recentWins / player.recentGames
}

export function seasonWinRate(rank: LolRank): number {
  const games = rank.wins + rank.losses
  return games === 0 ? 0 : rank.wins / games
}

/** "Gold II", "Master"; tier names stay English as in the game client. */
export function rankLabel(rank: LolRank): string {
  const tier = rank.tier.charAt(0) + rank.tier.slice(1).toLowerCase()
  return TIERS.indexOf(rank.tier) >= APEX_FROM ? tier : `${tier} ${rank.division}`
}

/** "Name#TAG" with a 3–16 character name and 3–5 letter or digit tag, as the backend checks it. */
export function isValidRiotId(text: string): boolean {
  return /^[^#]{3,16}#[\p{L}\p{N}]{3,5}$/u.test(text.trim())
}

export function kdaRatio(kills: number, deaths: number, assists: number): number {
  return (kills + assists) / Math.max(1, deaths)
}

const QUEUE_KEYS: Record<number, string> = {
  420: 'solo',
  440: 'flex',
  400: 'normal',
  430: 'normal',
  490: 'quickplay',
  450: 'aram',
  1700: 'arena',
  1900: 'urf',
}

/** Translation key for a queue id; anything unlisted reads as "other". */
export function queueKey(queueId: number): string {
  return QUEUE_KEYS[queueId] ?? 'other'
}

/** Short relative time, e.g. "3 h" or "2 d", for match lists. */
export function timeAgo(iso: string, now: Date = new Date()): { value: number; unit: 'm' | 'h' | 'd' } {
  // The API sends UTC without a zone suffix
  const then = new Date(iso.endsWith('Z') ? iso : `${iso}Z`)
  const minutes = Math.max(0, Math.round((now.getTime() - then.getTime()) / 60_000))
  if (minutes < 60) return { value: minutes, unit: 'm' }
  if (minutes < 48 * 60) return { value: Math.round(minutes / 60), unit: 'h' }
  return { value: Math.round(minutes / (24 * 60)), unit: 'd' }
}

export function formatDuration(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

const DDRAGON = 'https://ddragon.leagueoflegends.com'

export const ddragonVersionsUrl = `${DDRAGON}/api/versions.json`

export const profileIconUrl = (version: string, iconId: number) => `${DDRAGON}/cdn/${version}/img/profileicon/${iconId}.png`

/** Match data uses Data Dragon ids ("MonkeyKing" for Wukong), so the name maps straight to the file. */
export const championIconUrl = (version: string, champion: string) => `${DDRAGON}/cdn/${version}/img/champion/${champion}.png`
