import type { SetInput, WorkoutSet } from '../api/types'

/**
 * Offline outbox: set changes that could not reach the server wait here, in order, and are sent
 * once the phone is back online. The pure helpers below decide how a new change combines with the
 * waiting ones; the small store at the bottom keeps the queue in localStorage so a closed tab or a
 * reboot in the gym does not lose a logged set.
 */

export type OutboxKind = 'create' | 'update' | 'delete'

export interface OutboxItem {
  /** Client-generated id; stays the same across retries so one change is never sent twice */
  id: string
  kind: OutboxKind
  workoutId: number
  /** update/delete: the server id, or the negative local id of a create that is still waiting */
  setId?: number
  /** create/update: what to send */
  body?: SetInput
  createdAt: number
  /** create: the negative id the waiting set is shown under until the server gives it a real one */
  localSetId?: number
  /** create: shown on the waiting row, since the request body only has the exercise id */
  exerciseName?: string
  bodyweight?: boolean
  /**
   * A request was sent but its answer never arrived, so the server may have saved it. Before
   * sending again the workout is re-read and a matching set at expectedSetOrder counts as saved.
   */
  inFlight?: boolean
  /** create: the setOrder the server would give this set (max setOrder + 1 at the time it was sent) */
  expectedSetOrder?: number
}

/** A set as the page shows it: the server's sets with the waiting changes applied on top. */
export interface OverlaidSets {
  sets: WorkoutSet[]
  /** Sets that have a change still waiting to be sent (drawn with the "waiting" badge) */
  pendingIds: Set<number>
  /** Server sets that the waiting changes replace or remove (their volume is already in the server total) */
  replacedServerIds: Set<number>
}

const targets = (item: OutboxItem, setId: number) => item.kind !== 'create' && item.setId === setId
const isCreateOf = (item: OutboxItem, setId: number) => item.kind === 'create' && item.localSetId === setId

/**
 * Adds a change to the queue, folding it into a waiting one where that is safe:
 * an edit of a waiting set changes the waiting create, and deleting a waiting set simply cancels it.
 * Items already sent once (inFlight) are never changed, because the server may have them.
 */
export function enqueue(queue: OutboxItem[], item: OutboxItem): OutboxItem[] {
  if (item.kind === 'create' || item.setId === undefined) return [...queue, item]
  const setId = item.setId
  const create = queue.find((q) => isCreateOf(q, setId) && !q.inFlight)

  if (item.kind === 'update') {
    if (create) return queue.map((q) => (q === create ? { ...q, body: item.body, exerciseName: item.exerciseName ?? q.exerciseName, bodyweight: item.bodyweight ?? q.bodyweight } : q))
    const update = queue.find((q) => q.kind === 'update' && q.setId === setId && !q.inFlight)
    // Replaced in place: the earlier position keeps the order the user made the changes in
    if (update) return queue.map((q) => (q === update ? { ...q, body: item.body } : q))
    return [...queue, item]
  }

  // delete
  if (create) return queue.filter((q) => q !== create && !(targets(q, setId) && !q.inFlight))
  return [...queue.filter((q) => !(q.kind === 'update' && q.setId === setId && !q.inFlight)), item]
}

export function removeItem(queue: OutboxItem[], id: string): OutboxItem[] {
  return queue.filter((q) => q.id !== id)
}

export function markInFlight(queue: OutboxItem[], id: string, expectedSetOrder?: number): OutboxItem[] {
  return queue.map((q) => (q.id === id ? { ...q, inFlight: true, ...(expectedSetOrder !== undefined ? { expectedSetOrder } : {}) } : q))
}

/** Once a waiting create got its real id, later edits and deletes of it must use that id. */
export function resolveLocalId(queue: OutboxItem[], localSetId: number, realId: number): OutboxItem[] {
  return queue.map((q) => (q.kind !== 'create' && q.setId === localSetId ? { ...q, setId: realId } : q))
}

export function hasWaitingFor(queue: OutboxItem[], workoutId: number): boolean {
  return queue.some((q) => q.workoutId === workoutId)
}

export function maxSetOrder(sets: WorkoutSet[]): number {
  return sets.reduce((max, s) => Math.max(max, s.setOrder), 0)
}

/**
 * After a request whose answer was lost: the server numbers sets max + 1, so a set with the same
 * exercise, reps and weight at exactly the expected position is this create, already saved.
 */
export function findAlreadySaved(serverSets: WorkoutSet[], item: OutboxItem): WorkoutSet | undefined {
  if (item.kind !== 'create' || !item.body || item.expectedSetOrder === undefined) return undefined
  const body = item.body
  return serverSets.find(
    (s) => s.setOrder === item.expectedSetOrder && s.exerciseId === body.exerciseId && s.reps === body.reps && s.weightKg === body.weightKg,
  )
}

