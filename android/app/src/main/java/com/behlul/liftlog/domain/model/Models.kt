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
