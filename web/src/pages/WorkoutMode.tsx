import { useRef, useState, type ReactNode } from 'react'
import type { PlanItem, SetInput, WorkoutSet } from '../api/types'
import { Stepper } from '../components/Stepper'
import { formatKg, formatLoad } from '../lib/format'
import { t } from '../lib/i18n'
import { buildWorkoutPlan, cascadeEdit, stepReps, stepWeight, suggestionTag, WEIGHT_STEP_KG, type PlannedExercise, type PlannedRow, type RowValues } from '../lib/routineLogic'
import { sanitizeDecimal, validateSet } from '../lib/validation'
import type { ExerciseGroup } from '../lib/workoutLogic'

interface WorkoutModeProps {
  plan: PlanItem[]
  /** The workout's visible sets; planned rows are the routine minus these */
  sets: WorkoutSet[]
  muscleOf: (exerciseId: number) => string | null
  /** Draws one logged set exactly like the normal detail list (tap to edit, swipe to delete) */
  renderSet: (set: WorkoutSet, index: number, exerciseName: string) => ReactNode
  /** Draws an exercise that is not part of the routine */
  renderExtra: (group: ExerciseGroup, index: number) => ReactNode
  /** Posts one set; resolves once the workout and plan were refetched so the row can disappear */
  onLog: (input: SetInput) => Promise<void>
}

/** Workout mode: one card per routine exercise with its logged sets, then its remaining planned rows. */
export function WorkoutMode({ plan, sets, muscleOf, renderSet, renderExtra, onLog }: WorkoutModeProps) {
  const [edits, setEdits] = useState<Record<string, RowValues>>({})
  const [openKey, setOpenKey] = useState<string | null>(null)
  const [invalidKey, setInvalidKey] = useState<string | null>(null)
  const [pending, setPending] = useState<Set<string>>(new Set())
  // State updates land after the next render; the ref stops a fast double tap from posting the set twice
  const pendingRef = useRef(new Set<string>())

  const { exercises, extras } = buildWorkoutPlan(plan, sets, edits)

  const edit = (exercise: PlannedExercise, row: PlannedRow, patch: Partial<RowValues>) => {
    setEdits((current) => cascadeEdit(current, exercise.rows, row.setIndex, patch))
    if (row.key === invalidKey) setInvalidKey(null)
  }

  const tick = async (exercise: PlannedExercise, row: PlannedRow) => {
    if (pendingRef.current.has(row.key)) return
    const { input } = validateSet({ exerciseId: exercise.item.exerciseId, weight: row.weight, reps: row.reps, rpe: '' })
    if (!input) {
      // Nothing sensible to log yet (a first-time lift has no weight): open the steppers instead
      setOpenKey(row.key)
      setInvalidKey(row.key)
      return
    }
    pendingRef.current.add(row.key)
    setPending(new Set(pendingRef.current))
    try {
      await onLog(input)
      setOpenKey((key) => (key === row.key ? null : key))
    } finally {
      pendingRef.current.delete(row.key)
      setPending(new Set(pendingRef.current))
    }
  }

  return (
    <div className="set-groups">
      {exercises.map((exercise, index) => (
        <PlannedExerciseCard key={`${exercise.item.exerciseId}-${index}`} exercise={exercise} muscle={muscleOf(exercise.item.exerciseId)} index={index}>
          {exercise.logged.map((set, i) => renderSet(set, i, exercise.item.name))}
          {exercise.rows.map((row) => (
            <PlannedSetRow
              key={row.key}
              row={row}
              bodyweight={exercise.item.bodyweight}
              open={openKey === row.key}
              invalid={invalidKey === row.key}
              pending={pending.has(row.key)}
              onToggle={() => setOpenKey((key) => (key === row.key ? null : row.key))}
              onChange={(patch) => edit(exercise, row, patch)}
              onTick={() => tick(exercise, row)}
            />
          ))}
        </PlannedExerciseCard>
      ))}
      {extras.map((group, i) => renderExtra(group, exercises.length + i))}
    </div>
  )
}

