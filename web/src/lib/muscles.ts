// Muscle map logic: which muscles the body map draws, how sets turn into colour bands, and their names.
import type { MuscleSets, MuscleStat } from '../api/types'
import { locale, t } from './i18n'

/** The 16 regions the body map draws (backend ids). */
export const MAP_MUSCLES = [
  'CHEST',
  'FRONT_DELTS',
  'SIDE_DELTS',
  'REAR_DELTS',
  'BICEPS',
  'TRICEPS',
  'FOREARMS',
  'ABS',
  'OBLIQUES',
  'TRAPS',
  'LATS',
  'LOWER_BACK',
  'GLUTES',
  'QUADS',
  'HAMSTRINGS',
  'CALVES',
] as const

export type MapMuscle = (typeof MAP_MUSCLES)[number]
export type BodySide = 'front' | 'back'

/** Muscles visible on each figure; side delts, traps, forearms and calves show on both. */
export const SIDE_MUSCLES: Record<BodySide, readonly MapMuscle[]> = {
  front: ['TRAPS', 'FRONT_DELTS', 'SIDE_DELTS', 'CHEST', 'BICEPS', 'FOREARMS', 'ABS', 'OBLIQUES', 'QUADS', 'CALVES'],
  back: ['TRAPS', 'SIDE_DELTS', 'REAR_DELTS', 'TRICEPS', 'FOREARMS', 'LATS', 'LOWER_BACK', 'GLUTES', 'HAMSTRINGS', 'CALVES'],
}

// The backend tracks inner and outer thigh separately, but the map has no region for them
const FOLDED: Record<string, MapMuscle> = { ADDUCTORS: 'QUADS', ABDUCTORS: 'GLUTES' }

/** Backend muscle id -> map region; null for an id this version does not know. */
export function foldMuscle(id: string): MapMuscle | null {
  if (id in FOLDED) return FOLDED[id]
  return (MAP_MUSCLES as readonly string[]).includes(id) ? (id as MapMuscle) : null
}

/** Merges folded muscles (and their exercises) into their map region; unknown ids are dropped. */
export function foldMuscleStats<T extends MuscleSets | MuscleStat>(muscles: readonly T[]): Map<MapMuscle, MuscleStat> {
  const folded = new Map<MapMuscle, MuscleStat>()
  for (const entry of muscles) {
    const region = foldMuscle(entry.muscle)
    if (!region) continue
    const merged = folded.get(region) ?? { muscle: region, sets: 0, exercises: [] }
    merged.sets = roundSets(merged.sets + entry.sets)
    for (const exercise of 'exercises' in entry ? entry.exercises : []) {
      const same = merged.exercises.find((e) => e.name === exercise.name)
      if (same) same.sets = roundSets(same.sets + exercise.sets)
      else merged.exercises.push({ ...exercise })
    }
    folded.set(region, merged)
  }
  for (const stat of folded.values()) stat.exercises.sort((a, b) => b.sets - a.sets)
  return folded
}

// Half sets add up exactly, but 0.1 steps from the server do not (0.1 + 0.2)
const roundSets = (n: number) => Math.round(n * 10) / 10

export type Band = 'none' | 'low' | 'mid' | 'high'
/** 7 or 30 days of the Progress card, or a single workout of the summary sheet. */
export type BandScope = 1 | 7 | 30 | 'workout'

const WEEK_THRESHOLDS = [5, 10] as const

/** [mid, high]: from `mid` sets a muscle is orange, from `high` red. 30 days scale the weekly numbers. */
export function bandThresholds(scope: BandScope): readonly [number, number] {
  // One day is about one workout, so it uses the single-workout bands
  if (scope === 'workout' || scope === 1) return [2, 4]
  const scale = scope / 7
  return [Math.round(WEEK_THRESHOLDS[0] * scale), Math.round(WEEK_THRESHOLDS[1] * scale)]
}

export function bandFor(sets: number, scope: BandScope): Band {
  if (!(sets > 0)) return 'none'
  const [mid, high] = bandThresholds(scope)
  if (sets >= high) return 'high'
  return sets >= mid ? 'mid' : 'low'
}

export function bandsFor(muscles: Map<MapMuscle, MuscleSets>, scope: BandScope): Partial<Record<MapMuscle, Band>> {
  const bands: Partial<Record<MapMuscle, Band>> = {}
  for (const [muscle, stat] of muscles) bands[muscle] = bandFor(stat.sets, scope)
  return bands
}

export const muscleName = (muscle: MapMuscle) => t.muscleNames[muscle] ?? muscle

/** 12 -> "12", 2.5 -> "2.5" (one decimal at most, locale-aware). */
export function formatSets(sets: number, loc: string = locale): string {
  return new Intl.NumberFormat(loc, { maximumFractionDigits: 1 }).format(sets)
}

/**
 * Sets per muscle for a list of logged sets, from each exercise's muscles: 1 for a primary muscle, 0.5 for a
 * secondary one, and when two of an exercise's muscles fold onto one map region the primary role wins, so a
 * set is never counted twice for a region (the same rule as the server's summary).
 */
export function musclesOfSets(
  sets: readonly { exerciseId: number }[],
  exercises: readonly { id: number; muscles?: readonly { muscle: string; role: 'PRIMARY' | 'SECONDARY' }[] }[],
): MuscleSets[] {
  const byId = new Map(exercises.map((exercise) => [exercise.id, exercise]))
  const totals = new Map<MapMuscle, number>()
  for (const set of sets) {
    const regions = new Map<MapMuscle, number>()
    for (const { muscle, role } of byId.get(set.exerciseId)?.muscles ?? []) {
      const region = foldMuscle(muscle)
      if (!region) continue
      regions.set(region, Math.max(regions.get(region) ?? 0, role === 'PRIMARY' ? 1 : 0.5))
    }
    for (const [region, weight] of regions) totals.set(region, (totals.get(region) ?? 0) + weight)
  }
  return [...totals].map(([muscle, sets]) => ({ muscle, sets: Math.round(sets * 10) / 10 }))
}

