import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link, useViewTransitionState } from 'react-router'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { Routine, WorkoutSummary } from '../api/types'
import { RecoveryNudge } from '../components/RecoveryNudge'
import { WorkoutListSkeleton } from '../components/Skeletons'
import { ErrorState, MessageState } from '../components/StateViews'
import { StreakChip } from '../components/StreakChip'
import { SwipeToDelete } from '../components/SwipeToDelete'
import { useToast } from '../components/contexts'
import { formatKg, monthShort, parseDate, weekdayShort } from '../lib/format'
import { t } from '../lib/i18n'
import { routineSummary } from '../lib/routineLogic'
import type { RoutineTemplate } from '../lib/routineTemplates'
import { NewWorkoutSheet } from './NewWorkoutSheet'
import { RoutinesSheet } from './RoutinesSheet'
import { TemplateCards } from './RoutineTemplates'
import { useStartRoutine } from './useStartRoutine'
import { DumbbellIcon } from '../components/Icons'

export function WorkoutsPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [creating, setCreating] = useState(false)
  const [managingRoutines, setManagingRoutines] = useState(false)
  // A template tapped in the empty strip opens the Routines sheet on its preview
  const [previewTemplate, setPreviewTemplate] = useState<RoutineTemplate | null>(null)
  // An older server has no routines endpoint: the error just hides the strip
  const routines = useQuery({ queryKey: queryKeys.routines, queryFn: api.routines, retry: false })
  // Same for the streak: no chip on a server without the endpoint, and none before the first week counts
  const streak = useQuery({ queryKey: queryKeys.streak, queryFn: api.streak, retry: false })
  // Hidden while their undo window is open; the server delete happens when the toast expires
  const [pendingDelete, setPendingDelete] = useState<Set<number>>(new Set())

  const query = useInfiniteQuery({
    queryKey: queryKeys.workouts,
    queryFn: ({ pageParam }) => api.workouts(pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : last.page + 1),
  })

  const sentinel = useRef<HTMLDivElement>(null)
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = query
  useEffect(() => {
    const element = sentinel.current
    if (!element) return
    // Load the next page when the end of the list scrolls into view
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting && hasNextPage && !isFetchingNextPage) void fetchNextPage()
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  const removeWorkout = (workout: WorkoutSummary) => {
    setPendingDelete((s) => new Set(s).add(workout.id))
    const restore = () =>
      setPendingDelete((s) => {
        const next = new Set(s)
        next.delete(workout.id)
        return next
      })
    toast({
      message: t.workoutDeleted,
      actionLabel: t.undo,
      durationMs: 5000,
      onAction: restore,
      onTimeout: () =>
        api
          .deleteWorkout(workout.id)
          .catch(restore)
          .finally(() => queryClient.invalidateQueries({ queryKey: queryKeys.workouts }).then(restore)),
    })
  }

  const workouts = query.data?.pages.flatMap((page) => page.content).filter((w) => !pendingDelete.has(w.id)) ?? []

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">{t.workoutsTitle}</h1>
        <p className="page-subtitle">{t.workoutsSubtitle}</p>
        {streak.data && streak.data.weeks > 0 && (
          <StreakChip weeks={streak.data.weeks} thisWeekWorkouts={streak.data.thisWeekWorkouts} className="header-streak" />
        )}
        {routines.isSuccess && (
          <button className="routines-btn" onClick={() => setManagingRoutines(true)} data-testid="manage-routines">
            {t.routines}
          </button>
        )}
      </header>

      {routines.data && (routines.data.length > 0 ? <RoutineStrip routines={routines.data} /> : <TemplateStrip onSelect={setPreviewTemplate} />)}

      {query.isPending ? (
        <WorkoutListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : workouts.length === 0 ? (
        <MessageState icon={<DumbbellIcon size={32} />} title={t.workoutsEmptyTitle} body={t.workoutsEmptyBody} />
      ) : (
        <>
          <RecoveryNudge />
          <div className="workout-list">
            {workouts.map((workout, index) => (
              <SwipeToDelete key={workout.id} label={workout.notes ?? t.untitledWorkout} onDelete={() => removeWorkout(workout)}>
                <WorkoutCard workout={workout} index={index} />
              </SwipeToDelete>
            ))}
            <div ref={sentinel} />
            {isFetchingNextPage && <div className="spinner small" style={{ margin: '0 auto' }} />}
          </div>
        </>
      )}

      <button className="fab" onClick={() => setCreating(true)} data-testid="fab-new-workout">
        <span className="fab-plus">+</span> {t.newWorkout}
      </button>
      {creating && <NewWorkoutSheet routines={routines.data ?? []} offerTemplates={routines.isSuccess} onClose={() => setCreating(false)} />}
      {(managingRoutines || previewTemplate) && (
        <RoutinesSheet
          initialTemplate={previewTemplate}
          onClose={() => {
            setManagingRoutines(false)
            setPreviewTemplate(null)
          }}
        />
      )}
    </div>
  )
}

/** "Start a routine": one chip per routine, each a tile with a lime plate offset behind it. */
function RoutineStrip({ routines }: { routines: Routine[] }) {
  const { start, startingId } = useStartRoutine()
  return (
    <section className="routine-strip" aria-label={t.startWithRoutine}>
      <h2 className="routine-strip-title">{t.startWithRoutine}</h2>
      <div className="routine-chips">
        {routines.map((routine) => (
          <button
            key={routine.id}
            className={startingId === routine.id ? 'routine-chip starting' : 'routine-chip'}
            onClick={() => start(routine)}
            disabled={startingId !== null}
            title={routineSummary(routine.exercises)}
          >
            <span className="routine-chip-front">
              <strong>{routine.name}</strong>
              <small>{startingId === routine.id ? t.starting : t.exercises(routine.exercises.length)}</small>
            </span>
          </button>
        ))}
      </div>
    </section>
  )
}

/** No routines yet: the same spot offers the ready-made templates instead. */
function TemplateStrip({ onSelect }: { onSelect: (template: RoutineTemplate) => void }) {
  return (
    <section className="routine-strip" aria-label={t.readyTemplates}>
      <h2 className="routine-strip-title">{t.startWithRoutine}</h2>
      <p className="template-strip-hint">{t.noRoutinesYet}</p>
      <TemplateCards variant="strip" onSelect={onSelect} />
    </section>
  )
}

function WorkoutCard({ workout, index }: { workout: WorkoutSummary; index: number }) {
  const to = `/workouts/${workout.id}`
  // Only the tapped card gets the shared name, so it morphs into the detail page's summary card
  const opening = useViewTransitionState(to)
  return (
    <Link
      viewTransition
      to={to}
      className="workout-card"
      // Stagger the entrance of the first few cards; later pages appear immediately
      style={{ animationDelay: `${Math.min(index, 8) * 40}ms` }}
      draggable={false}
    >
      <div className="workout-card-body" style={{ viewTransitionName: opening ? 'workout-hero' : undefined }}>
        <h3 className="workout-card-title">{workout.notes ?? t.untitledWorkout}</h3>
        <div className="chips">
          <span className="chip">{t.sets(workout.setCount)}</span>
          <span className="chip">{t.exercises(workout.exerciseCount)}</span>
        </div>
        <span className="accent num">{t.volume(formatKg(workout.totalVolumeKg))}</span>
      </div>
      <div className="date-block" aria-hidden>
        <div className="date-block-front">
          <small>{weekdayShort(workout.workoutDate)}</small>
          <strong>{parseDate(workout.workoutDate).getDate()}</strong>
          <small>{monthShort(workout.workoutDate)}</small>
        </div>
      </div>
    </Link>
  )
}
