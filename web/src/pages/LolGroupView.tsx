import { useQueryClient, type QueryClient, type UseQueryResult } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { LolGroup, LolGroupMember } from '../api/types'
import { useToast } from '../components/contexts'
import { ControllerIcon, SettingsIcon, ShareIcon } from '../components/Icons'
import { LolBoardRow, RankBadge } from '../components/LolBits'
import { RiotIdSheet } from '../components/RiotIdSheet'
import { Sheet } from '../components/Sheet'
import { SortableTable, type SortableColumn } from '../components/SortableTable'
import { WorkoutListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { useDdragonVersion } from '../components/useDdragonVersion'
import { errorMessage, formatKg } from '../lib/format'
import { locale, t } from '../lib/i18n'
import { rankScore, timeAgo } from '../lib/lol'
import { roundTo, toPercent, winPercent } from '../lib/lolStats'
import { extractInviteCode, formatInviteCode, inviteMessage, oldestRefresh, splitGroupBoard, type LinkedMember } from '../lib/lolGroup'
import { isStandalone } from '../lib/platform'
import { initials } from '../lib/workoutLogic'

type OpenSheet = 'create' | 'join' | 'link' | 'manage' | null

const nameOf = (member: LolGroupMember) => member.displayName ?? t.memberFallback

/** The server sends UTC without a zone suffix. */
const joinDate = (iso: string) =>
  new Date(iso.endsWith('Z') ? iso : `${iso}Z`).toLocaleDateString(locale, { day: 'numeric', month: 'short' })

/**
 * Puts a mutation's answer into the group cache. A board GET that started before the mutation (for example the
 * refetch when the settings sheet opens) would otherwise finish afterwards and write the old state back, such as
 * a revoked invite code, so it is cancelled first.
 */
async function storeGroup(queryClient: QueryClient, next: LolGroup | null) {
  await queryClient.cancelQueries({ queryKey: queryKeys.lolGroup })
  queryClient.setQueryData(queryKeys.lolGroup, next)
}

const isAlreadyInGroup = (error: unknown): error is AppError =>
  error instanceof AppError && error.kind === 'groupRule' && error.detail === 'ALREADY_IN_GROUP'

/** The group half of the League tab: start or join a group, or the board everyone in it shares. */
export function LolGroupView({ group }: { group: UseQueryResult<LolGroup | null> }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [, setSearchParams] = useSearchParams()
  const [sheet, setSheet] = useState<OpenSheet>(null)

  if (group.isPending) return <WorkoutListSkeleton />
  // A failed refetch keeps the last answer (null too, which means "not in a group"), and replacing that board
  // would also close the open sheet and lose what was typed in it; only a first load that failed has nothing
  if (group.isError && group.data === undefined) return <ErrorState error={group.error} onRetry={() => group.refetch()} />

  const data = group.data
  const me = data?.members.find((member) => member.you)
  const setGroup = (next: LolGroup | null) => storeGroup(queryClient, next)
  const close = () => setSheet(null)
  // Without ?view= the default follows the data, and with no group left that would be the follow list;
  // pinning the group view shows the create or join state instead. Other choices in the URL (?board=) stay
  const stayOnGroupView = () =>
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.set('view', 'group')
        return next
      },
      { replace: true },
    )

  // The cache said "not in a group" but the server has one (say a create whose answer was lost on mobile data):
  // show that group, instead of an error that asks to leave a group the view does not show
  const showExistingGroup = (error: AppError) => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.lolGroup })
    toast({ message: errorMessage(error) })
    close()
  }

  const link = async (riotId: string, platform: string) => {
    try {
      await setGroup(await api.linkLolAccount(riotId, platform))
    } catch (error) {
      if (!(error instanceof AppError && error.kind === 'notFound')) throw error
      // The 404 is either a Riot ID on the wrong server or the owner removing us while the sheet was open;
      // only the group as it is now tells them apart. fetchQuery would join a board request that is already
      // running (say the one opening the settings started), whose answer can predate the removal, so that
      // one is cancelled and the check always comes from a request sent after the 404
      await queryClient.cancelQueries({ queryKey: queryKeys.lolGroup })
      const fresh = await queryClient.fetchQuery({ queryKey: queryKeys.lolGroup, queryFn: api.lolGroup, staleTime: 0 })
      if (fresh?.members.some((member) => member.you)) throw error
      // Returning normally closes the sheet; the fresh answer already took the board away
      stayOnGroupView()
      toast({ message: t.removedFromGroup })
    }
  }

  return (
    <>
      {data ? (
        <GroupBoard
          group={data}
          onLink={() => {
            stayOnGroupView()
            setSheet('link')
          }}
          onManage={() => {
            // Pinned before anything can take the group away (leaving, or a removal noticed while linking), so
            // the view never falls back to the follow list for a render and fetches it for nothing
            stayOnGroupView()
            setSheet('manage')
            // Members may have joined or left since the board loaded; the leave warning names the next owner
            void group.refetch()
          }}
        />
      ) : (
        <MessageState icon={<ControllerIcon size={32} />} title={t.groupEmptyTitle} body={t.groupEmptyBody}>
          <div className="group-start">
            <button className="btn btn-primary btn-block" onClick={() => setSheet('create')}>
              {t.createGroup}
            </button>
            <button className="btn btn-tonal btn-block" onClick={() => setSheet('join')}>
              {t.joinGroup}
            </button>
          </div>
        </MessageState>
      )}

      {sheet === 'create' && (
        <CreateGroupSheet
          onClose={close}
          onAlreadyInGroup={showExistingGroup}
          onCreated={async (created) => {
            await setGroup(created)
            setSheet('link')
          }}
        />
      )}
      {sheet === 'join' && (
        <JoinGroupSheet
          onClose={close}
          onAlreadyInGroup={showExistingGroup}
          onJoined={async (joined) => {
            await setGroup(joined)
            // The join answer is built from the cache; the GET also refreshes stale players
            void queryClient.invalidateQueries({ queryKey: queryKeys.lolGroup })
            setSheet('link')
          }}
        />
      )}
      {/* Linking only means something inside a group; a member removed meanwhile must not keep the sheet */}
      {sheet === 'link' && data && (
        <RiotIdSheet
          title={t.linkAccountTitle}
          submitLabel={t.linkButton}
          initialRiotId={me?.player ? `${me.player.gameName}#${me.player.tagLine}` : ''}
          initialPlatform={me?.player?.platform ?? 'TR1'}
          onSubmit={link}
          onClose={close}
        />
      )}
      {sheet === 'manage' && data && (
        <ManageGroupSheet
          group={data}
          onClose={close}
          onChangeAccount={() => setSheet('link')}
          onLeft={stayOnGroupView}
        />
      )}
    </>
  )
}

