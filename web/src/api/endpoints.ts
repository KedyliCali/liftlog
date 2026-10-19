import type { CreationOptionsJson, CredentialJson, RequestOptionsJson } from '../lib/webauthn'
import { request, requestFile } from './client'
import type { AuthResponse, GroupTraining, LolDeepStats, LolGroup, LolGroupDuo, LolMatchDetail, LolPlayer, LolPlayerDetail, LolRankPoint, LolStatsQueue, LolStatsWindow, LolTimeline, CatalogFood, MuscleStats, Exercise, FoodEntry, FoodInput, FrequentFood, NutritionDay, NutritionGoals, Page, Passkey, PasskeyCeremony, PersonalRecord, PlanItem, Profile, ProgressPoint, Routine, RoutineInput, SetInput, Streak, WeeklyVolume, WeightEntry, WeightHistory, WorkoutDetail, WorkoutRecap, WorkoutSet, WorkoutSummary } from './types'

export const api = {
  register: (email: string, password: string) =>
    request<AuthResponse>('/api/v1/auth/register', { method: 'POST', body: { email, password }, anonymous: true }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/v1/auth/login', { method: 'POST', body: { email, password }, anonymous: true }),

  exercises: () => request<Exercise[]>('/api/v1/exercises'),
  createExercise: (name: string, muscleGroup: string | null, bodyweight: boolean) =>
    request<Exercise>('/api/v1/exercises', { method: 'POST', body: { name, muscleGroup, bodyweight } }),

  workouts: (page: number, size = 20) => request<Page<WorkoutSummary>>(`/api/v1/workouts?page=${page}&size=${size}`),
  workout: (id: number) => request<WorkoutDetail>(`/api/v1/workouts/${id}`),
  createWorkout: (workoutDate: string, notes: string | null) =>
    request<WorkoutDetail>('/api/v1/workouts', { method: 'POST', body: { workoutDate, notes } }),
  updateWorkout: (id: number, workoutDate: string, notes: string | null) =>
    request<WorkoutDetail>(`/api/v1/workouts/${id}`, { method: 'PUT', body: { workoutDate, notes } }),
  deleteWorkout: (id: number) => request<void>(`/api/v1/workouts/${id}`, { method: 'DELETE', responseType: 'none' }),

  addSet: (workoutId: number, input: SetInput) =>
    request<WorkoutSet>(`/api/v1/workouts/${workoutId}/sets`, { method: 'POST', body: input }),
  updateSet: (id: number, input: SetInput) => request<WorkoutSet>(`/api/v1/sets/${id}`, { method: 'PUT', body: input }),
  deleteSet: (id: number) => request<void>(`/api/v1/sets/${id}`, { method: 'DELETE', responseType: 'none' }),

  routines: () => request<Routine[]>('/api/v1/routines'),
  createRoutine: (input: RoutineInput) => request<Routine>('/api/v1/routines', { method: 'POST', body: input }),
  updateRoutine: (id: number, input: RoutineInput) => request<Routine>(`/api/v1/routines/${id}`, { method: 'PUT', body: input }),
  deleteRoutine: (id: number) => request<void>(`/api/v1/routines/${id}`, { method: 'DELETE', responseType: 'none' }),
  /** Without a name the server uses the workout's note */
  routineFromWorkout: (workoutId: number, name?: string) =>
    request<Routine>(`/api/v1/routines/from-workout/${workoutId}`, { method: 'POST', body: name ? { name } : {} }),
  /** Creates an empty workout linked to the routine (today unless a date is given) */
  startRoutine: (id: number, workoutDate?: string) =>
    request<WorkoutDetail>(`/api/v1/routines/${id}/start`, { method: 'POST', body: workoutDate ? { workoutDate } : {} }),
  workoutPlan: (workoutId: number) => request<PlanItem[]>(`/api/v1/workouts/${workoutId}/plan`),
  workoutRecap: (workoutId: number) => request<WorkoutRecap>(`/api/v1/workouts/${workoutId}/summary`),

  personalRecords: () => request<PersonalRecord[]>('/api/v1/stats/prs'),
  progress: (exerciseId: number) => request<ProgressPoint[]>(`/api/v1/stats/progress?exerciseId=${exerciseId}`),
  streak: () => request<Streak>('/api/v1/stats/streak'),
  /** `today` is the phone's date, so "the last 7 days" ends on the user's day, not the server's */
  muscleStats: (days: 1 | 7 | 30, today: string) => request<MuscleStats>(`/api/v1/stats/muscles?days=${days}&today=${today}`),
  weeklyVolume: (weeks: number) => request<WeeklyVolume[]>(`/api/v1/stats/volume?weeks=${weeks}`),

  nutritionDay: (date: string) => request<NutritionDay>(`/api/v1/nutrition/days/${date}`),
  addFood: (input: FoodInput) => request<FoodEntry>('/api/v1/nutrition/entries', { method: 'POST', body: input }),
  /** Copies every entry of sourceDate onto date and returns the new entries (for undo). */
  copyDay: (date: string, sourceDate: string) => request<FoodEntry[]>(`/api/v1/nutrition/days/${date}/copy-from/${sourceDate}`, { method: 'POST' }),
  deleteFood: (id: number) => request<void>(`/api/v1/nutrition/entries/${id}`, { method: 'DELETE', responseType: 'none' }),
  frequentFoods: () => request<FrequentFood[]>('/api/v1/nutrition/frequent'),
  // The response is cached for a day; the app version in the URL makes each release fetch the list anew
  foods: () => request<CatalogFood[]>(`/api/v1/foods?v=${__APP_VERSION__}`),
  updateNutritionGoals: (goals: NutritionGoals) => request<NutritionGoals>('/api/v1/nutrition/goals', { method: 'PUT', body: goals }),

  lolPlayers: () => request<LolPlayer[]>('/api/v1/lol/players'),
  trackLolPlayer: (riotId: string, platform: string) =>
    request<LolPlayer>('/api/v1/lol/players', { method: 'POST', body: { riotId, platform } }),
  lolPlayer: (puuid: string) => request<LolPlayerDetail>(`/api/v1/lol/players/${encodeURIComponent(puuid)}`),
  refreshLolPlayer: (puuid: string) =>
    request<LolPlayerDetail>(`/api/v1/lol/players/${encodeURIComponent(puuid)}/refresh`, { method: 'POST' }),
  /** Solo/duo rank snapshots, oldest first */
  lolRankHistory: (puuid: string) => request<LolRankPoint[]>(`/api/v1/lol/players/${encodeURIComponent(puuid)}/rank-history`),
  untrackLolPlayer: (puuid: string) =>
    request<void>(`/api/v1/lol/players/${encodeURIComponent(puuid)}`, { method: 'DELETE', responseType: 'none' }),
  /** tzOffsetMinutes (east of UTC, e.g. 180 for Turkey) puts the time-of-day and weekday split on the phone's clock */
  lolStats: (puuid: string, queue: LolStatsQueue, games: LolStatsWindow, tzOffsetMinutes: number) =>
    request<LolDeepStats>(`/api/v1/lol/players/${encodeURIComponent(puuid)}/stats?queue=${queue}&games=${games}&tzOffsetMinutes=${tzOffsetMinutes}`),
  lolMatch: (matchId: string) => request<LolMatchDetail>(`/api/v1/lol/matches/${encodeURIComponent(matchId)}`),
  /** The first request asks Riot (503 when it is unavailable, 404 when it no longer has the match) */
  lolTimeline: (matchId: string) => request<LolTimeline>(`/api/v1/lol/matches/${encodeURIComponent(matchId)}/timeline`),

  // Not being in a group is a 204, which arrives as undefined; TanStack Query rejects undefined data, so it becomes null
  lolGroup: () => request<LolGroup | undefined>('/api/v1/lol/group').then((group) => group ?? null),
  /** Group members who played together; 204 (not in a group) becomes null, like lolGroup */
  lolGroupDuos: () => request<LolGroupDuo[] | undefined>('/api/v1/lol/group/duos').then((duos) => duos ?? null),
  createLolGroup: (name: string) => request<LolGroup>('/api/v1/lol/group', { method: 'POST', body: { name } }),
  joinLolGroup: (code: string) => request<LolGroup>('/api/v1/lol/group/join', { method: 'POST', body: { code } }),
  renameLolGroup: (name: string) => request<LolGroup>('/api/v1/lol/group', { method: 'PUT', body: { name } }),
  rotateLolInviteCode: () => request<LolGroup>('/api/v1/lol/group/invite-code', { method: 'POST' }),
  linkLolAccount: (riotId: string, platform: string) =>
    request<LolGroup>('/api/v1/lol/group/me/account', { method: 'PUT', body: { riotId, platform } }),
  leaveLolGroup: () => request<void>('/api/v1/lol/group/me', { method: 'DELETE', responseType: 'none' }),
  removeLolGroupMember: (userId: number) =>
    request<void>(`/api/v1/lol/group/members/${userId}`, { method: 'DELETE', responseType: 'none' }),
  /** The same group compared on training; 204 (not in a group) becomes null, like lolGroup */
  groupTraining: (days: 7 | 30, today: string) =>
    request<GroupTraining | undefined>(`/api/v1/group/training?days=${days}&today=${today}`).then((training) => training ?? null),
  setShareTraining: (enabled: boolean) => request<Profile>('/api/v1/profile/share-training', { method: 'PUT', body: { enabled } }),

  /** The recovery code's "New code": the web app generates both passwords itself */
  changePassword: (currentPassword: string, newPassword: string) =>
    request<AuthResponse>('/api/v1/profile/password', { method: 'PUT', body: { currentPassword, newPassword } }),
  /** Passkey backup: start a registration, then send the phone's answer back with the same ceremony id */
  passkeyCreationOptions: () =>
    request<PasskeyCeremony<CreationOptionsJson>>('/api/v1/profile/passkeys/options', { method: 'POST' }),
  registerPasskey: (ceremonyId: string, credential: CredentialJson) =>
    request<Pick<Passkey, 'id' | 'createdAt'>>('/api/v1/profile/passkeys', { method: 'POST', body: { ceremonyId, credential } }),
  passkeys: () => request<Passkey[]>('/api/v1/profile/passkeys'),
  /** Anonymous: the passkey itself names the account, and deviceSecret becomes this device's own password */
  passkeyRequestOptions: () =>
    request<PasskeyCeremony<RequestOptionsJson>>('/api/v1/auth/passkey/options', { method: 'POST', anonymous: true }),
  signInWithPasskey: (ceremonyId: string, credential: CredentialJson, deviceSecret: string) =>
    request<AuthResponse>('/api/v1/auth/passkey', { method: 'POST', body: { ceremonyId, credential, deviceSecret }, anonymous: true }),
  /** Fire-and-forget: starts waking a sleeping server while the user reads the welcome screen */
  health: () => request<void>('/api/v1/health', { anonymous: true, responseType: 'none' }),

  profile: () => request<Profile>('/api/v1/profile'),
  updateProfile: (displayName: string | null, heightCm: number | null, bodyWeightKg: number | null) =>
    request<Profile>('/api/v1/profile', { method: 'PUT', body: { displayName, heightCm, bodyWeightKg } }),
  uploadAvatar: (jpeg: Blob) => request<Profile>('/api/v1/profile/avatar', { method: 'PUT', blob: jpeg }),
  avatar: () => request<Blob>('/api/v1/profile/avatar', { responseType: 'blob' }),
  deleteAvatar: () => request<void>('/api/v1/profile/avatar', { method: 'DELETE', responseType: 'none' }),

  /** `today` is the phone's date, so the window ends on the user's day */
  weightHistory: (days: 30 | 90 | 365, today: string) => request<WeightHistory>(`/api/v1/weight?days=${days}&today=${today}`),
  /** One entry per day: saving the same date again replaces it */
  saveWeight: (date: string, weightKg: number) => request<WeightEntry>(`/api/v1/weight/${date}`, { method: 'PUT', body: { weightKg } }),
  deleteWeight: (date: string) => request<void>(`/api/v1/weight/${date}`, { method: 'DELETE', responseType: 'none' }),
  exportWorkouts: () => requestFile('/api/v1/export/workouts.csv'),
  exportNutrition: () => requestFile('/api/v1/export/nutrition.csv'),
  exportAll: () => requestFile('/api/v1/export/all.zip'),
}
