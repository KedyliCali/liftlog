import { notifyManager, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useSyncExternalStore } from 'react'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { outbox, type OutboxItem } from '../lib/outbox'
import { flushOutbox } from '../lib/outboxFlush'
import { useToast } from './contexts'

// Queue changes reach React through TanStack's batching, so a flush that updates the cache and the
// queue together re-renders once (see replacePending in outboxFlush.ts)
const subscribeOutbox = (onChange: () => void) => outbox.subscribe(notifyManager.batchCalls(onChange))

export function useOutbox(): OutboxItem[] {
  return useSyncExternalStore(subscribeOutbox, outbox.items)
}

const subscribeOnline = (onChange: () => void) => {
  window.addEventListener('online', onChange)
  window.addEventListener('offline', onChange)
  return () => {
    window.removeEventListener('online', onChange)
    window.removeEventListener('offline', onChange)
  }
}

export function useOnline(): boolean {
  return useSyncExternalStore(subscribeOnline, () => navigator.onLine)
}

/** Starts sending the queue now; a refused item is dropped with a toast saying why. */
export function useFlushOutbox(): () => Promise<void> {
  const queryClient = useQueryClient()
  const toast = useToast()
  return useCallback(
    () => flushOutbox({ queryClient, onDropped: (_, error) => toast({ message: t.waitingSetDropped(errorMessage(error)), durationMs: 6000 }) }),
    [queryClient, toast],
  )
}

export const RETRY_INTERVAL_MS = 30_000

/** Sends waiting sets when the app starts, comes back to the foreground or online, and every 30 s while some wait. */
export function useOutboxSync() {
  const flush = useFlushOutbox()
  const waiting = useOutbox().length > 0

  useEffect(() => {
    void flush()
    const onVisible = () => document.visibilityState === 'visible' && void flush()
    const onOnline = () => void flush()
    window.addEventListener('online', onOnline)
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.removeEventListener('online', onOnline)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [flush])

  useEffect(() => {
    if (!waiting) return
    const timer = setInterval(() => void flush(), RETRY_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [waiting, flush])
}
