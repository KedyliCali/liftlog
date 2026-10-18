import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { SetInput, WorkoutDetail, WorkoutSet } from '../api/types'
import { Confetti } from '../components/Confetti'
import { useRestTimer } from '../components/contexts'
import { Sheet } from '../components/Sheet'
import { ErrorState, Loading, MessageState } from '../components/StateViews'
import { SwipeToDelete } from '../components/SwipeToDelete'
import { useToast } from '../components/contexts'
import { unlockAudio, vibrate } from '../lib/feedback'
import { errorMessage, formatKg, formatLoad, formatLongDate } from '../lib/format'
import { t } from '../lib/i18n'
import { groupByExercise, visibleVolume } from '../lib/workoutLogic'
import { SetEditorSheet, type SetEditorProps } from './SetEditorSheet'

type Editor = { mode: 'add' } | { mode: 'edit'; set: WorkoutSet }

export function WorkoutDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const restTimer = useRestTimer()

  const [editor, setEditor] = useState<Editor | null>(null)
  const [saving, setSaving] = useState(false)
  const [editingWorkout, setEditingWorkout] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [celebrate, setCelebrate] = useState(false)
  const [newSetId, setNewSetId] = useState<number | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Set<number>>(new Set())

  const query = useQuery({ queryKey: queryKeys.workout(id), queryFn: () => api.workout(id) })
  const profile = useQuery({ queryKey: queryKeys.profile, queryFn: api.profile, staleTime: 5 * 60_000 })

  const refreshAll = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.workout(id) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.workouts }),
      queryClient.invalidateQueries({ queryKey: queryKeys.stats }),
    ])
  }

  const saveSet = async (input: SetInput) => {
    if (!editor) return
    unlockAudio() // inside a tap, so iOS allows the rest timer's beep later
    setSaving(true)
    try {
      if (editor.mode === 'add') {
        const saved = await api.addSet(id, input)
        setNewSetId(saved.id)
        restTimer.start()
        if (saved.newPersonalRecord) {
          setCelebrate(true)
          vibrate([30, 40, 30, 40, 80])
          setTimeout(() => setCelebrate(false), 1800)
          toast({ message: `🎉 ${t.newPr(saved.exerciseName)}`, durationMs: 4500 })
        } else {
          vibrate(20)
        }
      } else {
        await api.updateSet(editor.set.id, input)
      }
      setEditor(null)
      await refreshAll()
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      setSaving(false)
    }
  }

  /** Hides the set at once and only deletes it on the server if the undo window passes. */
  const deleteSet = (set: WorkoutSet) => {
    setEditor(null)
    setPendingDelete((s) => new Set(s).add(set.id))
    const restore = () =>
      setPendingDelete((s) => {
        const next = new Set(s)
        next.delete(set.id)
        return next
      })
    toast({
      message: t.setDeleted,
      actionLabel: t.undo,
      durationMs: 5000,
      onAction: restore,
      onTimeout: () =>
        api
          .deleteSet(set.id)
          .then(refreshAll)
          .catch((error) => toast({ message: errorMessage(error) }))
          .finally(restore),
    })
  }

  const deleteWorkout = async () => {
    setMenuOpen(false)
    if (!window.confirm(t.deleteWorkoutConfirm)) return
    try {
      await api.deleteWorkout(id)
      await queryClient.invalidateQueries({ queryKey: queryKeys.workouts })
      navigate('/', { replace: true })
    } catch (error) {
      toast({ message: errorMessage(error) })
    }
  }

  const workout = query.data
  const visibleSets = workout?.sets.filter((s) => !pendingDelete.has(s.id)) ?? []

  return (
    <div className="page">
      <div className="top-bar">
        <button className="icon-btn" onClick={() => navigate(-1)} aria-label={t.back}>
          ←
        </button>
        <h1>{t.workout}</h1>
        {workout && (
          <div style={{ position: 'relative' }}>
            <button className="icon-btn" onClick={() => setMenuOpen((open) => !open)} aria-label="Menu">
              ⋮
            </button>
            {menuOpen && (
              <div className="menu">
                <button onClick={() => (setMenuOpen(false), setEditingWorkout(true))}>{t.editWorkout}</button>
                <button className="danger" onClick={deleteWorkout}>
                  {t.deleteWorkout}
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : (
        <>
          <Summary workout={workout!} sets={visibleSets} volume={visibleVolume(workout!, pendingDelete, profile.data?.bodyWeightKg ?? null)} />
          {visibleSets.length === 0 ? (
            <MessageState icon="🏋" title={t.detailEmptyTitle} body={t.detailEmptyBody} />
          ) : (
            <div style={{ paddingBottom: 96 }}>
              {groupByExercise(visibleSets).map((group, index) => (
                <section key={group.exerciseId} className="exercise-group" style={{ animationDelay: `${index * 40}ms` }}>
                  <div className="exercise-rail" aria-hidden />
                  <div className="exercise-group-body">
                    <h3 className="exercise-group-title">{group.exerciseName}</h3>
                    {group.sets.map((set, i) => (
                      <SwipeToDelete key={set.id} label={`${group.exerciseName} #${i + 1}`} onDelete={() => deleteSet(set)}>
                        <button className={set.id === newSetId ? 'set-row new' : 'set-row'} onClick={() => setEditor({ mode: 'edit', set })}>
                          <span className="set-number num">#{i + 1}</span>
                          <span className={set.bodyweight ? 'set-weight num bodyweight' : 'set-weight num'}>{formatLoad(set.weightKg, set.bodyweight)}</span>
                          <span className="set-reps num">× {set.reps}</span>
                          <span className="set-rpe">{set.rpe != null ? t.rpe(formatKg(set.rpe)) : ''}</span>
                        </button>
                      </SwipeToDelete>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          )}
          <button className="fab" onClick={() => setEditor({ mode: 'add' })} data-testid="fab-add-set">
            <span className="fab-plus">+</span> {t.addSet}
          </button>
        </>
      )}

      {editor && workout && (
        <SetEditorSheet {...editorProps(editor, visibleSets, saving)} onSave={saveSet} onDelete={editor.mode === 'edit' ? () => deleteSet(editor.set) : undefined} onClose={() => setEditor(null)} />
      )}
      {editingWorkout && workout && <EditWorkoutSheet workout={workout} onClose={() => setEditingWorkout(false)} onSaved={refreshAll} />}
      {celebrate && <Confetti />}
    </div>
  )
}

/** Adding pre-fills the previous set, since lifters usually repeat it; editing shows the set's values. */
function editorProps(editor: Editor, sets: WorkoutSet[], saving: boolean): Omit<SetEditorProps, 'onSave' | 'onClose' | 'onDelete'> {
  const source = editor.mode === 'edit' ? editor.set : sets.at(-1)
  return {
    isEditing: editor.mode === 'edit',
    isSaving: saving,
    initial: {
      exerciseId: source?.exerciseId ?? null,
      exerciseName: source?.exerciseName ?? null,
      bodyweight: source?.bodyweight ?? false,
      reps: source ? String(source.reps) : '',
      weight: source ? String(source.weightKg) : '',
      rpe: editor.mode === 'edit' && editor.set.rpe != null ? String(editor.set.rpe) : '',
    },
  }
}

/** Hero block: date and notes on a card, with the volume on a raised tile overlapping its corner. */
function Summary({ workout, sets, volume }: { workout: WorkoutDetail; sets: WorkoutSet[]; volume: number }) {
  const exerciseCount = new Set(sets.map((s) => s.exerciseId)).size
  return (
    <div className="summary">
      <div className="summary-card">
        <div className="summary-date">{formatLongDate(workout.workoutDate)}</div>
        <h2 className="summary-title">{workout.notes ?? t.untitledWorkout}</h2>
        <div className="summary-stats">
          <div>
            <div className="stat-value num">{sets.length}</div>
            <div className="stat-label">{t.labelSets}</div>
          </div>
          <div>
            <div className="stat-value num">{exerciseCount}</div>
            <div className="stat-label">{t.labelExercises}</div>
          </div>
        </div>
      </div>
      <div className="volume-badge">
        <small>{t.totalVolume}</small>
        <strong>{t.kg(formatKg(volume))}</strong>
      </div>
    </div>
  )
}

function EditWorkoutSheet({ workout, onClose, onSaved }: { workout: WorkoutDetail; onClose: () => void; onSaved: () => Promise<void> }) {
  const toast = useToast()
  const [date, setDate] = useState(workout.workoutDate)
  const [notes, setNotes] = useState(workout.notes ?? '')
  const [saving, setSaving] = useState(false)

  const save = async () => {
    setSaving(true)
    try {
      await api.updateWorkout(workout.id, date, notes.trim() || null)
      await onSaved()
      onClose()
    } catch (error) {
      toast({ message: errorMessage(error) })
      setSaving(false)
    }
  }

  return (
    <Sheet title={t.editWorkout} onClose={onClose}>
      <div className="stack">
        <div className="field">
          <label htmlFor="edit-date">{t.date}</label>
          <input id="edit-date" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="edit-notes">{t.notesOptional}</label>
          <textarea id="edit-notes" value={notes} maxLength={2000} onChange={(e) => setNotes(e.target.value)} />
        </div>
        <button className="btn btn-primary btn-block" onClick={save} disabled={saving}>
          {saving ? <span className="spinner small" /> : t.save}
        </button>
      </div>
    </Sheet>
  )
}
