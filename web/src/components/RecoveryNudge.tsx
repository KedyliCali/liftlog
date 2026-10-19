import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { currentRecoveryCode, hasPasskeyHere, NUDGE_KEY, watchPasskeyCreation } from '../api/session'
import { t } from '../lib/i18n'
import { todayIso } from '../lib/nutrition'
import { addDays, shouldNudge } from '../lib/recoveryCode'
import { passkeysSupported } from '../lib/webauthn'
import { FaceIdIcon } from './Icons'
import { useCreatePasskey } from './useCreatePasskey'

function readState(): string | null {
  try {
    return localStorage.getItem(NUDGE_KEY)
  } catch {
    return null
  }
}

function snooze(days: number) {
  try {
    localStorage.setItem(NUDGE_KEY, `snooze:${addDays(todayIso(), days)}`)
  } catch {
    // ignore
  }
}

/**
 * Asks once there is something to lose (at least one workout). With passkeys it offers the one-tap Face ID
 * backup until a passkey exists; otherwise it asks until the code was copied or shared.
 */
export function RecoveryNudge() {
  const [passkeys] = useState(passkeysSupported)
  const [visible, setVisible] = useState(
    () => currentRecoveryCode() !== null && !(passkeys && hasPasskeyHere()) && shouldNudge(readState(), todayIso()),
  )
  if (!visible) return null
  if (passkeys) return <PasskeyNudge onDone={() => setVisible(false)} />

  return <CodeNudge onDone={() => setVisible(false)} />
}

/** One tap plus Face ID backs up right here; "Later" waits a month, since the code card still exists. */
function PasskeyNudge({ onDone }: { onDone: () => void }) {
  const { create, busy } = useCreatePasskey(onDone)
  // Ready before the tap, so Face ID opens inside the tap's user activation (see session.ts)
  useEffect(watchPasskeyCreation, [])
  return (
    <div className="recovery-nudge passkey-nudge" role="note">
      <p>{t.passkeyNudgeText}</p>
      <div className="row">
        <button className="btn btn-primary" onClick={create} disabled={busy} data-testid="nudge-passkey">
          {busy ? (
            <span className="spinner small" />
          ) : (
            <>
              <FaceIdIcon size={18} /> {t.backupButton}
            </>
          )}
        </button>
        <button className="btn btn-text" onClick={() => (snooze(30), onDone())}>
          {t.nudgeLater}
        </button>
      </div>
    </div>
  )
}

/** Without passkey support: send the user to the code card; "Later" snoozes it for two weeks. */
function CodeNudge({ onDone }: { onDone: () => void }) {
  const navigate = useNavigate()
  const later = () => {
    snooze(14)
    onDone()
  }

  return (
    <div className="recovery-nudge" role="note">
      <p>{t.nudgeText}</p>
      <div className="row">
        <button className="btn btn-primary" onClick={() => navigate('/profile#recovery')}>
          {t.nudgeSave}
        </button>
        <button className="btn btn-text" onClick={later}>
          {t.nudgeLater}
        </button>
      </div>
    </div>
  )
}
