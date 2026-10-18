import { useEffect, useRef, useState } from 'react'
import { countUpValue } from '../lib/countUp'

const prefersReducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

/**
 * Animates from the previously shown value to the new one, so adding a set makes the volume
 * visibly climb. The first render counts up from zero.
 */
export function CountUp({ value, format, durationMs = 700 }: { value: number; format: (n: number) => string; durationMs?: number }) {
  const [shown, setShown] = useState(prefersReducedMotion() ? value : 0)
  const shownRef = useRef(shown)

  useEffect(() => {
    const from = shownRef.current
    if (from === value || prefersReducedMotion()) {
      shownRef.current = value
      setShown(value)
      return
    }
    const start = performance.now()
    let frame = requestAnimationFrame(function tick(now) {
      const next = countUpValue(from, value, now - start, durationMs)
      shownRef.current = next
      setShown(next)
      if (now - start < durationMs) frame = requestAnimationFrame(tick)
    })
    return () => cancelAnimationFrame(frame)
  }, [value, durationMs])

  // Intermediate frames are rounded so "1.279,5" does not flicker through long decimals
  const display = shown === value ? value : Math.round(shown)
  return (
    <span className="num" aria-label={format(value)}>
      {format(display)}
    </span>
  )
}
