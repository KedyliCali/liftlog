import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { AppError } from '../api/client'
import { queryKeys } from '../api/queryKeys'
import { createPasskey } from '../api/session'
import { errorMessage } from '../lib/format'
import { t } from '../lib/i18n'
import { useToast } from './contexts'

/**
 * Runs "Back up with Face ID" with the shared feedback: a toast on success, silence when the user closes
 * the Face ID sheet, "Tap again" when Safari refused after a slow options request, and the passkey list
 * refetched so Profile shows the new state.
 */
export function useCreatePasskey(onCreated?: () => void) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)

  const create = async () => {
    setBusy(true)
    try {
      const result = await createPasskey()
      if (result === 'tapAgain') toast({ message: t.passkeyTapAgain })
      if (result === 'done') {
        toast({ message: t.backupCreated })
        onCreated?.()
        void queryClient.invalidateQueries({ queryKey: queryKeys.passkeys })
      }
    } catch (error) {
      // Server problems have their own words; a browser refusal (no passkey support for this site) does not
      toast({ message: error instanceof AppError ? errorMessage(error) : t.backupFailed })
    } finally {
      setBusy(false)
    }
  }
  return { create, busy }
}