type BoardMode = 'board' | 'table'

function GroupBoard({ group, onLink, onManage }: { group: LolGroup; onLink: () => void; onManage: () => void }) {
  const toast = useToast()
  const version = useDdragonVersion()
  // In the URL, so coming back from a player page shows the same mode
  const [searchParams, setSearchParams] = useSearchParams()
  const mode: BoardMode = searchParams.get('board') === 'table' ? 'table' : 'board'
  const selectMode = (next: BoardMode) =>
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current)
        if (next === 'table') params.set('board', 'table')
        else params.delete('board')
        return params
      },
      { replace: true },
    )
  const me = group.members.find((member) => member.you)
  const { ranked, unlinked } = splitGroupBoard(group.members)
  const oldest = oldestRefresh(group.members.map((member) => member.player))
  const updated = oldest ? timeAgo(oldest) : null

  const share = async () => {
    const text = inviteMessage(group.name, group.inviteCode, window.location.origin)
    if (navigator.share) {
      try {
        await navigator.share({ text })
        return
      } catch (error) {
        // Closing the share sheet is not a failure; anything else falls back to copying
        if (error instanceof DOMException && error.name === 'AbortError') return
      }
    }
    try {
      await navigator.clipboard.writeText(text)
      toast({ message: t.inviteCopied })
    } catch {
      toast({ message: t.copyFailed })
    }
  }

  return (
    <>
      <div className="lol-group-wrap">
        <section className="lol-group-card">
          <div className="lol-group-head">
            <h2>{group.name}</h2>
            <span className="muted num">{t.membersCount(group.members.length, group.maxMembers)}</span>
            <button className="icon-btn" onClick={onManage} aria-label={t.manageGroup}>
              <SettingsIcon />
            </button>
          </div>
          {updated && <p className="muted lol-group-updated">{t.updatedAgo(updated.value, updated.unit)}</p>}
          {/* The label replaces the visible text for screen readers, so it must carry the code too */}
          <button className="invite-chip" onClick={() => void share()} aria-label={`${t.shareInvite}: ${formatInviteCode(group.inviteCode)}`}>
            <small>{t.inviteCodeLabel}</small>
            <span className="invite-code">{formatInviteCode(group.inviteCode)}</span>
            <ShareIcon />
          </button>
        </section>
      </div>

      {me && !me.player && (
        <div className="group-banner">
          <p>{t.linkAccountBanner}</p>
          <button className="btn btn-primary" onClick={onLink}>
            {t.linkAccount}
          </button>
        </div>
      )}
      {me && me.displayName == null && (
        <Link to="/profile" className="group-name-hint">
          {t.setNameHint}
        </Link>
      )}

      {ranked.length > 0 && (
        <div className="board-toggle" role="group" aria-label={t.boardViewLabel}>
          <button type="button" aria-pressed={mode === 'board'} className={mode === 'board' ? 'selected' : ''} onClick={() => selectMode('board')}>
            {t.boardViewBoard}
          </button>
          <button type="button" aria-pressed={mode === 'table'} className={mode === 'table' ? 'selected' : ''} onClick={() => selectMode('table')}>
            {t.boardViewTable}
          </button>
        </div>
      )}

      <div className="lol-board group-board">
        {mode === 'table' && ranked.length > 0 ? (
          <GroupTable members={ranked} />
        ) : (
          ranked.map((member, index) => (
            <LolBoardRow
              key={member.userId}
              player={member.player}
              place={index + 1}
              version={version}
              memberName={nameOf(member)}
              you={member.you}
              style={{ animationDelay: `${Math.min(index, 8) * 40}ms` }}
            />
          ))
        )}
        {unlinked.length > 0 && <p className="muted lol-unlinked">{t.notLinkedYet(unlinked.map(nameOf).join(', '))}</p>}
      </div>
    </>
  )
}

