import { afterEach, describe, expect, it } from 'vitest'
import { insideHorizontalScroller } from './useTabSwipe'

/** jsdom does no layout, so the widths a real browser would measure are set by hand. */
function box(overflowX: string, scrollWidth: number, clientWidth: number): HTMLElement {
  const element = document.createElement('div')
  element.style.overflowX = overflowX
  Object.defineProperty(element, 'scrollWidth', { value: scrollWidth })
  Object.defineProperty(element, 'clientWidth', { value: clientWidth })
  return element
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('insideHorizontalScroller', () => {
  it('owns the gesture inside a strip that scrolls sideways', () => {
    const strip = box('auto', 900, 340)
    const card = document.createElement('button')
    strip.appendChild(card)
    document.body.appendChild(strip)

    expect(insideHorizontalScroller(card)).toBe(true)
  })

  it('leaves the gesture to tab swiping when nothing can scroll sideways', () => {
    const fits = box('auto', 340, 340)
    const clipped = box('hidden', 900, 340)
    const inFits = document.createElement('span')
    const inClipped = document.createElement('span')
    fits.appendChild(inFits)
    clipped.appendChild(inClipped)
    document.body.append(fits, clipped)

    expect(insideHorizontalScroller(inFits)).toBe(false)
    expect(insideHorizontalScroller(inClipped)).toBe(false)
    expect(insideHorizontalScroller(null)).toBe(false)
  })
})
