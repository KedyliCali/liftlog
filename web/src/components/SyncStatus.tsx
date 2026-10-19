import { t } from '../lib/i18n'
import { CloudOffIcon, CloudUploadIcon } from './Icons'
import { useOnline, useOutbox, useOutboxSync } from './useOutbox'

/**
 * Keeps the offline queue moving and says so at the top of every screen: "Offline" while there is
 * no connection, and how many sets still wait to be sent. Hidden when both are fine.
 */
export function SyncStatus() {
  useOutboxSync()
  const online = useOnline()
  const waiting = useOutbox().length
  if (online && waiting === 0) return null

  return (
    <div className={online ? 'sync-status' : 'sync-status offline'} role="status" data-testid="sync-status">
      {/* Icon tile sits on the card's edge, overlapping it, like the volume badge on the workout hero */}
      <span className="sync-status-tile" aria-hidden>
        {online ? <CloudUploadIcon size={18} /> : <CloudOffIcon size={18} />}
      </span>
      <span className="sync-status-card">
        {!online && <strong className="sync-status-title">{t.offline}</strong>}
        {waiting > 0 && <span className="sync-status-count">{t.setsWaiting(waiting)}</span>}
      </span>
    </div>
  )
}
