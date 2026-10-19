import { describe, expect, it } from 'vitest'
import { goalProgress, macroEnergySplit, portionLabel, shiftDay, suggestedProteinGoal, todayIso, validateFood, validateGoals } from './nutrition'

describe('portionLabel', () => {
  const format = (n: number) => String(n).replace('.', ',')

  it('labels a known portion', () => {
    expect(portionLabel({ name: 'Rice (1 bowl)', grams: 180 }, format)).toBe('180 g')
    expect(portionLabel({ name: 'Rice', grams: 112.5 }, format)).toBe('112,5 g')
  })

  it('stays quiet when the name already has it or the portion is unknown', () => {
    expect(portionLabel({ name: 'Yogurt (150 g)', grams: 150 }, format)).toBeNull()
    expect(portionLabel({ name: 'Shake', grams: null }, format)).toBeNull()
    expect(portionLabel({ name: 'Shake' }, format)).toBeNull()
  })
})

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
    expect(validateFood({ name: ' Oats ', calories: '380', protein: '', carbs: '', fat: '' }).input).toEqual({ name: 'Oats', caloriesKcal: 380, proteinG: 0, carbsG: null, fatG: null })
    expect(validateFood({ name: 'Egg', calories: '78', protein: '6.27', carbs: '0.56', fat: '5' }).input).toMatchObject({ proteinG: 6.3, carbsG: 0.6, fatG: 5 })
  })

  it('rejects a missing name and impossible numbers', () => {
    const { input, errors } = validateFood({ name: ' ', calories: '12.5', protein: '-1', carbs: '2000', fat: '1' })
    expect(input).toBeNull()
    expect([...errors].sort()).toEqual(['calories', 'carbs', 'name', 'protein'])
  })
})

describe('macroEnergySplit', () => {
  it('weights fat at 9 kcal per gram', () => {
    expect(macroEnergySplit(25, 25, 0)).toEqual({ protein: 0.5, carbs: 0.5, fat: 0 })
    const split = macroEnergySplit(10, 10, 10) // 40 + 40 + 90 kcal
    expect(split.fat).toBeCloseTo(90 / 170)
    expect(macroEnergySplit(0, 0, 0)).toEqual({ protein: 0, carbs: 0, fat: 0 })
  })
})

describe('validateGoals', () => {
  it('accepts empty fields as cleared goals and checks ranges', () => {
    expect(validateGoals('', '')).toMatchObject({ calorieGoalKcal: null, proteinGoalG: null })
    expect(validateGoals('2600', '160')).toMatchObject({ calorieGoalKcal: 2600, proteinGoalG: 160 })
    expect([...validateGoals('100', '9000').errors].sort()).toEqual(['calories', 'protein'])
  })
})
