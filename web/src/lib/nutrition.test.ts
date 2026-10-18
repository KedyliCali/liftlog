import { describe, expect, it } from 'vitest'
import { goalProgress, shiftDay, suggestedProteinGoal, todayIso, validateFood, validateGoals } from './nutrition'

describe('goalProgress', () => {
  it('caps the ring at a full circle and flags going over', () => {
    expect(goalProgress(1300, 2600)).toEqual({ fraction: 0.5, over: false })
    expect(goalProgress(3000, 2600)).toEqual({ fraction: 1, over: true })
  })

  it('draws nothing without a goal', () => {
    expect(goalProgress(500, null)).toEqual({ fraction: 0, over: false })
  })
})

describe('suggestedProteinGoal', () => {
  it('suggests about 1.8 g per kg, rounded to 5 g', () => {
    expect(suggestedProteinGoal(82.4)).toBe(150)
    expect(suggestedProteinGoal(null)).toBeNull()
  })
})

describe('dates', () => {
  it('moves across month and year boundaries by calendar day', () => {
    expect(shiftDay('2026-10-31', 1)).toBe('2026-11-01')
    expect(shiftDay('2026-01-01', -1)).toBe('2025-12-31')
  })

  it('uses the local calendar date, not UTC', () => {
    expect(todayIso(new Date(2026, 9, 18, 23, 30))).toBe('2026-10-18')
  })
})

describe('validateFood', () => {
  it('trims the name, treats empty protein as 0 and rounds to one decimal', () => {
    expect(validateFood({ name: ' Oats ', calories: '380', protein: '' }).input).toEqual({ name: 'Oats', caloriesKcal: 380, proteinG: 0 })
    expect(validateFood({ name: 'Egg', calories: '78', protein: '6.27' }).input?.proteinG).toBe(6.3)
  })

  it('rejects a missing name and impossible numbers', () => {
    const { input, errors } = validateFood({ name: ' ', calories: '12.5', protein: '-1' })
    expect(input).toBeNull()
    expect([...errors].sort()).toEqual(['calories', 'name', 'protein'])
  })
})

describe('validateGoals', () => {
  it('accepts empty fields as cleared goals and checks ranges', () => {
    expect(validateGoals('', '')).toMatchObject({ calorieGoalKcal: null, proteinGoalG: null })
    expect(validateGoals('2600', '160')).toMatchObject({ calorieGoalKcal: 2600, proteinGoalG: 160 })
    expect([...validateGoals('100', '9000').errors].sort()).toEqual(['calories', 'protein'])
  })
})
