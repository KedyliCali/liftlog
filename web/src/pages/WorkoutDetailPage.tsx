import { useQuery, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { Page, SetInput, WorkoutDetail, WorkoutSet, WorkoutSummary } from '../api/types'
import { Confetti } from '../components/Confetti'
import { CountUp } from '../components/CountUp'
import { useRestTimer } from '../components/contexts'
import { Sheet } from '../components/Sheet'
import { DetailSkeleton, SetListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { SwipeToDelete } from '../components/SwipeToDelete'
import { useToast } from '../components/contexts'
import { useFlushOutbox, useOutbox } from '../components/useOutbox'
import { unlockAudio, vibrate } from '../lib/feedback'
import { errorMessage, formatKg, formatLoad, formatLongDate } from '../lib/format'
import { t } from '../lib/i18n'
import { musclesOfSets } from '../lib/muscles'
import { RecapMuscles } from '../components/MuscleMapCard'
import { ROUTINE_NAME_MAX } from '../lib/routineLogic'
import { hasWaitingFor, maxSetOrder, outbox, overlaySets } from '../lib/outbox'
import { effectiveLoad, groupByExercise, visibleVolume, type ExerciseGroup } from '../lib/workoutLogic'
import { SetEditorSheet, type SetEditorProps } from './SetEditorSheet'
import { WorkoutMode } from './WorkoutMode'
import { WorkoutRecapSheet } from './WorkoutRecapSheet'
import { CheckIcon, CloudUploadIcon, DumbbellIcon } from '../components/Icons'

type Editor = { mode: 'add' } | { mode: 'edit'; set: WorkoutSet }

/** A new set either reached the server or is waiting on the phone under a negative local id. */
type AddResult = { saved: WorkoutSet } | { queuedId: number }

const isNetworkError = (error: unknown) => error instanceof AppError && error.kind === 'network'

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
  const [savingRoutine, setSavingRoutine] = useState(false)
  const [celebrate, setCelebrate] = useState(false)
  const [finishing, setFinishing] = useState(false)
  const [newSetId, setNewSetId] = useState<number | null>(null)
  // The API flags a record only in the add response, so this page remembers which sets earned one
  const [recordSetIds, setRecordSetIds] = useState<Set<number>>(new Set())
  const [pendingDelete, setPendingDelete] = useState<Set<number>>(new Set())

  const query = useQuery({ queryKey: queryKeys.workout(id), queryFn: () => api.workout(id) })
  const profile = useQuery({ queryKey: queryKeys.profile, queryFn: api.profile, staleTime: 5 * 60_000 })
  // Sets do not carry the muscle group; the exercise catalog (shared with the picker) does
  const exercises = useQuery({ queryKey: queryKeys.exercises, queryFn: api.exercises, staleTime: 5 * 60_000 })
  const muscleOf = (exerciseId: number) => exercises.data?.find((e) => e.id === exerciseId)?.muscleGroup ?? null
  // Workout mode: only a workout started from a routine has a plan (older servers never send routineId)
  const routineId = query.data?.routineId ?? null
  const plan = useQuery({ queryKey: queryKeys.workoutPlan(id), queryFn: () => api.workoutPlan(id), enabled: routineId != null })
  const queue = useOutbox()
  const flushOutbox = useFlushOutbox()

  const refreshAll = async () => {
    await Promise.all([
      // Also refetches the plan, whose key sits under the workout's
      queryClient.invalidateQueries({ queryKey: queryKeys.workout(id) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.workouts }),
      queryClient.invalidateQueries({ queryKey: queryKeys.stats }),
    ])
  }

  /** Name and bodyweight flag for a waiting row; the request body only carries the exercise id. */
  const describeExercise = (exerciseId: number) => {
    const known =
      exercises.data?.find((e) => e.id === exerciseId) ??
      plan.data?.find((p) => p.exerciseId === exerciseId) ??
      query.data?.sets.find((s) => s.exerciseId === exerciseId)
    return { exerciseName: known && 'exerciseName' in known ? known.exerciseName : (known?.name ?? ''), bodyweight: known?.bodyweight ?? false }
  }

  /**
   * Posts a set, or keeps it on the phone when there is no connection. While sets of this workout
   * are already waiting, a new one joins the queue instead of overtaking them (set order matters).
   */
  const addOrQueue = async (input: SetInput): Promise<AddResult> => {
    const display = describeExercise(input.exerciseId)
    if (hasWaitingFor(outbox.items(), id)) {
      const queuedId = outbox.addCreate(id, input, display)
      void flushOutbox()
      return { queuedId }
    }
    // Where the server would put it, in case the request arrived but its answer was lost
    const expectedSetOrder = maxSetOrder(queryClient.getQueryData<WorkoutDetail>(queryKeys.workout(id))?.sets ?? []) + 1
    try {
      return { saved: await api.addSet(id, input) }
    } catch (error) {
      if (!isNetworkError(error)) throw error
      // With the phone offline the request never left; otherwise it may have arrived, so the
      // flush checks the server before sending it again
      return { queuedId: outbox.addCreate(id, input, display, navigator.onLine ? { expectedSetOrder } : undefined) }
    }
  }

  /** Edits and deletes follow the same rule: a waiting set, or a workout with waiting changes, goes through the queue. */
  const changeOrQueue = async (setId: number, input: SetInput | null) => {
    const enqueue = () =>
      input ? outbox.addUpdate(id, setId, input, describeExercise(input.exerciseId)) : outbox.addDelete(id, setId)
    if (setId < 0 || hasWaitingFor(outbox.items(), id)) {
      enqueue()
      void flushOutbox()
      return
    }
    try {
      await (input ? api.updateSet(setId, input) : api.deleteSet(setId))
    } catch (error) {
      if (!isNetworkError(error)) throw error
      enqueue()
    }
  }

  const saveSet = async (input: SetInput, count: number) => {
    if (!editor) return
    unlockAudio() // inside a tap, so iOS allows the rest timer's beep later
    setSaving(true)
    let reachedServer = true
    try {
      if (editor.mode === 'add') {
        // One request per set, in order, so set numbers and the PR check match adding them by hand.
        // If one fails, the sets already saved stay and the list is refreshed in `finally`.
        const results: AddResult[] = []
        for (let i = 0; i < count; i++) results.push(await addOrQueue(input))
        reachedServer = afterSetsAdded(results)
      } else {
        await changeOrQueue(editor.set.id, input)
      }
      setEditor(null)
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      // Offline a refetch would only fail (after retries); the overlay already shows the change
      if (reachedServer) await refreshAll()
      setSaving(false)
    }
  }

  /**
   * Highlights the newest set, starts the rest timer and celebrates any personal record.
   * Returns false when every set is waiting on the phone, so there is nothing to refetch.
   */
  const afterSetsAdded = (results: AddResult[]): boolean => {
    const saved = results.flatMap((r) => ('saved' in r ? [r.saved] : []))
    const last = results.at(-1)!
    setNewSetId('saved' in last ? last.saved.id : last.queuedId)
    restTimer.start()
    const queued = results.length - saved.length
    // Explained once, when the queue starts; after that the banner keeps the count
    if (queued > 0 && outbox.items().length === queued) toast({ message: t.setSavedOffline, durationMs: 4500 })
    const records = saved.filter((s) => s.newPersonalRecord)
    if (records.length > 0) {
      setRecordSetIds((ids) => new Set([...ids, ...records.map((s) => s.id)]))
      setCelebrate(true)
      vibrate([30, 40, 30, 40, 80])
      setTimeout(() => setCelebrate(false), 1800)
      toast({ message: t.newPr(records[0].exerciseName), durationMs: 4500 })
    } else {
      vibrate(20)
    }
    return saved.length > 0
  }

  /** Workout mode's ✓: the planned row is logged exactly as shown, in one request (or queued offline). */
  const logPlannedSet = async (input: SetInput) => {
    unlockAudio()
    let reachedServer = true
    try {
      reachedServer = afterSetsAdded([await addOrQueue(input)])
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      if (reachedServer) await refreshAll()
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
        changeOrQueue(set.id, null)
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
  // The list already knows date, name and totals, so the hero card can render (and be morphed into) at once
  const preview = query.isPending ? cachedSummary(queryClient.getQueryData(queryKeys.workouts), id) : undefined
  // The server's sets with the changes still waiting on this phone drawn on top
  const overlaid = overlaySets(workout?.sets ?? [], queue, id)
  const visibleSets = overlaid.sets.filter((s) => !pendingDelete.has(s.id))
  const bodyWeightKg = profile.data?.bodyWeightKg ?? null
  const volume = workout
    ? visibleVolume(workout, new Set([...pendingDelete, ...overlaid.replacedServerIds]), bodyWeightKg) +
      visibleSets.filter((s) => overlaid.pendingIds.has(s.id)).reduce((sum, s) => sum + s.reps * effectiveLoad(s, bodyWeightKg), 0)
    : 0

  const renderSet = (set: WorkoutSet, i: number, exerciseName: string) => {
    const waiting = overlaid.pendingIds.has(set.id)
    return (
      <SwipeToDelete key={set.id} label={`${exerciseName} #${i + 1}`} onDelete={() => deleteSet(set)}>
        <button
          className={['set-row', set.id === newSetId && 'new', waiting && 'pending'].filter(Boolean).join(' ')}
          onClick={() => setEditor({ mode: 'edit', set })}
          data-testid={waiting ? 'pending-set' : 'saved-set'}
        >
          <span className="set-number num">
            #{i + 1}
            {recordSetIds.has(set.id) && <span className="pr-badge">{t.prBadge}</span>}
          </span>
          <span className={set.bodyweight ? 'set-weight num bodyweight' : 'set-weight num'}>{formatLoad(set.weightKg, set.bodyweight)}</span>
          <span className="set-reps num">× {set.reps}</span>
          <span className="set-rpe">{set.rpe != null ? t.rpe(formatKg(set.rpe)) : ''}</span>
          {waiting ? (
            <span className="pending-badge">
              <CloudUploadIcon size={13} />
              {t.waitingBadge}
            </span>
          ) : (
            set.id === newSetId && <SavedCheck />
          )}
        </button>
      </SwipeToDelete>
    )
  }

  const renderGroup = (group: ExerciseGroup, index: number) => (
    <section
      key={group.exerciseId}
      className="exercise-group"
      data-muscle={muscleOf(group.exerciseId) ?? undefined}
      style={{ animationDelay: `${index * 40}ms` }}
    >
      <div className="exercise-rail" aria-hidden />
      <div className="exercise-group-body">
        <h3 className="exercise-group-title">
          {group.exerciseName}
          {muscleOf(group.exerciseId) && <span className="muscle-tag">{t.muscleGroups[muscleOf(group.exerciseId)!] ?? muscleOf(group.exerciseId)}</span>}
        </h3>
        {group.sets.map((set, i) => renderSet(set, i, group.exerciseName))}
      </div>
    </section>
  )

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
                <button onClick={() => (setMenuOpen(false), setFinishing(true))}>{t.finishWorkout}</button>
                <button onClick={() => (setMenuOpen(false), setEditingWorkout(true))}>{t.editWorkout}</button>
                {visibleSets.length > 0 && <button onClick={() => (setMenuOpen(false), setSavingRoutine(true))}>{t.saveAsRoutine}</button>}
                <button className="danger" onClick={deleteWorkout}>
                  {t.deleteWorkout}
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {query.isPending ? (
        preview ? (
          <>
            <Summary date={preview.workoutDate} notes={preview.notes} setCount={preview.setCount} exerciseCount={preview.exerciseCount} volume={preview.totalVolumeKg} />
            <SetListSkeleton />
          </>
        ) : (
          <DetailSkeleton />
        )
      ) : !workout ? (
        // A failed refetch keeps the loaded workout on screen (offline in the gym); only a first load shows the error
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : (
        <>
          <Summary
            date={workout.workoutDate}
            notes={workout.notes}
            setCount={visibleSets.length}
            exerciseCount={new Set(visibleSets.map((s) => s.exerciseId)).size}
            volume={volume}
            fromRoutine={routineId != null}
          />
          {plan.data && plan.data.length > 0 ? (
            <WorkoutMode plan={plan.data} sets={visibleSets} muscleOf={muscleOf} renderSet={renderSet} renderExtra={renderGroup} onLog={logPlannedSet} />
          ) : visibleSets.length === 0 ? (
            <MessageState icon={<DumbbellIcon size={32} />} title={t.detailEmptyTitle} body={t.detailEmptyBody} />
          ) : (
            <div className="set-groups">{groupByExercise(visibleSets).map(renderGroup)}</div>
          )}
          {/* Which muscles this workout hit so far, from each exercise's muscles; no extra request */}
          {visibleSets.length > 0 && <RecapMuscles muscles={musclesOfSets(visibleSets, exercises.data ?? [])} />}
          {visibleSets.length > 0 && (
            <button className="finish-btn" onClick={() => setFinishing(true)} data-testid="finish-workout">
              <span className="finish-btn-front">
                <CheckIcon size={20} />
                {t.finishWorkout}
              </span>
            </button>
          )}
          <button className="fab" onClick={() => setEditor({ mode: 'add' })} data-testid="fab-add-set">
            <span className="fab-plus">+</span> {t.addSet}
          </button>
        </>
      )}

      {editor && workout && (
        <SetEditorSheet {...editorProps(editor, visibleSets, saving)} onSave={saveSet} onDelete={editor.mode === 'edit' ? () => deleteSet(editor.set) : undefined} onClose={() => setEditor(null)} />
      )}
      {savingRoutine && workout && <SaveAsRoutineSheet workout={workout} onClose={() => setSavingRoutine(false)} />}
      {editingWorkout && workout && <EditWorkoutSheet workout={workout} onClose={() => setEditingWorkout(false)} onSaved={refreshAll} />}
      {finishing && <WorkoutRecapSheet workoutId={id} onClose={() => setFinishing(false)} onDone={() => navigate('/')} />}
      {celebrate && <Confetti />}
    </div>
  )
}

/** Stroke draws itself in, then fades: confirms the save without another toast. */
function SavedCheck() {
  return (
    <svg className="saved-check" width="22" height="22" viewBox="0 0 24 24" aria-hidden>
      <circle cx="12" cy="12" r="10" />
      <path d="M7 12.5l3.2 3.2L17 9" />
    </svg>
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
function cachedSummary(cache: InfiniteData<Page<WorkoutSummary>> | undefined, id: number): WorkoutSummary | undefined {
  return cache?.pages.flatMap((page) => page.content).find((w) => w.id === id)
}

interface SummaryProps {
  date: string
  notes: string | null
  setCount: number
  exerciseCount: number
  volume: number
  fromRoutine?: boolean
}

function Summary({ date, notes, setCount, exerciseCount, volume, fromRoutine }: SummaryProps) {
  return (
    <div className="summary">
      <div className="summary-card" style={{ viewTransitionName: 'workout-hero' }}>
        {fromRoutine && <span className="summary-routine-tag">{t.routineTag}</span>}
        <div className="summary-date">{formatLongDate(date)}</div>
        <h2 className="summary-title">{notes ?? t.untitledWorkout}</h2>
        <div className="summary-stats">
          <div>
            <div className="stat-value">
              <CountUp value={setCount} format={String} durationMs={500} />
            </div>
            <div className="stat-label">{t.labelSets}</div>
          </div>
          <div>
            <div className="stat-value">
              <CountUp value={exerciseCount} format={String} durationMs={500} />
            </div>
            <div className="stat-label">{t.labelExercises}</div>
          </div>
        </div>
      </div>
      <div className="volume-badge">
        <small>{t.totalVolume}</small>
        <strong>
          <CountUp value={volume} format={(n) => t.kg(formatKg(n))} />
        </strong>
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

/** Turns this workout into a routine: the server takes each exercise's set count and most common reps. */
function SaveAsRoutineSheet({ workout, onClose }: { workout: WorkoutDetail; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [name, setName] = useState(() => (workout.notes ?? '').trim().slice(0, ROUTINE_NAME_MAX))
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const save = async () => {
    const trimmed = name.trim()
    if (trimmed.length === 0 || trimmed.length > ROUTINE_NAME_MAX) {
      setError(t.errorRoutineName)
      return
    }
    setSaving(true)
    try {
      const routine = await api.routineFromWorkout(workout.id, trimmed)
      await queryClient.invalidateQueries({ queryKey: queryKeys.routines })
      toast({ message: t.routineSaved(routine.name) })
      onClose()
    } catch (e) {
      setError(errorMessage(e))
      setSaving(false)
    }
  }

  return (
    <Sheet title={t.saveAsRoutine} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          void save()
        }}
      >
        <p className="muted" style={{ margin: 0, fontSize: 14 }}>
          {t.saveAsRoutineBody}
        </p>
        <div className={error ? 'field invalid' : 'field'}>
          <label htmlFor="routine-from-workout-name">{t.routineName}</label>
          <input id="routine-from-workout-name" value={name} maxLength={ROUTINE_NAME_MAX} onChange={(e) => (setName(e.target.value), setError(null))} />
          {error && <span className="field-error">{error}</span>}
        </div>
        <button type="submit" className="btn btn-primary btn-block" disabled={saving} data-testid="save-routine">
          {saving ? <span className="spinner small" /> : t.save}
        </button>
      </form>
    </Sheet>
  )
}
