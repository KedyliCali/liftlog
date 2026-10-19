import { describe, expect, it } from 'vitest'
import type { Exercise, PersonalRecord, WorkoutDetail, WorkoutSet, WorkoutSummary } from '../api/types'
import { chooseExercise, effectiveLoad, formatClock, groupByExercise, groupByMuscle, initials, matchesExerciseFilter, pickTemplates, visibleVolume, weightAfterPick, yDomain } from './workoutLogic'

const set = (id: number, exerciseId: number, name: string): WorkoutSet => ({
  id,
  exerciseId,
  exerciseName: name,
  setOrder: id,
  reps: 5,
  weightKg: 100,
  rpe: null,
  newPersonalRecord: false,
  bodyweight: false,
})

const workout = (id: number, notes: string | null, setCount = 3): WorkoutSummary => ({
  id,
  workoutDate: '2026-10-17',
  notes,
  setCount,
  exerciseCount: 1,
  totalVolumeKg: 0,
})

const record = (exerciseId: number, totalSets: number) => ({ exerciseId, totalSets }) as PersonalRecord

describe('groupByExercise', () => {
  it('keeps the order in which exercises were first performed', () => {
    const groups = groupByExercise([set(1, 9, 'Bench'), set(2, 1, 'Squat'), set(3, 9, 'Bench')])
    expect(groups.map((g) => g.exerciseName)).toEqual(['Bench', 'Squat'])
    expect(groups[0].sets.map((s) => s.id)).toEqual([1, 3])
  })
})

describe('pickTemplates', () => {
  it('skips empty workouts and repeats of the same name (newest kept)', () => {
    const picked = pickTemplates([workout(5, 'Push day'), workout(4, 'Empty', 0), workout(3, 'push day '), workout(2, null), workout(1, null)])
    expect(picked.map((w) => w.id)).toEqual([5, 2, 1])
  })
})

describe('chooseExercise', () => {
  it('keeps the current choice while it still has records', () => {
    expect(chooseExercise([record(1, 5), record(9, 20)], 1)).toBe(1)
  })

  it('falls back to the most trained exercise', () => {
    expect(chooseExercise([record(1, 5), record(9, 20)], 42)).toBe(9)
    expect(chooseExercise([], null)).toBeNull()
  })
})

describe('yDomain', () => {
  it('rounds to 5 kg steps around the data instead of starting at 0', () => {
    expect(yDomain([81.7, 93.5])).toEqual([75, 100])
  })
})

describe('small formatters', () => {
  it('builds initials and clock strings', () => {
    expect(initials('Behlül Akdağ')).toBe('BA')
    expect(initials('')).toBe('?')
    expect(formatClock(90)).toBe('1:30')
    expect(formatClock(5)).toBe('0:05')
  })
})

describe('bodyweight load', () => {
  const pullUp = { ...set(1, 7, 'Pull-up'), reps: 8, weightKg: 0, bodyweight: true }
  const weightedPullUp = { ...set(2, 7, 'Pull-up'), reps: 5, weightKg: 10, bodyweight: true }
  const squat = { ...set(3, 1, 'Squat'), reps: 5, weightKg: 100 }

  it('adds body weight only to bodyweight exercises', () => {
    expect(effectiveLoad(weightedPullUp, 80)).toBe(90)
    expect(effectiveLoad(pullUp, null)).toBe(0)
    expect(effectiveLoad(squat, 80)).toBe(100)
  })

  it('takes hidden sets out of the server volume', () => {
    const workout: WorkoutDetail = { id: 1, workoutDate: '2026-10-18', notes: null, totalVolumeKg: 1590, sets: [pullUp, weightedPullUp, squat] }
    expect(visibleVolume(workout, new Set(), 80)).toBe(1590)
    expect(visibleVolume(workout, new Set([2]), 80)).toBe(1140)
    expect(visibleVolume(workout, new Set([1, 2, 3]), 80)).toBe(0)
  })
})

describe('groupByMuscle', () => {
  const exercise = (id: number, muscleGroup: string | null): Exercise => ({ id, name: `E${id}`, muscleGroup, custom: false, bodyweight: false })

  it('orders sections by training order, custom groups after, ungrouped last', () => {
    const sections = groupByMuscle([exercise(1, null), exercise(2, 'Legs'), exercise(3, 'Grip'), exercise(4, 'Chest'), exercise(5, 'Legs')])
    expect(sections.map((s) => s.muscleGroup)).toEqual(['Chest', 'Legs', 'Grip', 'Other'])
    expect(sections[1].exercises.map((e) => e.id)).toEqual([2, 5])
  })
})

describe('weightAfterPick', () => {
  it('keeps the weight within the same kind of exercise', () => {
    expect(weightAfterPick('100', false, { bodyweight: false })).toBe('100')
    expect(weightAfterPick('10', true, { bodyweight: true })).toBe('10')
  })

  it('resets when switching kinds', () => {
    expect(weightAfterPick('100', false, { bodyweight: true })).toBe('0')
    expect(weightAfterPick('0', true, { bodyweight: false })).toBe('')
    expect(weightAfterPick('', false, { bodyweight: true })).toBe('0')
  })
})

describe('matchesExerciseFilter', () => {
  it('puts machines and cables under machines, and keeps everything under all', () => {
    expect(matchesExerciseFilter({ bodyweight: false, equipment: 'MACHINE' }, 'machine')).toBe(true)
    expect(matchesExerciseFilter({ bodyweight: false, equipment: 'CABLE' }, 'machine')).toBe(true)
    expect(matchesExerciseFilter({ bodyweight: false, equipment: 'BARBELL' }, 'machine')).toBe(false)
    expect(matchesExerciseFilter({ bodyweight: false }, 'machine')).toBe(false)
    expect(matchesExerciseFilter({ bodyweight: true, equipment: 'BODYWEIGHT' }, 'bodyweight')).toBe(true)
    expect(matchesExerciseFilter({ bodyweight: false, equipment: null }, 'all')).toBe(true)
  })
})

