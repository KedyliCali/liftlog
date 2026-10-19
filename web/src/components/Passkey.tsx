import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import { hasPasskeyHere, rememberPasskey, restoreWithPasskey, watchPasskeyCreation, watchPasskeyRequest } from '../api/session'
import type { Passkey } from '../api/types'
import { errorMessage } from '../lib/format'
import { locale, t } from '../lib/i18n'
import { CheckIcon, FaceIdIcon } from './Icons'
import { WakeUpHint } from './StateViews'
import { useCreatePasskey } from './useCreatePasskey'

/** "19 Eki": the day the newest passkey was made. */
function backupDate(passkeys: Passkey[]): string {
  const newest = passkeys.map((p) => p.createdAt).sort().at(-1)
  return newest ? new Date(newest).toLocaleDateString(locale, { day: 'numeric', month: 'short' }).replace('.', '') : ''
}

/** Profile card above the recovery code: back up with Face ID, or show that a passkey exists. */
export function BackupCard() {
  const passkeys = useQuery({ queryKey: queryKeys.passkeys, queryFn: api.passkeys })
  const { create, busy } = useCreatePasskey()

  // The server is the truth here; the local flag only lets the Workouts banner decide without a request
  useEffect(() => {
    if (passkeys.data) rememberPasskey(passkeys.data.length > 0)
  }, [passkeys.data])

  // Until the list arrives (or when it fails) the local flag is the best guess
  const backedUp = passkeys.data ? passkeys.data.length > 0 : hasPasskeyHere()

  // Ready before the tap, so Face ID opens inside the tap's user activation (see session.ts). Also while
  // backed up, for "Create again on this device".
  useEffect(watchPasskeyCreation, [])

  return (
    <section className={backedUp ? 'backup-card done' : 'backup-card'} data-testid="backup-card">
      <span className="backup-tag" aria-hidden>
        {t.backupTag}
      </span>
      <div className="recovery-card-head">
        <span className="recovery-icon" aria-hidden>
          <FaceIdIcon />
        </span>
        <h2>{t.backupTitle}</h2>
      </div>
      {backedUp ? (
        <div className="backup-done">
          <span className="backup-check" aria-hidden>
            <CheckIcon />
          </span>
          <div>
            <strong data-testid="backup-done">{t.backupDone(passkeys.data ? backupDate(passkeys.data) : '')}</strong>
            <p className="muted">{t.backupDoneHint}</p>
            {/* A new phone that came back with the code, or a passkey deleted from the keychain */}
            <button className="btn btn-text backup-again" onClick={create} disabled={busy} data-testid="backup-again">
              {busy ? <span className="spinner small" /> : t.backupCreateAgain}
            </button>
          </div>
        </div>
      ) : (
        <>
          <p className="muted recovery-text">{t.backupExplain}</p>
          <button className="btn btn-primary btn-block backup-button" onClick={create} disabled={busy} data-testid="backup-create">
            {busy ? (
              <span className="spinner small" />
            ) : (
              <>
                <FaceIdIcon /> {t.backupButton}
              </>
            )}
          </button>
        </>
      )}
    </section>
  )
}

/**
 * "Come back with Face ID" on the welcome and signed-out screens. No toast context exists before the app
 * renders, so problems show inline under the button. It prefetches its options on mount unless the screen
 * does that itself (the welcome screen waits for its server wake-up ping first).
 */
export function PasskeyRestoreButton({ onRestored, prefetch = true }: { onRestored: () => void; prefetch?: boolean }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => (prefetch ? watchPasskeyRequest() : undefined), [prefetch])

  const restore = async () => {
    setBusy(true)
    setError(null)
    try {
      const result = await restoreWithPasskey()
      if (result === 'done') onRestored()
      // Safari refused after a slow options request; fresh ones are ready now, so a second tap works
      if (result === 'tapAgain') setError(t.passkeyTapAgain)
    } catch (e) {
      if (e instanceof AppError) setError(e.kind === 'unauthorized' ? t.passkeyNotRecognized : errorMessage(e))
      else setError(t.passkeyRestoreFailed)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="stack passkey-restore">
      <button className="btn btn-tonal btn-block" onClick={restore} disabled={busy} data-testid="passkey-restore">
        {busy ? (
          <span className="spinner small" />
        ) : (
          <>
            <FaceIdIcon size={18} /> {t.passkeyRestore}
          </>
        )}
      </button>
      {error && (
        <span className="field-error" role="alert">
          {error}
        </span>
      )}
      {busy && <WakeUpHint />}
    </div>
  )
}
