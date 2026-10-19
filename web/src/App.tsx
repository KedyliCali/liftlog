import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AppError } from './api/client'
import { ConfirmProvider } from './components/Confirm'
import { Layout } from './components/Layout'
import { RestTimerProvider } from './components/RestTimer'
import { ToastProvider } from './components/Toast'
import { prefetchRoutePages } from './routing/pageLoaders'
import { pageRoutes } from './routing/pageRoutes'
import { SessionGate } from './SessionGate'

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

const router = createBrowserRouter([{ element: <Layout />, children: pageRoutes }])

// Safari has no requestIdleCallback; a short delay still lets the first screen and its API calls go first
const whenIdle = (callback: () => void) =>
  window.requestIdleCallback ? window.requestIdleCallback(callback, { timeout: 4000 }) : window.setTimeout(callback, 1500)

export function App() {
  // After the first paint, so the other pages' chunks never delay the start page
  useEffect(() => {
    whenIdle(() => void prefetchRoutePages())
  }, [])

  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <ConfirmProvider>
          <RestTimerProvider>
            <SessionGate>
              <RouterProvider router={router} />
            </SessionGate>
          </RestTimerProvider>
        </ConfirmProvider>
      </ToastProvider>
    </QueryClientProvider>
  )
}
