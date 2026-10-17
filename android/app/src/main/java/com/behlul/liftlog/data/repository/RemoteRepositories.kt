package com.behlul.liftlog.data.repository

import com.behlul.liftlog.data.remote.LiftLogApi
import com.behlul.liftlog.data.remote.dto.CreateExerciseRequestDto
import com.behlul.liftlog.data.remote.dto.ExerciseDto
import com.behlul.liftlog.data.remote.dto.SetDto
import com.behlul.liftlog.data.remote.dto.SetRequestDto
import com.behlul.liftlog.data.remote.dto.WorkoutDetailDto
import com.behlul.liftlog.data.remote.dto.WorkoutRequestDto
import com.behlul.liftlog.data.remote.dto.WorkoutSummaryDto
import com.behlul.liftlog.domain.model.Exercise
import com.behlul.liftlog.domain.model.Page
import com.behlul.liftlog.domain.model.SetInput
import com.behlul.liftlog.domain.model.WorkoutDetail
import com.behlul.liftlog.domain.model.WorkoutSet
import com.behlul.liftlog.domain.model.WorkoutSummary
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteWorkoutRepository @Inject constructor(
    private val api: LiftLogApi,
    private val json: Json,
) : WorkoutRepository {

    override suspend fun getWorkouts(page: Int, size: Int) = apiCall(json) {
        val dto = api.getWorkouts(page, size)
        Page(items = dto.content.map { it.toDomain() }, page = dto.page, isLast = dto.last)
    }

    override suspend fun getWorkout(id: Long) = apiCall(json) { api.getWorkout(id).toDomain() }

    override suspend fun createWorkout(date: LocalDate, notes: String?) = apiCall(json) {
        api.createWorkout(WorkoutRequestDto(date.toString(), notes)).toDomain()
    }

    override suspend fun updateWorkout(id: Long, date: LocalDate, notes: String?) = apiCall(json) {
        api.updateWorkout(id, WorkoutRequestDto(date.toString(), notes)).toDomain()
    }

    override suspend fun deleteWorkout(id: Long) = apiCall(json) { api.deleteWorkout(id) }

    override suspend fun addSet(workoutId: Long, input: SetInput) = apiCall(json) {
        api.addSet(workoutId, input.toDto()).toDomain()
    }

    override suspend fun updateSet(setId: Long, input: SetInput) = apiCall(json) {
        api.updateSet(setId, input.toDto()).toDomain()
    }

    override suspend fun deleteSet(setId: Long) = apiCall(json) { api.deleteSet(setId) }
}

@Singleton
class RemoteExerciseRepository @Inject constructor(
    private val api: LiftLogApi,
    private val json: Json,
) : ExerciseRepository {

    override suspend fun getExercises() = apiCall(json) { api.getExercises().map { it.toDomain() } }

    override suspend fun createExercise(name: String, muscleGroup: String?) = apiCall(json) {
        api.createExercise(CreateExerciseRequestDto(name, muscleGroup)).toDomain()
    }
}

private fun WorkoutSummaryDto.toDomain() = WorkoutSummary(
    id = id,
    date = LocalDate.parse(workoutDate),
    notes = notes,
    setCount = setCount,
    exerciseCount = exerciseCount,
    totalVolumeKg = totalVolumeKg,
)

private fun WorkoutDetailDto.toDomain() = WorkoutDetail(
    id = id,
    date = LocalDate.parse(workoutDate),
    notes = notes,
    sets = sets.map { it.toDomain() },
)

private fun SetDto.toDomain() = WorkoutSet(
    id = id,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    order = setOrder,
    reps = reps,
    weightKg = weightKg,
    rpe = rpe,
    isNewPersonalRecord = newPersonalRecord,
)

private fun ExerciseDto.toDomain() = Exercise(id = id, name = name, muscleGroup = muscleGroup, isCustom = custom)

private fun SetInput.toDto() = SetRequestDto(exerciseId = exerciseId, reps = reps, weightKg = weightKg, rpe = rpe)
