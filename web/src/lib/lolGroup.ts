import type { LolGroupMember, LolPlayer } from '../api/types'
import { t } from './i18n'
import { sortForLeaderboard } from './lol'

// Pure helpers for the League group view; the server checks every code again.

/** The symbols codes are made of: no 0/O and no 1/I/L, so a code read aloud or retyped cannot be misread. */
const ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'
const CODE_LENGTH = 10
const HALF = `[${ALPHABET}]{5}`
/** A code inside other text, as XXXXX-XXXXX (the shared form) or with a space or nothing in between. */
const CODE_IN_TEXT = new RegExp(`(?:^|[^A-Z0-9])(${HALF})([-\\s]?)(${HALF})(?![A-Z0-9])`, 'g')

/** toUpperCase, not toLocaleUpperCase: on a Turkish phone "i" must not become a dotted "İ". */
export function normalizeInviteCode(text: string): string | null {
  const code = text.toUpperCase().replace(/[\s-]/g, '')
  return code.length === CODE_LENGTH && [...code].every((symbol) => ALPHABET.includes(symbol)) ? code : null
}

/**
 * Finds the code in whatever was pasted: the bare code, or the whole shared message. In a message the dashed
 * form wins, so a group name that happens to look like a code is not taken instead.
 */
export function extractInviteCode(text: string): string | null {
  const whole = normalizeInviteCode(text)
  if (whole) return whole
  const matches = [...text.toUpperCase().matchAll(CODE_IN_TEXT)]
  const best = matches.findLast((match) => match[2] === '-') ?? matches.at(-1)
  return best ? best[1] + best[3] : null
}

/** "K7QXM2PFD9" → "K7QXM-2PFD9", easier to read out and compare. */
export function formatInviteCode(code: string): string {
  return `${code.slice(0, 5)}-${code.slice(5)}`
}

/** The text sent through the share sheet: the link is inside the text, so Messages and WhatsApp keep it all. */
export function inviteMessage(name: string, code: string, origin: string): string {
  return t.inviteText(name, formatInviteCode(code), origin)
}

export type LinkedMember = LolGroupMember & { player: LolPlayer }

/** Linked members in leaderboard order, and members without an account in join order. */
export function splitGroupBoard(members: LolGroupMember[]): { ranked: LinkedMember[]; unlinked: LolGroupMember[] } {
  const linked = members.filter((member): member is LinkedMember => member.player !== null)
  const memberByPuuid = new Map(linked.map((member) => [member.player.puuid, member]))
  return {
    ranked: sortForLeaderboard(linked.map((member) => member.player)).map((player) => memberByPuuid.get(player.puuid)!),
    unlinked: members.filter((member) => member.player === null),
  }
}

/** When the stalest data on the board was fetched, for the "updated … ago" line; null when nothing was. */
export function oldestRefresh(players: (LolPlayer | null)[]): string | null {
  const times = players.map((player) => player?.refreshedAt).filter((time): time is string => time != null)
  return times.length === 0 ? null : times.reduce((oldest, time) => (time < oldest ? time : oldest))
}
