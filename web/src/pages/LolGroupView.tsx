import { useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { LolGroup, LolGroupMember } from '../api/types'
import { useToast } from '../components/contexts'
import { ControllerIcon, SettingsIcon, ShareIcon } from '../components/Icons'
import { LolBoardRow } from '../components/LolBits'
import { RiotIdSheet } from '../components/RiotIdSheet'
import { Sheet } from '../components/Sheet'
import { WorkoutListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { useDdragonVersion } from '../components/useDdragonVersion'
import { errorMessage } from '../lib/format'
import { locale, t } from '../lib/i18n'
import { timeAgo } from '../lib/lol'
import { extractInviteCode, formatInviteCode, inviteMessage, oldestRefresh, splitGroupBoard } from '../lib/lolGroup'
import { isStandalone } from '../lib/platform'
import { initials } from '../lib/workoutLogic'

type OpenSheet = 'create' | 'join' | 'link' | 'manage' | null

const nameOf = (member: LolGroupMember) => member.displayName ?? t.memberFallback

/** The server sends UTC without a zone suffix. */
const joinDate = (iso: string) =>
  new Date(iso.endsWith('Z') ? iso : `${iso}Z`).toLocaleDateString(locale, { day: 'numeric', month: 'short' })

/** The group half of the League tab: start or join a group, or the board everyone in it shares. */
export function LolGroupView({ group }: { group: UseQueryResult<LolGroup | null> }) {
  const queryClient = useQueryClient()
  const [, setSearchParams] = useSearchParams()
  const [sheet, setSheet] = useState<OpenSheet>(null)

  if (group.isPending) return <WorkoutListSkeleton />
  if (group.isError) return <ErrorState error={group.error} onRetry={() => group.refetch()} />

  const data = group.data
  const me = data?.members.find((member) => member.you)
  const setGroup = (next: LolGroup | null) => queryClient.setQueryData(queryKeys.lolGroup, next)
  const close = () => setSheet(null)

  const link = async (riotId: string, platform: string) => {
    try {
      setGroup(await api.linkLolAccount(riotId, platform))
    } catch (error) {
      // Removed from the group while the sheet was open: refetching shows that
      if (error instanceof AppError && error.kind === 'notFound') void queryClient.invalidateQueries({ queryKey: queryKeys.lolGroup })
      throw error
    }
  }

  return (
    <>
      {data ? (
        <GroupBoard
          group={data}
          onLink={() => setSheet('link')}
          onManage={() => {
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
          onCreated={(created) => {
            setGroup(created)
            setSheet('link')
          }}
        />
      )}
      {sheet === 'join' && (
        <JoinGroupSheet
          onClose={close}
          onJoined={(joined) => {
            setGroup(joined)
            // The join answer is built from the cache; the GET also refreshes stale players
            void queryClient.invalidateQueries({ queryKey: queryKeys.lolGroup })
            setSheet('link')
          }}
        />
      )}
      {sheet === 'link' && (
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
          // Stay on the group view, which now offers to create or join a group
          onLeft={() => setSearchParams({ view: 'group' }, { replace: true })}
        />
      )}
    </>
  )
}

function GroupBoard({ group, onLink, onManage }: { group: LolGroup; onLink: () => void; onManage: () => void }) {
  const toast = useToast()
  const version = useDdragonVersion()
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
          <button className="invite-chip" onClick={() => void share()} aria-label={t.shareInvite}>
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

      <div className="lol-board group-board">
        {ranked.map((member, index) => (
          <LolBoardRow
            key={member.userId}
            player={member.player}
            place={index + 1}
            version={version}
            memberName={nameOf(member)}
            you={member.you}
            style={{ animationDelay: `${Math.min(index, 8) * 40}ms` }}
          />
        ))}
        {unlinked.length > 0 && <p className="muted lol-unlinked">{t.notLinkedYet(unlinked.map(nameOf).join(', '))}</p>}
      </div>
    </>
  )
}

function CreateGroupSheet({ onCreated, onClose }: { onCreated: (group: LolGroup) => void; onClose: () => void }) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async () => {
    setSaving(true)
    try {
      onCreated(await api.createLolGroup(name.trim()))
    } catch (e) {
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

function JoinGroupSheet({ onJoined, onClose }: { onJoined: (group: LolGroup) => void; onClose: () => void }) {
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const code = extractInviteCode(text)

  const submit = async () => {
    if (!code) return
    setSaving(true)
    try {
      onJoined(await api.joinLolGroup(code))
    } catch (e) {
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
  const setGroup = (next: LolGroup | null) => queryClient.setQueryData(queryKeys.lolGroup, next)

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
      setGroup(await api.rotateLolInviteCode())
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
      setGroup(null)
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
                setGroup(await api.renameLolGroup(name.trim()))
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
