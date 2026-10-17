import { describe, expect, it } from 'vitest'
import { bmi, sanitizeDecimal, validateProfile, validateSet } from './validation'

describe('sanitizeDecimal', () => {
  it('accepts a comma and keeps a single separator', () => {
    expect(sanitizeDecimal('142,5')).toBe('142.5')
    expect(sanitizeDecimal('1.2.5')).toBe('1.25')
    expect(sanitizeDecimal('1a0b0')).toBe('100')
  })
})

describe('validateSet', () => {
  it('builds the request for valid input, RPE optional', () => {
    const result = validateSet({ exerciseId: 1, reps: '5', weight: '142.5', rpe: '' })
    expect(result.input).toEqual({ exerciseId: 1, reps: 5, weightKg: 142.5, rpe: null })
    expect(result.errors.size).toBe(0)
  })

  it('accepts boundary values', () => {
    expect(validateSet({ exerciseId: 1, reps: '1', weight: '0', rpe: '10' }).input).not.toBeNull()
  })

  it('reports every invalid field and builds no request', () => {
    const result = validateSet({ exerciseId: null, reps: '0', weight: '', rpe: '11' })
    expect(result.input).toBeNull()
    expect([...result.errors].sort()).toEqual(['exercise', 'reps', 'rpe', 'weight'])
  })
})

describe('validateProfile', () => {
  it('treats empty fields as "clear the value"', () => {
    expect(validateProfile('', ' ')).toEqual({ heightCm: null, bodyWeightKg: null, errors: new Set() })
  })

  it('rejects values outside the backend limits', () => {
    expect([...validateProfile('20', '600').errors].sort()).toEqual(['height', 'weight'])
  })
})

describe('bmi', () => {
  it('needs both height and weight', () => {
    expect(bmi(180, null)).toBeNull()
    expect(bmi(180, 81)).toBeCloseTo(25, 5)
  })
})
