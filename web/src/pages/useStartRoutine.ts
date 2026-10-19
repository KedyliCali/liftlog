import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { Routine } from '../api/types'
import { useToast } from '../components/contexts'
import { errorMessage } from '../lib/format'
import { todayIso } from '../lib/nutrition'

/** One tap on a routine: the server creates the linked workout, then its page opens in workout mode. */
export function useStartRoutine(onStarted?: () => void) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const toast = useToast()
  const [startingId, setStartingId] = useState<number | null>(null)
  // The state only disables the chips after a re-render; the ref also stops a double tap in between
  const busy = useRef(false)

  const start = async (routine: Routine, workoutDate?: string) => {
    if (busy.current) return
    busy.current = true
    setStartingId(routine.id)
    try {
      // The phone's own date: the server's "today" is UTC, which is still yesterday in Turkey until 03:00
      const workout = await api.startRoutine(routine.id, workoutDate ?? todayIso())
      queryClient.setQueryData(queryKeys.workout(workout.id), workout)
      await queryClient.invalidateQueries({ queryKey: queryKeys.workouts })
      onStarted?.()
      navigate(`/workouts/${workout.id}`, { viewTransition: true })
    } catch (error) {
      toast({ message: errorMessage(error) })
      busy.current = false
      setStartingId(null)
    }
  }

  return { start, startingId }
}
