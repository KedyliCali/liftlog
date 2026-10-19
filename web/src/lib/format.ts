import { AppError } from '../api/client'
import type { PersonalRecord } from '../api/types'
import { locale, t } from './i18n'

/** 140 -> "140", 142.5 -> "142.5", 12500 -> "12,500" (locale-aware grouping). */
export function formatKg(value: number, loc: string = locale): string {
  return new Intl.NumberFormat(loc, { maximumFractionDigits: 2 }).format(value)
}

/** ISO date strings are parsed as local dates; `new Date("2026-10-17")` would be UTC midnight. */
export function parseDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function toIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export const formatLongDate = (iso: string) =>
  parseDate(iso).toLocaleDateString(locale, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })

export const formatShortDate = (iso: string) => parseDate(iso).toLocaleDateString(locale, { day: 'numeric', month: 'short' })

export const weekdayShort = (iso: string) => parseDate(iso).toLocaleDateString(locale, { weekday: 'short' }).replace('.', '')

export const monthShort = (iso: string) =>
  parseDate(iso).toLocaleDateString(locale, { month: 'short' }).replace('.', '').toLocaleUpperCase(locale)

export function errorMessage(error: unknown): string {
  if (!(error instanceof AppError)) return t.errorUnknown
  switch (error.kind) {
    case 'network':
      return t.errorNetwork
    case 'notFound':
      return t.errorNotFound
    case 'server':
      return t.errorServer
    case 'validation':
      return error.detail ?? t.errorInvalid
    case 'conflict':
      return error.detail ?? t.errorConflict
    case 'signedOut':
      return t.signedOutTitle
    case 'forbidden':
      return t.errorForbidden
    case 'rateLimited':
      return t.errorRateLimited
    case 'groupRule':
      return (error.detail && t.groupRules[error.detail]) || t.errorConflict
    case 'riotNoKey':
      return t.riotNoKey
    case 'riotUnavailable':
      return error.detail === 'KEY_REJECTED' ? t.riotKeyRejected : t.riotBusy
    default:
      return t.errorUnknown
  }
}

/** How a set's load reads: "100 kg", or for bodyweight exercises "Bodyweight" / "BW + 10 kg". */
export function formatLoad(weightKg: number, bodyweight: boolean): string {
  if (!bodyweight) return t.kg(formatKg(weightKg))
  return weightKg === 0 ? t.bodyweightOnly : t.bodyweightPlus(formatKg(weightKg))
}

/** "100 kg × 5", or "BW + 10 kg × 5" so a weighted pull-up reads the way it was logged. */
export function formatRecordSet(record: Pick<PersonalRecord, 'heaviestWeightKg' | 'heaviestAddedKg' | 'heaviestReps' | 'bodyweight'>): string {
  if (!record.bodyweight) return t.weightTimesReps(formatKg(record.heaviestWeightKg), record.heaviestReps)
  return `${formatLoad(record.heaviestAddedKg, true)} × ${record.heaviestReps}`
}
