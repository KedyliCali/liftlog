import { useRef, useState, type PointerEvent, type ReactNode } from 'react'
import { vibrate } from '../lib/feedback'
import { TrashIcon } from './Icons'

const DELETE_THRESHOLD_PX = 96

interface SwipeToDeleteProps {
  /** Asked once the row is let go past the threshold; false slides the row back and nothing is deleted */
  confirm: () => Promise<boolean>
  /** Runs after a confirmed swipe, once the row has slid out */
  onDelete: () => void
  label: string
  children: ReactNode
}

/**
 * Drag the row left past the threshold to delete it. A short vibration marks the threshold; letting go there
 * asks for confirmation while the row waits half-open. Vertical drags are left to the page so scrolling still works.
 */
export function SwipeToDelete({ confirm, onDelete, label, children }: SwipeToDeleteProps) {
  const [offset, setOffset] = useState(0)
  const [leaving, setLeaving] = useState(false)
  // The row follows the finger without easing; every other move (snap back, rest, slide out) animates
  const [dragging, setDragging] = useState(false)
  // While the question is open the row ignores new drags
  const asking = useRef(false)
  const start = useRef<{ x: number; y: number; horizontal: boolean | null } | null>(null)
  const pastThreshold = useRef(false)
  // Set after a horizontal drag so the click the browser fires on release does not open the row
  const dragged = useRef(false)

  const onPointerDown = (event: PointerEvent) => {
    if (asking.current) return
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
        setDragging(true)
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
    setDragging(false)
    if (wasHorizontal && pastThreshold.current) {
      void askThenDelete()
    } else {
      setOffset(0)
    }
    pastThreshold.current = false
  }

  const askThenDelete = async () => {
    asking.current = true
    // Rest just past the threshold so the red trash stays visible behind the question
    setOffset(-DELETE_THRESHOLD_PX - 8)
    const confirmed = await confirm().catch(() => false)
    asking.current = false
    if (!confirmed) {
      setOffset(0)
      return
    }
    setLeaving(true)
    setOffset(-window.innerWidth)
    // Let the slide-out animation finish before the row is removed
    setTimeout(onDelete, 180)
  }

  return (
    <div className="swipe">
      <div className={offset < -DELETE_THRESHOLD_PX ? 'swipe-bg armed' : 'swipe-bg'} aria-hidden>
        <TrashIcon />
      </div>
      <div
        className={dragging && !leaving ? 'swipe-content' : 'swipe-content settle'}
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
