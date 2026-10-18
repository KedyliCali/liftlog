import { useEffect, useState, useSyncExternalStore, type ReactNode } from 'react'
import { AppError } from './api/client'
import { hasDeviceCredentials, session, signInDevice, startFresh } from './api/session'
import { SignedOutScreen, WelcomeScreen } from './components/Recovery'
import { ErrorState, Loading } from './components/StateViews'

/** A full reload after switching accounts, so nothing from the previous account stays in memory. */
const reloadHome = () => window.location.replace('/')

/**
 * Root gate. With a token the app renders. Without one: a device that has never had an account sees the
 * welcome screen (start or restore), a known device signs in quietly, and a device whose recovery code was
 * replaced elsewhere is told so instead of silently getting an empty account.
 */
export function SessionGate({ children, onRestored = reloadHome }: { children: ReactNode; onRestored?: () => void }) {
  const token = useSyncExternalStore(session.subscribe, session.getToken)
  const [welcome, setWelcome] = useState(() => !token && !hasDeviceCredentials())
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (token || welcome) return
    signInDevice().catch(setError)
  }, [token, welcome])

  if (token) return <>{children}</>
  if (welcome) return <WelcomeScreen onStart={() => setWelcome(false)} onRestored={onRestored} />
  if (error instanceof AppError && error.kind === 'signedOut') {
    return (
      <SignedOutScreen
        onRestored={onRestored}
        onStartFresh={() => {
          setError(null)
          startFresh().catch(setError)
        }}
      />
    )
  }
  if (error) {
    return (
      <ErrorState
        error={error}
        onRetry={() => {
          setError(null)
          signInDevice().catch(setError)
        }}
      />
    )
  }
  return <Loading />
}
