import { describe, expect, it } from 'vitest'
import type { GroupTrainingMember } from '../api/types'
import { bestOf, columnLeaders, splitTraining } from './groupTraining'

const shared = (userId: number, workouts: number, flags: Partial<GroupTrainingMember> = {}): GroupTrainingMember => ({
  userId,
  displayName: `M${userId}`,
  you: false,
  shares: true,
  workouts,
  totalVolumeKg: 1000,
  sets: 10,
  weekStreak: 2,
  best: [
    { exercise: 'Bench Press', estimated1rmKg: 100 },
    { exercise: 'Squat', estimated1rmKg: null },
    { exercise: 'Deadlift', estimated1rmKg: 180 },
    { exercise: 'Overhead Press', estimated1rmKg: null },
  ],
  ...flags,
})

describe('splitTraining', () => {
  it('turns members with numbers into rows and keeps the rest as names only', () => {
    const { rows, hidden } = splitTraining([
      shared(1, 3, { you: true, shares: false }),
      { displayName: 'Ece', shares: false },
      shared(2, 1),
      { shares: false },
    ])

    expect(rows.map((row) => row.userId)).toEqual([1, 2])
    expect(rows[0]).toMatchObject({ you: true, shares: false, workouts: 3, totalVolumeKg: 1000, sets: 10, weekStreak: 2 })
    expect(rows[1].you).toBe(false)
    expect(hidden).toEqual([{ displayName: 'Ece', shares: false }, { shares: false }])
  })

  it('reads a missing name as null', () => {
    expect(splitTraining([shared(1, 0, { displayName: undefined })]).rows[0].displayName).toBeNull()
  })
})

describe('bestOf', () => {
  it('finds the lift and gives null for a lift never logged', () => {
    const [row] = splitTraining([shared(1, 1)]).rows
    expect(bestOf(row, 'Deadlift')).toBe(180)
    expect(bestOf(row, 'Squat')).toBeNull()
  })
})

describe('columnLeaders', () => {
  const rows = [{ v: 3 }, { v: 5 }, { v: 5 }, { v: null }]

  it('marks every row holding the top value', () => {
    expect([...columnLeaders(rows, (row) => row.v)]).toEqual([rows[1], rows[2]])
  })

  it('has no leader when nobody has anything', () => {
    expect(columnLeaders([{ v: 0 }, { v: null }], (row) => row.v).size).toBe(0)
  })
})
