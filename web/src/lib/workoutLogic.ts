import type { PersonalRecord, WorkoutSet, WorkoutSummary } from '../api/types'
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
