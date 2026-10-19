import { describe, expect, it } from 'vitest'
import type { PlanItem, RoutineExercise, WorkoutSet } from '../api/types'
import { formatKg } from './format'
import { t } from './i18n'
import {
  buildWorkoutPlan,
  cascadeEdit,
  clampRoutineSets,
  clampTargetReps,
  defaultRowValues,
  moveItem,
  routineSummary,
  stepReps,
  stepWeight,
  suggestionTag,
  validateRoutine,
} from './routineLogic'

const item = (exerciseId: number, patch: Partial<PlanItem> = {}): PlanItem => ({
  exerciseId,
  name: `Exercise ${exerciseId}`,
  bodyweight: false,
  plannedSets: 3,
  loggedSets: 0,
  targetReps: 8,
  suggestedWeightKg: 82.5,
  lastWeightKg: 80,
  lastReps: [8, 8, 8],
  suggestion: 'INCREASE',
  increaseKg: 2.5,
  ...patch,
})

let nextSetId = 1
const set = (exerciseId: number): WorkoutSet => ({
  id: nextSetId++,
  exerciseId,
  exerciseName: `Exercise ${exerciseId}`,
  setOrder: nextSetId,
  reps: 8,
  weightKg: 80,
  rpe: null,
  newPersonalRecord: false,
  bodyweight: false,
})

const routineExercise = (exerciseId: number, name: string, sets = 3, targetReps = 8): RoutineExercise => ({
  exerciseId,
  name,
  bodyweight: false,
  sets,
  targetReps,
})

describe('defaultRowValues', () => {
  it('prefers the suggested weight, then last time, then empty', () => {
    expect(defaultRowValues(item(1))).toEqual({ weight: '82.5', reps: '8' })
    expect(defaultRowValues(item(1, { suggestedWeightKg: null }))).toEqual({ weight: '80', reps: '8' })
    expect(defaultRowValues(item(1, { suggestedWeightKg: null, lastWeightKg: null }))).toEqual({ weight: '', reps: '8' })
  })

  it('starts bodyweight exercises at no added load and uses the suggested reps', () => {
    const pullUps = item(2, { bodyweight: true, suggestedWeightKg: null, lastWeightKg: null, suggestedReps: 9 })
    expect(defaultRowValues(pullUps)).toEqual({ weight: '0', reps: '9' })
  })
})

describe('buildWorkoutPlan', () => {
  it('shows only the sets that are not logged yet, keeping their position in the plan', () => {
    const { exercises } = buildWorkoutPlan([item(1), item(2)], [set(1)], {})
    expect(exercises[0].logged).toHaveLength(1)
    expect(exercises[0].rows.map((r) => r.setIndex)).toEqual([1, 2])
    expect(exercises[1].rows).toHaveLength(3)
  })

  it('has no rows left once every planned set is logged, and keeps extra sets on the card', () => {
    const { exercises } = buildWorkoutPlan([item(1, { plannedSets: 2 })], [set(1), set(1), set(1)], {})
    expect(exercises[0].rows).toEqual([])
    expect(exercises[0].logged).toHaveLength(3)
  })

  it('lists exercises outside the routine as extras', () => {
    const { extras } = buildWorkoutPlan([item(1)], [set(1), set(7)], {})
    expect(extras.map((g) => g.exerciseId)).toEqual([7])
  })

  it('lets the first of two entries for the same exercise take its sets first', () => {
    const { exercises } = buildWorkoutPlan([item(1, { plannedSets: 2 }), item(1, { plannedSets: 2 })], [set(1), set(1), set(1)], {})
    expect(exercises[0].logged).toHaveLength(2)
    expect(exercises[0].rows).toEqual([])
    expect(exercises[1].logged).toHaveLength(1)
    expect(exercises[1].rows.map((r) => r.key)).toEqual(['1:1'])
  })

  it('uses the user’s edits over the prefill', () => {
    const { exercises } = buildWorkoutPlan([item(1)], [], { '0:1': { weight: '90', reps: '5' } })
    expect(exercises[0].rows.map((r) => r.weight)).toEqual(['82.5', '90', '82.5'])
  })
})

