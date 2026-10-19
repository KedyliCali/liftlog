import { useEffect } from 'react'
import { useNavigate } from 'react-router'
import { vibrate } from '../lib/feedback'
import { setNavDirection } from '../lib/navDirection'
import { swipeTarget, TAB_PATHS, tabIndexOf } from '../lib/tabSwipe'

/** Places that own horizontal gestures themselves: swipe-to-delete rows, scrolling chip rows, charts, inputs, sheets. */
const OWNS_HORIZONTAL_GESTURE = '.swipe, .chip-row, .recharts-wrapper, .data-table-scroll, input, textarea, select, .sheet-backdrop, .menu'

/**
 * True when the finger landed in something that scrolls sideways (a strip of routine chips or template
 * cards, a wide table): there a left/right drag scrolls it, so it must not also switch tabs. Checked by
 * computed style rather than a list of class names, so new strips are covered without remembering this.
 */
export function insideHorizontalScroller(target: Element | null): boolean {
  for (let node = target; node && node !== document.body; node = node.parentElement) {
    if (node.scrollWidth > node.clientWidth + 1) {
      const overflowX = getComputedStyle(node).overflowX
      if (overflowX === 'auto' || overflowX === 'scroll') return true
    }
  }
  return false
}

/** Left/right swipe anywhere on a tab page opens the neighbouring tab. */
export function useTabSwipe() {
  const navigate = useNavigate()

  useEffect(() => {
    let start: { x: number; y: number; time: number } | null = null

    const onStart = (event: TouchEvent) => {
      const target = event.target as Element | null
      const ignored = event.touches.length > 1 || target?.closest(OWNS_HORIZONTAL_GESTURE) || insideHorizontalScroller(target)
      start = ignored ? null : { x: event.touches[0].clientX, y: event.touches[0].clientY, time: event.timeStamp }
    }

    const onEnd = (event: TouchEvent) => {
      if (!start) return
      const touch = event.changedTouches[0]
      const gesture = { dx: touch.clientX - start.x, dy: touch.clientY - start.y, durationMs: event.timeStamp - start.time }
      start = null
      // Read from the URL, not React state: during a view transition the rendered location can lag one step
      const current = tabIndexOf(window.location.pathname)
      if (current === null) return
      const target = swipeTarget(gesture, current)
      if (target === null) return
      vibrate(8)
      setNavDirection(target > current ? 'forward' : 'back')
      navigate(TAB_PATHS[target], { viewTransition: true })
    }

    // Passive: the page keeps scrolling normally; the swipe is only judged once the finger lifts
    window.addEventListener('touchstart', onStart, { passive: true })
    window.addEventListener('touchend', onEnd, { passive: true })
    return () => {
      window.removeEventListener('touchstart', onStart)
      window.removeEventListener('touchend', onEnd)
    }
  }, [navigate])
}
