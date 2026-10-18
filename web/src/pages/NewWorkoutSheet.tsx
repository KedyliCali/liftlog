import { useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { Page, WorkoutSummary } from '../api/types'
import { Sheet } from '../components/Sheet'
import { useToast } from '../components/contexts'
import { errorMessage, formatShortDate, toIsoDate } from '../lib/format'
import { t } from '../lib/i18n'
import { pickTemplates } from '../lib/workoutLogic'

export function NewWorkoutSheet({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const toast = useToast()
  const [date, setDate] = useState(() => toIsoDate(new Date()))
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState<'creating' | 'copying' | null>(null)

  const cached = queryClient.getQueryData<InfiniteData<Page<WorkoutSummary>>>(queryKeys.workouts)
  const templates = pickTemplates(cached?.pages.flatMap((page) => page.content) ?? [])

  const create = async (template: WorkoutSummary | null) => {
    if (busy) return
    setBusy(template ? 'copying' : 'creating')
    try {
      const name = notes.trim() || template?.notes || null
      const workout = await api.createWorkout(date, name)
      if (template) {
        const source = await api.workout(template.id)
        // Sequential on purpose: the server numbers sets in arrival order
        for (const set of source.sets) {
          await api.addSet(workout.id, { exerciseId: set.exerciseId, reps: set.reps, weightKg: set.weightKg, rpe: null })
        }
      }
      await queryClient.invalidateQueries({ queryKey: queryKeys.workouts })
      onClose()
      navigate(`/workouts/${workout.id}`)
    } catch (error) {
      toast({ message: errorMessage(error) })
      setBusy(null)
    }
  }

  return (
    <Sheet title={t.newWorkout} onClose={onClose}>
      <div className="stack">
        <div className="field">
          <label htmlFor="workout-date">{t.date}</label>
          <input id="workout-date" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="workout-notes">{t.notesOptional}</label>
          <textarea id="workout-notes" value={notes} maxLength={2000} onChange={(e) => setNotes(e.target.value)} />
        </div>
        <button className="btn btn-primary btn-block" onClick={() => create(null)} disabled={busy !== null} data-testid="create-workout">
          {busy === 'creating' ? <span className="spinner small" /> : t.create}
        </button>

        {templates.length > 0 && (
          <>
            <h3 className="section-title" style={{ margin: '12px 0 0' }}>
              {t.repeatWorkout}
            </h3>
            <p className="muted" style={{ margin: 0, fontSize: 14 }}>
              {t.repeatWorkoutBody}
            </p>
            {busy === 'copying' ? (
              <div className="row" style={{ alignItems: 'center', justifyContent: 'center', gap: 10 }}>
                <span className="spinner small" style={{ flex: 'none' }} />
                <span className="muted" style={{ flex: 'none' }}>
                  {t.copying}
                </span>
              </div>
            ) : (
              templates.map((template) => (
                <button key={template.id} className="template-item" onClick={() => create(template)} disabled={busy !== null}>
                  <strong>{t.startFrom(template.notes ?? t.untitledWorkout)}</strong>
                  <span className="muted" style={{ fontSize: 13 }}>
                    {formatShortDate(template.workoutDate)} · {t.sets(template.setCount)} · {t.exercises(template.exerciseCount)}
                  </span>
                </button>
              ))
            )}
          </>
        )}
      </div>
    </Sheet>
  )
}
