// Mirrors the backend JSON contract (backend/src/main/java/com/behlul/liftlog/dto).

export interface Exercise {
  id: number
  name: string
  muscleGroup: string | null
  custom: boolean
  /** Calisthenics: the logged weight is the added load, and body weight counts in stats */
  bodyweight: boolean
}

export interface WorkoutSummary {
  id: number
  workoutDate: string // ISO date, e.g. "2026-10-17"
  notes: string | null
  setCount: number
  exerciseCount: number
  totalVolumeKg: number
}

export interface WorkoutSet {
  id: number
  exerciseId: number
  exerciseName: string
  setOrder: number
  reps: number
  weightKg: number
  rpe: number | null
  newPersonalRecord: boolean
  /** weightKg is the added load (0 = bodyweight only) when true */
  bodyweight: boolean
}

export interface WorkoutDetail {
  id: number
  workoutDate: string
  notes: string | null
  /** Includes body weight for bodyweight exercises, so the client cannot simply sum reps × weight */
  totalVolumeKg: number
  sets: WorkoutSet[]
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface SetInput {
  exerciseId: number
  reps: number
  weightKg: number
  rpe: number | null
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: { id: number; email: string; displayName: string | null }
}

/** A passkey on this account; the private key never leaves the phone's keychain. */
export interface Passkey {
  id: number
  createdAt: string // ISO instant
  lastUsedAt: string | null
}

/** A started WebAuthn ceremony: its id goes back with the answer, publicKey goes to navigator.credentials. */
export interface PasskeyCeremony<Options> {
  ceremonyId: string
  publicKey: Options
  /** When the server forgets this ceremony (ISO instant); older servers leave it out */
  expiresAt?: string
}

export interface PersonalRecord {
  exerciseId: number
  exerciseName: string
  heaviestWeightKg: number
  heaviestReps: number
  heaviestDate: string
  bestEstimated1RmKg: number
  bestEstimated1RmDate: string
  totalSets: number
  /** For bodyweight exercises heaviestWeightKg includes body weight; this is the extra load alone */
  heaviestAddedKg: number
  bodyweight: boolean
}

export interface ProgressPoint {
  date: string
  topWeightKg: number
  estimated1RmKg: number
  volumeKg: number
}

export interface WeeklyVolume {
  weekStart: string
  volumeKg: number
  setCount: number
}

export interface Profile {
  id: number
  displayName: string | null
  heightCm: number | null
  bodyWeightKg: number | null
  avatarVersion: number | null
}

/** carbsG and fatG are null for entries logged without them. */
export interface FoodEntry {
  id: number
  entryDate: string
  name: string
  caloriesKcal: number
  proteinG: number
  carbsG: number | null
  fatG: number | null
}

export interface FoodInput {
  entryDate: string
  name: string
  caloriesKcal: number
  proteinG: number
  carbsG: number | null
  fatG: number | null
}

export interface NutritionGoals {
  calorieGoalKcal: number | null
  proteinGoalG: number | null
}

export interface NutritionDay {
  date: string
  entries: FoodEntry[]
  totalCaloriesKcal: number
  totalProteinG: number
  totalCarbsG: number
  totalFatG: number
  goals: NutritionGoals
}

/** Built-in food list row; nutrition is per 100 g plus one typical serving. */
export interface CatalogFood {
  id: number
  category: string
  nameTr: string
  nameEn: string
  kcalPer100g: number
  proteinPer100g: number
  carbsPer100g: number
  fatPer100g: number
  servingG: number
  servingTr: string
  servingEn: string
}

export interface FrequentFood {
  name: string
  caloriesKcal: number
  proteinG: number
  carbsG: number | null
  fatG: number | null
  timesEaten: number
}

/** Solo/duo rank; null while unranked. */
export interface LolRank {
  tier: string
  division: string
  lp: number
  wins: number
  losses: number
}

export interface LolPlayer {
  puuid: string
  gameName: string
  tagLine: string
  platform: string
  profileIconId: number | null
  summonerLevel: number | null
  rank: LolRank | null
  recentGames: number
  recentWins: number
  /** When Riot was last asked; older data is shown when Riot is unavailable */
  refreshedAt: string | null
}

export interface LolMatch {
  matchId: string
  queueId: number
  endedAt: string
  durationS: number
  champion: string
  position: string | null
  kills: number
  deaths: number
  assists: number
  cs: number
  damage: number
  win: boolean
}

export interface LolChampionStats {
  champion: string
  games: number
  wins: number
  kda: number
}

export interface LolSummary {
  games: number
  wins: number
  avgKills: number
  avgDeaths: number
  avgAssists: number
  kda: number
  csPerMinute: number
  champions: LolChampionStats[]
  /** Newest first, true for a win */
  form: boolean[]
}

export interface LolPlayerDetail {
  player: LolPlayer
  summary: LolSummary
  matches: LolMatch[]
}

export interface LolGroupMember {
  userId: number
  displayName: string | null
  you: boolean
  owner: boolean
  joinedAt: string
  /** The member's own Riot account; null until they link one */
  player: LolPlayer | null
}

export interface LolGroup {
  name: string
  /** 10 symbols without the dash; formatInviteCode adds it for display */
  inviteCode: string
  maxMembers: number
  /** Join order, so the first member is the owner */
  members: LolGroupMember[]
}

export interface ApiErrorBody {
  status: number
  message?: string
  fieldErrors?: Record<string, string>
}
