import { formatKg } from './format'
import { locale, t } from './i18n'
import { roundTo } from './lolStats'

// Number and date formatting shared by the League pages and tables.

export const DASH = '–'

/** The API sends UTC without a zone suffix. */
export const utcDate = (iso: string) => new Date(iso.endsWith('Z') ? iso : `${iso}Z`)
export const shortDate = (iso: string) => utcDate(iso).toLocaleDateString(locale, { day: 'numeric', month: 'short' })
export const compact = (n: number) => new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(n)
export const decimal = (n: number | null | undefined, digits = 1) => (n == null ? DASH : formatKg(roundTo(n, digits)))
export const percentText = (pct: number | null) => (pct == null ? DASH : t.percent(pct))