const dash = (value: number | null | undefined, format: (n: number) => string) => (value == null ? '–' : format(value))

/** The members side by side with their averages from the stored recent games; a header tap sorts. */
function GroupTable({ members }: { members: LinkedMember[] }) {
  const recentWin = (m: LinkedMember) => winPercent(m.player.recentWins, m.player.recentGames)
  const columns: SortableColumn<LinkedMember>[] = [
    {
      key: 'player',
      label: t.playerColumn,
      sortValue: (m) => m.player.gameName.toLocaleLowerCase(),
      firstDirection: 'asc',
      render: (m) => (
        <Link to={`/game/${encodeURIComponent(m.player.puuid)}`} viewTransition className="table-player">
          <span className="table-player-name">
            {m.player.gameName}
            {m.you && <span className="table-you">{t.youTag}</span>}
          </span>
          <small>{nameOf(m)}</small>
        </Link>
      ),
    },
    { key: 'rank', label: t.rankColumn, sortValue: (m) => rankScore(m.player.rank), render: (m) => <RankBadge rank={m.player.rank} /> },
    { key: 'winRate', label: t.winRateColumn, sortValue: recentWin, render: (m) => dash(recentWin(m), t.percent) },
    { key: 'kda', label: t.statKda, sortValue: (m) => m.player.summary?.kda, render: (m) => dash(m.player.summary?.kda, (n) => formatKg(roundTo(n, 2))) },
    {
      key: 'kp',
      label: t.kpColumn,
      sortValue: (m) => toPercent(m.player.summary?.killParticipation),
      render: (m) => dash(toPercent(m.player.summary?.killParticipation), t.percent),
    },
    { key: 'cs', label: t.statCsPerMin, sortValue: (m) => m.player.summary?.csPerMinute, render: (m) => dash(m.player.summary?.csPerMinute, (n) => formatKg(roundTo(n, 1))) },
    {
      key: 'damage',
      label: t.damagePerMinColumn,
      sortValue: (m) => m.player.summary?.damagePerMinute,
      render: (m) => dash(m.player.summary?.damagePerMinute, (n) => formatKg(Math.round(n))),
    },
    {
      key: 'vision',
      label: t.visionPerMinColumn,
      sortValue: (m) => m.player.summary?.visionPerMinute,
      render: (m) => dash(m.player.summary?.visionPerMinute, (n) => formatKg(roundTo(n, 2))),
    },
  ]
  return (
    <SortableTable
      label={t.boardViewTable}
      rows={members}
      columns={columns}
      rowKey={(m) => String(m.userId)}
      initialSort={{ key: 'rank', direction: 'desc' }}
      rowClassName={(m) => (m.you ? 'you' : undefined)}
    />
  )
}

