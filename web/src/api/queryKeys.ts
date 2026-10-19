// TanStack Query cache keys, shared so any screen can refresh another screen's data after a change.
export const queryKeys = {
  workouts: ['workouts'] as const,
  workout: (id: number) => ['workout', id] as const,
  exercises: ['exercises'] as const,
  stats: ['stats'] as const,
  profile: ['profile'] as const,
  passkeys: ['passkeys'] as const,
  nutrition: ['nutrition'] as const,
  nutritionDay: (date: string) => ['nutrition', 'day', date] as const,
  frequentFoods: ['nutrition', 'frequent'] as const,
  foods: ['foods'] as const,
  lolPlayers: ['lol', 'players'] as const,
  lolPlayer: (puuid: string) => ['lol', 'player', puuid] as const,
  lolGroup: ['lol', 'group'] as const,
  ddragonVersion: ['ddragon', 'version'] as const,
  avatar: (version: number) => ['avatar', version] as const,
}
