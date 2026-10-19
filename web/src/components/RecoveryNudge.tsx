import { useState } from 'react'
import { useNavigate } from 'react-router'
import { currentRecoveryCode, NUDGE_KEY } from '../api/session'
import { t } from '../lib/i18n'
import { todayIso } from '../lib/nutrition'
import { addDays, shouldNudge } from '../lib/recoveryCode'

function readState(): string | null {
  try {
    return localStorage.getItem(NUDGE_KEY)
  } catch {
    return null
  }
}

/**
 * Asks once there is something to lose (at least one workout) and until the code was copied or shared.
 * "Later" snoozes it for two weeks on this device.
 */
export function RecoveryNudge() {
  const navigate = useNavigate()
  const [visible, setVisible] = useState(() => currentRecoveryCode() !== null && shouldNudge(readState(), todayIso()))
  if (!visible) return null

  const later = () => {
    try {
      localStorage.setItem(NUDGE_KEY, `snooze:${addDays(todayIso(), 14)}`)
    } catch {
      // ignore
    }
    setVisible(false)
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
