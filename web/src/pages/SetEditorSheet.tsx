import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { Exercise, SetInput } from '../api/types'
import { Sheet } from '../components/Sheet'
import { ErrorState } from '../components/StateViews'
import { errorMessage, formatKg } from '../lib/format'
import { t } from '../lib/i18n'
import { clampSetCount, MAX_SETS_AT_ONCE, sanitizeDecimal, validateSet, type SetField, type SetForm } from '../lib/validation'
import { groupByMuscle, weightAfterPick } from '../lib/workoutLogic'
import { BodyweightIcon } from '../components/Icons'

export interface SetEditorProps {
  initial: SetForm & { exerciseName: string | null; bodyweight: boolean }
  isEditing: boolean
  isSaving: boolean
  /** count > 1 adds that many identical sets (add mode only) */
  onSave: (input: SetInput, count: number) => void
  onDelete?: () => void
  onClose: () => void
}

export function SetEditorSheet({ initial, isEditing, isSaving, onSave, onDelete, onClose }: SetEditorProps) {
  const [form, setForm] = useState<SetForm>(initial)
  const [exerciseName, setExerciseName] = useState(initial.exerciseName)
  const [bodyweight, setBodyweight] = useState(initial.bodyweight)
  const [errors, setErrors] = useState<Set<SetField>>(new Set())
  const [picking, setPicking] = useState(initial.exerciseId === null)
  const [setCount, setSetCount] = useState(1)

  const update = (patch: Partial<SetForm>, cleared: SetField) => {
    setForm((f) => ({ ...f, ...patch }))
    setErrors((e) => {
      const next = new Set(e)
      next.delete(cleared)
      return next
    })
  }

  const submit = () => {
    const result = validateSet(form)
    if (!result.input) {
      setErrors(result.errors)
      return
    }
    onSave(result.input, isEditing ? 1 : setCount)
  }

  if (picking) {
    return (
      <Sheet title={t.chooseExercise} onClose={onClose}>
        <ExercisePicker
          onPick={(exercise) => {
            update({ exerciseId: exercise.id, weight: weightAfterPick(form.weight, bodyweight, exercise) }, 'exercise')
            setExerciseName(exercise.name)
            setBodyweight(exercise.bodyweight)
            setPicking(false)
          }}
          onBack={form.exerciseId !== null ? () => setPicking(false) : undefined}
        />
      </Sheet>
    )
  }

  return (
    <Sheet title={isEditing ? t.editSet : t.addSet} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
      >
        <button type="button" className={errors.has('exercise') ? 'selector invalid' : 'selector'} onClick={() => setPicking(true)}>
          <span className="selector-text">
            <span className="selector-label">{t.exercise}</span>
            <span className="selector-value">
              {exerciseName ?? t.chooseExercise}
              {bodyweight && <span className="badge badge-bodyweight">{t.calisthenics}</span>}
            </span>
          </span>
          <span aria-hidden>›</span>
        </button>
        <div className="row">
          <NumberField
            id="weight"
            label={bodyweight ? t.addedWeightKg : t.weightKg}
            value={form.weight}
            error={errors.has('weight') ? t.errorWeight : null}
            inputMode="decimal"
            onChange={(value) => update({ weight: sanitizeDecimal(value) }, 'weight')}
          />
          <NumberField
            id="reps"
            label={t.reps}
            value={form.reps}
            error={errors.has('reps') ? t.errorReps : null}
            inputMode="numeric"
            onChange={(value) => update({ reps: value.replace(/\D/g, '').slice(0, 4) }, 'reps')}
          />
        </div>
        {bodyweight && <BodyweightHint />}
        <div className="row">
          <NumberField
            id="rpe"
            label={t.rpeOptional}
            value={form.rpe}
            error={errors.has('rpe') ? t.errorRpe : null}
            inputMode="decimal"
            onChange={(value) => update({ rpe: sanitizeDecimal(value, 4) }, 'rpe')}
          />
          {!isEditing && <SetCountStepper value={setCount} onChange={setSetCount} />}
        </div>
        <div className="row" style={{ alignItems: 'center' }}>
          {isEditing && onDelete && (
            <button type="button" className="btn btn-danger" style={{ flex: 'none' }} onClick={onDelete} disabled={isSaving}>
              {t.delete}
            </button>
          )}
          <button type="submit" className="btn btn-primary btn-block" disabled={isSaving} data-testid="save-set">
            {isSaving ? <span className="spinner small" /> : setCount > 1 && !isEditing ? t.saveSets(setCount) : t.save}
          </button>
        </div>
      </form>
    </Sheet>
  )
}

/** "4 sets × 8 reps" in one go: the same weight and reps are saved as that many sets. */
function SetCountStepper({ value, onChange }: { value: number; onChange: (count: number) => void }) {
  return (
    <div className="field">
      <span className="field-label" id="set-count-label">
        {t.setCount}
      </span>
      <div className="stepper" role="group" aria-labelledby="set-count-label">
        <button type="button" onClick={() => onChange(clampSetCount(value - 1))} disabled={value <= 1} aria-label={t.fewerSets}>
          −
        </button>
        <span className="stepper-value num" aria-live="polite" key={value}>
          {value}
        </span>
        <button type="button" onClick={() => onChange(clampSetCount(value + 1))} disabled={value >= MAX_SETS_AT_ONCE} aria-label={t.moreSets}>
          +
        </button>
      </div>
    </div>
  )
}

