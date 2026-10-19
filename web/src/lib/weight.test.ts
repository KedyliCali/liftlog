import { describe, expect, it } from 'vitest'
import { changeDirection, formatWeight, formatWeightChange, parseWeight, startingWeight, stepWeight, weightDomain, weightText, withMovingAverage } from './weight'

describe('withMovingAverage', () => {
  it('averages the calendar week ending on each entry', () => {
    const points = withMovingAverage([
      { date: '2026-10-01', weightKg: 82 },
      { date: '2026-10-04', weightKg: 81 },
      { date: '2026-10-07', weightKg: 80 },
      // 7 days after 10-01, so 10-01 has left the window
      { date: '2026-10-08', weightKg: 79 },
    ])
    expect(points.map((p) => p.averageKg)).toEqual([82, 81.5, 81, 80])
    expect(points[3]).toEqual({ date: '2026-10-08', weightKg: 79, averageKg: 80 })
  })

  it('does not let an old entry across a gap pull the average', () => {
    const points = withMovingAverage([
      { date: '2026-09-01', weightKg: 90 },
      { date: '2026-10-01', weightKg: 80 },
    ])
    expect(points[1].averageKg).toBe(80)
  })

  it('rounds to 2 decimals and handles an empty list', () => {
    expect(withMovingAverage([])).toEqual([])
    const points = withMovingAverage([
      { date: '2026-10-01', weightKg: 80 },
      { date: '2026-10-02', weightKg: 80.1 },
      { date: '2026-10-03', weightKg: 80.1 },
    ])
    expect(points[2].averageKg).toBe(80.07)
  })
})

describe('stepWeight', () => {
  it('moves on a 0.1 kg grid without floating point drift', () => {
    expect(stepWeight(80, 0.1)).toBe(80.1)
    expect(stepWeight(80.1, 0.1)).toBe(80.2)
    expect(stepWeight(80.25, -0.1)).toBe(80.2)
    expect(stepWeight(80, -0.5)).toBe(79.5)
  })

  it('stays within 20–400 kg', () => {
    expect(stepWeight(20, -0.5)).toBe(20)
    expect(stepWeight(399.8, 0.5)).toBe(400)
  })
})

describe('parseWeight', () => {
  it('accepts a dot or a comma and up to 2 decimals', () => {
    expect(parseWeight('80.4')).toBe(80.4)
    expect(parseWeight(' 80,45 ')).toBe(80.45)
    expect(parseWeight('80')).toBe(80)
  })

  it('rejects empty, out of range and malformed input', () => {
    for (const text of ['', 'abc', '19.9', '400.1', '80.123', '1e2', '-80', '80.']) {
      expect(parseWeight(text)).toBeNull()
    }
  })
})

describe('formatting', () => {
  it('signs a change with a real minus and the locale decimal mark', () => {
    expect(formatWeightChange(-0.8, 'tr')).toBe('−0,8 kg')
    expect(formatWeightChange(1.24, 'en')).toBe('+1.2 kg')
    expect(formatWeightChange(0.04, 'en')).toBe('±0 kg')
  })

  it('reads the direction after rounding to what is shown', () => {
    expect(changeDirection(-0.8)).toBe('down')
    expect(changeDirection(0.3)).toBe('up')
    expect(changeDirection(-0.04)).toBe('flat')
  })

  it('writes the field value without grouping', () => {
    expect(weightText(80.4, 'tr')).toBe('80,4')
    expect(weightText(80.4, 'en')).toBe('80.4')
  })
})

describe('chart helpers', () => {
  it('pads the y-range by about a kilo on whole numbers', () => {
    expect(weightDomain([80.4, 82.9, 81])).toEqual([79, 84])
  })

  it('shows weights with one decimal', () => {
    expect(formatWeight(81.18, 'tr')).toBe('81,2')
    expect(formatWeight(80, 'en')).toBe('80')
  })
})

describe('startingWeight', () => {
  const entries = [
    { date: '2026-10-18', weightKg: 81 },
    { date: '2026-10-19', weightKg: 80.6 },
  ]

  it("prefers today's entry, then the newest, then the profile", () => {
    expect(startingWeight(entries, entries[1], '2026-10-19', 85)).toBe(80.6)
    expect(startingWeight(entries.slice(0, 1), entries[0], '2026-10-19', 85)).toBe(81)
    expect(startingWeight([], null, '2026-10-19', 85)).toBe(85)
    expect(startingWeight([], null, '2026-10-19', null)).toBeNull()
  })
})
