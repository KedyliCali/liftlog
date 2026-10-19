import { afterEach, describe, expect, it, vi } from 'vitest'
import type { LolGroup } from './types'
import { AppError, request } from './client'
import { api } from './endpoints'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function failureOf(status: number, body: unknown): Promise<AppError> {
  vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(status, body)))
  try {
    await request('/api/v1/anything')
  } catch (error) {
    return error as AppError
  }
  throw new Error('request did not fail')
}

afterEach(() => vi.unstubAllGlobals())

describe('error kinds', () => {
  it('turns a group rule conflict into its rule', async () => {
    const error = await failureOf(409, { status: 409, message: 'This group is full (20 members)', fieldErrors: { group: 'GROUP_FULL' } })
    expect(error.kind).toBe('groupRule')
    expect(error.detail).toBe('GROUP_FULL')
  })

  it('keeps other conflicts as conflicts with their message', async () => {
    const error = await failureOf(409, { status: 409, message: 'You can follow at most 20 players' })
    expect(error.kind).toBe('conflict')
    expect(error.detail).toBe('You can follow at most 20 players')
  })

  it('reads 403 as forbidden and keeps the Riot reasons', async () => {
    expect((await failureOf(403, { status: 403, message: 'Only the group owner can do that' })).kind).toBe('forbidden')
    expect((await failureOf(503, { status: 503, fieldErrors: { riot: 'NO_KEY' } })).kind).toBe('riotNoKey')
  })
})

describe('api.lolGroup', () => {
  it('is null when the user is in no group (204)', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })))
    await expect(api.lolGroup()).resolves.toBeNull()
  })

  it('is the group otherwise', async () => {
    const group: LolGroup = { name: 'Duo Queue', inviteCode: 'K7QXM2PFD9', maxMembers: 20, members: [] }
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(200, group)))
    await expect(api.lolGroup()).resolves.toEqual(group)
  })
})
