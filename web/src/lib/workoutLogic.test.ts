import { describe, expect, it } from 'vitest'
import type { PersonalRecord, WorkoutSet, WorkoutSummary } from '../api/types'
import { chooseExercise, formatClock, groupByExercise, initials, pickTemplates, yDomain } from './workoutLogic'

const set = (id: number, exerciseId: number, name: string): WorkoutSet => ({
  id,
  exerciseId,
  exerciseName: name,
  setOrder: id,
  reps: 5,
  weightKg: 100,
  rpe: null,
  newPersonalRecord: false,
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
