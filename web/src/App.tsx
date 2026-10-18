import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense, useEffect, useState, useSyncExternalStore } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AppError } from './api/client'
import { session, signInDevice } from './api/session'
import { Layout } from './components/Layout'
import { RestTimerProvider } from './components/RestTimer'
import { ProgressSkeleton } from './components/Skeletons'
import { ErrorState, Loading } from './components/StateViews'
import { ToastProvider } from './components/Toast'
import { t } from './lib/i18n'
import { GamePage } from './pages/GamePage'
import { LolPlayerPage } from './pages/LolPlayerPage'
import { NutritionPage } from './pages/NutritionPage'
import { ProfilePage } from './pages/ProfilePage'
import { WorkoutDetailPage } from './pages/WorkoutDetailPage'
import { WorkoutsPage } from './pages/WorkoutsPage'

const loadProgressPage = () => import('./pages/ProgressPage')
const ProgressPage = lazy(() => loadProgressPage().then((m) => ({ default: m.ProgressPage })))
// The chart library stays out of the first load, but is fetched once the browser is idle so opening
// the tab rarely has to wait for it
const whenIdle = window.requestIdleCallback ?? ((callback: () => void) => setTimeout(callback, 1500))
whenIdle(() => void loadProgressPage())

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A 401 is handled by signing in again, and 404 will not fix itself; only retry transient failures
      retry: (failureCount, error) =>
        failureCount < 2 && error instanceof AppError && (error.kind === 'network' || error.kind === 'server'),
      staleTime: 30_000,
    },
  },
})

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <WorkoutsPage /> },
      { path: '/workouts/:id', element: <WorkoutDetailPage /> },
      { path: '/nutrition', element: <NutritionPage /> },
      { path: '/game', element: <GamePage /> },
      { path: '/game/:puuid', element: <LolPlayerPage /> },
      {
        path: '/progress',
        element: (
          <Suspense
            fallback={
              <div className="page">
                <header className="page-header">
                  <h1 className="page-title">{t.progressTitle}</h1>
                  <p className="page-subtitle">{t.progressSubtitle}</p>
                </header>
                <ProgressSkeleton />
              </div>
            }
          >
            <ProgressPage />
          </Suspense>
        ),
      },
      { path: '/profile', element: <ProfilePage /> },
    ],
  },
])

/** Root gate: without a token, sign in with this browser's hidden account before showing anything. */
function SessionGate() {
  const token = useSyncExternalStore(session.subscribe, session.getToken)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!token) signInDevice().catch(setError)
  }, [token])

  const retry = () => {
    setError(null)
    signInDevice().catch(setError)
  }

  if (token) return <RouterProvider router={router} />
  if (error) return <ErrorState error={error} onRetry={retry} />
  return <Loading />
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RestTimerProvider>
          <SessionGate />
        </RestTimerProvider>
      </ToastProvider>
    </QueryClientProvider>
  )
}
