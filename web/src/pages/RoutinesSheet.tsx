import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { Routine, RoutineExercise } from '../api/types'
import { Sheet } from '../components/Sheet'
import { ErrorState } from '../components/StateViews'
import { Stepper } from '../components/Stepper'
import { useConfirm, useToast } from '../components/contexts'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import {
  clampRoutineSets,
  clampTargetReps,
  moveItem,
  ROUTINE_MAX_EXERCISES,
  ROUTINE_MAX_REPS,
  ROUTINE_MAX_SETS,
  ROUTINE_NAME_MAX,
  routineSummary,
  validateRoutine,
  type RoutineField,
} from '../lib/routineLogic'
import type { RoutineTemplate } from '../lib/routineTemplates'
import { TemplateCards, TemplatePreview } from './RoutineTemplates'
import { ExercisePicker } from './SetEditorSheet'

interface Draft {
  /** null while creating a new routine */
  id: number | null
  name: string
  exercises: RoutineExercise[]
}

/**
 * Manage routines: the list (with the ready-made templates under it), then an editor for one routine or a
 * template's preview in the same sheet. initialTemplate opens straight on that preview.
 */
export function RoutinesSheet({ onClose, initialTemplate = null }: { onClose: () => void; initialTemplate?: RoutineTemplate | null }) {
  const [draft, setDraft] = useState<Draft | null>(null)
  const [template, setTemplate] = useState<RoutineTemplate | null>(initialTemplate)
  if (draft) return <RoutineEditor initial={draft} onDone={() => setDraft(null)} onClose={onClose} />
  if (template) {
    return (
      <Sheet title={t.readyTemplates} onClose={onClose}>
        <TemplatePreview template={template} backLabel={t.routines} onBack={() => setTemplate(null)} onAdded={() => setTemplate(null)} onStarted={onClose} />
      </Sheet>
    )
  }
  return <RoutineList onEdit={setDraft} onPickTemplate={setTemplate} onClose={onClose} />
}

function RoutineList({ onEdit, onPickTemplate, onClose }: { onEdit: (draft: Draft) => void; onPickTemplate: (template: RoutineTemplate) => void; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const confirm = useConfirm()
  const routines = useQuery({ queryKey: queryKeys.routines, queryFn: api.routines })
  const [deleting, setDeleting] = useState<number | null>(null)

  const remove = async (routine: Routine) => {
    if (!(await confirm({ item: routine.name, message: t.deleteRoutineConsequence }))) return
    setDeleting(routine.id)
    try {
      await api.deleteRoutine(routine.id)
      await queryClient.invalidateQueries({ queryKey: queryKeys.routines })
      toast({ message: t.routineDeleted })
    } catch (error) {
      toast({ message: errorMessage(error) })
    } finally {
      setDeleting(null)
    }
  }

  return (
    <Sheet title={t.routines} onClose={onClose}>
      <div className="stack">
        <button className="btn btn-primary btn-block" onClick={() => onEdit({ id: null, name: '', exercises: [] })} data-testid="new-routine">
          + {t.newRoutine}
        </button>
        {routines.isPending ? (
          <div className="spinner" style={{ margin: '24px auto' }} />
        ) : routines.isError ? (
          <ErrorState error={routines.error} onRetry={() => routines.refetch()} />
        ) : routines.data.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>
            {t.routinesEmpty}
          </p>
        ) : (
          routines.data.map((routine) => (
            <div key={routine.id} className="routine-item">
              <button
                className="routine-item-body"
                onClick={() => onEdit({ id: routine.id, name: routine.name, exercises: routine.exercises })}
                aria-label={`${t.editRoutine}: ${routine.name}`}
              >
                <strong>{routine.name}</strong>
                <span className="routine-item-summary">{routineSummary(routine.exercises)}</span>
              </button>
              <button
                className="icon-btn routine-item-delete"
                onClick={() => remove(routine)}
                disabled={deleting === routine.id}
                aria-label={`${t.delete}: ${routine.name}`}
              >
                {deleting === routine.id ? <span className="spinner small" /> : '✕'}
              </button>
            </div>
          ))
        )}
        <h3 className="section-title templates-title">{t.readyTemplates}</h3>
        <p className="templates-body">{t.readyTemplatesBody}</p>
        <TemplateCards variant="list" onSelect={onPickTemplate} />
      </div>
    </Sheet>
  )
}

