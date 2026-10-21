import { useState, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router'
import type { LolPlayer, LolRank } from '../api/types'
import { t, termLang } from '../lib/i18n'
import { championIconUrl, itemIconUrl, profileIconUrl, rankLabel, recentWinRate, runeIconUrl, summonerSpellIconUrl } from '../lib/lol'
import type { RuneInfo } from '../lib/lolDeep'
import { initials } from '../lib/workoutLogic'

export function LolAvatar({ version, iconId, name, size = 44 }: { version: string | null; iconId: number | null; name: string; size?: number }) {
  const [failed, setFailed] = useState(false)
  const showImage = version && iconId != null && !failed
  return (
    <span className="lol-avatar" style={{ width: size, height: size }}>
      {showImage ? <img src={profileIconUrl(version, iconId)} alt="" loading="lazy" onError={() => setFailed(true)} /> : initials(name)}
    </span>
  )
}

export function ChampionIcon({ version, champion, size = 40 }: { version: string | null; champion: string; size?: number }) {
  const [failed, setFailed] = useState(false)
  return (
    <span className="champion-icon" style={{ width: size, height: size }}>
      {version && !failed ? (
        <img src={championIconUrl(version, champion)} alt={champion} loading="lazy" onError={() => setFailed(true)} />
      ) : (
        champion.slice(0, 2)
      )}
    </span>
  )
}

/** One inventory slot; 0 (or a picture that fails to load) is an empty square, so the seven slots line up. */
export function ItemIcon({ version, itemId, name }: { version: string | null; itemId: number; name?: string }) {
  const [failed, setFailed] = useState(false)
  if (!version || itemId === 0 || failed) return <span className="item-icon empty" aria-hidden />
  return (
    <span className="item-icon" title={name}>
      <img src={itemIconUrl(version, itemId)} alt={name ?? ''} loading="lazy" onError={() => setFailed(true)} />
    </span>
  )
}

/** A summoner spell by its numeric key; nothing at all when the key is not in Data Dragon's list. */
export function SpellIcon({ version, spellKey, spells }: { version: string | null; spellKey: number; spells: Record<number, string> | null }) {
  const [failed, setFailed] = useState(false)
  const file = spells?.[spellKey]
  if (!version || !file || failed) return null
  return (
    <span className="spell-icon">
      <img src={summonerSpellIconUrl(version, file)} alt="" loading="lazy" onError={() => setFailed(true)} />
    </span>
  )
}

/** A rune or rune tree by id; its name as a short text badge until runesReforged.json (or the picture) arrives. */
export function RuneIcon({ runeId, runes, size = 22 }: { runeId: number | null | undefined; runes: Record<number, RuneInfo> | null; size?: number }) {
  const [failed, setFailed] = useState(false)
  if (runeId == null) return null
  const rune = runes?.[runeId]
  return (
    <span className="rune-icon" style={{ width: size, height: size }} title={rune?.name}>
      {rune && !failed ? <img src={runeIconUrl(rune.icon)} alt={rune.name} loading="lazy" onError={() => setFailed(true)} /> : (rune?.name ?? String(runeId)).slice(0, 2)}
    </span>
  )
}

/** Tier name with LP; the tier sets the badge color (CSS keys on data-tier). */
export function RankBadge({ rank }: { rank: LolRank | null }) {
  if (!rank) return <span className="rank-badge unranked" lang={termLang(t.unranked)}>{t.unranked}</span>
  // Tier names are English in every language (as in the game client), so they uppercase as English
  return (
    <span className="rank-badge" data-tier={rank.tier} lang="en">
      {rankLabel(rank)} <span className="num">{t.lp(rank.lp)}</span>
    </span>
  )
}

/** Wins as a filled share of a thin bar: recent form at a glance without reading numbers. */
export function FormBar({ wins, games }: { wins: number; games: number }) {
  return (
    <span className="form-bar" aria-hidden>
      <span style={{ width: games === 0 ? 0 : `${(wins / games) * 100}%` }} />
    </span>
  )
}

/**
 * One leaderboard line, shared by the follow list and the group board. On the group board the member's
 * LiftLog name sits under the Riot name, and your own row is marked.
 */
export function LolBoardRow({ player, place, version, memberName, you = false, style, badge }: {
  player: LolPlayer
  /** 1-based; the top three get the signal colors */
  place: number
  version: string | null
  memberName?: string
  you?: boolean
  style?: CSSProperties
  /** Shown next to the rank, e.g. the group board's "in game now" tag */
  badge?: ReactNode
}) {
  return (
    <Link to={`/game/${encodeURIComponent(player.puuid)}`} viewTransition className={you ? 'lol-row you' : 'lol-row'} style={style}>
      {you && <span className="you-tag">{t.youTag}</span>}
      <span className={place <= 3 ? `lol-place top-${place}` : 'lol-place'}>{place}</span>
      <LolAvatar version={version} iconId={player.profileIconId} name={player.gameName} />
      <span className="lol-row-main">
        <span className="lol-name">
          {player.gameName}
          <small>#{player.tagLine}</small>
        </span>
        {memberName && <span className="lol-member-name">{memberName}</span>}
        <span className="lol-row-badges">
          <RankBadge rank={player.rank} />
          {badge}
        </span>
      </span>
      <span className="lol-row-side">
        <FormBar wins={player.recentWins} games={player.recentGames} />
        <small className="muted num">{player.recentGames > 0 ? t.winRate(Math.round(recentWinRate(player) * 100)) : '–'}</small>
      </span>
    </Link>
  )
}
