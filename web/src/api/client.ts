import type { ApiErrorBody } from './types'

export const API_BASE_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

export type AppErrorKind = 'network' | 'notFound' | 'unauthorized' | 'validation' | 'conflict' | 'server' | 'unknown'

/** Typed failure the UI knows how to explain; components never inspect HTTP details. */
export class AppError extends Error {
  readonly kind: AppErrorKind
  readonly detail: string | null

  constructor(kind: AppErrorKind, detail: string | null = null) {
    super(detail ?? kind)
    this.kind = kind
    this.detail = detail
  }
}

function errorFromResponse(status: number, body: ApiErrorBody | null): AppError {
  switch (true) {
    case status === 400:
      return new AppError('validation', Object.values(body?.fieldErrors ?? {})[0] ?? body?.message ?? null)
    case status === 401:
      return new AppError('unauthorized')
    case status === 404:
      return new AppError('notFound')
    case status === 409:
      return new AppError('conflict', body?.message ?? null)
    case status >= 500:
      return new AppError('server')
    default:
      return new AppError('unknown')
  }
}

type TokenSource = {
  getToken: () => string | null
  /** Called when an authenticated request gets 401 (token expired or revoked). */
  onUnauthorized: () => void
}

let tokenSource: TokenSource = { getToken: () => null, onUnauthorized: () => {} }

export function configureAuth(source: TokenSource) {
  tokenSource = source
}

interface RequestOptions {
  method?: string
  body?: unknown
  /** Raw body such as an image; sent as-is with its own content type. */
  blob?: Blob
  /** Auth endpoints must not send an old token or trigger the 401 handler. */
  anonymous?: boolean
  responseType?: 'json' | 'blob' | 'none'
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, blob, anonymous = false, responseType = 'json' } = options
  const headers: Record<string, string> = {}
  const token = anonymous ? null : tokenSource.getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (blob) headers['Content-Type'] = blob.type

  let response: Response
  try {
    response = await fetch(API_BASE_URL + path, {
      method,
      headers,
      body: blob ?? (body !== undefined ? JSON.stringify(body) : undefined),
    })
  } catch {
    // fetch only rejects when the request never got a response (offline, DNS, CORS)
    throw new AppError('network')
  }

  if (!response.ok) {
    if (response.status === 401 && token) tokenSource.onUnauthorized()
    const errorBody = (await response.json().catch(() => null)) as ApiErrorBody | null
    throw errorFromResponse(response.status, errorBody)
  }
  if (responseType === 'none' || response.status === 204) return undefined as T
  if (responseType === 'blob') return (await response.blob()) as T
  return (await response.json()) as T
}
