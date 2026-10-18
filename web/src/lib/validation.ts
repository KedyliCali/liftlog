import type { SetInput } from '../api/types'

// Same limits as the backend's SetRequest and UpdateProfileRequest, so bad input never reaches the network.

/** Accepts both "." and "," as the decimal separator and keeps at most one of them. */
export function sanitizeDecimal(value: string, maxLength = 7): string {
  const normalized = value.replace(/,/g, '.').replace(/[^\d.]/g, '')
  const firstDot = normalized.indexOf('.')
  const single = firstDot < 0 ? normalized : normalized.slice(0, firstDot + 1) + normalized.slice(firstDot + 1).replace(/\./g, '')
  return single.slice(0, maxLength)
}

export type SetField = 'exercise' | 'reps' | 'weight' | 'rpe'

export interface SetForm {
  exerciseId: number | null
  reps: string
  weight: string
  rpe: string
}

export function validateSet(form: SetForm): { input: SetInput | null; errors: Set<SetField> } {
  const errors = new Set<SetField>()
  const reps = Number(form.reps)
  if (form.reps === '' || !Number.isInteger(reps) || reps < 1 || reps > 1000) errors.add('reps')
  const weight = Number(form.weight)
  if (form.weight === '' || Number.isNaN(weight) || weight < 0 || weight > 9999.99) errors.add('weight')
  const rpe = form.rpe === '' ? null : Number(form.rpe)
  if (rpe !== null && (Number.isNaN(rpe) || rpe < 1 || rpe > 10)) errors.add('rpe')
  if (form.exerciseId === null) errors.add('exercise')

  const input = errors.size === 0 && form.exerciseId !== null ? { exerciseId: form.exerciseId, reps, weightKg: weight, rpe } : null
  return { input, errors }
}

export const MAX_SETS_AT_ONCE = 10

/** Stepper value for "add N identical sets", kept within 1…MAX_SETS_AT_ONCE. */
export function clampSetCount(count: number): number {
  return Math.min(MAX_SETS_AT_ONCE, Math.max(1, Math.round(count)))
}

export type ProfileField = 'height' | 'weight'

/** Empty fields are allowed and clear the value. */
export function validateProfile(height: string, weight: string) {
  const errors = new Set<ProfileField>()
  const parse = (text: string, min: number, max: number, field: ProfileField) => {
    if (text.trim() === '') return null
    const value = Number(text)
    if (Number.isNaN(value) || value < min || value > max) {
      errors.add(field)
      return null
    }
    return value
  }
  const heightCm = parse(height, 50, 272, 'height')
  const bodyWeightKg = parse(weight, 20, 500, 'weight')
  return { heightCm, bodyWeightKg, errors }
}

export function bmi(heightCm: number | null, weightKg: number | null): number | null {
  if (heightCm == null || weightKg == null) return null
  const meters = heightCm / 100
  return weightKg / (meters * meters)
}
