// Swiping left/right on a tab page moves to the neighbouring tab. Kept free of DOM code so the
// thresholds can be unit-tested.

export const TAB_PATHS = ['/', '/nutrition', '/progress', '/game', '/profile'] as const

/** Tab index for a tab's own page; null on pages like a workout detail, where swiping does nothing. */
export function tabIndexOf(pathname: string): number | null {
  const index = TAB_PATHS.indexOf(pathname as (typeof TAB_PATHS)[number])
  return index >= 0 ? index : null
}

const MIN_DISTANCE_PX = 70
const MIN_FLICK_DISTANCE_PX = 35
const MIN_FLICK_SPEED_PX_PER_MS = 0.45
/** Horizontal movement must clearly dominate, or a slightly diagonal scroll would switch tabs. */
const HORIZONTAL_RATIO = 1.8

export interface SwipeGesture {
  dx: number
  dy: number
  durationMs: number
}

/** Index of the tab to open, or null when the gesture was a scroll, too short, or past the last tab. */
export function swipeTarget(gesture: SwipeGesture, currentIndex: number, tabCount: number = TAB_PATHS.length): number | null {
  const distance = Math.abs(gesture.dx)
  if (distance < HORIZONTAL_RATIO * Math.abs(gesture.dy)) return null
  const speed = distance / Math.max(gesture.durationMs, 1)
  const isSwipe = distance >= MIN_DISTANCE_PX || (distance >= MIN_FLICK_DISTANCE_PX && speed >= MIN_FLICK_SPEED_PX_PER_MS)
  if (!isSwipe) return null
  // Finger moving left reveals the tab on the right, like turning a page
  const target = currentIndex + (gesture.dx < 0 ? 1 : -1)
  return target >= 0 && target < tabCount ? target : null
}
