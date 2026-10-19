import { useEffect, useRef, useState } from 'react'
import { AppError } from '../api/client'
import { currentRecoveryCode, hasPendingPassword, NUDGE_KEY, regenerateRecoveryCode } from '../api/session'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { shareText } from '../lib/recoveryCode'
import { useToast } from './contexts'
import { KeyIcon } from './Icons'
import { RestoreForm } from './Recovery'
import { Sheet } from './Sheet'

const reloadHome = () => window.location.replace('/')

function markSaved() {
  try {
    localStorage.setItem(NUDGE_KEY, 'saved')
  } catch {
    // Private mode: the reminder simply shows again
  }
}

/** Profile card: show, copy or share this device's code, make a new one, or switch to another log. */
export function RecoveryCodeCard() {
  const toast = useToast()
  const [code, setCode] = useState(currentRecoveryCode)
  const [revealed, setRevealed] = useState(false)
  const [saving, setSaving] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [pending, setPending] = useState(hasPendingPassword)
  const card = useRef<HTMLElement>(null)

  // Profile#recovery (from the reminder banner) scrolls straight to this card
  useEffect(() => {
    if (window.location.hash === '#recovery') card.current?.scrollIntoView({ block: 'start' })
  }, [])

  if (!code) return null

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      markSaved()
      toast({ message: t.codeCopied })
    } catch {
      setRevealed(true)
      toast({ message: t.copyFailed })
    }
  }

  const share = async () => {
    try {
      await navigator.share({ text: shareText(t.recoveryShareLabel, code) })
      markSaved()
    } catch (error) {
      // Closing the share sheet is not an error
      if ((error as Error).name !== 'AbortError') toast({ message: t.copyFailed })
    }
  }

  const regenerate = async () => {
    if (!window.confirm(t.newCodeConfirm)) return
    setSaving(true)
    try {
      setCode(await regenerateRecoveryCode())
      setRevealed(true)
      setPending(false)
      try {
        localStorage.removeItem(NUDGE_KEY)
      } catch {
        // ignore
      }
      toast({ message: t.newCodeDone, durationMs: 6000 })
    } catch (error) {
      setPending(hasPendingPassword())
      // A 401 means the new code did reach the server and revoked this token; the app signs in again with
      // it and announces the new code itself, so an error here would only contradict that
      if (!(error instanceof AppError && error.kind === 'unauthorized')) toast({ message: errorMessage(error) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="recovery-card" id="recovery" ref={card}>
      <div className="recovery-card-head">
        <span className="recovery-icon" aria-hidden>
          <KeyIcon />
        </span>
        <h2>{t.recoveryTitle}</h2>
      </div>
      <p className="muted recovery-text">{t.recoveryExplain}</p>
      <p className="recovery-warning">{t.recoveryWarning}</p>

      {pending ? (
        // The old code may already be dead (the server can have taken the new one without our hearing
        // back), so it is not offered for saving until New code settles which one is real
        <p className="recovery-pending">{t.pendingCode}</p>
      ) : (
        /* Code box with a lime edge and an offset plate behind it, so it reads as a physical card */
        <div className="recovery-code-wrap">
          <button
            className={revealed ? 'recovery-code revealed' : 'recovery-code'}
            onClick={() => setRevealed((r) => !r)}
            aria-label={revealed ? undefined : t.showCode}
            data-testid="recovery-code"
          >
            {revealed ? code : '•'.repeat(16)}
          </button>
        </div>
      )}

      <div className="recovery-buttons">
        {!pending && (
          <>
            <button className="btn btn-tonal" onClick={() => setRevealed((r) => !r)}>
              {revealed ? t.hideCode : t.showCode}
            </button>
            <button className="btn btn-tonal" onClick={copy}>
              {t.copyCode}
            </button>
            {'share' in navigator && (
              <button className="btn btn-tonal" onClick={share}>
                {t.shareCode}
              </button>
            )}
          </>
        )}
        <button className={pending ? 'btn btn-primary' : 'btn btn-text recovery-new'} onClick={regenerate} disabled={saving}>
          {saving ? <span className="spinner small" /> : t.newCode}
        </button>
      </div>

      <button className="btn btn-text recovery-switch" onClick={() => setRestoring(true)}>
        {t.useRecoveryCode}
      </button>
      {restoring && (
        <Sheet title={t.restoreTitle} onClose={() => setRestoring(false)}>
          <RestoreForm needsConfirm onRestored={reloadHome} />
        </Sheet>
      )}
    </section>
  )
}
