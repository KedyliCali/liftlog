import type { WeightEntry } from '../api/types'
import { parseDate } from './format'
import { locale } from './i18n'

// Same limits as the backend's WeightEntryRequest (and the weight_entries CHECK constraint).
export const MIN_WEIGHT_KG = 20
export const MAX_WEIGHT_KG = 400

export const WEIGHT_RANGES = [30, 90, 365] as const
export type WeightRange = (typeof WEIGHT_RANGES)[number]

const DAY_MS = 24 * 60 * 60 * 1000

export interface WeightChartPoint {
  date: string
  weightKg: number
  /** Mean of the entries in the 7 days ending on this one (calendar days, so gaps shrink the sample) */
  averageKg: number
}

/**
 * Adds a trailing 7-day moving average to each entry. Calendar days rather than "the last 7 entries", so a
 * week-old weigh-in never pulls today's average just because nothing was logged in between.
 * @param entries oldest first, at most one per date
 */
export function withMovingAverage(entries: WeightEntry[], windowDays = 7): WeightChartPoint[] {
  return entries.map((entry, index) => {
    const end = parseDate(entry.date).getTime()
    let sum = 0
    let count = 0
    for (let i = index; i >= 0; i--) {
      // Rounded because a daylight-saving change makes one "day" 23 or 25 hours long
      if (Math.round((end - parseDate(entries[i].date).getTime()) / DAY_MS) >= windowDays) break
      sum += entries[i].weightKg
      count++
    }
    return { date: entry.date, weightKg: entry.weightKg, averageKg: roundTo(sum / count, 2) }
  })
}

/** One stepper press: moves by `deltaKg` on a 0.1 kg grid and stays within the allowed range. */
export function stepWeight(currentKg: number, deltaKg: number): number {
  const next = roundTo(currentKg + deltaKg, 1)
  return Math.min(MAX_WEIGHT_KG, Math.max(MIN_WEIGHT_KG, next))
}

/** The typed weight, or null when it is empty, not a number, out of range or has more than 2 decimals. */
export function parseWeight(text: string): number | null {
  const normalized = text.trim().replace(',', '.')
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(normalized)) return null
  const value = Number(normalized)
  return value >= MIN_WEIGHT_KG && value <= MAX_WEIGHT_KG ? value : null
}

/** "80.4" in the field's locale: a comma for Turkish, so the number looks like the rest of the screen. */
export function weightText(kg: number, loc: string = locale): string {
  return new Intl.NumberFormat(loc, { maximumFractionDigits: 2, useGrouping: false }).format(kg)
}

/**
 * A signed change for the chips: "−0,8 kg", "+1,2 kg", "±0 kg". A real minus sign (U+2212) lines up with
 * the plus in tabular figures; a hyphen would look like a dash.
 */
export function formatWeightChange(kg: number, loc: string = locale): string {
  const rounded = roundTo(kg, 1)
  if (rounded === 0) return '±0 kg'
  const number = new Intl.NumberFormat(loc, { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(Math.abs(rounded))
  return `${rounded < 0 ? '−' : '+'}${number} kg`
}

export type ChangeDirection = 'down' | 'up' | 'flat'

/** Neither direction is "good": the app does not know whether the user is cutting or bulking. */
export function changeDirection(kg: number): ChangeDirection {
  const rounded = roundTo(kg, 1)
  return rounded < 0 ? 'down' : rounded > 0 ? 'up' : 'flat'
}

/** Where the quick-entry stepper starts: today's entry, else the newest weight, else the profile's. */
export function startingWeight(entries: WeightEntry[], latest: WeightEntry | null, today: string, profileKg: number | null): number | null {
  const todays = entries.find((e) => e.date === today)
  return todays?.weightKg ?? latest?.weightKg ?? profileKg ?? null
}

function roundTo(value: number, decimals: number): number {
  const factor = 10 ** decimals
  return Math.round(value * factor) / factor
}
