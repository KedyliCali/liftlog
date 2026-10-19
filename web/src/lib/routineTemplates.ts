import type { Exercise, RoutineExercise, RoutineInput } from '../api/types'
import { t } from './i18n'
import { clampRoutineSets, clampTargetReps, ROUTINE_MAX_EXERCISES, ROUTINE_NAME_MAX } from './routineLogic'

// Ready-made routines. Exercises are listed by their exact built-in (English) names, not ids: ids differ
// between databases, so a template is resolved against the user's own GET /exercises when it is used.

export type TemplateId = 'push' | 'pull' | 'legs' | 'fullBodyA' | 'fullBodyB' | 'upper' | 'lower' | 'machineStarter' | 'calisthenics'
export type TemplateFocus = 'ppl' | 'fullBody' | 'upperLower' | 'machines' | 'bodyweight'

export interface TemplateExercise {
  /** Exact built-in name, as seeded by the Flyway migrations */
  name: string
  /** Gym shorthand for the one-line summary ("OHP"); the full name is used when missing */
  short?: string
  sets: number
  targetReps: number
}

export interface RoutineTemplate {
  id: TemplateId
  focus: TemplateFocus
  beginner: boolean
  exercises: TemplateExercise[]
}

const ex = (name: string, sets: number, targetReps: number, short?: string): TemplateExercise => ({ name, short, sets, targetReps })

export const ROUTINE_TEMPLATES: readonly RoutineTemplate[] = [
  {
    id: 'push',
    focus: 'ppl',
    beginner: false,
    exercises: [
      ex('Bench Press', 4, 8, 'Bench'),
      ex('Overhead Press', 3, 8, 'OHP'),
      ex('Incline Dumbbell Press', 3, 10, 'Incline DB'),
      ex('Lateral Raise', 3, 12, 'Lateral'),
      ex('Triceps Pushdown', 3, 12, 'Pushdown'),
    ],
  },
  {
    id: 'pull',
    focus: 'ppl',
    beginner: false,
    exercises: [
      ex('Deadlift', 3, 5),
      ex('Lat Pulldown', 3, 10, 'Pulldown'),
      ex('Seated Cable Row', 3, 10, 'Cable Row'),
      ex('Face Pull', 3, 15),
      ex('Barbell Curl', 3, 10, 'Curl'),
    ],
  },
  {
    id: 'legs',
    focus: 'ppl',
    beginner: false,
    exercises: [
      ex('Squat', 4, 6),
      ex('Romanian Deadlift', 3, 8, 'RDL'),
      ex('Leg Press', 3, 10),
      ex('Leg Extension', 3, 12, 'Leg Ext'),
      ex('Seated Leg Curl', 3, 12, 'Leg Curl'),
      ex('Standing Calf Raise Machine', 3, 15, 'Calf Raise'),
    ],
  },
  {
    id: 'fullBodyA',
    focus: 'fullBody',
    beginner: true,
    exercises: [
      ex('Squat', 3, 8),
      ex('Bench Press', 3, 8, 'Bench'),
      ex('Barbell Row', 3, 8, 'Row'),
      ex('Lateral Raise', 2, 12, 'Lateral'),
      ex('Dumbbell Curl', 2, 12, 'DB Curl'),
    ],
  },
  {
    id: 'fullBodyB',
    focus: 'fullBody',
    beginner: true,
    exercises: [
      ex('Deadlift', 3, 5),
      ex('Overhead Press', 3, 8, 'OHP'),
      ex('Lat Pulldown', 3, 10, 'Pulldown'),
      ex('Goblet Squat', 3, 10, 'Goblet'),
      ex('Triceps Pushdown', 2, 12, 'Pushdown'),
    ],
  },
  {
    id: 'upper',
    focus: 'upperLower',
    beginner: false,
    exercises: [
      ex('Bench Press', 4, 6, 'Bench'),
      ex('Barbell Row', 4, 8, 'Row'),
      ex('Dumbbell Shoulder Press', 3, 10, 'DB Press'),
      ex('Lat Pulldown', 3, 10, 'Pulldown'),
      ex('Barbell Curl', 2, 12, 'Curl'),
      ex('Triceps Pushdown', 2, 12, 'Pushdown'),
    ],
  },
  {
    id: 'lower',
    focus: 'upperLower',
    beginner: false,
    exercises: [
      ex('Squat', 4, 6),
      ex('Romanian Deadlift', 3, 8, 'RDL'),
      ex('Bulgarian Split Squat', 3, 10, 'Split Squat'),
      ex('Lying Leg Curl', 3, 12, 'Leg Curl'),
      ex('Seated Calf Raise', 3, 15, 'Calf Raise'),
      ex('Hanging Leg Raise', 3, 12, 'Leg Raise'),
    ],
  },
  {
    id: 'machineStarter',
    focus: 'machines',
    beginner: true,
    exercises: [
      ex('Chest Press Machine', 3, 12, 'Chest Press'),
      ex('Seated Row Machine', 3, 12, 'Seated Row'),
      ex('Shoulder Press Machine', 3, 12, 'Shoulder Press'),
      ex('Leg Press', 3, 12),
      ex('Leg Extension', 3, 12, 'Leg Ext'),
      ex('Seated Leg Curl', 3, 12, 'Leg Curl'),
      ex('Ab Crunch Machine', 3, 15, 'Ab Crunch'),
    ],
  },
  {
    id: 'calisthenics',
    focus: 'bodyweight',
    beginner: false,
    exercises: [
      ex('Pull-up', 4, 6),
      ex('Push-up', 4, 15),
      ex('Dip', 3, 10),
      ex('Inverted Row', 3, 12),
      ex('Pistol Squat', 3, 6, 'Pistol'),
      ex('Hanging Leg Raise', 3, 12, 'Leg Raise'),
    ],
  },
]

