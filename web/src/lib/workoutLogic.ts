import type { Exercise, PersonalRecord, WorkoutDetail, WorkoutSet, WorkoutSummary } from '../api/types'
import { locale } from './i18n'

// Pure logic used by the pages, kept here so it can be unit-tested without rendering anything.

export interface ExerciseGroup {
  exerciseId: number
  exerciseName: string
  sets: WorkoutSet[]
}

/** Sets grouped by exercise, in the order each exercise was first performed. */
export function groupByExercise(sets: WorkoutSet[]): ExerciseGroup[] {
  const groups = new Map<number, ExerciseGroup>()
  for (const set of sets) {
    const group = groups.get(set.exerciseId) ?? { exerciseId: set.exerciseId, exerciseName: set.exerciseName, sets: [] }
    group.sets.push(set)
    groups.set(set.exerciseId, group)
  }
  return [...groups.values()]
}

const MAX_TEMPLATES = 6

/**
 * Past workouts double as templates: choosing one copies its exercises, reps and weights into the
 * new session. Most recent first, one entry per distinct name so "Push day" appears once.
 */
export function pickTemplates(workouts: WorkoutSummary[]): WorkoutSummary[] {
  const seen = new Set<string>()
  const templates: WorkoutSummary[] = []
  for (const workout of workouts) {
    if (workout.setCount === 0) continue
    const key = (workout.notes ?? '').trim().toLowerCase() || `#${workout.id}`
    if (seen.has(key)) continue
    seen.add(key)
    templates.push(workout)
    if (templates.length === MAX_TEMPLATES) break
  }
  return templates
}

/** Keeps the user's choice while it still has records, otherwise the most trained exercise. */
export function chooseExercise(records: PersonalRecord[], current: number | null): number | null {
  if (current !== null && records.some((r) => r.exerciseId === current)) return current
  let best: PersonalRecord | null = null
  for (const record of records) if (best === null || record.totalSets > best.totalSets) best = record
  return best?.exerciseId ?? null
}

/** Rounded y-range around the data; starting at 0 would flatten an 80 → 94 kg climb into a line. */
export function yDomain(values: number[]): [number, number] {
  const min = Math.min(...values)
  const max = Math.max(...values)
  return [Math.floor((min - 5) / 5) * 5, Math.ceil((max + 5) / 5) * 5]
}

export function initials(name: string): string {
  return (
    name
      .split(/[\s._-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0].toLocaleUpperCase(locale))
      .join('') || '?'
  )
}

export function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

/** Weight that actually moved: for bodyweight exercises the logged weight is only the extra load. */
export function effectiveLoad(set: Pick<WorkoutSet, 'weightKg' | 'bodyweight'>, bodyWeightKg: number | null): number {
  return set.weightKg + (set.bodyweight ? (bodyWeightKg ?? 0) : 0)
}

/**
 * The server's total already counts body weight; sets hidden while their undo toast is open are
 * taken out locally so the number reacts immediately.
 */
export function visibleVolume(workout: WorkoutDetail, hiddenSetIds: Set<number>, bodyWeightKg: number | null): number {
  let volume = workout.totalVolumeKg
  for (const set of workout.sets) if (hiddenSetIds.has(set.id)) volume -= set.reps * effectiveLoad(set, bodyWeightKg)
  return Math.max(0, volume)
}

const MUSCLE_ORDER = ['Chest', 'Back', 'Shoulders', 'Legs', 'Arms', 'Core']

export interface MuscleSection {
  muscleGroup: string
  exercises: Exercise[]
}

/** Picker sections in a fixed training order; custom groups follow alphabetically, ungrouped last. */
export function groupByMuscle(exercises: Exercise[]): MuscleSection[] {
  const sections = new Map<string, Exercise[]>()
  for (const exercise of exercises) {
    const key = exercise.muscleGroup?.trim() || 'Other'
    sections.set(key, [...(sections.get(key) ?? []), exercise])
  }
  const rank = (group: string) => {
    const index = MUSCLE_ORDER.indexOf(group)
    return index >= 0 ? index : group === 'Other' ? Number.MAX_SAFE_INTEGER : MUSCLE_ORDER.length
  }
  return [...sections.entries()]
    .sort(([a], [b]) => rank(a) - rank(b) || a.localeCompare(b))
    .map(([muscleGroup, list]) => ({ muscleGroup, exercises: list }))
}

/**
 * Switching between a barbell lift and a bodyweight one makes the old number meaningless
 * (100 kg squat → "+100 kg" pull-up), so the weight resets; within the same kind it is kept.
 */
export function weightAfterPick(currentWeight: string, wasBodyweight: boolean, picked: Pick<Exercise, 'bodyweight'>): string {
  if (picked.bodyweight === wasBodyweight && currentWeight !== '') return currentWeight
  return picked.bodyweight ? '0' : ''
}

export type ExerciseFilter = 'all' | 'machine' | 'bodyweight'

/** The picker's filter chips: machines include cable stations, since both are gym equipment you sit or stand at. */
export function matchesExerciseFilter(exercise: { bodyweight: boolean; equipment?: string | null }, filter: ExerciseFilter): boolean {
  if (filter === 'bodyweight') return exercise.bodyweight
  if (filter === 'machine') return exercise.equipment === 'MACHINE' || exercise.equipment === 'CABLE'
  return true
}
