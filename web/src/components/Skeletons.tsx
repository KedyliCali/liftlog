import type { CSSProperties, ReactNode } from 'react'
import { t } from '../lib/i18n'
import { WakeUpHint } from './StateViews'

// Placeholders shaped like the real cards (same classes, greyed out), so content drops into place
// instead of the layout jumping when data arrives.

function Bone({ width, height = 14, style }: { width: string | number; height?: number; style?: CSSProperties }) {
  return <span className="bone" style={{ width, height, ...style }} />
}

function SkeletonFrame({ children }: { children: ReactNode }) {
  return (
    <div className="skeleton" role="status" aria-label={t.loading}>
      {children}
      <WakeUpHint />
    </div>
  )
}

export function WorkoutListSkeleton() {
  return (
    <SkeletonFrame>
      <div className="workout-list">
        {[0.62, 0.48, 0.7, 0.55].map((titleWidth, i) => (
          <div key={i} className="workout-card" style={{ animationDelay: `${i * 60}ms` }}>
            <div className="workout-card-body">
              <Bone width={`${titleWidth * 100}%`} height={20} />
              <div className="chips">
                <Bone width={52} height={24} />
                <Bone width={72} height={24} />
              </div>
              <Bone width={96} />
            </div>
            <div className="date-block">
              <div className="date-block-front" />
            </div>
          </div>
        ))}
      </div>
    </SkeletonFrame>
  )
}

export function SetListSkeleton() {
  return (
    <SkeletonFrame>
      {[3, 2].map((rows, i) => (
        <section key={i} className="exercise-group" style={{ animationDelay: `${i * 60}ms` }}>
          <div className="exercise-rail" aria-hidden />
          <div className="exercise-group-body">
            <Bone width="45%" height={18} style={{ margin: '4px 0 12px' }} />
            {Array.from({ length: rows }, (_, row) => (
              <div key={row} className="skeleton-set-row">
                <Bone width={28} height={22} />
                <Bone width={72} height={18} />
                <Bone width={36} height={18} />
              </div>
            ))}
          </div>
        </section>
      ))}
    </SkeletonFrame>
  )
}

export function DetailSkeleton() {
  return (
    <>
      <div className="summary skeleton">
        <div className="summary-card">
          <Bone width={140} />
          <Bone width="60%" height={30} style={{ margin: '10px 0 18px' }} />
          <div className="summary-stats">
            <Bone width={40} height={34} />
            <Bone width={40} height={34} />
          </div>
        </div>
        <div className="volume-badge">
          <Bone width={70} height={12} />
          <Bone width={90} height={22} />
        </div>
      </div>
      <SetListSkeleton />
    </>
  )
}

export function ProgressSkeleton() {
  return (
    <SkeletonFrame>
      <div className="chip-row">
        <Bone width={82} height={36} />
        <Bone width={110} height={36} />
        <Bone width={70} height={36} />
      </div>
      <div className="record-highlight">
        <div className="stat-tile">
          <Bone width="50%" height={12} />
          <Bone width="80%" height={24} style={{ margin: '10px 0' }} />
          <Bone width="40%" height={12} />
        </div>
        <div className="stat-tile">
          <Bone width="60%" height={12} />
          <Bone width="70%" height={24} style={{ margin: '10px 0' }} />
          <Bone width="30%" height={12} />
        </div>
      </div>
      <section className="chart-card">
        <Bone width="40%" height={20} />
        <Bone width="70%" height={12} style={{ margin: '10px 0 18px' }} />
        <div className="skeleton-chart">
          {[40, 65, 50, 80, 60, 90, 75].map((h, i) => (
            <span key={i} className="bone" style={{ height: `${h}%` }} />
          ))}
        </div>
      </section>
    </SkeletonFrame>
  )
}

export function NutritionSkeleton() {
  return (
    <SkeletonFrame>
      <div className="goal-rings skeleton-rings">
        <span className="bone ring-calories" />
        <span className="bone ring-protein" />
      </div>
      <div className="chip-row">
        <Bone width={120} height={52} />
        <Bone width={100} height={52} />
        <Bone width={110} height={52} />
      </div>
      <div className="food-list">
        {[0.5, 0.65, 0.4].map((w, i) => (
          <div key={i} className="food-row">
            <Bone width={`${w * 100}%`} height={16} />
            <Bone width={56} height={16} style={{ marginLeft: 'auto' }} />
          </div>
        ))}
      </div>
    </SkeletonFrame>
  )
}

export function ProfileSkeleton() {
  return (
    <SkeletonFrame>
      <div className="skeleton-profile">
        <Bone width={96} height={96} style={{ borderRadius: '50%' }} />
        <Bone width={160} height={22} />
      </div>
      <div className="stack">
        {[0, 1, 2].map((i) => (
          <Bone key={i} width="100%" height={56} style={{ borderRadius: 'var(--radius-m)' }} />
        ))}
      </div>
    </SkeletonFrame>
  )
}
