import { useState } from 'react'
import type { DownloadedFile } from '../api/client'
import { api } from '../api/endpoints'
import { saveFile } from '../lib/download'
import { errorMessage } from '../lib/format'
import { todayIso } from '../lib/nutrition'
import { t } from '../lib/i18n'
import { useToast } from './contexts'
import { DownloadIcon } from './Icons'

type ExportKind = 'workouts' | 'nutrition' | 'all'

const EXPORTS: Record<ExportKind, { fetch: () => Promise<DownloadedFile>; fallbackName: string; format: string }> = {
  workouts: { fetch: api.exportWorkouts, fallbackName: 'liftlog-antrenman', format: 'CSV' },
  nutrition: { fetch: api.exportNutrition, fallbackName: 'liftlog-beslenme', format: 'CSV' },
  all: { fetch: api.exportAll, fallbackName: 'liftlog-veriler', format: 'ZIP' },
}

const titles: Record<ExportKind, () => string> = {
  workouts: () => t.exportWorkouts,
  nutrition: () => t.exportNutrition,
  all: () => t.exportAll,
}

/** Profile card: download workouts, food diary, or everything as a ZIP. */
export function ExportCard() {
  const toast = useToast()
  const [busy, setBusy] = useState<ExportKind | null>(null)
  // Safari only opens the share sheet right after a tap; a slow download needs a second, fresh one
  const [ready, setReady] = useState<{ blob: Blob; filename: string } | null>(null)

  const save = async (blob: Blob, filename: string) => {
    const outcome = await saveFile(blob, filename)
    setReady(outcome === 'needsTap' ? { blob, filename } : null)
    if (outcome === 'downloaded') toast({ message: t.exportStarted })
  }

  const download = async (kind: ExportKind) => {
    const { fetch, fallbackName, format } = EXPORTS[kind]
    setBusy(kind)
    setReady(null)
    try {
      const file = await fetch()
      await save(file.blob, file.filename ?? `${fallbackName}-${todayIso()}.${format.toLowerCase()}`)
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      setBusy(null)
    }
  }

  const saveReady = async () => {
    if (!ready) return
    try {
      await save(ready.blob, ready.filename)
    } catch (error) {
      toast({ message: errorMessage(error) })
    }
  }

  const tile = (kind: ExportKind, extraClass = '') => {
    const { format } = EXPORTS[kind]
    return (
      <button
        className={`export-tile ${extraClass}`.trim()}
        onClick={() => download(kind)}
        disabled={busy !== null}
        aria-label={t.exportLabel(titles[kind](), format)}
        aria-busy={busy === kind}
        data-testid={`export-${kind}`}
      >
        <span className="export-format" aria-hidden>
          {format}
        </span>
        {kind === 'all' && (
          <span className="export-tile-icon" aria-hidden>
            <DownloadIcon size={20} />
          </span>
        )}
        <span>
          <strong>{titles[kind]()}</strong>
          {kind === 'all' && <small style={{ display: 'block' }}>{t.exportAllHint}</small>}
        </span>
        {busy === kind && <span className="spinner small" />}
      </button>
    )
  }

  return (
    <section className="export-card" aria-labelledby="export-title">
      <span className="export-tag" aria-hidden>
        {t.exportTag}
      </span>
      <div className="recovery-card-head">
        <span className="recovery-icon" aria-hidden>
          <DownloadIcon size={18} />
        </span>
        <h2 id="export-title">{t.exportTitle}</h2>
      </div>
      <p className="muted export-text">{t.exportBody}</p>
      <div className="export-grid">
        {tile('workouts')}
        {tile('nutrition')}
        {tile('all', 'all')}
      </div>
      {ready && (
        <div className="export-ready" role="status">
          <span>{t.exportReady(ready.filename)}</span>
          <button className="btn btn-primary" onClick={saveReady} data-testid="export-save-ready">
            {t.exportSave}
          </button>
        </div>
      )}
    </section>
  )
}
