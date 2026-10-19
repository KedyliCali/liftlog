import type { RouteObject } from 'react-router'
import { WorkoutsPage } from '../pages/WorkoutsPage'
import {
  LazyGamePage,
  LazyLolMatchPage,
  LazyLolPlayerPage,
  LazyNutritionPage,
  LazyProfilePage,
  LazyProgressPage,
  LazyWorkoutDetailPage,
} from './LazyPages'

/** The pages under the bottom-nav layout. Exported on their own so a test can mount them without the app shell. */
export const pageRoutes: RouteObject[] = [
  // The start page stays in the entry chunk, so the first screen paints without waiting for a second request
  { path: '/', element: <WorkoutsPage /> },
  { path: '/workouts/:id', element: <LazyWorkoutDetailPage /> },
  { path: '/nutrition', element: <LazyNutritionPage /> },
  { path: '/progress', element: <LazyProgressPage /> },
  { path: '/game', element: <LazyGamePage /> },
  { path: '/game/:puuid', element: <LazyLolPlayerPage /> },
  // Two segments after /game, so it never collides with a player's puuid
  { path: '/game/match/:matchId', element: <LazyLolMatchPage /> },
  { path: '/profile', element: <LazyProfilePage /> },
]