describe('cascadeEdit', () => {
  it('changes the edited row and the ones after it, not the ones before', () => {
    const { exercises } = buildWorkoutPlan([item(1)], [], {})
    const edits = cascadeEdit({}, exercises[0].rows, 1, { weight: '85' })
    expect(edits).toEqual({ '0:1': { weight: '85', reps: '8' }, '0:2': { weight: '85', reps: '8' } })
  })
})

describe('steppers', () => {
  it('steps the weight by 2.5 kg and never below 0', () => {
    expect(stepWeight('80', 2.5)).toBe('82.5')
    expect(stepWeight('1', -2.5)).toBe('0')
    expect(stepWeight('', 2.5)).toBe('2.5')
    expect(stepWeight('', -2.5)).toBe('0')
  })

  it('rounds away floating point noise', () => {
    expect(stepWeight('0.1', 0.2)).toBe('0.3')
  })

  it('keeps reps at 1 or more', () => {
    expect(stepReps('8', 1)).toBe('9')
    expect(stepReps('1', -1)).toBe('1')
    expect(stepReps('', 1)).toBe('1')
  })
})

describe('suggestionTag', () => {
  it('names the increase in kilograms', () => {
    expect(suggestionTag(item(1))).toEqual({ kind: 'increase', text: t.suggestIncreaseKg(formatKg(2.5)) })
  })

  it('suggests more reps, not weight, for bodyweight exercises', () => {
    const tag = suggestionTag(item(1, { bodyweight: true, increaseKg: null, suggestedReps: 9 }))
    expect(tag).toEqual({ kind: 'increase', text: t.suggestMoreReps(1) })
    expect(suggestionTag(item(1, { bodyweight: true, increaseKg: 2.5, suggestedReps: 10 })).text).toBe(t.suggestMoreReps(2))
  })

  it('reads same weight and first time', () => {
    expect(suggestionTag(item(1, { suggestion: 'SAME', increaseKg: null })).text).toBe(t.suggestSame)
    expect(suggestionTag(item(1, { suggestion: 'FIRST', increaseKg: null })).text).toBe(t.suggestFirst)
  })
})

describe('routines', () => {
  it('summarizes exercises as sets × reps', () => {
    expect(routineSummary([routineExercise(1, 'Bench', 3, 8), routineExercise(2, 'Row', 3, 10)])).toBe('Bench 3×8 · Row 3×10')
  })

  it('clamps sets to 1–10 and reps to 1–100', () => {
    expect(clampRoutineSets(0)).toBe(1)
    expect(clampRoutineSets(11)).toBe(10)
    expect(clampTargetReps(0)).toBe(1)
    expect(clampTargetReps(101)).toBe(100)
  })

  it('moves an exercise up or down and ignores moves past either end', () => {
    expect(moveItem(['a', 'b', 'c'], 1, -1)).toEqual(['b', 'a', 'c'])
    expect(moveItem(['a', 'b', 'c'], 1, 1)).toEqual(['a', 'c', 'b'])
    expect(moveItem(['a', 'b'], 0, -1)).toEqual(['a', 'b'])
  })

  it('requires a 1–60 character name and 1–20 exercises', () => {
    expect([...validateRoutine('  ', []).errors].sort()).toEqual(['exercises', 'name'])
    expect(validateRoutine('x'.repeat(61), [routineExercise(1, 'Bench')]).errors).toEqual(new Set(['name']))
    const many = Array.from({ length: 21 }, (_, i) => routineExercise(i, 'E'))
    expect(validateRoutine('Push', many).errors).toEqual(new Set(['exercises']))
  })

  it('rejects the same exercise twice, like the server', () => {
    expect(validateRoutine('Push', [routineExercise(1, 'Bench'), routineExercise(1, 'Bench')]).errors).toEqual(new Set(['duplicate']))
  })

  it('builds the request body with a trimmed name', () => {
    const { input } = validateRoutine(' Push ', [routineExercise(4, 'Bench', 4, 6)])
    expect(input).toEqual({ name: 'Push', exercises: [{ exerciseId: 4, sets: 4, targetReps: 6 }] })
  })
})
