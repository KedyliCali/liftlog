import { useState } from 'react'
import { AppError } from '../api/client'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { isValidRiotId } from '../lib/lol'
import { Sheet } from './Sheet'

const PLATFORMS = ['TR1', 'EUW1', 'EUN1', 'RU', 'ME1', 'NA1', 'BR1', 'LA1', 'LA2', 'KR', 'JP1', 'OC1']

/**
 * Asks for a Riot ID and server: following a player, or linking your own account to a group. The caller's
 * onSubmit does the request; this sheet shows its errors in place and closes itself only on success.
 */
export function RiotIdSheet({ title, submitLabel, initialRiotId = '', initialPlatform = 'TR1', onSubmit, onClose }: {
  title: string
  submitLabel: string
  initialRiotId?: string
  initialPlatform?: string
  onSubmit: (riotId: string, platform: string) => Promise<void>
  onClose: () => void
}) {
  const [riotId, setRiotId] = useState(initialRiotId)
  const [platform, setPlatform] = useState(initialPlatform)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async () => {
    if (!isValidRiotId(riotId)) {
      setError(t.errorRiotId)
      return
    }
    setSaving(true)
    try {
      await onSubmit(riotId.trim(), platform)
      onClose()
    } catch (e) {
      // A Riot ID on the wrong server is also a 404, so the message names the server to check
      setError(e instanceof AppError && e.kind === 'notFound' ? t.riotIdNotFound(riotId.trim(), platform) : errorMessage(e))
      setSaving(false)
    }
  }

  return (
    <Sheet title={title} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          void submit()
        }}
      >
        <div className={error ? 'field invalid' : 'field'}>
          <label htmlFor="riot-id">{t.riotId}</label>
          <input id="riot-id" autoFocus autoCapitalize="off" autoComplete="off" placeholder="Behlul#TR1" value={riotId} maxLength={22} onChange={(e) => (setRiotId(e.target.value), setError(null))} />
          {error && <span className="field-error">{error}</span>}
        </div>
        <div className="field">
          <label htmlFor="riot-platform">{t.lolServer}</label>
          <select id="riot-platform" value={platform} onChange={(e) => (setPlatform(e.target.value), setError(null))}>
            {PLATFORMS.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </div>
        <p className="muted" style={{ margin: 0, fontSize: 13 }}>
          {t.riotIdHint}
        </p>
        <button type="submit" className="btn btn-primary btn-block" disabled={saving}>
          {saving ? <span className="spinner small" /> : submitLabel}
        </button>
      </form>
    </Sheet>
  )
}
