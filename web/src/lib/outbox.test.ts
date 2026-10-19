import { afterEach, describe, expect, it } from 'vitest'
import type { SetInput, WorkoutSet } from '../api/types'
import { enqueue, findAlreadySaved, hasWaitingFor, markInFlight, outbox, overlaySets, resolveLocalId, type OutboxItem } from './outbox'

const body = (reps: number, weightKg = 80): SetInput => ({ exerciseId: 1, reps, weightKg, rpe: null })

const create = (id: string, localSetId: number, reps = 8, extra: Partial<OutboxItem> = {}): OutboxItem => ({
  id,
  kind: 'create',
  workoutId: 7,
  localSetId,
  body: body(reps),
  exerciseName: 'Bench Press',
  bodyweight: false,
  createdAt: 0,
  ...extra,
})
const update = (id: string, setId: number, reps: number): OutboxItem => ({ id, kind: 'update', workoutId: 7, setId, body: body(reps), createdAt: 0 })
const del = (id: string, setId: number): OutboxItem => ({ id, kind: 'delete', workoutId: 7, setId, createdAt: 0 })

const serverSet = (id: number, setOrder: number, reps = 8, weightKg = 80): WorkoutSet => ({
  id,
  exerciseId: 1,
  exerciseName: 'Bench Press',
  setOrder,
  reps,
  weightKg,
  rpe: null,
  newPersonalRecord: false,
  bodyweight: false,
})

describe('enqueue', () => {
  it('keeps creates in the order they were made', () => {
    const queue = [create('a', -1), create('b', -2), create('c', -3)].reduce(enqueue, [] as OutboxItem[])
    expect(queue.map((q) => q.id)).toEqual(['a', 'b', 'c'])
  })

  it('folds an edit of a waiting set into its create', () => {
    const queue = enqueue([create('a', -1), create('b', -2)], update('u', -1, 10))
    expect(queue).toHaveLength(2)
    expect(queue[0]).toMatchObject({ id: 'a', kind: 'create', body: body(10) })
  })

  it('cancels a waiting create when it is deleted, without sending anything', () => {
    const queue = enqueue([create('a', -1), create('b', -2)], del('d', -1))
    expect(queue.map((q) => q.id)).toEqual(['b'])
  })

  it('never rewrites a create that was already sent once', () => {
    const sent = create('a', -1, 8, { inFlight: true, expectedSetOrder: 3 })
    expect(enqueue([sent], update('u', -1, 10)).map((q) => q.id)).toEqual(['a', 'u'])
    expect(enqueue([sent], del('d', -1)).map((q) => q.id)).toEqual(['a', 'd'])
  })

  it('replaces an earlier edit of a server set in place and drops edits before a delete', () => {
    const edited = enqueue([update('u1', 50, 6), create('a', -1)], update('u2', 50, 7))
    expect(edited.map((q) => q.id)).toEqual(['u1', 'a'])
    expect(edited[0].body).toEqual(body(7))

    expect(enqueue(edited, del('d', 50)).map((q) => q.id)).toEqual(['a', 'd'])
  })
})

describe('queue bookkeeping', () => {
  it('hands the real id to changes queued after a create that was in flight', () => {
    const queue = resolveLocalId([update('u', -1, 9), del('d', 50)], -1, 123)
    expect(queue.map((q) => q.setId)).toEqual([123, 50])
  })

  it('marks an item as sent with the position the server will give it', () => {
    expect(markInFlight([create('a', -1)], 'a', 4)[0]).toMatchObject({ inFlight: true, expectedSetOrder: 4 })
  })

  it('knows whether a workout still has waiting changes', () => {
    expect(hasWaitingFor([create('a', -1)], 7)).toBe(true)
    expect(hasWaitingFor([create('a', -1)], 8)).toBe(false)
  })
})

describe('findAlreadySaved', () => {
  const sent = create('a', -1, 8, { inFlight: true, expectedSetOrder: 2 })

  it('finds the same set at the expected position', () => {
    expect(findAlreadySaved([serverSet(10, 1), serverSet(11, 2)], sent)?.id).toBe(11)
  })

  it('does not mistake an identical earlier set for it', () => {
    expect(findAlreadySaved([serverSet(10, 1)], sent)).toBeUndefined()
  })

  it('does not match a different set at that position', () => {
    expect(findAlreadySaved([serverSet(10, 1), serverSet(11, 2, 5)], sent)).toBeUndefined()
  })
})

describe('overlaySets', () => {
  it('appends waiting sets after the server sets and applies edits and deletes', () => {
    const queue = [create('a', -1, 10), update('u', 10, 6), del('d', 11), create('other', -2, 5, { workoutId: 8 })]
    const { sets, pendingIds, replacedServerIds } = overlaySets([serverSet(10, 1), serverSet(11, 2)], queue, 7)

    expect(sets.map((s) => [s.id, s.reps, s.setOrder])).toEqual([
      [10, 6, 1],
      [-1, 10, 3],
    ])
    expect([...pendingIds]).toEqual([-1, 10])
    expect([...replacedServerIds]).toEqual([10, 11])
  })

  it('hides a sent create once a refetch shows the server saved it', () => {
    const queue = [create('a', -1, 8, { inFlight: true, expectedSetOrder: 2 })]
    const { sets } = overlaySets([serverSet(10, 1), serverSet(11, 2)], queue, 7)
    expect(sets.map((s) => s.id)).toEqual([10, 11])
  })
})

describe('outbox store', () => {
  afterEach(() => outbox.reset())

  it('persists the queue and resolves later edits of a saved create', () => {
    const localId = outbox.addCreate(7, body(8), { exerciseName: 'Bench Press', bodyweight: false })
    expect(JSON.parse(localStorage.getItem('liftlog.outbox.v1')!)).toHaveLength(1)

    outbox.done(outbox.items()[0], 99)
    // An editor opened on the waiting row still holds the local id
    outbox.addUpdate(7, localId, body(9))
    expect(outbox.items()).toMatchObject([{ kind: 'update', setId: 99 }])
  })

  it('drops the changes queued on a create the server refused', () => {
    const localId = outbox.addCreate(7, body(8), { exerciseName: 'Bench Press', bodyweight: false })
    outbox.markInFlight(outbox.items()[0].id, 1)
    outbox.addDelete(7, localId)
    expect(outbox.items()).toHaveLength(2)

    outbox.drop(outbox.items()[0])
    expect(outbox.items()).toEqual([])
  })
})
