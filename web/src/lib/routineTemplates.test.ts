import { describe, expect, it } from 'vitest'
import type { Exercise } from '../api/types'
import { t } from './i18n'
import { ROUTINE_TEMPLATES, resolveTemplate, templateName, templateRoutineInput, templateSummary, type RoutineTemplate } from './routineTemplates'

const builtIn = (id: number, name: string, bodyweight = false): Exercise => ({ id, name, muscleGroup: null, custom: false, bodyweight })

const push = ROUTINE_TEMPLATES.find((template) => template.id === 'push')!

// Every built-in name the templates use, copied from the V2, V5 and V16 seed migrations
const SEEDED = new Set([
  'Ab Crunch Machine',
  'Barbell Curl',
  'Barbell Row',
  'Bench Press',
  'Bulgarian Split Squat',
  'Chest Press Machine',
  'Deadlift',
  'Dip',
  'Dumbbell Curl',
  'Dumbbell Shoulder Press',
  'Face Pull',
  'Goblet Squat',
  'Hanging Leg Raise',
  'Incline Dumbbell Press',
  'Inverted Row',
  'Lat Pulldown',
  'Lateral Raise',
  'Leg Extension',
  'Leg Press',
  'Lying Leg Curl',
  'Overhead Press',
  'Pistol Squat',
  'Pull-up',
  'Push-up',
  'Romanian Deadlift',
  'Seated Cable Row',
  'Seated Calf Raise',
  'Seated Leg Curl',
  'Seated Row Machine',
  'Shoulder Press Machine',
  'Squat',
  'Standing Calf Raise Machine',
  'Triceps Pushdown',
])

describe('routine templates', () => {
  it('only use seeded exercise names, each once, within the routine limits, and have a name in both languages', () => {
    for (const template of ROUTINE_TEMPLATES) {
      const names = template.exercises.map((e) => e.name)
      expect(names.filter((name) => !SEEDED.has(name)), template.id).toEqual([])
      expect(new Set(names).size).toBe(names.length)
      expect(names.length).toBeGreaterThan(0)
      expect(names.length).toBeLessThanOrEqual(20)
      expect(t.templateNames[template.id]).toBeTruthy()
      expect(t.templateFocus[template.focus]).toBeTruthy()
    }
  })

  it('summarise with gym shorthand', () => {
    expect(templateSummary(push)).toBe('Bench 4×8 · OHP 3×8 · Incline DB 3×10 · Lateral 3×12 · Pushdown 3×12')
  })

  it('map names to the user’s ids and skip the ones that are missing', () => {
    const catalog = [builtIn(11, 'Bench Press'), builtIn(12, 'Overhead Press'), builtIn(14, 'Lateral Raise'), builtIn(15, 'Triceps Pushdown')]
    const resolved = resolveTemplate(push, catalog)
    expect(resolved.missing).toEqual(['Incline Dumbbell Press'])
    expect(resolved.exercises).toEqual([
      { exerciseId: 11, name: 'Bench Press', bodyweight: false, sets: 4, targetReps: 8 },
      { exerciseId: 12, name: 'Overhead Press', bodyweight: false, sets: 3, targetReps: 8 },
      { exerciseId: 14, name: 'Lateral Raise', bodyweight: false, sets: 3, targetReps: 12 },
      { exerciseId: 15, name: 'Triceps Pushdown', bodyweight: false, sets: 3, targetReps: 12 },
    ])
    expect(templateRoutineInput(push, resolved)).toEqual({
      name: templateName(push),
      exercises: [
        { exerciseId: 11, sets: 4, targetReps: 8 },
        { exerciseId: 12, sets: 3, targetReps: 8 },
        { exerciseId: 14, sets: 3, targetReps: 12 },
        { exerciseId: 15, sets: 3, targetReps: 12 },
      ],
    })
  })

  it('prefer a built-in over a custom exercise of the same name, and fall back to a case-insensitive match', () => {
    const template: RoutineTemplate = {
      id: 'calisthenics',
      focus: 'bodyweight',
      beginner: false,
      exercises: [
        { name: 'Pull-up', sets: 3, targetReps: 5 },
        { name: 'Dip', sets: 3, targetReps: 8 },
      ],
    }
    const catalog: Exercise[] = [
      { id: 90, name: 'Pull-up', muscleGroup: null, custom: true, bodyweight: false },
      builtIn(7, 'Pull-up', true),
      { id: 91, name: ' dip ', muscleGroup: null, custom: true, bodyweight: true },
    ]
    const resolved = resolveTemplate(template, catalog)
    expect(resolved.exercises.map((e) => e.exerciseId)).toEqual([7, 91])
    expect(resolved.exercises[0].bodyweight).toBe(true)
    expect(resolved.missing).toEqual([])
  })

  it('never list the same id twice and give no body when nothing resolves', () => {
    const template: RoutineTemplate = {
      id: 'push',
      focus: 'ppl',
      beginner: false,
      exercises: [
        { name: 'Bench Press', sets: 3, targetReps: 8 },
        { name: 'bench press', sets: 3, targetReps: 8 },
      ],
    }
    expect(resolveTemplate(template, [builtIn(1, 'Bench Press')]).exercises).toHaveLength(1)

    const empty = resolveTemplate(push, [])
    expect(empty.missing).toHaveLength(5)
    expect(templateRoutineInput(push, empty)).toBeNull()
  })
})
