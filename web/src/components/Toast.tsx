import { useCallback, useRef, useState, type ReactNode } from 'react'
import { ToastContext, type ToastOptions } from './contexts'

interface ToastState extends ToastOptions {
  id: number
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const current = useRef<ToastState | null>(null)

  const dismiss = useCallback((runTimeout: boolean) => {
    if (timer.current) clearTimeout(timer.current)
    if (runTimeout) current.current?.onTimeout?.()
    current.current = null
    setToast(null)
  }, [])

  const show = useCallback(
    (options: ToastOptions) => {
      // A new toast replaces the old one; the old one's pending work must still happen
      if (current.current) dismiss(true)
      const next = { ...options, id: Date.now() }
      current.current = next
      setToast(next)
      timer.current = setTimeout(() => dismiss(true), options.durationMs ?? 4000)
    },
    [dismiss],
  )

  return (
    <ToastContext.Provider value={show}>
      {children}
      {toast && (
        <div className="toast" key={toast.id} role="status">
          <span>{toast.message}</span>
          {toast.actionLabel && (
            <button
              className="toast-action"
              onClick={() => {
                toast.onAction?.()
                dismiss(false)
              }}
            >
              {toast.actionLabel}
            </button>
          )}
        </div>
      )}
    </ToastContext.Provider>
  )
}
