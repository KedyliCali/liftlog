import { createContext, useContext } from 'react'

export interface ToastOptions {
  message: string
  actionLabel?: string
  onAction?: () => void
  /** Runs when the toast disappears without the action being pressed (e.g. commit a deferred delete). */
  onTimeout?: () => void
  durationMs?: number
}

export const ToastContext = createContext<(options: ToastOptions) => void>(() => {})
export const useToast = () => useContext(ToastContext)

export interface RestTimerApi {
  start: () => void
}

export const RestTimerContext = createContext<RestTimerApi>({ start: () => {} })
export const useRestTimer = () => useContext(RestTimerContext)

export interface ConfirmOptions {
  /** Defaults to the generic "Delete this?" question */
  title?: string
  /** The thing being acted on, on its own line so the user sees exactly what goes (a routine name, "Bench Press #3") */
  item?: string
  /** What happens afterwards, e.g. "Past workouts stay." */
  message?: string
  /** Defaults to "Delete" */
  confirmLabel?: string
  /** 'danger' (default) paints the confirm button red; 'neutral' is for confirms that remove nothing */
  tone?: 'danger' | 'neutral'
}

/** Resolves true only when the confirm button is pressed; cancel, Escape and a backdrop tap resolve false. */
export type ConfirmFn = (options?: ConfirmOptions) => Promise<boolean>

export const ConfirmContext = createContext<ConfirmFn | null>(null)

export function useConfirm(): ConfirmFn {
  const confirm = useContext(ConfirmContext)
  // Failing loudly beats a silent default: without the provider a delete would either skip the question or never run
  if (!confirm) throw new Error('useConfirm must be used inside <ConfirmProvider>')
  return confirm
}