function RoutineEditor({ initial, onDone, onClose }: { initial: Draft; onDone: () => void; onClose: () => void }) {
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [name, setName] = useState(initial.name)
  const [exercises, setExercises] = useState(initial.exercises)
  const [errors, setErrors] = useState<Set<RoutineField>>(new Set())
  const [requestError, setRequestError] = useState<string | null>(null)
  const [picking, setPicking] = useState(false)
  const [saving, setSaving] = useState(false)

  const clearError = (field: RoutineField) =>
    setErrors((current) => {
      const next = new Set(current)
      next.delete(field)
      return next
    })

  const patchExercise = (index: number, patch: Partial<RoutineExercise>) =>
    setExercises((list) => list.map((e, i) => (i === index ? { ...e, ...patch } : e)))

  const removeExercise = async (exercise: RoutineExercise) => {
    if (!(await confirm({ title: t.removeExerciseTitle, item: exercise.name, confirmLabel: t.remove }))) return
    // By identity, not index: the list could have been reordered while the question was open
    setExercises((list) => list.filter((e) => e !== exercise))
    clearError('duplicate')
  }

  const save = async () => {
    const result = validateRoutine(name, exercises)
    if (!result.input) {
      setErrors(result.errors)
      return
    }
    setSaving(true)
    setRequestError(null)
    try {
      if (initial.id === null) await api.createRoutine(result.input)
      else await api.updateRoutine(initial.id, result.input)
      await queryClient.invalidateQueries({ queryKey: queryKeys.routines })
      onDone()
    } catch (error) {
      setRequestError(errorMessage(error))
      setSaving(false)
    }
  }

  if (picking) {
    return (
      <Sheet title={t.chooseExercise} onClose={onClose}>
        <ExercisePicker
          onPick={(exercise) => {
            // The server rejects an exercise listed twice; say so instead of adding it
            if (exercises.some((e) => e.exerciseId === exercise.id)) {
              setErrors((current) => new Set(current).add('duplicate'))
              setPicking(false)
              return
            }
            // A new exercise starts with the previous one's scheme: routines tend to repeat it
            const previous = exercises.at(-1)
            setExercises((list) => [
              ...list,
              { exerciseId: exercise.id, name: exercise.name, bodyweight: exercise.bodyweight, sets: previous?.sets ?? 3, targetReps: previous?.targetReps ?? 10 },
            ])
            clearError('exercises')
            clearError('duplicate')
            setPicking(false)
          }}
          onBack={() => setPicking(false)}
        />
      </Sheet>
    )
  }

  return (
    <Sheet title={initial.id === null ? t.newRoutine : t.editRoutine} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          void save()
        }}
      >
        <button type="button" className="btn-text btn" style={{ alignSelf: 'flex-start', padding: 0 }} onClick={onDone}>
          ‹ {t.routines}
        </button>
        <div className={errors.has('name') ? 'field invalid' : 'field'}>
          <label htmlFor="routine-name">{t.routineName}</label>
          <input id="routine-name" value={name} maxLength={ROUTINE_NAME_MAX} onChange={(e) => (setName(e.target.value), clearError('name'))} />
          {errors.has('name') && <span className="field-error">{t.errorRoutineName}</span>}
        </div>

        <ol className="routine-exercises">
          {exercises.map((exercise, index) => (
            <li key={`${exercise.exerciseId}-${index}`} className="routine-exercise" data-testid={`routine-exercise-${index}`}>
              <div className="routine-exercise-head">
                <span className="routine-exercise-index num">{index + 1}</span>
                <strong className="routine-exercise-name">
                  {exercise.name}
                  {exercise.bodyweight && <span className="badge badge-bodyweight">{t.calisthenics}</span>}
                </strong>
                <button type="button" className="icon-btn small" onClick={() => setExercises((l) => moveItem(l, index, -1))} disabled={index === 0} aria-label={`${t.moveUp}: ${exercise.name}`}>
                  ↑
                </button>
                <button
                  type="button"
                  className="icon-btn small"
                  onClick={() => setExercises((l) => moveItem(l, index, 1))}
                  disabled={index === exercises.length - 1}
                  aria-label={`${t.moveDown}: ${exercise.name}`}
                >
                  ↓
                </button>
                <button type="button" className="icon-btn small danger" onClick={() => void removeExercise(exercise)} aria-label={`${t.remove}: ${exercise.name}`}>
                  ✕
                </button>
              </div>
              <div className="row">
                <div className="field">
                  <span className="field-label">{t.setCount}</span>
                  <Stepper
                    label={`${t.setCount}: ${exercise.name}`}
                    value={exercise.sets}
                    onMinus={() => patchExercise(index, { sets: clampRoutineSets(exercise.sets - 1) })}
                    onPlus={() => patchExercise(index, { sets: clampRoutineSets(exercise.sets + 1) })}
                    minusLabel={t.fewerSets}
                    plusLabel={t.moreSets}
                    minusDisabled={exercise.sets <= 1}
                    plusDisabled={exercise.sets >= ROUTINE_MAX_SETS}
                  />
                </div>
                <div className="field">
                  <span className="field-label">{t.reps}</span>
                  <Stepper
                    label={`${t.reps}: ${exercise.name}`}
                    value={exercise.targetReps}
                    onMinus={() => patchExercise(index, { targetReps: clampTargetReps(exercise.targetReps - 1) })}
                    onPlus={() => patchExercise(index, { targetReps: clampTargetReps(exercise.targetReps + 1) })}
                    minusLabel={t.fewerReps}
                    plusLabel={t.moreReps}
                    minusDisabled={exercise.targetReps <= 1}
                    plusDisabled={exercise.targetReps >= ROUTINE_MAX_REPS}
                  />
                </div>
              </div>
            </li>
          ))}
        </ol>

        <button
          type="button"
          className={errors.has('exercises') ? 'add-exercise-btn invalid' : 'add-exercise-btn'}
          onClick={() => setPicking(true)}
          disabled={exercises.length >= ROUTINE_MAX_EXERCISES}
        >
          + {t.addExercise}
        </button>
        {errors.has('exercises') && <span className="field-error">{t.errorRoutineExercises}</span>}
        {errors.has('duplicate') && <span className="field-error">{t.errorRoutineDuplicate}</span>}
        {requestError && <span className="field-error">{requestError}</span>}

        <button type="submit" className="btn btn-primary btn-block" disabled={saving} data-testid="save-routine">
          {saving ? <span className="spinner small" /> : t.save}
        </button>
      </form>
    </Sheet>
  )
}
