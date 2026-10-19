import { t } from '../lib/i18n'
import { StreakIcon } from './Icons'

/**
 * "4 WEEK STREAK" as a layered chip: a dark tile lifted off an offset lime plate, with a lime strip on its
 * left edge and, when given, a "2× this week" tag overlapping its bottom-right corner.
 */
export function StreakChip({ weeks, thisWeekWorkouts, className }: { weeks: number; thisWeekWorkouts?: number; className?: string }) {
  return (
    <div className={className ? `week-streak ${className}` : 'week-streak'} role="img" aria-label={t.streakAria(weeks, thisWeekWorkouts ?? 0)}>
      <span className="week-streak-front">
        <span className="week-streak-icon">
          <StreakIcon />
        </span>
        <strong className="num">{weeks}</strong>
        <small>{t.streakLabel}</small>
      </span>
      {thisWeekWorkouts != null && thisWeekWorkouts > 0 && <span className="week-streak-tag">{t.streakThisWeek(thisWeekWorkouts)}</span>}
    </div>
  )
}
