import type { PlanItem, RoutineExercise, RoutineInput, WorkoutSet } from '../api/types'
import { formatKg } from './format'
import { t } from './i18n'
import { groupByExercise, type ExerciseGroup } from './workoutLogic'

// Pure logic for routines and workout mode, kept apart from the components so it can be unit-tested.

export const WEIGHT_STEP_KG = 2.5
const MAX_WEIGHT_KG = 9999.99
const MAX_REPS = 1000

export const ROUTINE_NAME_MAX = 60
export const ROUTINE_MAX_EXERCISES = 20
export const ROUTINE_MAX_SETS = 10
export const ROUTINE_MAX_REPS = 100

/** A planned row's values as the user sees them; strings so an empty weight can be told apart from 0. */
export interface RowValues {
  weight: string
  reps: string
}

export interface PlannedRow extends RowValues {
  key: string
  /** 0-based position of this set within the exercise's plan; stays the same as earlier rows get logged */
  setIndex: number
}

export interface PlannedExercise {
  item: PlanItem
  /** Sets already logged for this plan entry, shown above the planned rows */
  logged: WorkoutSet[]
  rows: PlannedRow[]
}

/** Prefill: the suggested weight, else last time's, else empty (bodyweight: no added load). */
export function defaultRowValues(item: PlanItem): RowValues {
  const weight = item.suggestedWeightKg ?? item.lastWeightKg ?? (item.bodyweight ? 0 : null)
  return { weight: weight === null ? '' : String(weight), reps: String(item.suggestedReps ?? item.targetReps) }
}

export const rowKey = (itemIndex: number, setIndex: number) => `${itemIndex}:${setIndex}`

/**
 * Planned rows are never stored: they are the routine minus the sets already logged. The count comes
 * from the workout's own sets (not the plan's loggedSets) so a set that was just added or is hidden
 * behind an undo toast changes the rows at once. If a routine lists an exercise twice, the earlier entry
 * takes its sets first and the last one also collects any extra sets of that exercise.
 */
export function buildWorkoutPlan(
  items: PlanItem[],
  sets: WorkoutSet[],
  edits: Record<string, RowValues>,
): { exercises: PlannedExercise[]; extras: ExerciseGroup[] } {
  const groups = groupByExercise(sets)
  const queues = new Map(groups.map((group) => [group.exerciseId, [...group.sets]]))
  const lastIndexOf = new Map(items.map((item, index) => [item.exerciseId, index]))

  const exercises = items.map((item, itemIndex) => {
    const queue = queues.get(item.exerciseId) ?? []
    const logged = lastIndexOf.get(item.exerciseId) === itemIndex ? queue.splice(0) : queue.splice(0, item.plannedSets)
    const defaults = defaultRowValues(item)
    const rows: PlannedRow[] = []
    for (let setIndex = logged.length; setIndex < item.plannedSets; setIndex++) {
      const key = rowKey(itemIndex, setIndex)
      rows.push({ key, setIndex, ...(edits[key] ?? defaults) })
    }
    return { item, logged, rows }
  })
  const extras = groups.filter((group) => !lastIndexOf.has(group.exerciseId))
  return { exercises, extras }
}

/**
 * Changing a row also changes the rows after it: lifters mostly keep the new weight for the remaining
 * sets, so bumping set 2 to 82.5 kg should not leave set 3 at 80.
 */
export function cascadeEdit(
  edits: Record<string, RowValues>,
  rows: PlannedRow[],
  fromSetIndex: number,
  patch: Partial<RowValues>,
): Record<string, RowValues> {
  const next = { ...edits }
  for (const row of rows) {
    if (row.setIndex < fromSetIndex) continue
    next[row.key] = { weight: row.weight, reps: row.reps, ...patch }
  }
  return next
}

/** ± on the weight: never below 0, rounded to 2 decimals so 0.1 + 0.2 style float noise never shows. */
export function stepWeight(weight: string, deltaKg: number): string {
  const current = Number(weight)
  const base = Number.isFinite(current) ? current : 0
  return String(Math.min(MAX_WEIGHT_KG, Math.max(0, Math.round((base + deltaKg) * 100) / 100)))
}

/** ± on the reps: at least 1, since a set of 0 reps is not a set. */
export function stepReps(reps: string, delta: number): string {
  const current = Number(reps)
  const base = Number.isFinite(current) ? Math.round(current) : 0
  return String(Math.min(MAX_REPS, Math.max(1, base + delta)))
}

export type SuggestionKind = 'increase' | 'same' | 'first'

/** The small tag on an exercise card: "↑ 2,5 kg", "+1 rep", "Same weight" or "First time". */
export function suggestionTag(item: PlanItem): { kind: SuggestionKind; text: string } {
  switch (item.suggestion) {
    case 'INCREASE':
      // Bodyweight exercises progress by reps (suggestedReps > targetReps), everything else by weight
      if (item.bodyweight) {
        const extraReps = item.suggestedReps != null ? item.suggestedReps - item.targetReps : 1
        return { kind: 'increase', text: t.suggestMoreReps(Math.max(1, extraReps)) }
      }
      if (item.increaseKg != null && item.increaseKg > 0) return { kind: 'increase', text: t.suggestIncreaseKg(formatKg(item.increaseKg)) }
      return { kind: 'increase', text: t.suggestIncreaseKg(formatKg(WEIGHT_STEP_KG)) }
    case 'SAME':
      return { kind: 'same', text: t.suggestSame }
    default:
      return { kind: 'first', text: t.suggestFirst }
  }
}

/** "Bench Press 3×8 · Row 3×10" */
export function routineSummary(exercises: Pick<RoutineExercise, 'name' | 'sets' | 'targetReps'>[]): string {
  return exercises.map((e) => `${e.name} ${e.sets}×${e.targetReps}`).join(' · ')
}

export const clampRoutineSets = (n: number) => Math.min(ROUTINE_MAX_SETS, Math.max(1, Math.round(n)))
export const clampTargetReps = (n: number) => Math.min(ROUTINE_MAX_REPS, Math.max(1, Math.round(n)))

/** The list with one element moved up (-1) or down (+1); out-of-range moves return it unchanged. */
export function moveItem<T>(list: T[], index: number, offset: -1 | 1): T[] {
  const target = index + offset
  if (index < 0 || index >= list.length || target < 0 || target >= list.length) return list
  const next = [...list]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

export type RoutineField = 'name' | 'exercises' | 'duplicate'

/** Same limits as the backend: name 1–60 after trimming, 1–20 exercises, no exercise twice. */
export function validateRoutine(name: string, exercises: RoutineExercise[]): { input: RoutineInput | null; errors: Set<RoutineField> } {
  const errors = new Set<RoutineField>()
  const trimmed = name.trim()
  if (trimmed.length === 0 || trimmed.length > ROUTINE_NAME_MAX) errors.add('name')
  if (exercises.length === 0 || exercises.length > ROUTINE_MAX_EXERCISES) errors.add('exercises')
  if (new Set(exercises.map((e) => e.exerciseId)).size !== exercises.length) errors.add('duplicate')
  if (errors.size > 0) return { input: null, errors }
  return {
    input: {
      name: trimmed,
      exercises: exercises.map((e) => ({ exerciseId: e.exerciseId, sets: clampRoutineSets(e.sets), targetReps: clampTargetReps(e.targetReps) })),
    },
    errors,
  }
}
