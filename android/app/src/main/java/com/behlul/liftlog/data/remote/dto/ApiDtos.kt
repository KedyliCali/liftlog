package com.behlul.liftlog.data.remote.dto

import kotlinx.serialization.Serializable

// Mirrors the backend's JSON contract exactly; mapped to domain models in the repository.

@Serializable
data class ExerciseDto(
    val id: Long,
    val name: String,
    val muscleGroup: String? = null,
    val custom: Boolean = false,
)

@Serializable
data class CreateExerciseRequestDto(
    val name: String,
    val muscleGroup: String? = null,
)

@Serializable
data class WorkoutRequestDto(
    val workoutDate: String,
    val notes: String? = null,
)

@Serializable
data class WorkoutSummaryDto(
    val id: Long,
    val workoutDate: String,
    val notes: String? = null,
    val setCount: Int,
    val exerciseCount: Int,
    val totalVolumeKg: Double,
)

@Serializable
data class WorkoutDetailDto(
    val id: Long,
    val workoutDate: String,
    val notes: String? = null,
    val sets: List<SetDto>,
)

@Serializable
data class SetDto(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val setOrder: Int,
    val reps: Int,
    val weightKg: Double,
    val rpe: Double? = null,
)

@Serializable
data class SetRequestDto(
    val exerciseId: Long,
    val reps: Int,
    val weightKg: Double,
    val rpe: Double? = null,
)

@Serializable
data class PageDto<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val last: Boolean,
)

@Serializable
data class ApiErrorDto(
    val status: Int,
    val message: String? = null,
    val fieldErrors: Map<String, String>? = null,
)
