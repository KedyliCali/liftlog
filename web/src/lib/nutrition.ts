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
  carbs: string
  fat: string
}

export type FoodField = 'name' | 'calories' | 'protein' | 'carbs' | 'fat'

export interface ValidFood {
  name: string
  caloriesKcal: number
  proteinG: number
  carbsG: number | null
  fatG: number | null
}

/** Grams to one decimal, 0–1000; empty is `empty` (0 for protein, unknown for carbs and fat). */
function parseGrams(text: string, empty: number | null): number | null | undefined {
  if (text === '') return empty
  const value = Number(text)
  return Number.isNaN(value) || value < 0 || value > 1000 ? undefined : Math.round(value * 10) / 10
}

/**
 * Same limits as the backend's FoodEntryRequest. Empty protein means 0 g; empty carbs or fat mean
 * "not known" rather than zero, so the day total does not pretend to be exact.
 */
export function validateFood(form: FoodForm): { input: ValidFood | null; errors: Set<FoodField> } {
  const errors = new Set<FoodField>()
  const name = form.name.trim()
  if (name === '' || name.length > 100) errors.add('name')
  const calories = Number(form.calories)
  if (form.calories === '' || !Number.isInteger(calories) || calories < 0 || calories > 10000) errors.add('calories')
  const protein = parseGrams(form.protein, 0)
  if (protein === undefined) errors.add('protein')
  const carbs = parseGrams(form.carbs, null)
  if (carbs === undefined) errors.add('carbs')
  const fat = parseGrams(form.fat, null)
  if (fat === undefined) errors.add('fat')
  const input = errors.size === 0 ? { name, caloriesKcal: calories, proteinG: protein ?? 0, carbsG: carbs ?? null, fatG: fat ?? null } : null
  return { input, errors }
}

/** Share of the day's calories from each macro (4 / 4 / 9 kcal per gram); all zero before anything is logged. */
export function macroEnergySplit(proteinG: number, carbsG: number, fatG: number): { protein: number; carbs: number; fat: number } {
  const protein = proteinG * 4
  const carbs = carbsG * 4
  const fat = fatG * 9
  const total = protein + carbs + fat
  if (total === 0) return { protein: 0, carbs: 0, fat: 0 }
  return { protein: protein / total, carbs: carbs / total, fat: fat / total }
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
