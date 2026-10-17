import { useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'

const COLORS = ['var(--primary)', 'var(--tertiary)', '#ffd166', 'var(--on-primary-container)', '#f2f3f5']

/** A one-off burst of falling pieces; pure CSS animation, removed by the parent after ~1.6 s. */
function makePieces() {
  return Array.from({ length: 48 }, (_, i) => ({
    left: Math.random() * 100,
    delay: Math.random() * 0.25,
    duration: 1 + Math.random() * 0.6,
    drift: (Math.random() - 0.5) * 160,
    rotate: Math.random() * 720,
    color: COLORS[i % COLORS.length],
    wide: i % 3 === 0,
  }))
}

export function Confetti() {
  // Lazy initializer: the random layout is created once, not on every render
  const [pieces] = useState(makePieces)
  return createPortal(
    <div className="confetti" aria-hidden>
      {pieces.map((p, i) => (
        <span
          key={i}
          className={p.wide ? 'confetti-piece wide' : 'confetti-piece'}
          style={
            {
              left: `${p.left}%`,
              background: p.color,
              animationDelay: `${p.delay}s`,
              animationDuration: `${p.duration}s`,
              '--drift': `${p.drift}px`,
              '--rotate': `${p.rotate}deg`,
            } as CSSProperties
          }
        />
      ))}
    </div>,
    document.body,
  )
}
