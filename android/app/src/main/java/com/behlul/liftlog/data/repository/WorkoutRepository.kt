package com.behlul.liftlog.data.repository

import com.behlul.liftlog.domain.model.Exercise
import com.behlul.liftlog.domain.model.Page
import com.behlul.liftlog.domain.model.SetInput
import com.behlul.liftlog.domain.model.WorkoutDetail
import com.behlul.liftlog.domain.model.WorkoutSet
import com.behlul.liftlog.domain.model.WorkoutSummary
import java.time.LocalDate

/** The single source of workout data for ViewModels; tests replace it with a fake. */
interface WorkoutRepository {
    suspend fun getWorkouts(page: Int, size: Int = PAGE_SIZE): Result<Page<WorkoutSummary>>
    suspend fun getWorkout(id: Long): Result<WorkoutDetail>
    suspend fun createWorkout(date: LocalDate, notes: String?): Result<WorkoutDetail>
    suspend fun updateWorkout(id: Long, date: LocalDate, notes: String?): Result<WorkoutDetail>
    suspend fun deleteWorkout(id: Long): Result<Unit>
    suspend fun addSet(workoutId: Long, input: SetInput): Result<WorkoutSet>
    suspend fun updateSet(setId: Long, input: SetInput): Result<WorkoutSet>
    suspend fun deleteSet(setId: Long): Result<Unit>

    companion object {
        const val PAGE_SIZE = 20
    }
}

interface ExerciseRepository {
    suspend fun getExercises(): Result<List<Exercise>>
    suspend fun createExercise(name: String, muscleGroup: String?): Result<Exercise>
}
