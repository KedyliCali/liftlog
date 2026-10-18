import { describe, expect, it } from 'vitest'
import { formatLoad, formatRecordSet } from './format'
import { t } from './i18n'

describe('formatLoad', () => {
  it('shows plain kilograms for regular lifts', () => {
    expect(formatLoad(100, false)).toBe(t.kg('100'))
  })

  it('reads bodyweight sets as bodyweight plus the extra load', () => {
    expect(formatLoad(0, true)).toBe(t.bodyweightOnly)
    expect(formatLoad(12.5, true)).toBe(t.bodyweightPlus('12.5'))
  })
})

describe('formatRecordSet', () => {
  it('uses the added load for bodyweight records, not the total', () => {
    const record = { heaviestWeightKg: 90, heaviestAddedKg: 10, heaviestReps: 5, bodyweight: true }
    expect(formatRecordSet(record)).toBe(`${t.bodyweightPlus('10')} × 5`)
    expect(formatRecordSet({ ...record, bodyweight: false })).toBe(t.weightTimesReps('90', 5))
  })
})
