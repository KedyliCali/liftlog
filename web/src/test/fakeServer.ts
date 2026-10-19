import { vi } from 'vitest'

export function jsonResponse(status: number, body: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** A promise lets a test hold an answer back and release it later, like a slow server. */
export type Handler = (body: unknown) => Response | Promise<Response>

export interface Call {
  method: string
  path: string
  body: unknown
}

/** Answers API calls by "METHOD /path" (unknown routes are a 404); every request is recorded in order. */
export function fakeServer(routes: Record<string, Handler>): Call[] {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      const method = init.method ?? 'GET'
      const path = new URL(url).pathname
      const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined
      calls.push({ method, path, body })
      return routes[`${method} ${path}`]?.(body) ?? jsonResponse(404, { status: 404 })
    }),
  )
  return calls
}
