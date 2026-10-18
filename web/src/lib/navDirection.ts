// The page-transition CSS reads this attribute to slide the new tab in from the side it lives on.

const CLEAR_AFTER_MS = 600

let clearTimer: ReturnType<typeof setTimeout> | undefined

export function setNavDirection(direction: 'forward' | 'back') {
  document.documentElement.dataset.navDirection = direction
  clearTimeout(clearTimer)
  // Cleared afterwards so later transitions (opening a workout) keep their own animation
  clearTimer = setTimeout(() => delete document.documentElement.dataset.navDirection, CLEAR_AFTER_MS)
}
