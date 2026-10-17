import { useCallback, useEffect, useRef, useState, type ReactNode, type CSSProperties } from 'react'
import { RestTimerContext } from './contexts'
import { beep, vibrate } from '../lib/feedback'
import { t } from '../lib/i18n'
import { formatClock } from '../lib/workoutLogic'

const DEFAULT_REST_SECONDS = 90
const STEP_SECONDS = 15
const PREFERRED_KEY = 'liftlog.restSeconds'

function preferredSeconds() {
  const saved = Number(localStorage.getItem(PREFERRED_KEY))
  return Number.isFinite(saved) && saved >= STEP_SECONDS ? saved : DEFAULT_REST_SECONDS
}

/**
 * Floating countdown started after each saved set. It keeps an absolute end time instead of
 * counting ticks, so it stays correct when the phone locks or the tab is throttled.
 */
export function RestTimerProvider({ children }: { children: ReactNode }) {
  const [endsAt, setEndsAt] = useState<number | null>(null)
  const [total, setTotal] = useState(preferredSeconds)
  const [now, setNow] = useState(() => Date.now())
  const [finished, setFinished] = useState(false)
  const finishTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const start = useCallback(() => {
    const seconds = preferredSeconds()
    setTotal(seconds)
    setFinished(false)
    setNow(Date.now())
    setEndsAt(Date.now() + seconds * 1000)
  }, [])

  const stop = useCallback(() => {
    setEndsAt(null)
    setFinished(false)
  }, [])

  const adjust = (delta: number) => {
    if (endsAt === null) return
    const remaining = Math.max(0, endsAt - Date.now())
    const next = Math.max(STEP_SECONDS * 1000, remaining + delta * 1000)
    setEndsAt(Date.now() + next)
    // Remember the user's preference for the next set
    const preferred = Math.max(STEP_SECONDS, total + delta)
    setTotal(preferred)
    localStorage.setItem(PREFERRED_KEY, String(preferred))
  }

  useEffect(() => {
    if (endsAt === null || finished) return
    const interval = setInterval(() => {
      const current = Date.now()
      setNow(current)
      if (current >= endsAt) {
        setFinished(true)
        vibrate([200, 100, 200, 100, 400])
        beep()
      }
    }, 250)
    return () => clearInterval(interval)
  }, [endsAt, finished])

  useEffect(() => {
    if (!finished) return
    finishTimer.current = setTimeout(stop, 4000)
    return () => {
      if (finishTimer.current) clearTimeout(finishTimer.current)
    }
  }, [finished, stop])

  const remainingSeconds = endsAt === null ? 0 : Math.max(0, Math.ceil((endsAt - now) / 1000))
  const progress = endsAt === null ? 0 : Math.max(0, 1 - remainingSeconds / total)

  return (
    <RestTimerContext.Provider value={{ start }}>
      {children}
      {endsAt !== null && (
        <div className={finished ? 'rest-timer done' : 'rest-timer'} role="timer" aria-live="polite">
          <div className="rest-ring" style={{ '--progress': Math.min(1, progress) } as CSSProperties}>
            <span className="num">{finished ? '✓' : formatClock(remainingSeconds)}</span>
          </div>
          <div className="rest-body">
            <span className="rest-label">{finished ? t.restDone : t.restTimer}</span>
            {!finished && (
              <div className="rest-actions">
                <button onClick={() => adjust(-STEP_SECONDS)} aria-label="-15s">
                  −15
                </button>
                <button onClick={() => adjust(STEP_SECONDS)} aria-label="+15s">
                  +15
                </button>
                <button onClick={stop}>{t.skip}</button>
              </div>
            )}
          </div>
        </div>
      )}
    </RestTimerContext.Provider>
  )
}

