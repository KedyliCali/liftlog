import { parseDate, toIsoDate } from './format'

// Pure helpers for the nutrition page, kept apart from React so they can be unit-tested.

/** Share of the goal reached, capped at 1 for drawing; `over` lets the ring change color past the goal. */
export function goalProgress(value: number, goal: number | null): { fraction: number; over: boolean } {
  if (goal == null || goal <= 0) return { fraction: 0, over: false }
  return { fraction: Math.min(1, value / goal), over: value > goal }
}

/**
 * Common guidance for people lifting to build muscle is 1.6–2.2 g of protein per kg; 1.8 sits in the
 * middle. Rounded to 5 g so the suggestion looks like a target, not a calculation.
 */
export function suggestedProteinGoal(bodyWeightKg: number | null): number | null {
  if (bodyWeightKg == null) return null
  return Math.round((bodyWeightKg * 1.8) / 5) * 5
}

/** The day `days` away from `iso`, in local calendar terms (DST-safe because it never adds milliseconds). */
export function shiftDay(iso: string, days: number): string {
  const date = parseDate(iso)
  date.setDate(date.getDate() + days)
  return toIsoDate(date)
}

export function todayIso(now: Date = new Date()): string {
  return toIsoDate(now)
}

export interface FoodForm {
  name: string
  calories: string
  protein: string
}

export type FoodField = 'name' | 'calories' | 'protein'

/** Same limits as the backend's FoodEntryRequest. An empty protein field means 0 g. */
export function validateFood(form: FoodForm): { input: { name: string; caloriesKcal: number; proteinG: number } | null; errors: Set<FoodField> } {
  const errors = new Set<FoodField>()
  const name = form.name.trim()
  if (name === '' || name.length > 100) errors.add('name')
  const calories = Number(form.calories)
  if (form.calories === '' || !Number.isInteger(calories) || calories < 0 || calories > 10000) errors.add('calories')
  const protein = form.protein === '' ? 0 : Number(form.protein)
  if (Number.isNaN(protein) || protein < 0 || protein > 1000) errors.add('protein')
  const input = errors.size === 0 ? { name, caloriesKcal: calories, proteinG: Math.round(protein * 10) / 10 } : null
  return { input, errors }
}

export type GoalField = 'calories' | 'protein'

/** Empty clears a goal; otherwise the backend's ranges apply. */
export function validateGoals(calories: string, protein: string) {
  const errors = new Set<GoalField>()
  const parse = (text: string, min: number, max: number, field: GoalField) => {
    if (text.trim() === '') return null
    const value = Number(text)
    if (!Number.isInteger(value) || value < min || value > max) {
      errors.add(field)
      return null
    }
    return value
  }
  const calorieGoalKcal = parse(calories, 500, 10000, 'calories')
  const proteinGoalG = parse(protein, 10, 500, 'protein')
  return { calorieGoalKcal, proteinGoalG, errors }
}
