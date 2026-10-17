import { useRef, useState, type PointerEvent, type ReactNode } from 'react'
import { vibrate } from '../lib/feedback'

const DELETE_THRESHOLD_PX = 96

/**
 * Drag the row left past the threshold to delete it. A short vibration marks the point of no return.
 * Vertical drags are left to the page so scrolling still works.
 */
export function SwipeToDelete({ onDelete, label, children }: { onDelete: () => void; label: string; children: ReactNode }) {
  const [offset, setOffset] = useState(0)
  const [leaving, setLeaving] = useState(false)
  const start = useRef<{ x: number; y: number; horizontal: boolean | null } | null>(null)
  const pastThreshold = useRef(false)
  // Set after a horizontal drag so the click the browser fires on release does not open the row
  const dragged = useRef(false)

  const onPointerDown = (event: PointerEvent) => {
    dragged.current = false
    start.current = { x: event.clientX, y: event.clientY, horizontal: null }
  }

  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const s = start.current
    if (!s) return
    const dx = event.clientX - s.x
    const dy = event.clientY - s.y
    if (s.horizontal === null && Math.hypot(dx, dy) > 8) {
      s.horizontal = Math.abs(dx) > Math.abs(dy)
      if (s.horizontal) {
        dragged.current = true
        event.currentTarget.setPointerCapture(event.pointerId)
      }
    }
    if (!s.horizontal) return
    const next = Math.min(0, dx)
    setOffset(next)
    const past = -next > DELETE_THRESHOLD_PX
    if (past !== pastThreshold.current) {
      pastThreshold.current = past
      if (past) vibrate(15)
    }
  }

  const onPointerUp = () => {
    const wasHorizontal = start.current?.horizontal
    start.current = null
    if (wasHorizontal && pastThreshold.current) {
      setLeaving(true)
      setOffset(-window.innerWidth)
      // Let the slide-out animation finish before the row is removed
      setTimeout(onDelete, 180)
    } else {
      setOffset(0)
    }
    pastThreshold.current = false
  }

  return (
    <div className="swipe">
      <div className={offset < -DELETE_THRESHOLD_PX ? 'swipe-bg armed' : 'swipe-bg'} aria-hidden>
        <span>🗑</span>
      </div>
      <div
        className={offset === 0 || leaving ? 'swipe-content settle' : 'swipe-content'}
        style={{ transform: `translateX(${offset}px)` }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onClickCapture={(event) => {
          if (!dragged.current) return
          dragged.current = false
          event.preventDefault()
          event.stopPropagation()
        }}
        aria-label={label}
      >
        {children}
      </div>
    </div>
  )
}
