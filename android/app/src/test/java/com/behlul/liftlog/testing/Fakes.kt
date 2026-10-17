package com.behlul.liftlog.testing

import com.behlul.liftlog.data.repository.AppException
import com.behlul.liftlog.data.repository.ExerciseRepository
import com.behlul.liftlog.data.repository.WorkoutRepository
import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.domain.model.Exercise
import com.behlul.liftlog.domain.model.Page
import com.behlul.liftlog.domain.model.SetInput
import com.behlul.liftlog.domain.model.WorkoutDetail
import com.behlul.liftlog.domain.model.WorkoutSet
import com.behlul.liftlog.domain.model.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.LocalDate

/** Replaces Dispatchers.Main (used by viewModelScope) with a controllable test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** In-memory repository that keeps workouts in a map, so ViewModel tests need no network. */
class FakeWorkoutRepository : WorkoutRepository {
    val workouts = linkedMapOf<Long, WorkoutDetail>()
    var failWith: AppError? = null
    val addedSets = mutableListOf<Pair<Long, SetInput>>()
    private var nextId = 1L

    fun seed(date: LocalDate, notes: String? = null, sets: List<WorkoutSet> = emptyList()): WorkoutDetail {
        val detail = WorkoutDetail(nextId++, date, notes, sets)
        workouts[detail.id] = detail
        return detail
    }

    private fun <T> result(block: () -> T): Result<T> =
        failWith?.let { Result.failure(AppException(it)) } ?: Result.success(block())

    override suspend fun getWorkouts(page: Int, size: Int) = result {
        val all = workouts.values.sortedByDescending { it.date }.map {
            WorkoutSummary(it.id, it.date, it.notes, it.sets.size, it.sets.distinctBy { s -> s.exerciseId }.size, it.totalVolumeKg)
        }
        val chunk = all.drop(page * size).take(size)
        Page(chunk, page, isLast = (page + 1) * size >= all.size)
    }

    override suspend fun getWorkout(id: Long) =
        failWith?.let { Result.failure(AppException(it)) }
            ?: workouts[id]?.let { Result.success(it) }
            ?: Result.failure(AppException(AppError.NotFound))

    override suspend fun createWorkout(date: LocalDate, notes: String?) = result { seed(date, notes) }

    override suspend fun updateWorkout(id: Long, date: LocalDate, notes: String?) = result {
        workouts.getValue(id).copy(date = date, notes = notes).also { workouts[id] = it }
    }

    override suspend fun deleteWorkout(id: Long) = result { workouts.remove(id); Unit }

    override suspend fun addSet(workoutId: Long, input: SetInput) = result {
        addedSets += workoutId to input
        val workout = workouts.getValue(workoutId)
        val set = WorkoutSet(nextId++, input.exerciseId, "Exercise ${input.exerciseId}", workout.sets.size + 1,
            input.reps, input.weightKg, input.rpe)
        workouts[workoutId] = workout.copy(sets = workout.sets + set)
        set
    }

    override suspend fun updateSet(setId: Long, input: SetInput) = result {
        val workout = workouts.values.first { w -> w.sets.any { it.id == setId } }
        val updated = workout.sets.first { it.id == setId }.copy(reps = input.reps, weightKg = input.weightKg, rpe = input.rpe)
        workouts[workout.id] = workout.copy(sets = workout.sets.map { if (it.id == setId) updated else it })
        updated
    }

    override suspend fun deleteSet(setId: Long) = result {
        workouts.replaceAll { _, w -> w.copy(sets = w.sets.filterNot { it.id == setId }) }
    }
}

class FakeExerciseRepository : ExerciseRepository {
    val exercises = mutableListOf(
        Exercise(1, "Squat", "Legs", false),
        Exercise(2, "Bench Press", "Chest", false),
    )

    override suspend fun getExercises() = Result.success(exercises.toList())

    override suspend fun createExercise(name: String, muscleGroup: String?) =
        Result.success(Exercise(100L + exercises.size, name, muscleGroup, true).also { exercises += it })
}
