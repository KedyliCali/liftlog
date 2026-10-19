import { lazy, Suspense, type ReactNode } from 'react'
import { DetailSkeleton, NutritionSkeleton, ProfileSkeleton, ProgressSkeleton, SetListSkeleton, WorkoutListSkeleton } from '../components/Skeletons'
import { t } from '../lib/i18n'
import { pageLoaders } from './pageLoaders'

const NutritionPage = lazy(pageLoaders.nutrition)
const ProgressPage = lazy(pageLoaders.progress)
const GamePage = lazy(pageLoaders.game)
const ProfilePage = lazy(pageLoaders.profile)
const WorkoutDetailPage = lazy(pageLoaders.workoutDetail)
const LolPlayerPage = lazy(pageLoaders.lolPlayer)
const LolMatchPage = lazy(pageLoaders.lolMatch)

/**
 * The page's own header over the same skeleton it shows while its data loads, so the chunk arriving and
 * then the data arriving look like one continuous load instead of a blank flash.
 */
function PageFallback({ title, subtitle, children }: { title?: string; subtitle?: string; children: ReactNode }) {
  return (
    <div className="page" data-testid="page-fallback">
      {title && (
        <header className="page-header">
          <h1 className="page-title">{title}</h1>
          {subtitle && <p className="page-subtitle">{subtitle}</p>}
        </header>
      )}
      {children}
    </div>
  )
}

export function LazyNutritionPage() {
  return (
    <Suspense fallback={<PageFallback title={t.nutritionTitle} subtitle={t.nutritionSubtitle}><NutritionSkeleton /></PageFallback>}>
      <NutritionPage />
    </Suspense>
  )
}

export function LazyProgressPage() {
  return (
    <Suspense fallback={<PageFallback title={t.progressTitle} subtitle={t.progressSubtitle}><ProgressSkeleton /></PageFallback>}>
      <ProgressPage />
    </Suspense>
  )
}

export function LazyGamePage() {
  return (
    <Suspense fallback={<PageFallback title={t.gameTitle} subtitle={t.gameSubtitle}><WorkoutListSkeleton /></PageFallback>}>
      <GamePage />
    </Suspense>
  )
}

export function LazyProfilePage() {
  return (
    <Suspense fallback={<PageFallback title={t.profileTitle}><ProfileSkeleton /></PageFallback>}>
      <ProfilePage />
    </Suspense>
  )
}

export function LazyWorkoutDetailPage() {
  return (
    <Suspense fallback={<PageFallback><DetailSkeleton /></PageFallback>}>
      <WorkoutDetailPage />
    </Suspense>
  )
}

export function LazyLolPlayerPage() {
  return (
    <Suspense fallback={<PageFallback><SetListSkeleton /></PageFallback>}>
      <LolPlayerPage />
    </Suspense>
  )
}

export function LazyLolMatchPage() {
  return (
    <Suspense fallback={<PageFallback><SetListSkeleton /></PageFallback>}>
      <LolMatchPage />
    </Suspense>
  )
}
