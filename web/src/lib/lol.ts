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

/** Where Master starts on the chart ladder: 7 tiers below it, 4 divisions of 100 LP each. */
export const LADDER_APEX = APEX_FROM * 400

/**
 * The rank as a position on one continuous LP ladder, for the LP chart: Gold II 45 LP is 45 above Gold II 0 LP,
 * and Gold I 0 LP is 100 above that. It orders ranks the same way as rankScore below Master, but without
 * rankScore's gaps of 900 between divisions, so a promotion looks like the 100 LP step it is instead of a
 * cliff. Master and above have one shared LP ladder in the game, so there LP alone decides.
 */
export function rankLadder(rank: Pick<LolRank, 'tier' | 'division' | 'lp'>): number | null {
  const tier = TIERS.indexOf(rank.tier)
  if (tier < 0) return null
  if (tier >= APEX_FROM) return LADDER_APEX + rank.lp
  return tier * 400 + Math.max(0, DIVISIONS.indexOf(rank.division)) * 100 + rank.lp
}

/** Axis label for a ladder value: the division that starts there ("Gold II"), or LP above Master. */
export function ladderLabel(value: number): string {
  if (value >= LADDER_APEX) return value === LADDER_APEX ? rankLabel({ tier: 'MASTER', division: 'I', lp: 0, wins: 0, losses: 0 }) : `${value - LADDER_APEX} LP`
  const tier = TIERS[Math.max(0, Math.floor(value / 400))]
  const division = DIVISIONS[Math.floor((value % 400) / 100)]
  return rankLabel({ tier, division, lp: 0, wins: 0, losses: 0 })
}

/**
 * Y axis for the LP chart: whole divisions around the data, with at most six labelled lines. The step doubles
 * (division, two divisions, tier, …) until the labels fit, and stays aligned so each line starts a division.
 */
export function ladderAxis(values: number[]): { domain: [number, number]; ticks: number[] } {
  const low = Math.floor(Math.min(...values) / 100) * 100
  let high = Math.ceil(Math.max(...values) / 100) * 100
  if (high === low) high += 100
  let step = 100
  let ticks: number[] = []
  for (;;) {
    ticks = []
    for (let v = Math.ceil(low / step) * step; v <= high; v += step) ticks.push(v)
    if (ticks.length <= 6) break
    step *= 2
  }
  return { domain: [low, high], ticks: ticks.length >= 2 ? ticks : [low, high] }
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

export const itemIconUrl = (version: string, itemId: number) => `${DDRAGON}/cdn/${version}/img/item/${itemId}.png`

/** Spell icons are named by the spell ("SummonerFlash.png"), while matches only carry its numeric key. */
export const summonerSpellDataUrl = (version: string) => `${DDRAGON}/cdn/${version}/data/en_US/summoner.json`

export const summonerSpellIconUrl = (version: string, file: string) => `${DDRAGON}/cdn/${version}/img/spell/${file}`

/** summoner.json → numeric key to icon file. Anything unexpected gives an empty map, so no icons show. */
export function parseSummonerSpells(json: unknown): Record<number, string> {
  const map: Record<number, string> = {}
  const data = (json as { data?: unknown } | null)?.data
  if (!data || typeof data !== 'object') return map
  for (const spell of Object.values(data as Record<string, { key?: unknown; image?: { full?: unknown } }>)) {
    const key = Number(spell?.key)
    const file = spell?.image?.full
    if (Number.isFinite(key) && typeof file === 'string') map[key] = file
  }
  return map
}

/** Match data uses Data Dragon ids ("MonkeyKing" for Wukong), so the name maps straight to the file. */
export const championIconUrl = (version: string, champion: string) => `${DDRAGON}/cdn/${version}/img/champion/${champion}.png`