function CreateGroupSheet({ onCreated, onAlreadyInGroup, onClose }: {
  onCreated: (group: LolGroup) => Promise<void>
  onAlreadyInGroup: (error: AppError) => void
  onClose: () => void
}) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async () => {
    setSaving(true)
    try {
      await onCreated(await api.createLolGroup(name.trim()))
    } catch (e) {
      if (isAlreadyInGroup(e)) return onAlreadyInGroup(e)
      setError(errorMessage(e))
      setSaving(false)
    }
  }

  return (
    <Sheet title={t.createGroup} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          if (name.trim()) void submit()
        }}
      >
        <div className={error ? 'field invalid' : 'field'}>
          <label htmlFor="group-name">{t.groupName}</label>
          <input id="group-name" autoFocus autoComplete="off" maxLength={32} placeholder="Duo Queue" value={name} onChange={(e) => (setName(e.target.value), setError(null))} />
          {error && <span className="field-error">{error}</span>}
        </div>
        <p className="muted sheet-note">{t.groupPrivacyHint}</p>
        <button type="submit" className="btn btn-primary btn-block" disabled={saving || !name.trim()}>
          {saving ? <span className="spinner small" /> : t.create}
        </button>
      </form>
    </Sheet>
  )
}

function JoinGroupSheet({ onJoined, onAlreadyInGroup, onClose }: {
  onJoined: (group: LolGroup) => Promise<void>
  onAlreadyInGroup: (error: AppError) => void
  onClose: () => void
}) {
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const code = extractInviteCode(text)

  const submit = async () => {
    if (!code) return
    setSaving(true)
    try {
      await onJoined(await api.joinLolGroup(code))
    } catch (e) {
      if (isAlreadyInGroup(e)) return onAlreadyInGroup(e)
      setError(e instanceof AppError && e.kind === 'notFound' ? t.inviteCodeNotFound : errorMessage(e))
      setSaving(false)
    }
  }

  return (
    <Sheet title={t.joinGroup} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          void submit()
        }}
      >
        <div className={error ? 'field invalid' : 'field'}>
          <label htmlFor="invite-code">{t.inviteCodeLabel}</label>
          {/* A textarea, because inputs drop the line breaks of a pasted message and glue the code to the link */}
          <textarea
            id="invite-code"
            className="invite-input"
            rows={2}
            autoFocus
            autoCapitalize="characters"
            autoCorrect="off"
            autoComplete="off"
            spellCheck={false}
            maxLength={400}
            placeholder="ABCDE-FGHJK"
            value={text}
            onChange={(e) => (setText(e.target.value), setError(null))}
          />
          {error && <span className="field-error">{error}</span>}
        </div>
        {code ? (
          <p className="join-preview">{t.joinCodePreview(formatInviteCode(code))}</p>
        ) : (
          <p className="muted sheet-note">{text.trim() ? t.inviteCodeLookalikes : t.joinCodeHint}</p>
        )}
        <p className="muted sheet-note">{t.groupPrivacyHint}</p>
        {!isStandalone() && <p className="muted sheet-note">{t.joinInBrowserHint}</p>}
        <button type="submit" className="btn btn-primary btn-block" disabled={saving || !code}>
          {saving ? <span className="spinner small" /> : t.join}
        </button>
      </form>
    </Sheet>
  )
}

