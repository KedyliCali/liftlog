import { useQuery } from '@tanstack/react-query'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { RecapRecord, WorkoutRecap } from '../api/types'
import { useToast } from '../components/contexts'
import { CountUp } from '../components/CountUp'
import { RecapMuscles } from '../components/MuscleMapCard'
import { ShareIcon, TrophyIcon } from '../components/Icons'
import { Sheet } from '../components/Sheet'
import { ErrorState } from '../components/StateViews'
import { StreakChip } from '../components/StreakChip'
import { formatKg, formatLongDate } from '../lib/format'
import { t } from '../lib/i18n'
import { formatSignedKg, recapShareText, volumeDelta } from '../lib/recap'

/** "Finish": the server-computed summary of a workout, with a share button. */
export function WorkoutRecapSheet({ workoutId, onClose, onDone }: { workoutId: number; onClose: () => void; onDone: () => void }) {
  const query = useQuery({ queryKey: queryKeys.workoutRecap(workoutId), queryFn: () => api.workoutRecap(workoutId) })

  return (
    <Sheet title={t.recapTitle} onClose={onClose}>
      {query.isPending ? (
        <div className="recap-loading">
          <span className="spinner" />
        </div>
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : (
        <RecapCard recap={query.data} onDone={onDone} />
      )}
    </Sheet>
  )
}

function RecapCard({ recap, onDone }: { recap: WorkoutRecap; onDone: () => void }) {
  const toast = useToast()
  const delta = volumeDelta(recap)
  const records = recap.personalRecords

  const share = async () => {
    const text = recapShareText(recap)
    if (navigator.share) {
      try {
        await navigator.share({ text })
        return
      } catch (error) {
        // Closing the share sheet is not a failure; anything else falls back to copying
        if (error instanceof DOMException && error.name === 'AbortError') return
      }
    }
    try {
      await navigator.clipboard.writeText(text)
      toast({ message: t.recapCopied })
    } catch {
      toast({ message: t.copyFailed })
    }
  }

  return (
    <div className="recap" data-testid="workout-recap">
      {/* Hero: the day's volume on a dark plate lifted off a lime one, the comparison tag straddling its bottom edge */}
      <div className="recap-hero">
        <div className="recap-hero-front">
          <small className="recap-date">{formatLongDate(recap.workoutDate)}</small>
          <p className="recap-lifted">
            <CountUp value={recap.totalVolumeKg} format={(n) => t.recapLifted(formatKg(n))} />
          </p>
        </div>
        <span className={`recap-delta ${deltaTone(delta)}`} data-testid="recap-delta">
          {delta == null ? t.recapFirstTime : delta === 0 ? t.recapSameAsLast : t.recapVsLast(formatSignedKg(delta))}
        </span>
      </div>

      <div className="recap-stats">
        <div className="recap-stat">
          <strong className="num">{recap.sets}</strong>
          <small>{t.labelSets}</small>
        </div>
        <div className="recap-stat">
          <strong className="num">{recap.exercises}</strong>
          <small>{t.labelExercises}</small>
        </div>
        {recap.weekStreak > 0 && <StreakChip weeks={recap.weekStreak} className="recap-streak" />}
      </div>

      {records.length > 0 && (
        <section className="recap-records" aria-label={t.recapRecords(records.length)}>
          <h3 className="recap-records-title">
            <TrophyIcon size={18} />
            {t.recapRecords(records.length)}
          </h3>
          <ul>
            {records.map((record) => (
              <li key={record.exerciseName} className="recap-record">
                <span className="recap-record-name">{record.exerciseName}</span>
                <span className="recap-record-set num">{t.weightTimesReps(formatKg(record.weightKg), record.reps)}</span>
                <span className="recap-record-kind">{kindLabel(record)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <RecapMuscles muscles={recap.muscles} />

      <div className="recap-actions">
        <button className="btn btn-primary" onClick={share} data-testid="recap-share">
          <ShareIcon /> {t.recapShare}
        </button>
        <button className="btn btn-tonal" onClick={onDone}>
          {t.recapDone}
        </button>
      </div>
    </div>
  )
}

function deltaTone(delta: number | null): string {
  if (delta == null || delta === 0) return 'flat'
  return delta > 0 ? 'up' : 'down'
}

const kindLabel = (record: RecapRecord) => (record.kind === 'WEIGHT' ? t.recordWeight : t.recordE1rm)