/** The workout's sets with this workout's waiting changes applied, in the order they will be saved. */
export function overlaySets(serverSets: WorkoutSet[], queue: OutboxItem[], workoutId: number): OverlaidSets {
  const pendingIds = new Set<number>()
  const replacedServerIds = new Set<number>()
  let sets = [...serverSets]
  let nextOrder = maxSetOrder(serverSets)

  for (const item of queue) {
    if (item.workoutId !== workoutId) continue
    if (item.kind === 'create' && item.body && item.localSetId !== undefined) {
      // Sent, but the answer was lost and a refetch already shows it: do not draw it twice
      if (item.inFlight && findAlreadySaved(serverSets, item)) continue
      nextOrder += 1
      sets.push({
        id: item.localSetId,
        setOrder: nextOrder,
        exerciseName: item.exerciseName ?? '',
        bodyweight: item.bodyweight ?? false,
        newPersonalRecord: false,
        ...item.body,
      })
      pendingIds.add(item.localSetId)
    } else if (item.setId !== undefined) {
      const setId = item.setId
      if (setId > 0) replacedServerIds.add(setId)
      if (item.kind === 'update' && item.body) {
        const body = item.body
        sets = sets.map((s) => (s.id === setId ? { ...s, ...body } : s))
        pendingIds.add(setId)
      } else if (item.kind === 'delete') {
        sets = sets.filter((s) => s.id !== setId)
        pendingIds.delete(setId)
      }
    }
  }
  return { sets, pendingIds, replacedServerIds }
}

// ---- Persistent store ----

const STORAGE_KEY = 'liftlog.outbox.v1'

function load(): OutboxItem[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(parsed) ? (parsed as OutboxItem[]) : []
  } catch {
    return []
  }
}

let items: OutboxItem[] = load()
const listeners = new Set<() => void>()
// Local ids of creates that were saved after a screen had already shown them (e.g. an open editor)
const resolvedIds = new Map<number, number>()

function commit(next: OutboxItem[]) {
  items = next
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {
    // Storage full or blocked: the queue still works for this session
  }
  listeners.forEach((listener) => listener())
}

/** Unique negative ids: never clash with server ids, and stay distinct from ids handed out earlier. */
let lastLocalId = 0
function newLocalSetId(): number {
  lastLocalId = Math.min(lastLocalId - 1, -Date.now())
  return lastLocalId
}

export const outbox = {
  items: () => items,
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => void listeners.delete(listener)
  },
  /** Queues a new set and returns the local id it is shown under. */
  addCreate(workoutId: number, body: SetInput, display: { exerciseName: string; bodyweight: boolean }, sent?: { expectedSetOrder: number }): number {
    const localSetId = newLocalSetId()
    commit(
      enqueue(items, {
        id: crypto.randomUUID(),
        kind: 'create',
        workoutId,
        body,
        localSetId,
        createdAt: Date.now(),
        ...display,
        ...(sent ? { inFlight: true, expectedSetOrder: sent.expectedSetOrder } : {}),
      }),
    )
    return localSetId
  },
  addUpdate(workoutId: number, setId: number, body: SetInput, display?: { exerciseName: string; bodyweight: boolean }) {
    const id = resolvedIds.get(setId) ?? setId
    commit(enqueue(items, { id: crypto.randomUUID(), kind: 'update', workoutId, setId: id, body, createdAt: Date.now(), ...display }))
  },
  addDelete(workoutId: number, setId: number) {
    const id = resolvedIds.get(setId) ?? setId
    commit(enqueue(items, { id: crypto.randomUUID(), kind: 'delete', workoutId, setId: id, createdAt: Date.now() }))
  },
  markInFlight(id: string, expectedSetOrder?: number) {
    commit(markInFlight(items, id, expectedSetOrder))
  },
  /** The item reached the server; a create also hands its real id to the changes queued after it. */
  done(item: OutboxItem, realSetId?: number) {
    let next = removeItem(items, item.id)
    if (item.kind === 'create' && item.localSetId !== undefined && realSetId !== undefined) {
      resolvedIds.set(item.localSetId, realSetId)
      next = resolveLocalId(next, item.localSetId, realSetId)
    }
    commit(next)
  },
  /** The server refused the item for good; changes queued on a refused create go with it. */
  drop(item: OutboxItem) {
    const localId = item.kind === 'create' ? item.localSetId : undefined
    commit(items.filter((q) => q.id !== item.id && !(localId !== undefined && targets(q, localId))))
  },
  /** Tests only: start from an empty queue. */
  reset() {
    resolvedIds.clear()
    commit([])
  },
}
