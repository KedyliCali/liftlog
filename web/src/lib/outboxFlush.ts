import { notifyManager, type QueryClient } from '@tanstack/react-query'
import { AppError } from '../api/client'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { WorkoutDetail, WorkoutSet } from '../api/types'
import { findAlreadySaved, maxSetOrder, outbox, type OutboxItem } from './outbox'

/** Failures that may fix themselves (no signal, server waking up, token being renewed): keep the item. */
const RETRY_LATER = new Set(['network', 'server', 'unauthorized', 'signedOut', 'rateLimited'])

export function isRetryable(error: unknown): boolean {
  return !(error instanceof AppError) || RETRY_LATER.has(error.kind)
}

export interface FlushOptions {
  queryClient: QueryClient
  /** The server refused an item for good (4xx); it was removed from the queue */
  onDropped: (item: OutboxItem, error: unknown) => void
}

let running: Promise<void> | null = null

/** Sends the queue in order, one request at a time. Calls made while a flush runs share it. */
export function flushOutbox(options: FlushOptions): Promise<void> {
  running ??= sendAll(options).finally(() => (running = null))
  return running
}

async function sendAll({ queryClient, onDropped }: FlushOptions) {
  // The server's sets per workout, read once per flush and kept up to date as creates succeed
  const known = new Map<number, WorkoutSet[]>()
  const touched = new Set<number>()

  for (let item = outbox.items()[0]; item; item = outbox.items()[0]) {
    try {
      await send(item, known, queryClient)
      touched.add(item.workoutId)
    } catch (error) {
      if (isRetryable(error)) break // stop here; the next trigger starts again from this item
      outbox.drop(item)
      touched.add(item.workoutId)
      onDropped(item, error)
    }
  }

  if (touched.size === 0) return
  await Promise.all([
    ...[...touched].map((id) => queryClient.invalidateQueries({ queryKey: queryKeys.workout(id) })),
    queryClient.invalidateQueries({ queryKey: queryKeys.workouts }),
    queryClient.invalidateQueries({ queryKey: queryKeys.stats }),
  ])
}

async function send(item: OutboxItem, known: Map<number, WorkoutSet[]>, queryClient: QueryClient) {
  if (item.kind === 'create') {
    let sets = known.get(item.workoutId)
    if (!sets) {
      sets = (await api.workout(item.workoutId)).sets
      known.set(item.workoutId, sets)
    }
    // An earlier attempt may have reached the server even though its answer never came back
    const saved = item.inFlight ? findAlreadySaved(sets, item) : undefined
    if (saved) {
      replacePending(queryClient, item, saved)
      return
    }
    outbox.markInFlight(item.id, maxSetOrder(sets) + 1)
    const created = await api.addSet(item.workoutId, item.body!)
    known.set(item.workoutId, [...sets, created])
    replacePending(queryClient, item, created)
    return
  }

  outbox.markInFlight(item.id)
  if (item.kind === 'update') {
    const updated = await api.updateSet(item.setId!, item.body!)
    batch(() => {
      queryClient.setQueryData<WorkoutDetail>(queryKeys.workout(item.workoutId), (old) =>
        old && { ...old, sets: old.sets.map((s) => (s.id === updated.id ? updated : s)) },
      )
      outbox.done(item)
    })
    return
  }

  try {
    await api.deleteSet(item.setId!)
  } catch (error) {
    // Already gone (deleted on another device): that is what the user wanted
    if (!(error instanceof AppError && error.kind === 'notFound')) throw error
  }
  batch(() => {
    queryClient.setQueryData<WorkoutDetail>(queryKeys.workout(item.workoutId), (old) => old && { ...old, sets: old.sets.filter((s) => s.id !== item.setId) })
    outbox.done(item)
  })
}

/**
 * Swaps the waiting row for the saved set in one render: the cache gains the set and the queue loses
 * the item inside the same batch, so the row neither blinks out nor shows up twice.
 */
function replacePending(queryClient: QueryClient, item: OutboxItem, saved: WorkoutSet) {
  batch(() => {
    queryClient.setQueryData<WorkoutDetail>(queryKeys.workout(item.workoutId), (old) =>
      old && (old.sets.some((s) => s.id === saved.id) ? old : { ...old, sets: [...old.sets, saved] }),
    )
    outbox.done(item, saved.id)
  })
}

const batch = (fn: () => void) => notifyManager.batch(fn)