function ManageGroupSheet({ group, onClose, onChangeAccount, onLeft }: {
  group: LolGroup
  onClose: () => void
  onChangeAccount: () => void
  onLeft: () => void
}) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [name, setName] = useState(group.name)
  const [busy, setBusy] = useState(false)
  const me = group.members.find((member) => member.you)
  const isOwner = me?.owner === true
  const setGroup = (next: LolGroup | null) => storeGroup(queryClient, next)

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    try {
      await action()
    } catch (error) {
      toast({ message: errorMessage(error) })
      // Ownership changed or we were removed since this sheet was drawn: show the group as it is now
      if (error instanceof AppError && (error.kind === 'forbidden' || error.kind === 'notFound')) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.lolGroup })
      }
    } finally {
      setBusy(false)
    }
  }

  const rotate = () => {
    if (!window.confirm(t.newInviteCodeConfirm)) return
    void run(async () => {
      await setGroup(await api.rotateLolInviteCode())
      toast({ message: t.inviteCodeChanged })
    })
  }

  const remove = (member: LolGroupMember) => {
    if (!window.confirm(t.removeMemberConfirm(nameOf(member)))) return
    void run(async () => {
      await api.removeLolGroupMember(member.userId)
      await queryClient.invalidateQueries({ queryKey: queryKeys.lolGroup })
      toast({ message: t.memberRemoved })
    })
  }

  const leave = () => {
    const others = group.members.filter((member) => !member.you)
    // Members come in join order, so the first other member is the next owner
    const consequence = others.length === 0 ? t.leaveConfirmLast : isOwner ? t.leaveConfirmOwner(nameOf(others[0])) : t.leaveConfirmMember
    if (!window.confirm(`${t.leaveConfirmTitle}\n${consequence}`)) return
    void run(async () => {
      await api.leaveLolGroup()
      await setGroup(null)
      onLeft()
      onClose()
      toast({ message: t.leftGroup })
    })
  }

  return (
    <Sheet title={t.manageGroup} onClose={onClose}>
      <div className="stack">
        {isOwner && (
          <form
            className="owner-tools"
            onSubmit={(event) => {
              event.preventDefault()
              void run(async () => {
                await setGroup(await api.renameLolGroup(name.trim()))
              })
            }}
          >
            <div className="field">
              <label htmlFor="group-rename">{t.groupName}</label>
              <input id="group-rename" autoComplete="off" maxLength={32} value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="owner-buttons">
              <button type="submit" className="btn btn-tonal" disabled={busy || !name.trim() || name.trim() === group.name}>
                {t.save}
              </button>
              <button type="button" className="btn btn-text" onClick={rotate} disabled={busy}>
                {t.newInviteCode}
              </button>
            </div>
          </form>
        )}

        <button className="btn btn-tonal btn-block" onClick={onChangeAccount}>
          {me?.player ? t.changeAccount : t.linkAccount}
        </button>

        <ul className="member-list">
          {group.members.map((member) => (
            <li key={member.userId} className={member.you ? 'member-item you' : 'member-item'}>
              <span className="member-initials" aria-hidden>
                {initials(nameOf(member))}
              </span>
              <span className="member-main">
                <span className="member-name">
                  {nameOf(member)}
                  {member.owner && <span className="member-tag">{t.ownerTag}</span>}
                  {member.you && <span className="member-tag you">{t.youTag}</span>}
                </span>
                <small className="muted">
                  {member.player ? `${member.player.gameName}#${member.player.tagLine}` : t.notLinked} · {t.joinedOn(joinDate(member.joinedAt))}
                </small>
              </span>
              {isOwner && !member.you && (
                <button className="btn btn-text member-remove" onClick={() => remove(member)} disabled={busy}>
                  {t.removeMember}
                </button>
              )}
            </li>
          ))}
        </ul>

        <button className="btn btn-danger btn-block" onClick={leave} disabled={busy}>
          {t.leaveGroup}
        </button>
      </div>
    </Sheet>
  )
}
