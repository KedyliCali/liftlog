import { describe, expect, it } from 'vitest'
import { countUpValue, easeOutCubic } from './countUp'

describe('count-up easing', () => {
  it('starts at the old value, ends exactly on the new one and never overshoots', () => {
    expect(countUpValue(100, 200, 0, 700)).toBe(100)
    expect(countUpValue(100, 200, 700, 700)).toBe(200)
    expect(countUpValue(100, 200, 5000, 700)).toBe(200)
    const halfway = countUpValue(0, 100, 350, 700)
    expect(halfway).toBeGreaterThan(50) // ease-out covers most of the distance early
    expect(halfway).toBeLessThan(100)
  })

  it('clamps progress and handles a zero duration', () => {
    expect(easeOutCubic(-1)).toBe(0)
    expect(easeOutCubic(2)).toBe(1)
    expect(countUpValue(5, 9, 0, 0)).toBe(9)
  })
})
