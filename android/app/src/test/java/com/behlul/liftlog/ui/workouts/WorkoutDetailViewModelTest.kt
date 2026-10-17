package com.behlul.liftlog.ui.workouts

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.behlul.liftlog.domain.model.Exercise
import com.behlul.liftlog.domain.model.SetInput
import com.behlul.liftlog.domain.model.WorkoutSet
import com.behlul.liftlog.testing.FakeExerciseRepository
import com.behlul.liftlog.testing.FakeWorkoutRepository
import com.behlul.liftlog.testing.MainDispatcherRule
import com.behlul.liftlog.ui.components.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val workouts = FakeWorkoutRepository()
    private val exercises = FakeExerciseRepository()
    private val squat = Exercise(1, "Squat", "Legs", false)

    private fun TestScope.viewModelFor(workoutId: Long) =
        WorkoutDetailViewModel(SavedStateHandle(mapOf("workoutId" to workoutId)), workouts, exercises)
            .also { advanceUntilIdle() }

    @Test
    fun `add set is prefilled from the last set`() = runTest(mainRule.dispatcher) {
        val workout = workouts.seed(
            LocalDate.of(2026, 10, 17),
            sets = listOf(WorkoutSet(50, 1, "Squat", 1, 5, 140.0, 8.0)),
        )
        val viewModel = viewModelFor(workout.id)

        viewModel.openAddSet()

        val editor = viewModel.uiState.value.editor!!
        assertEquals(1L, editor.exercise?.id)
        assertEquals("5", editor.reps)
        assertEquals("140", editor.weight)
        assertEquals("", editor.rpe)
        assertNull(editor.editingSetId)
    }

    @Test
    fun `invalid set is rejected locally without calling the repository`() = runTest(mainRule.dispatcher) {
        val workout = workouts.seed(LocalDate.of(2026, 10, 17))
        val viewModel = viewModelFor(workout.id)

        viewModel.openAddSet()
        viewModel.onRepsChange("0")
        viewModel.onWeightChange("")
        viewModel.onRpeChange("11")
        viewModel.saveSet()
        advanceUntilIdle()

        val errors = viewModel.uiState.value.editor!!.errors
        assertEquals(setOf(SetField.EXERCISE, SetField.REPS, SetField.WEIGHT, SetField.RPE), errors)
        assertTrue(workouts.addedSets.isEmpty())
    }

    @Test
    fun `valid set is saved, sheet closes and workout reloads`() = runTest(mainRule.dispatcher) {
        val workout = workouts.seed(LocalDate.of(2026, 10, 17))
        val viewModel = viewModelFor(workout.id)

        viewModel.openAddSet()
        viewModel.onExerciseSelected(squat)
        viewModel.onRepsChange("5")
        viewModel.onWeightChange("142,5")
        viewModel.onRpeChange("8")
        viewModel.saveSet()
        advanceUntilIdle()

        assertEquals(listOf(workout.id to SetInput(1, 5, 142.5, 8.0)), workouts.addedSets)
        val state = viewModel.uiState.value
        assertNull(state.editor)
        assertEquals(1, (state.workout as UiState.Success).data.sets.size)
    }

    @Test
    fun `deleting the workout emits a deleted event`() = runTest(mainRule.dispatcher) {
        val workout = workouts.seed(LocalDate.of(2026, 10, 17))
        val viewModel = viewModelFor(workout.id)

        viewModel.workoutDeleted.test {
            viewModel.deleteWorkout()
            awaitItem()
        }
        assertTrue(workouts.workouts.isEmpty())
    }

    @Test
    fun `sanitizeDecimal accepts comma and keeps a single separator`() {
        assertEquals("142.5", WorkoutDetailViewModel.sanitizeDecimal("142,5"))
        assertEquals("1.25", WorkoutDetailViewModel.sanitizeDecimal("1.2.5"))
        assertEquals("100", WorkoutDetailViewModel.sanitizeDecimal("1a0b0"))
    }

    @Test
    fun `validation accepts boundary values`() {
        val result = validateSetInput(SetEditorState(exercise = squat, reps = "1", weight = "0", rpe = "10"))
        assertEquals(SetInput(1, 1, 0.0, 10.0), result.input)
        assertTrue(result.errors.isEmpty())
    }
}
