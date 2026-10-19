// TanStack Query cache keys, shared so any screen can refresh another screen's data after a change.
export const queryKeys = {
  workouts: ['workouts'] as const,
  workout: (id: number) => ['workout', id] as const,
  // Under the workout's key, so refreshing a workout refreshes its plan too
  workoutPlan: (id: number) => ['workout', id, 'plan'] as const,
  workoutRecap: (id: number) => ['workout', id, 'summary'] as const,
  routines: ['routines'] as const,
  exercises: ['exercises'] as const,
  stats: ['stats'] as const,
  // Under stats, so logging or deleting sets refreshes the streak with the other numbers
  streak: ['stats', 'streak'] as const,
  profile: ['profile'] as const,
  weight: ['weight'] as const,
  // Under weight, so saving an entry refreshes every range at once
  weightHistory: (days: number) => ['weight', days] as const,
  passkeys: ['passkeys'] as const,
  nutrition: ['nutrition'] as const,
  nutritionDay: (date: string) => ['nutrition', 'day', date] as const,
  frequentFoods: ['nutrition', 'frequent'] as const,
  foods: ['foods'] as const,
  lolPlayers: ['lol', 'players'] as const,
  lolPlayer: (puuid: string) => ['lol', 'player', puuid] as const,
  lolRankHistory: (puuid: string) => ['lol', 'rank-history', puuid] as const,
  lolGroup: ['lol', 'group'] as const,
  ddragonVersion: ['ddragon', 'version'] as const,
  ddragonSummonerSpells: (version: string) => ['ddragon', 'summoner', version] as const,
  avatar: (version: number) => ['avatar', version] as const,
}
