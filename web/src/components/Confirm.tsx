import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { t } from '../lib/i18n'
import { ConfirmContext, type ConfirmOptions } from './contexts'
import { AlertIcon, TrashIcon } from './Icons'

interface PendingConfirm {
  options: ConfirmOptions
  resolve: (confirmed: boolean) => void
}

/**
 * Owns the one "are you sure?" sheet of the app. Pages call `await confirm({...})` from useConfirm and get
 * true only for an explicit tap on the confirm button.
 */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<PendingConfirm | null>(null)
  const pendingRef = useRef<PendingConfirm | null>(null)

  const settle = useCallback((confirmed: boolean) => {
    const current = pendingRef.current
    pendingRef.current = null
    setPending(null)
    current?.resolve(confirmed)
  }, [])

  const confirm = useCallback(
    (options: ConfirmOptions = {}) =>
      new Promise<boolean>((resolve) => {
        // Only one question at a time: a newer one cancels the older, which must not hang forever
        pendingRef.current?.resolve(false)
        const next = { options, resolve }
        pendingRef.current = next
        setPending(next)
      }),
    [],
  )

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {pending && <ConfirmSheet options={pending.options} onSettle={settle} />}
    </ConfirmContext.Provider>
  )
}

function ConfirmSheet({ options, onSettle }: { options: ConfirmOptions; onSettle: (confirmed: boolean) => void }) {
  const { title = t.confirmDeleteTitle, item, message, confirmLabel = t.delete, tone = 'danger' } = options
  const cancelRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null
    // Focus starts on Cancel so a stray Enter (or a double tap) never deletes anything
    cancelRef.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      // Captured on window and stopped, so a sheet underneath (e.g. the set editor) stays open
      event.stopImmediatePropagation()
      onSettle(false)
    }
    window.addEventListener('keydown', onKey, true)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey, true)
      document.body.style.overflow = previousOverflow
      returnFocusTo?.focus?.()
    }
  }, [onSettle])

  return createPortal(
    <div className="sheet-backdrop confirm-backdrop" onClick={() => onSettle(false)}>
      <div
        className={`sheet confirm-sheet confirm-${tone}`}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby={item || message ? 'confirm-body' : undefined}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="sheet-handle" />
        <div className="confirm-head">
          <span className="confirm-badge" aria-hidden>
            {tone === 'danger' ? <TrashIcon size={22} /> : <AlertIcon size={22} />}
          </span>
          <h2 className="confirm-title" id="confirm-title">
            {title}
          </h2>
        </div>
        {(item || message) && (
          <div className="confirm-body" id="confirm-body">
            {item && <strong className="confirm-item">{item}</strong>}
            {message && <p className="confirm-message">{message}</p>}
          </div>
        )}
        <div className="confirm-actions">
          <button ref={cancelRef} type="button" className="btn btn-tonal confirm-cancel" onClick={() => onSettle(false)}>
            {t.cancel}
          </button>
          <button type="button" className="btn confirm-ok" onClick={() => onSettle(true)}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
