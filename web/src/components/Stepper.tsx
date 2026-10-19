import type { ReactNode } from 'react'

interface StepperProps {
  label: string
  /** Shown between the buttons; ignored when `onType` makes the value an input */
  value: ReactNode
  onMinus: () => void
  onPlus: () => void
  minusLabel: string
  plusLabel: string
  minusDisabled?: boolean
  plusDisabled?: boolean
  /** Lets the value be typed as well (for a first weight that is far from 0) */
  onType?: { text: string; inputMode: 'decimal' | 'numeric'; onChange: (text: string) => void }
  invalid?: boolean
}

/** Large − value + control: the buttons sit as raised tabs on the value's pill, so no keyboard is needed. */
export function Stepper({ label, value, onMinus, onPlus, minusLabel, plusLabel, minusDisabled, plusDisabled, onType, invalid }: StepperProps) {
  return (
    <div className={invalid ? 'stepper invalid' : 'stepper'} role="group" aria-label={label}>
      <button type="button" onClick={onMinus} disabled={minusDisabled} aria-label={minusLabel}>
        −
      </button>
      {onType ? (
        <input
          className="stepper-value stepper-input num"
          aria-label={label}
          inputMode={onType.inputMode}
          autoComplete="off"
          value={onType.text}
          onChange={(event) => onType.onChange(event.target.value)}
        />
      ) : (
        <span className="stepper-value num" aria-live="polite">
          {value}
        </span>
      )}
      <button type="button" onClick={onPlus} disabled={plusDisabled} aria-label={plusLabel}>
        +
      </button>
    </div>
  )
}
