import type { ComponentType } from 'react'

// Every page except the start page is its own chunk: the chart library, the body map and the League
// helpers only download when (or just before) a page that uses them opens.

export const pageLoaders = {
  nutrition: () => import('../pages/NutritionPage').then((m) => ({ default: m.NutritionPage })),
  progress: () => import('../pages/ProgressPage').then((m) => ({ default: m.ProgressPage })),
  game: () => import('../pages/GamePage').then((m) => ({ default: m.GamePage })),
  profile: () => import('../pages/ProfilePage').then((m) => ({ default: m.ProfilePage })),
  workoutDetail: () => import('../pages/WorkoutDetailPage').then((m) => ({ default: m.WorkoutDetailPage })),
  lolPlayer: () => import('../pages/LolPlayerPage').then((m) => ({ default: m.LolPlayerPage })),
  lolMatch: () => import('../pages/LolMatchPage').then((m) => ({ default: m.LolMatchPage })),
} satisfies Record<string, () => Promise<{ default: ComponentType }>>

type PageName = keyof typeof pageLoaders

/** Bottom tabs first: those are what a swipe or a tap opens next, so they are warmed before detail pages. */
export const PREFETCH_ORDER: readonly PageName[] = ['nutrition', 'progress', 'game', 'profile', 'workoutDetail', 'lolPlayer', 'lolMatch']

/**
 * Imports every lazy page one after another. Run once the first screen is up: later tab switches then find
 * the module already loaded, and React renders it without ever showing the fallback. Sequential so the
 * downloads do not crowd out the start page's own API calls on a slow mobile connection.
 */
export async function prefetchRoutePages(loaders: Record<PageName, () => Promise<unknown>> = pageLoaders): Promise<void> {
  for (const name of PREFETCH_ORDER) {
    // A failed prefetch (offline before the service worker cached it) is simply retried by the navigation
    await loaders[name]().catch(() => undefined)
  }
}