/** The template's name in the UI language; also the name the new routine gets. */
export const templateName = (template: RoutineTemplate) => (t.templateNames[template.id] ?? template.id).slice(0, ROUTINE_NAME_MAX)

/** "Bench 4×8 · OHP 3×8 · …" for the cards. */
export function templateSummary(template: RoutineTemplate): string {
  return template.exercises.map((e) => `${e.short ?? e.name} ${e.sets}×${e.targetReps}`).join(' · ')
}

export interface ResolvedTemplate {
  /** Exercises found in the catalog, in template order, with the user's ids */
  exercises: RoutineExercise[]
  /** Template names that are not in the catalog (an older server, or a renamed exercise); they are skipped */
  missing: string[]
}

const normalize = (name: string) => name.trim().toLowerCase()

/**
 * Maps a template's names to the user's exercise ids. A built-in exercise wins over a custom one with the
 * same name; an exact match wins over one that differs only in case or spaces. Names that are not found
 * are skipped and reported instead of failing the whole template, and an id is never used twice because
 * the server rejects a routine that lists an exercise twice.
 */
export function resolveTemplate(template: RoutineTemplate, catalog: Exercise[]): ResolvedTemplate {
  const exact = new Map<string, Exercise>()
  const loose = new Map<string, Exercise>()
  // Built-ins go in last so they overwrite a custom exercise of the same name
  for (const exercise of [...catalog].sort((a, b) => Number(b.custom) - Number(a.custom))) {
    exact.set(exercise.name, exercise)
    loose.set(normalize(exercise.name), exercise)
  }

  const exercises: RoutineExercise[] = []
  const missing: string[] = []
  const used = new Set<number>()
  for (const item of template.exercises) {
    const match = exact.get(item.name) ?? loose.get(normalize(item.name))
    if (!match) {
      missing.push(item.name)
      continue
    }
    if (used.has(match.id) || exercises.length >= ROUTINE_MAX_EXERCISES) continue
    used.add(match.id)
    exercises.push({
      exerciseId: match.id,
      name: match.name,
      bodyweight: match.bodyweight,
      sets: clampRoutineSets(item.sets),
      targetReps: clampTargetReps(item.targetReps),
    })
  }
  return { exercises, missing }
}

/** The POST /routines body for a template, or null when none of its exercises could be resolved. */
export function templateRoutineInput(template: RoutineTemplate, resolved: ResolvedTemplate): RoutineInput | null {
  if (resolved.exercises.length === 0) return null
  return {
    name: templateName(template),
    exercises: resolved.exercises.map(({ exerciseId, sets, targetReps }) => ({ exerciseId, sets, targetReps })),
  }
}
