import { describe, expect, it } from 'vitest'
import { swipeTarget, tabIndexOf } from './tabSwipe'

describe('tabIndexOf', () => {
  it('only matches the tab pages themselves', () => {
    expect(tabIndexOf('/')).toBe(0)
    expect(tabIndexOf('/nutrition')).toBe(1)
    expect(tabIndexOf('/progress')).toBe(2)
    expect(tabIndexOf('/game')).toBe(3)
    expect(tabIndexOf('/profile')).toBe(4)
    expect(tabIndexOf('/workouts/3')).toBeNull()
  })
})

describe('swipeTarget', () => {
  it('moves to the next tab on a left swipe and back on a right swipe', () => {
    expect(swipeTarget({ dx: -120, dy: 10, durationMs: 300 }, 0)).toBe(1)
    expect(swipeTarget({ dx: 120, dy: 10, durationMs: 300 }, 1)).toBe(0)
  })

  it('stops at the first and last tab', () => {
    expect(swipeTarget({ dx: 120, dy: 0, durationMs: 300 }, 0)).toBeNull()
    expect(swipeTarget({ dx: -120, dy: 0, durationMs: 300 }, 4)).toBeNull()
  })

  it('ignores vertical scrolls and short slow drags', () => {
    expect(swipeTarget({ dx: -100, dy: 80, durationMs: 300 }, 0)).toBeNull()
    expect(swipeTarget({ dx: -40, dy: 0, durationMs: 500 }, 0)).toBeNull()
  })

  it('accepts a short but fast flick', () => {
    expect(swipeTarget({ dx: -40, dy: 5, durationMs: 60 }, 0)).toBe(1)
  })
})
