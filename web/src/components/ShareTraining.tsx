import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { useToast } from './contexts'

/** Turns sharing on or off; the profile cache takes the answer and the group comparison is fetched again. */
function useSetShareTraining() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const set = async (enabled: boolean) => {
    setBusy(true)
    try {
      queryClient.setQueryData(queryKeys.profile, await api.setShareTraining(enabled))
      await queryClient.invalidateQueries({ queryKey: queryKeys.groupTrainingAll })
      toast({ message: enabled ? t.sharingOn : t.sharingOff })
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      setBusy(false)
    }
  }
  return { set, busy }
}

/** An on/off switch: a real checkbox (keyboard and screen readers get role="switch") drawn as a track. */
function Switch({ checked, busy, label, onChange }: { checked: boolean; busy: boolean; label: string; onChange: (on: boolean) => void }) {
  return (
    <label className="share-switch">
      <span className="share-switch-label">{label}</span>
      <input type="checkbox" role="switch" checked={checked} disabled={busy} onChange={(e) => onChange(e.target.checked)} />
      <span className="share-switch-track" aria-hidden>
        <span className="share-switch-thumb" />
      </span>
    </label>
  )
}

/** Shown in the group's training view until the caller opts in: exactly what the group would see. */
export function ShareTrainingCard() {
  const { set, busy } = useSetShareTraining()
  return (
    <section className="share-card-wrap" aria-labelledby="share-card-title">
      <div className="share-card">
        <h2 id="share-card-title">{t.shareCardTitle}</h2>
        <p className="share-card-lead">{t.shareCardLead}</p>
        <ul className="share-card-items">
          {t.shareCardItems.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
        <p className="share-card-private">{t.shareCardNotShared}</p>
      </div>
      <div className="share-card-action">
        <Switch checked={false} busy={busy} label={t.shareSwitch} onChange={(on) => void set(on)} />
      </div>
    </section>
  )
}

/** The same choice on the Profile page, where it can also be turned off. */
export function ShareTrainingToggle({ enabled }: { enabled: boolean }) {
  const { set, busy } = useSetShareTraining()
  return (
    <div className="share-toggle">
      <div className="share-toggle-text">
        <strong>{t.shareProfileTitle}</strong>
        <small className="muted">{t.shareProfileHint}</small>
      </div>
      <Switch checked={enabled} busy={busy} label={t.shareSwitch} onChange={(on) => void set(on)} />
    </div>
  )
}
