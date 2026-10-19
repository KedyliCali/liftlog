import type { BigLift, GroupTrainingMember, LiftBest } from '../api/types'

/** The four big lifts in table order, with the short names gyms use for them. */
export const BIG_LIFTS: readonly { lift: BigLift; short: string }[] = [
  { lift: 'Bench Press', short: 'Bench' },
  { lift: 'Squat', short: 'Squat' },
  { lift: 'Deadlift', short: 'Deadlift' },
  { lift: 'Overhead Press', short: 'OHP' },
]

/** A member whose numbers the caller may see: an opted-in member, or the caller themselves. */
export interface TrainingRow {
  userId: number
  displayName: string | null
  you: boolean
  shares: boolean
  workouts: number
  totalVolumeKg: number
  sets: number
  weekStreak: number
  best: LiftBest[]
}

/** Members with numbers become table rows; the rest (did not opt in) are only names. */
export function splitTraining(members: readonly GroupTrainingMember[]): { rows: TrainingRow[]; hidden: GroupTrainingMember[] } {
  const rows: TrainingRow[] = []
  const hidden: GroupTrainingMember[] = []
  for (const member of members) {
    if (member.userId != null && member.workouts != null) {
      rows.push({
        userId: member.userId,
        displayName: member.displayName ?? null,
        you: member.you === true,
        shares: member.shares,
        workouts: member.workouts,
        totalVolumeKg: member.totalVolumeKg ?? 0,
        sets: member.sets ?? 0,
        weekStreak: member.weekStreak ?? 0,
        best: member.best ?? [],
      })
    } else {
      hidden.push(member)
    }
  }
  return { rows, hidden }
}

export function bestOf(row: TrainingRow, lift: BigLift): number | null {
  return row.best.find((best) => best.exercise === lift)?.estimated1rmKg ?? null
}

/**
 * The rows holding a column's top value; ties all lead. Nobody leads with nothing: a zero or a missing value
 * is not a lead, so a quiet week highlights no one instead of everyone.
 */
export function columnLeaders<T>(rows: readonly T[], value: (row: T) => number | null | undefined): Set<T> {
  let top = 0
  for (const row of rows) top = Math.max(top, value(row) ?? 0)
  return new Set(top > 0 ? rows.filter((row) => value(row) === top) : [])
}
