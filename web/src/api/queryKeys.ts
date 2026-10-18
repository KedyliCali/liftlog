// TanStack Query cache keys, shared so any screen can refresh another screen's data after a change.
export const queryKeys = {
  workouts: ['workouts'] as const,
  workout: (id: number) => ['workout', id] as const,
  exercises: ['exercises'] as const,
  stats: ['stats'] as const,
  profile: ['profile'] as const,
  avatar: (version: number) => ['avatar', version] as const,
}
