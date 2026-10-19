import { useEffect, useState } from 'react'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { commitRestore, currentRecoveryCode, isThisDevicesCode, verifyRecoveryCode, watchPasskeyRequest } from '../api/session'
import type { AuthResponse } from '../api/types'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { parseRecoveryCode, type Credentials } from '../lib/recoveryCode'
import { passkeysSupported } from '../lib/webauthn'
import { useConfirm } from './contexts'
import { DumbbellIcon, KeyIcon } from './Icons'
import { PasskeyRestoreButton } from './Passkey'
import { MessageState, WakeUpHint } from './StateViews'

// Screens shown before the router exists (no router hooks here): first visit, restore, signed out.

/** A device with no stored account: start fresh, or bring an existing log back with its code. */
export function WelcomeScreen({ onStart, onRestored }: { onStart: () => void; onRestored: () => void }) {
  const [restoring, setRestoring] = useState(false)

  // Starts waking a sleeping server while the user reads or pastes; the answer does not matter. Once it is
  // awake, the Face ID options are fetched, so they are ready before the tap.
  useEffect(() => {
    let left = false
    let unwatch: (() => void) | undefined
    api
      .health()
      .catch(() => {})
      .finally(() => {
        if (!left && passkeysSupported()) unwatch = watchPasskeyRequest()
      })
    return () => {
      left = true
      unwatch?.()
    }
  }, [])

  if (restoring) {
    return (
      <div className="page recovery-screen">
        <button className="btn btn-text recovery-back" onClick={() => setRestoring(false)}>
          ‹ {t.back}
        </button>
        <h1 className="page-title">{t.restoreTitle}</h1>
        <RestoreForm needsConfirm={false} onRestored={onRestored} />
      </div>
    )
  }
  return (
    <div className="page recovery-screen">
      {/* CSS uppercases the title in the page language; lang="en" keeps the brand from becoming "LİFTLOG" in Turkish */}
      <MessageState icon={<DumbbellIcon size={32} />} title={<span lang="en">LiftLog</span>} body={t.welcomeBody}>
        <div className="stack recovery-actions">
          <button className="btn btn-primary btn-block" onClick={onStart} data-testid="welcome-start">
            {t.welcomeStart}
          </button>
          {passkeysSupported() && <PasskeyRestoreButton onRestored={onRestored} prefetch={false} />}
          <button className="btn btn-tonal btn-block" onClick={() => setRestoring(true)}>
            <KeyIcon size={18} /> {t.haveRecoveryCode}
          </button>
        </div>
        <p className="muted recovery-footnote">{t.welcomeFootnote}</p>
      </MessageState>
    </div>
  )
}

/** This device's code was replaced elsewhere, so its stored password no longer works. */
export function SignedOutScreen({ onRestored, onStartFresh }: { onRestored: () => void; onStartFresh: () => void }) {
  const confirm = useConfirm()
  const startFresh = async () => {
    const confirmed = await confirm({ title: t.startFreshTitle, message: t.startFreshBody, confirmLabel: t.startFreshAction, tone: 'neutral' })
    if (confirmed) onStartFresh()
  }
  return (
    <div className="page recovery-screen">
      <MessageState icon={<KeyIcon size={32} />} title={t.signedOutTitle} body={t.signedOutBody} />
      {passkeysSupported() && (
        <>
          <PasskeyRestoreButton onRestored={onRestored} />
          <p className="recovery-divider">{t.orWithCode}</p>
        </>
      )}
      {/* The stored credentials are known to be dead here, so pasting them must read as "not recognized" */}
      <RestoreForm needsConfirm={false} checkSameDevice={false} onRestored={onRestored} />
      <button className="btn btn-text recovery-fresh" onClick={() => void startFresh()}>
        {t.startFresh}
      </button>
      <p className="muted recovery-footnote">{t.signedOutHint}</p>
    </div>
  )
}

/**
 * Paste a code, check it with the server (a plain login that changes nothing), then switch this device to
 * it. When the device already has a log (needsConfirm), the target is named first and the current code is
 * offered for copying, so the log being replaced stays reachable.
 */
export function RestoreForm({
  needsConfirm,
  checkSameDevice = true,
  onRestored,
}: {
  needsConfirm: boolean
  checkSameDevice?: boolean
  onRestored: () => void
}) {
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [checking, setChecking] = useState(false)
  const [verified, setVerified] = useState<{ credentials: Credentials; auth: AuthResponse } | null>(null)

  const finish = (credentials: Credentials, auth: AuthResponse) => {
    commitRestore(credentials, auth)
    onRestored()
  }

  const submit = async () => {
    const credentials = parseRecoveryCode(text)
    if (!credentials) {
      setError(t.restoreInvalid)
      return
    }
    if (checkSameDevice && isThisDevicesCode(credentials)) {
      setError(t.restoreSameAccount)
      return
    }
    setChecking(true)
    try {
      const auth = await verifyRecoveryCode(credentials)
      if (needsConfirm) setVerified({ credentials, auth })
      else finish(credentials, auth)
    } catch (e) {
      setError(e instanceof AppError && e.kind === 'unauthorized' ? t.restoreNotRecognized : errorMessage(e))
    } finally {
      setChecking(false)
    }
  }

  if (verified) {
    const currentCode = currentRecoveryCode()
    return (
      <div className="stack restore-confirm">
        <strong className="restore-confirm-title">{t.restoreConfirmTitle(verified.auth.user.displayName ?? t.defaultName)}</strong>
        {currentCode && (
          <>
            <p className="muted" style={{ margin: 0 }}>
              {t.restoreConfirmBody}
            </p>
            <CopyCodeButton code={currentCode} />
          </>
        )}
        <div className="row">
          <button className="btn btn-tonal" onClick={() => setVerified(null)}>
            {t.cancel}
          </button>
          <button className="btn btn-primary" onClick={() => finish(verified.credentials, verified.auth)} data-testid="switch-log">
            {t.switchLog}
          </button>
        </div>
      </div>
    )
  }

  return (
    <form
      className="stack"
      onSubmit={(event) => {
        event.preventDefault()
        void submit()
      }}
    >
      <div className={error ? 'field invalid' : 'field'}>
        <textarea
          className="code-input"
          aria-label={t.restorePlaceholder}
          placeholder={t.restorePlaceholder}
          value={text}
          rows={3}
          autoCapitalize="off"
          autoCorrect="off"
          autoComplete="off"
          spellCheck={false}
          onChange={(e) => (setText(e.target.value), setError(null))}
        />
        {error && (
          <span className="field-error" role="alert">
            {error}
          </span>
        )}
      </div>
      <button type="submit" className="btn btn-primary btn-block" disabled={checking || text.trim() === ''} data-testid="restore-submit">
        {checking ? <span className="spinner small" /> : t.restoreButton}
      </button>
      {checking && <WakeUpHint />}
    </form>
  )
}

function CopyCodeButton({ code }: { code: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      className="btn btn-tonal"
      onClick={() =>
        navigator.clipboard
          ?.writeText(code)
          .then(() => setCopied(true))
          .catch(() => {})
      }
    >
      {copied ? t.codeCopied : t.copyCode}
    </button>
  )
}