/** Explains that 0 is a valid weight here, and whether the profile weight is there to be added. */
function BodyweightHint() {
  const profile = useQuery({ queryKey: queryKeys.profile, queryFn: api.profile, staleTime: 5 * 60_000 })
  const kg = profile.data?.bodyWeightKg
  return <p className="field-hint">{kg != null ? t.bodyweightHintKnown(formatKg(kg)) : t.bodyweightHint}</p>
}

function NumberField(props: {
  id: string
  label: string
  value: string
  error: string | null
  inputMode: 'decimal' | 'numeric'
  onChange: (value: string) => void
}) {
  return (
    <div className={props.error ? 'field invalid' : 'field'}>
      <label htmlFor={props.id}>{props.label}</label>
      <input
        id={props.id}
        inputMode={props.inputMode}
        autoComplete="off"
        value={props.value}
        onChange={(event) => props.onChange(event.target.value)}
      />
      {props.error && <span className="field-error">{props.error}</span>}
    </div>
  )
}

/** Searchable exercise list grouped by muscle, with a bodyweight filter and "new exercise"; also used by the routine editor. */
export function ExercisePicker({ onPick, onBack }: { onPick: (exercise: Exercise) => void; onBack?: () => void }) {
  const [query, setQuery] = useState('')
  const [onlyBodyweight, setOnlyBodyweight] = useState(false)
  const [creating, setCreating] = useState(false)
  const exercises = useQuery({ queryKey: queryKeys.exercises, queryFn: api.exercises, staleTime: 5 * 60_000 })

  if (creating) return <NewExerciseForm initialName={query} onCreated={onPick} onCancel={() => setCreating(false)} />

  const term = query.trim().toLowerCase()
  const filtered = (exercises.data ?? []).filter((e) => e.name.toLowerCase().includes(term) && (!onlyBodyweight || e.bodyweight))

  return (
    <div className="stack">
      {onBack && (
        <button className="btn-text btn" style={{ alignSelf: 'flex-start', padding: 0 }} onClick={onBack}>
          ‹ {t.back}
        </button>
      )}
      <div className="field">
        <input placeholder={t.searchExercises} value={query} onChange={(e) => setQuery(e.target.value)} aria-label={t.searchExercises} />
      </div>
      <div className="chip-row">
        <button className={onlyBodyweight ? 'filter-chip' : 'filter-chip selected'} onClick={() => setOnlyBodyweight(false)}>
          {t.allExercises}
        </button>
        <button className={onlyBodyweight ? 'filter-chip selected' : 'filter-chip'} onClick={() => setOnlyBodyweight(true)}>
          <BodyweightIcon /> {t.calisthenics}
        </button>
        <button className="btn btn-text" style={{ marginLeft: 'auto' }} onClick={() => setCreating(true)}>
          + {t.newExercise}
        </button>
      </div>
      {exercises.isPending ? (
        <div className="spinner" style={{ margin: '24px auto' }} />
      ) : exercises.isError ? (
        <ErrorState error={exercises.error} onRetry={() => exercises.refetch()} />
      ) : (
        <div className="exercise-list">
          {filtered.length === 0 && <p className="muted">{t.noExerciseMatches(query.trim())}</p>}
          {groupByMuscle(filtered).map((section) => (
            <section key={section.muscleGroup} className="exercise-section">
              <h4 className="exercise-section-title" data-muscle={section.muscleGroup}>
                {t.muscleGroups[section.muscleGroup] ?? section.muscleGroup}
              </h4>
              {section.exercises.map((exercise) => (
                <button key={exercise.id} className="exercise-item" onClick={() => onPick(exercise)}>
                  <span style={{ flex: 1 }}>{exercise.name}</span>
                  {exercise.bodyweight && <span className="badge badge-bodyweight">{t.calisthenics}</span>}
                  {exercise.custom && <span className="badge">{t.custom}</span>}
                </button>
              ))}
            </section>
          ))}
        </div>
      )}
    </div>
  )
}

function NewExerciseForm({ initialName, onCreated, onCancel }: { initialName: string; onCreated: (e: Exercise) => void; onCancel: () => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(initialName)
  const [group, setGroup] = useState('')
  const [bodyweight, setBodyweight] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async () => {
    if (!name.trim()) {
      setError(t.errorName)
      return
    }
    setSaving(true)
    try {
      const created = await api.createExercise(name.trim(), group.trim() || null, bodyweight)
      await queryClient.invalidateQueries({ queryKey: queryKeys.exercises })
      onCreated(created)
    } catch (e) {
      setError(errorMessage(e))
      setSaving(false)
    }
  }

  return (
    <div className="stack">
      <div className={error ? 'field invalid' : 'field'}>
        <label htmlFor="exercise-name">{t.exerciseName}</label>
        <input id="exercise-name" value={name} maxLength={100} onChange={(e) => (setName(e.target.value), setError(null))} />
        {error && <span className="field-error">{error}</span>}
      </div>
      <div className="field">
        <label htmlFor="exercise-group">{t.muscleGroupOptional}</label>
        <input id="exercise-group" value={group} maxLength={50} onChange={(e) => setGroup(e.target.value)} />
      </div>
      <label className="toggle-row">
        <input type="checkbox" checked={bodyweight} onChange={(e) => setBodyweight(e.target.checked)} />
        <span>
          <strong>{t.bodyweightExercise}</strong>
          <small className="muted">{t.bodyweightExerciseHint}</small>
        </span>
      </label>
      <div className="row">
        <button className="btn btn-tonal" onClick={onCancel}>
          {t.cancel}
        </button>
        <button className="btn btn-primary" onClick={submit} disabled={saving}>
          {saving ? <span className="spinner small" /> : t.create}
        </button>
      </div>
    </div>
  )
}
