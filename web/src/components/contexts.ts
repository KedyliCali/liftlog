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
