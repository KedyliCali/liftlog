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

export interface FoodEntry {
  id: number
  entryDate: string
  name: string
  caloriesKcal: number
  proteinG: number
}

export interface FoodInput {
  entryDate: string
  name: string
  caloriesKcal: number
  proteinG: number
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
  goals: NutritionGoals
}

export interface FrequentFood {
  name: string
  caloriesKcal: number
  proteinG: number
  timesEaten: number
}

export interface ApiErrorBody {
  status: number
  message?: string
  fieldErrors?: Record<string, string>
}
