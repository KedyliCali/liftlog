import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { LolPlayer } from '../api/types'
import { useToast } from '../components/contexts'
import { ControllerIcon } from '../components/Icons'
import { LolAvatar, RankBadge } from '../components/LolBits'
import { useDdragonVersion } from '../components/useDdragonVersion'
import { Sheet } from '../components/Sheet'
import { WorkoutListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { SwipeToDelete } from '../components/SwipeToDelete'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { isValidRiotId, recentWinRate, sortForLeaderboard } from '../lib/lol'

const PLATFORMS = ['TR1', 'EUW1', 'EUN1', 'RU', 'ME1', 'NA1', 'BR1', 'LA1', 'LA2', 'KR', 'JP1', 'OC1']

/** Followed players, ranked against each other: the list is the leaderboard. */
export function GamePage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [adding, setAdding] = useState(false)
  const [pendingDelete, setPendingDelete] = useState<Set<string>>(new Set())
  const players = useQuery({ queryKey: queryKeys.lolPlayers, queryFn: api.lolPlayers })
  const version = useDdragonVersion()

  const unfollow = (player: LolPlayer) => {
    setPendingDelete((s) => new Set(s).add(player.puuid))
    const restore = () =>
      setPendingDelete((s) => {
        const next = new Set(s)
        next.delete(player.puuid)
        return next
      })
    toast({
      message: t.lolUnfollowed(player.gameName),
      actionLabel: t.undo,
      durationMs: 5000,
      onAction: restore,
      onTimeout: () =>
        api
          .untrackLolPlayer(player.puuid)
          .then(() => queryClient.invalidateQueries({ queryKey: queryKeys.lolPlayers }))
          .catch((error) => toast({ message: errorMessage(error) }))
          .finally(restore),
    })
  }

  const header = (
    <header className="page-header">
      <h1 className="page-title">{t.gameTitle}</h1>
      <p className="page-subtitle">{t.gameSubtitle}</p>
    </header>
  )

  if (players.isPending) return <div className="page">{header}<WorkoutListSkeleton /></div>
  if (players.isError) {
    const noKey = players.error instanceof AppError && players.error.kind === 'riotNoKey'
    return (
      <div className="page">
        {header}
        {noKey ? (
          <MessageState icon={<ControllerIcon size={32} />} title={t.riotNoKeyTitle} body={t.riotNoKey} />
        ) : (
          <ErrorState error={players.error} onRetry={() => players.refetch()} />
        )}
      </div>
    )
  }

  const ranked = sortForLeaderboard(players.data.filter((p) => !pendingDelete.has(p.puuid)))

  return (
    <div className="page">
      {header}
      {ranked.length === 0 ? (
        <MessageState icon={<ControllerIcon size={32} />} title={t.lolEmptyTitle} body={t.lolEmptyBody} />
      ) : (
        <div className="lol-board">
          {ranked.map((player, index) => (
            <SwipeToDelete key={player.puuid} label={player.gameName} onDelete={() => unfollow(player)}>
              <Link to={`/game/${encodeURIComponent(player.puuid)}`} viewTransition className="lol-row" style={{ animationDelay: `${Math.min(index, 8) * 40}ms` }}>
                <span className={index < 3 ? `lol-place top-${index + 1}` : 'lol-place'}>{index + 1}</span>
                <LolAvatar version={version} iconId={player.profileIconId} name={player.gameName} />
                <span className="lol-row-main">
                  <span className="lol-name">
                    {player.gameName}
                    <small>#{player.tagLine}</small>
                  </span>
                  <RankBadge rank={player.rank} />
                </span>
                <span className="lol-row-side">
                  <FormBar wins={player.recentWins} games={player.recentGames} />
                  <small className="muted num">{player.recentGames > 0 ? t.winRate(Math.round(recentWinRate(player) * 100)) : '–'}</small>
                </span>
              </Link>
            </SwipeToDelete>
          ))}
        </div>
      )}
      <button className="fab" onClick={() => setAdding(true)} data-testid="fab-add-player">
        <span className="fab-plus">+</span> {t.addPlayer}
      </button>
      {adding && <AddPlayerSheet onClose={() => setAdding(false)} />}
    </div>
  )
}

/** Wins as a filled share of a thin bar: recent form at a glance without reading numbers. */
function FormBar({ wins, games }: { wins: number; games: number }) {
  return (
    <span className="form-bar" aria-hidden>
      <span style={{ width: games === 0 ? 0 : `${(wins / games) * 100}%` }} />
    </span>
  )
}

function AddPlayerSheet({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const [riotId, setRiotId] = useState('')
  const [platform, setPlatform] = useState('TR1')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async () => {
    if (!isValidRiotId(riotId)) {
      setError(t.errorRiotId)
      return
    }
    setSaving(true)
    try {
      await api.trackLolPlayer(riotId.trim(), platform)
      await queryClient.invalidateQueries({ queryKey: queryKeys.lolPlayers })
      onClose()
    } catch (e) {
      setError(e instanceof AppError && e.kind === 'notFound' ? t.riotIdNotFound(riotId.trim(), platform) : errorMessage(e))
      setSaving(false)
    }
  }

  return (
    <Sheet title={t.addPlayer} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          void submit()
        }}
      >
        <div className={error ? 'field invalid' : 'field'}>
          <label htmlFor="riot-id">{t.riotId}</label>
          <input id="riot-id" autoFocus autoCapitalize="off" autoComplete="off" placeholder="Behlul#TR1" value={riotId} maxLength={22} onChange={(e) => (setRiotId(e.target.value), setError(null))} />
          {error && <span className="field-error">{error}</span>}
        </div>
        <div className="field">
          <label htmlFor="riot-platform">{t.lolServer}</label>
          <select id="riot-platform" value={platform} onChange={(e) => setPlatform(e.target.value)}>
            {PLATFORMS.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </div>
        <p className="muted" style={{ margin: 0, fontSize: 13 }}>
          {t.riotIdHint}
        </p>
        <button type="submit" className="btn btn-primary btn-block" disabled={saving}>
          {saving ? <span className="spinner small" /> : t.follow}
        </button>
      </form>
    </Sheet>
  )
}
