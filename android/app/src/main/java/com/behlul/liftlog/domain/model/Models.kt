package com.behlul.liftlog.domain.model

import java.time.LocalDate

data class Exercise(
    val id: Long,
    val name: String,
    val muscleGroup: String?,
    val isCustom: Boolean,
)

data class WorkoutSummary(
    val id: Long,
    val date: LocalDate,
    val notes: String?,
    val setCount: Int,
    val exerciseCount: Int,
    val totalVolumeKg: Double,
)

data class WorkoutDetail(
    val id: Long,
    val date: LocalDate,
    val notes: String?,
    val sets: List<WorkoutSet>,
) {
    /** Sets grouped by exercise, in the order each exercise was first performed. */
    val setsByExercise: List<ExerciseGroup>
        get() = sets.groupBy { it.exerciseId }
            .map { (exerciseId, groupSets) -> ExerciseGroup(exerciseId, groupSets.first().exerciseName, groupSets) }

    val totalVolumeKg: Double get() = sets.sumOf { it.volumeKg }
}

data class ExerciseGroup(
    val exerciseId: Long,
    val exerciseName: String,
    val sets: List<WorkoutSet>,
)

data class WorkoutSet(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val order: Int,
    val reps: Int,
    val weightKg: Double,
    val rpe: Double?,
    /** Only ever true on the set returned right after adding it. */
    val isNewPersonalRecord: Boolean = false,
) {
    val volumeKg: Double get() = reps * weightKg
}

/** What the user typed into the set form, before it gets an id from the server. */
data class SetInput(
    val exerciseId: Long,
    val reps: Int,
    val weightKg: Double,
    val rpe: Double?,
)

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val isLast: Boolean,
)

data class Session(
    val token: String,
    val userId: Long,
    val email: String,
    val displayName: String?,
)

sealed interface SessionState {
    /** DataStore has not been read yet; the UI shows a splash instead of guessing. */
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val session: Session) : SessionState
}

data class PersonalRecord(
    val exerciseId: Long,
    val exerciseName: String,
    val heaviestWeightKg: Double,
    val heaviestReps: Int,
    val heaviestDate: LocalDate,
    val bestEstimated1RmKg: Double,
    val bestEstimated1RmDate: LocalDate,
    val totalSets: Int,
)

data class ProgressPoint(
    val date: LocalDate,
    val topWeightKg: Double,
    val estimated1RmKg: Double,
    val volumeKg: Double,
)

data class WeeklyVolume(val weekStart: LocalDate, val volumeKg: Double, val setCount: Int)

data class Profile(
    val displayName: String?,
    val heightCm: Double?,
    val bodyWeightKg: Double?,
    val avatarVersion: Long?,
) {
    /** Body mass index, only when both height and weight are known. */
    val bmi: Double?
        get() {
            val heightM = (heightCm ?: return null) / 100
            return (bodyWeightKg ?: return null) / (heightM * heightM)
        }
}