function PlannedExerciseCard({ exercise, muscle, index, children }: { exercise: PlannedExercise; muscle: string | null; index: number; children: ReactNode }) {
  const { item, rows } = exercise
  const tag = suggestionTag(item)
  const done = rows.length === 0
  return (
    <section
      className={done ? 'exercise-group plan-card done' : 'exercise-group plan-card'}
      data-muscle={muscle ?? undefined}
      data-testid={`plan-card-${item.exerciseId}`}
      style={{ animationDelay: `${index * 40}ms` }}
    >
      <div className="exercise-rail" aria-hidden />
      <div className="exercise-group-body">
        {!done && <span className={`suggestion-tag ${tag.kind}`}>{tag.text}</span>}
        <h3 className="exercise-group-title">
          {item.name}
          {muscle && <span className="muscle-tag">{t.muscleGroups[muscle] ?? muscle}</span>}
        </h3>
        <div className="plan-meta">
          <span className={done ? 'plan-progress done' : 'plan-progress'}>{done ? `✓ ${t.exerciseDone}` : t.setsLeft(rows.length)}</span>
          {item.lastWeightKg != null && item.lastReps.length > 0 && (
            <span className="muted num">{t.lastTime(`${formatLoad(item.lastWeightKg, item.bodyweight)} × ${item.lastReps.join(' · ')}`)}</span>
          )}
        </div>
        {children}
      </div>
    </section>
  )
}

interface PlannedSetRowProps {
  row: PlannedRow
  bodyweight: boolean
  open: boolean
  invalid: boolean
  pending: boolean
  onToggle: () => void
  onChange: (patch: Partial<RowValues>) => void
  onTick: () => void
}

function PlannedSetRow({ row, bodyweight, open, invalid, pending, onToggle, onChange, onTick }: PlannedSetRowProps) {
  const number = row.setIndex + 1
  const weightText = row.weight === '' ? t.emptyWeight : bodyweight ? formatLoad(Number(row.weight), true) : t.kg(formatKg(Number(row.weight)))
  const step = formatKg(WEIGHT_STEP_KG)
  const errors = invalid ? validateSet({ exerciseId: 0, weight: row.weight, reps: row.reps, rpe: '' }).errors : null
  const error = errors?.has('weight') ? (row.weight === '' ? t.enterWeightFirst : t.errorWeight) : errors?.has('reps') ? t.errorReps : null
  return (
    <div className={open ? 'planned-row open' : 'planned-row'} data-testid={`planned-row-${row.key}`}>
      <div className="planned-row-main">
        <span className="set-number num planned">#{number}</span>
        <button
          type="button"
          className={`planned-value num${bodyweight ? ' bodyweight' : ''}${row.weight === '' ? ' empty' : ''}`}
          onClick={onToggle}
          aria-expanded={open}
          aria-label={`${bodyweight ? t.addedWeightKg : t.weightKg}: ${weightText}`}
        >
          {weightText}
        </button>
        <button type="button" className="planned-value reps num" onClick={onToggle} aria-expanded={open} aria-label={`${t.reps}: ${row.reps}`}>
          × {row.reps}
        </button>
        <button type="button" className="tick-btn" onClick={onTick} disabled={pending} aria-label={t.logPlannedSet(number)}>
          {pending ? <span className="spinner small" /> : '✓'}
        </button>
      </div>
      {open && (
        <div className="planned-steppers">
          <div className="field">
            <span className="field-label">{bodyweight ? t.addedWeightKg : t.weightKg}</span>
            <Stepper
              label={bodyweight ? t.addedWeightKg : t.weightKg}
              value={row.weight}
              invalid={errors?.has('weight')}
              onMinus={() => onChange({ weight: stepWeight(row.weight, -WEIGHT_STEP_KG) })}
              onPlus={() => onChange({ weight: stepWeight(row.weight, WEIGHT_STEP_KG) })}
              minusLabel={t.lighter(step)}
              plusLabel={t.heavier(step)}
              minusDisabled={row.weight === '' || Number(row.weight) <= 0}
              onType={{ text: row.weight, inputMode: 'decimal', onChange: (text) => onChange({ weight: sanitizeDecimal(text) }) }}
            />
          </div>
          <div className="field">
            <span className="field-label">{t.reps}</span>
            <Stepper
              label={t.reps}
              value={row.reps}
              invalid={errors?.has('reps')}
              onMinus={() => onChange({ reps: stepReps(row.reps, -1) })}
              onPlus={() => onChange({ reps: stepReps(row.reps, 1) })}
              minusLabel={t.fewerReps}
              plusLabel={t.moreReps}
              minusDisabled={Number(row.reps) <= 1}
              onType={{ text: row.reps, inputMode: 'numeric', onChange: (text) => onChange({ reps: text.replace(/\D/g, '').slice(0, 4) }) }}
            />
          </div>
          {error && <span className="field-error planned-error">{error}</span>}
        </div>
      )}
    </div>
  )
}
