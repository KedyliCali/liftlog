import { useState } from 'react'
import type { LolRank } from '../api/types'
import { t } from '../lib/i18n'
import { championIconUrl, profileIconUrl, rankLabel } from '../lib/lol'
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

/** Tier name with LP; the tier sets the badge color (CSS keys on data-tier). */
export function RankBadge({ rank }: { rank: LolRank | null }) {
  if (!rank) return <span className="rank-badge unranked">{t.unranked}</span>
  return (
    <span className="rank-badge" data-tier={rank.tier}>
      {rankLabel(rank)} <span className="num">{t.lp(rank.lp)}</span>
    </span>
  )
}
