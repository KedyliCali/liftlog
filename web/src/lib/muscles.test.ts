import { describe, expect, it } from 'vitest'
import { bandFor, bandsFor, bandThresholds, foldMuscle, foldMuscleStats, formatSets, MAP_MUSCLES, SIDE_MUSCLES, musclesOfSets } from './muscles'

describe('bandThresholds', () => {
  it('uses 5 and 10 sets for a week', () => {
    expect(bandThresholds(7)).toEqual([5, 10])
  })

  it('scales 30 days by 30/7 and rounds', () => {
    // 5 × 30/7 = 21.4, 10 × 30/7 = 42.9
    expect(bandThresholds(30)).toEqual([21, 43])
  })

  it('uses 2 and 4 sets for a single workout', () => {
    expect(bandThresholds('workout')).toEqual([2, 4])
  })
})

describe('bandFor', () => {
  it.each([
    [0, 'none'],
    [0.5, 'low'],
    [4.5, 'low'],
    [5, 'mid'],
    [9.5, 'mid'],
    [10, 'high'],
    [24, 'high'],
  ] as const)('7 days: %s sets is %s', (sets, band) => {
    expect(bandFor(sets, 7)).toBe(band)
  })

  it.each([
    [20.5, 'low'],
    [21, 'mid'],
    [42.5, 'mid'],
    [43, 'high'],
  ] as const)('30 days: %s sets is %s', (sets, band) => {
    expect(bandFor(sets, 30)).toBe(band)
  })

  it.each([
    [1.5, 'low'],
    [2, 'mid'],
    [3.5, 'mid'],
    [4, 'high'],
  ] as const)('one workout: %s sets is %s', (sets, band) => {
    expect(bandFor(sets, 'workout')).toBe(band)
  })

  it('treats negative or missing numbers as not trained', () => {
    expect(bandFor(-1, 7)).toBe('none')
    expect(bandFor(Number.NaN, 7)).toBe('none')
  })
})

describe('folding', () => {
  it('maps adductors to quads and abductors to glutes', () => {
    expect(foldMuscle('ADDUCTORS')).toBe('QUADS')
    expect(foldMuscle('ABDUCTORS')).toBe('GLUTES')
    expect(foldMuscle('CHEST')).toBe('CHEST')
    expect(foldMuscle('NECK')).toBeNull()
  })

  it('adds folded sets and merges exercises by name, busiest first', () => {
    const folded = foldMuscleStats([
      { muscle: 'QUADS', sets: 8, exercises: [{ name: 'Squat', sets: 5 }, { name: 'Leg Press', sets: 3 }] },
      { muscle: 'ADDUCTORS', sets: 2.5, exercises: [{ name: 'Squat', sets: 2.5 }] },
      { muscle: 'NECK', sets: 3, exercises: [{ name: 'Neck curl', sets: 3 }] },
    ])
    expect([...folded.keys()]).toEqual(['QUADS'])
    expect(folded.get('QUADS')).toEqual({
      muscle: 'QUADS',
      sets: 10.5,
      exercises: [
        { name: 'Squat', sets: 7.5 },
        { name: 'Leg Press', sets: 3 },
      ],
    })
  })

  it('works without exercises (single workout) and keeps one decimal exact', () => {
    const folded = foldMuscleStats([
      { muscle: 'GLUTES', sets: 0.1 },
      { muscle: 'ABDUCTORS', sets: 0.2 },
    ])
    expect(folded.get('GLUTES')?.sets).toBe(0.3)
    expect(bandsFor(folded, 'workout')).toEqual({ GLUTES: 'low' })
  })
})

describe('sides', () => {
  it('shows every map muscle on at least one side', () => {
    const shown = new Set([...SIDE_MUSCLES.front, ...SIDE_MUSCLES.back])
    expect([...shown].sort()).toEqual([...MAP_MUSCLES].sort())
  })
})

it('formats sets with one decimal at most', () => {
  expect(formatSets(12, 'en')).toBe('12')
  expect(formatSets(2.5, 'tr')).toBe('2,5')
  expect(formatSets(1.25, 'en')).toBe('1.3')
})

describe('musclesOfSets', () => {
  const exercises = [
    { id: 1, muscles: [{ muscle: 'CHEST', role: 'PRIMARY' as const }, { muscle: 'TRICEPS', role: 'SECONDARY' as const }] },
    // QUADS primary and ADDUCTORS secondary land on one region: the primary role wins, nothing counts twice
    { id: 2, muscles: [{ muscle: 'QUADS', role: 'PRIMARY' as const }, { muscle: 'ADDUCTORS', role: 'SECONDARY' as const }] },
  ]

  it('counts 1 per primary and 0.5 per secondary muscle for every set', () => {
    const sets = [{ exerciseId: 1 }, { exerciseId: 1 }, { exerciseId: 1 }, { exerciseId: 2 }, { exerciseId: 99 }]
    expect(musclesOfSets(sets, exercises)).toEqual([
      { muscle: 'CHEST', sets: 3 },
      { muscle: 'TRICEPS', sets: 1.5 },
      { muscle: 'QUADS', sets: 1 },
    ])
  })

  it('uses the single-workout bands for one day', () => {
    expect(bandFor(1, 1)).toBe('low')
    expect(bandFor(2, 1)).toBe('mid')
    expect(bandFor(4, 1)).toBe('high')
  })
})

