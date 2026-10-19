import { describe, expect, it } from 'vitest'
import type { WorkoutRecap } from '../api/types'
import { formatSignedKg, recapShareText, volumeDelta } from './recap'

const recap: WorkoutRecap = {
  workoutId: 7,
  workoutDate: '2026-10-19',
  totalVolumeKg: 5240,
  sets: 18,
  exercises: 4,
  personalRecords: [
    { exerciseName: 'Squat', weightKg: 140, reps: 3, kind: 'WEIGHT' },
    { exerciseName: 'Bench Press', weightKg: 100, reps: 8, kind: 'ESTIMATED_1RM' },
  ],
  previousVolumeKg: 4920,
  previousWorkoutId: 3,
  previousWorkoutDate: '2026-10-12',
  weekStreak: 4,
}

describe('volumeDelta', () => {
  it('is the change against the previous workout', () => {
    expect(volumeDelta(recap)).toBe(320)
    expect(volumeDelta({ totalVolumeKg: 100, previousVolumeKg: 150.5 })).toBe(-50.5)
  })

  it('is null without a previous workout', () => {
    expect(volumeDelta({ totalVolumeKg: 100, previousVolumeKg: null })).toBeNull()
  })

  it('rounds away floating point noise', () => {
    expect(volumeDelta({ totalVolumeKg: 0.3, previousVolumeKg: 0.1 + 0.2 })).toBe(0)
  })
})

describe('formatSignedKg', () => {
  it('always shows the sign, with grouping', () => {
    expect(formatSignedKg(320, 'en')).toBe('+320')
    expect(formatSignedKg(1250, 'tr')).toBe('+1.250')
    expect(formatSignedKg(-45.5, 'en')).toBe('−45.5')
    expect(formatSignedKg(0, 'en')).toBe('0')
  })
})

describe('recapShareText', () => {
  it('mentions volume, records and the streak', () => {
    expect(recapShareText(recap, 'en')).toBe(
      'Today I lifted 5,240 kg on LiftLog: 18 sets, 4 exercises. New PRs: Squat, Bench Press. 4 week streak.',
    )
  })

  it('leaves out records and streak when there are none', () => {
    expect(recapShareText({ ...recap, personalRecords: [], weekStreak: 0 }, 'en')).toBe(
      'Today I lifted 5,240 kg on LiftLog: 18 sets, 4 exercises.',
    )
  })
})
