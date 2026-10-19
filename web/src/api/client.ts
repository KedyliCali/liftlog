import type { ApiErrorBody } from './types'

export const API_BASE_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

export type AppErrorKind = 'network' | 'notFound' | 'unauthorized' | 'validation' | 'conflict' | 'server' | 'riotNoKey' | 'riotUnavailable' | 'signedOut' | 'forbidden' | 'groupRule' | 'rateLimited' | 'unknown'

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
    case status === 403:
      return new AppError('forbidden')
    case status === 404:
      return new AppError('notFound')
    case status === 429:
      return new AppError('rateLimited')
    // League group rules name themselves, so the app shows its own text instead of the English message
    case status === 409 && body?.fieldErrors?.group != null:
      return new AppError('groupRule', body?.fieldErrors?.group ?? null)
    case status === 409:
      return new AppError('conflict', body?.message ?? null)
    // The League tracker marks Riot problems so the app can say "not set up yet" instead of "server error"
    case status === 503 && body?.fieldErrors?.riot === 'NO_KEY':
      return new AppError('riotNoKey')
    case status === 503 && body?.fieldErrors?.riot != null:
      return new AppError('riotUnavailable', body?.fieldErrors?.riot ?? null)
    case status >= 500:
      return new AppError('server')
    default:
      return new AppError('unknown')
  }
}

type TokenSource = {
  getToken: () => string | null
  /** Called with the token that got a 401 (expired or revoked), which may no longer be the current one. */
  onUnauthorized: (rejectedToken: string) => void
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

/** Sends the request and turns a failed status into an AppError; callers read the body they expect. */
async function send(path: string, options: RequestOptions): Promise<Response> {
  const { method = 'GET', body, blob, anonymous = false } = options
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
    if (response.status === 401 && token) tokenSource.onUnauthorized(token)
    const errorBody = (await response.json().catch(() => null)) as ApiErrorBody | null
    throw errorFromResponse(response.status, errorBody)
  }
  return response
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { responseType = 'json' } = options
  const response = await send(path, options)
  if (responseType === 'none' || response.status === 204) return undefined as T
  if (responseType === 'blob') return (await response.blob()) as T
  return (await response.json()) as T
}

export interface DownloadedFile {
  blob: Blob
  /** The name the server chose (Content-Disposition), or null when it did not send one. */
  filename: string | null
}

/** A file download that still carries the bearer token, which a plain link to the API cannot. */
export async function requestFile(path: string): Promise<DownloadedFile> {
  const response = await send(path, {})
  return { blob: await response.blob(), filename: filenameFromDisposition(response.headers.get('Content-Disposition')) }
}

/** Reads `attachment; filename="x.csv"` or the RFC 5987 form `filename*=UTF-8''x.csv`. */
export function filenameFromDisposition(header: string | null): string | null {
  if (!header) return null
  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header)
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim())
    } catch {
      // Malformed escape: fall back to the plain parameter below
    }
  }
  const plain = /filename\s*=\s*"([^"]*)"|filename\s*=\s*([^;]+)/i.exec(header)
  const name = (plain?.[1] ?? plain?.[2])?.trim()
  return name ? name : null
}
