import type { WorkoutRecap } from '../api/types'
import { formatKg } from './format'
import { t } from './i18n'

/** Volume change against the compared workout, or null when there is nothing to compare with. */
export function volumeDelta(recap: Pick<WorkoutRecap, 'totalVolumeKg' | 'previousVolumeKg'>): number | null {
  if (recap.previousVolumeKg == null) return null
  // Volumes are sums of 2-decimal loads; rounding keeps 0.1 + 0.2 noise out of the "+0 kg" check
  const delta = Math.round((recap.totalVolumeKg - recap.previousVolumeKg) * 100) / 100
  return delta === 0 ? 0 : delta // turns -0 into 0
}

/** "+320", "−45.5" (true minus sign), "0": the sign is always shown so the tag reads as a change. */
export function formatSignedKg(delta: number, locale?: string): string {
  const magnitude = formatKg(Math.abs(delta), locale)
  if (delta > 0) return `+${magnitude}`
  if (delta < 0) return `−${magnitude}`
  return magnitude
}

/** The short text the share button sends: volume, sets and exercises, then records and the streak when there are any. */
export function recapShareText(recap: WorkoutRecap, locale?: string): string {
  const parts = [t.recapShareText(formatKg(recap.totalVolumeKg, locale), recap.sets, recap.exercises)]
  if (recap.personalRecords.length > 0) {
    parts.push(t.recapShareRecords(recap.personalRecords.map((record) => record.exerciseName).join(', ')))
  }
  if (recap.weekStreak > 0) parts.push(t.recapShareStreak(recap.weekStreak))
  return parts.join(' ')
}
