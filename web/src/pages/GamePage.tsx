import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type Dispatch, type SetStateAction } from 'react'
import { useSearchParams } from 'react-router'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { LolPlayer } from '../api/types'
import { useConfirm, useToast } from '../components/contexts'
import { ControllerIcon } from '../components/Icons'
import { LolBoardRow } from '../components/LolBits'
import { RiotIdSheet } from '../components/RiotIdSheet'
import { useDdragonVersion } from '../components/useDdragonVersion'
import { WorkoutListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { SwipeToDelete } from '../components/SwipeToDelete'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { sortForLeaderboard } from '../lib/lol'
import { LolGroupView } from './LolGroupView'

/** 'sport' is the group compared on training: the same group, so it lives under the Group tab */
type View = 'group' | 'sport' | 'following'

const isView = (value: string | null): value is View => value === 'group' || value === 'sport' || value === 'following'

/**
 * The League tab: the group board everyone in your group shares (on League or on training), and your own
 * follow list. The chosen view lives in the URL (?view=), so coming back from a player page returns to it.
 */
export function GamePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const group = useQuery({ queryKey: queryKeys.lolGroup, queryFn: api.lolGroup })
  // Swiped-away players waiting out the undo toast. Kept above the view switch, which unmounts the follow list:
  // a fresh list would show them again while their unfollow is still scheduled
  const [pendingDelete, setPendingDelete] = useState<Set<string>>(new Set())
  const requested = searchParams.get('view')
  const chosen: View | null = isView(requested) ? requested : null
  // Without a choice: the group board when there is one, otherwise the follow list
  const view: View = chosen ?? (group.data ? 'group' : 'following')

  const header = (
    <header className="page-header">
      <h1 className="page-title">{t.gameTitle}</h1>
      <p className="page-subtitle">{t.gameSubtitle}</p>
    </header>
  )
  if (!chosen && group.isPending) return <div className="page">{header}<WorkoutListSkeleton /></div>

  // Keeps the group board's own choice (?board=table) for when the group view comes back
  const select = (next: View) =>
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current)
        params.set('view', next)
        return params
      },
      { replace: true },
    )
  const onGroup = view !== 'following'
  return (
    <div className="page">
      {header}
      <div className="segmented lol-views" role="tablist">
        {/* Tapping Group while the training comparison is open keeps it open */}
        <button role="tab" aria-selected={onGroup} className={onGroup ? 'selected' : ''} onClick={() => !onGroup && select('group')}>
          {t.lolViewGroup}
        </button>
        <button role="tab" aria-selected={!onGroup} className={!onGroup ? 'selected' : ''} onClick={() => select('following')}>
          {t.lolViewFollowing}
        </button>
      </div>
      {onGroup ? (
        <LolGroupView group={group} sport={view === 'sport'} onSportChange={(sport) => select(sport ? 'sport' : 'group')} />
      ) : (
        <FollowingView pendingDelete={pendingDelete} setPendingDelete={setPendingDelete} />
      )}
    </div>
  )
}

/** Followed players, ranked against each other: the list is the leaderboard. Only fetched while shown. */
function FollowingView({ pendingDelete, setPendingDelete }: {
  pendingDelete: Set<string>
  setPendingDelete: Dispatch<SetStateAction<Set<string>>>
}) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const confirm = useConfirm()
  const [adding, setAdding] = useState(false)
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
          .then(async () => {
            // The invalidate refetches only while the list is shown; with the group view open it returns at once,
            // and restore would let the cached list show the player again until the next fetch. So the cache
            // drops them first, after any older list request is cancelled so it cannot bring them back
            await queryClient.cancelQueries({ queryKey: queryKeys.lolPlayers })
            queryClient.setQueryData<LolPlayer[]>(queryKeys.lolPlayers, (list) => list?.filter((p) => p.puuid !== player.puuid))
            await queryClient.invalidateQueries({ queryKey: queryKeys.lolPlayers })
          })
          .catch((error) => toast({ message: errorMessage(error) }))
          .finally(restore),
    })
  }

  const follow = async (riotId: string, platform: string) => {
    await api.trackLolPlayer(riotId, platform)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lolPlayers })
  }

  let content
  if (players.isPending) {
    content = <WorkoutListSkeleton />
  } else if (players.isError) {
    const noKey = players.error instanceof AppError && players.error.kind === 'riotNoKey'
    content = noKey ? (
      <MessageState icon={<ControllerIcon size={32} />} title={t.riotNoKeyTitle} body={t.riotNoKey} />
    ) : (
      <ErrorState error={players.error} onRetry={() => players.refetch()} />
    )
  } else {
    const ranked = sortForLeaderboard(players.data.filter((p) => !pendingDelete.has(p.puuid)))
    content =
      ranked.length === 0 ? (
        <MessageState icon={<ControllerIcon size={32} />} title={t.lolEmptyTitle} body={t.lolEmptyBody} />
      ) : (
        <div className="lol-board">
          {ranked.map((player, index) => (
            <SwipeToDelete
              key={player.puuid}
              label={player.gameName}
              confirm={() =>
                confirm({ title: t.lolUnfollowTitle, item: `${player.gameName}#${player.tagLine}`, confirmLabel: t.lolUnfollowAction })
              }
              onDelete={() => unfollow(player)}
            >
              <LolBoardRow player={player} place={index + 1} version={version} style={{ animationDelay: `${Math.min(index, 8) * 40}ms` }} />
            </SwipeToDelete>
          ))}
        </div>
      )
  }

  return (
    <>
      {content}
      <button className="fab" onClick={() => setAdding(true)} data-testid="fab-add-player">
        <span className="fab-plus">+</span> {t.addPlayer}
      </button>
      {adding && <RiotIdSheet title={t.addPlayer} submitLabel={t.follow} onSubmit={follow} onClose={() => setAdding(false)} />}
    </>
  )
}
