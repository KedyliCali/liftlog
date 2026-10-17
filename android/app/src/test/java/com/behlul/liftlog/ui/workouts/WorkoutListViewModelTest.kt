package com.behlul.liftlog.ui.workouts

import app.cash.turbine.test
import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.testing.FakeWorkoutRepository
import com.behlul.liftlog.testing.MainDispatcherRule
import com.behlul.liftlog.ui.components.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutListViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakeWorkoutRepository()

    @Test
    fun `loads workouts newest first`() = runTest(mainRule.dispatcher) {
        repository.seed(LocalDate.of(2026, 10, 1), "Old")
        repository.seed(LocalDate.of(2026, 10, 15), "New")

        val viewModel = WorkoutListViewModel(repository)
        advanceUntilIdle()

        val workouts = (viewModel.uiState.value.workouts as UiState.Success).data
        assertEquals(listOf("New", "Old"), workouts.map { it.notes })
    }

    @Test
    fun `initial failure shows full screen error`() = runTest(mainRule.dispatcher) {
        repository.failWith = AppError.Network

        val viewModel = WorkoutListViewModel(repository)
        advanceUntilIdle()

        assertEquals(UiState.Error(AppError.Network), viewModel.uiState.value.workouts)
    }

    @Test
    fun `failed refresh keeps old data and reports a transient error`() = runTest(mainRule.dispatcher) {
        repository.seed(LocalDate.of(2026, 10, 1), "Kept")
        val viewModel = WorkoutListViewModel(repository)
        advanceUntilIdle()

        repository.failWith = AppError.Server
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.workouts is UiState.Success)
        assertEquals(AppError.Server, state.actionError)
    }

    @Test
    fun `load more appends the next page`() = runTest(mainRule.dispatcher) {
        repeat(25) { day -> repository.seed(LocalDate.of(2026, 9, 1).plusDays(day.toLong())) }
        val viewModel = WorkoutListViewModel(repository)
        advanceUntilIdle()
        assertEquals(20, (viewModel.uiState.value.workouts as UiState.Success).data.size)

        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(25, (state.workouts as UiState.Success).data.size)
        assertTrue(state.endReached)
    }

    @Test
    fun `resuming the screen picks up workouts changed elsewhere`() = runTest(mainRule.dispatcher) {
        val viewModel = WorkoutListViewModel(repository)
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), (viewModel.uiState.value.workouts as UiState.Success).data)

        repository.seed(LocalDate.of(2026, 10, 17), "Added on detail screen")
        viewModel.onScreenResumed()
        advanceUntilIdle()

        val workouts = (viewModel.uiState.value.workouts as UiState.Success).data
        assertEquals(listOf("Added on detail screen"), workouts.map { it.notes })
    }

    @Test
    fun `creating a workout opens it`()= runTest(mainRule.dispatcher) {
        val viewModel = WorkoutListViewModel(repository)
        advanceUntilIdle()

        viewModel.openWorkout.test {
            viewModel.createWorkout(LocalDate.of(2026, 10, 17), "  Push day  ")
            val openedId = awaitItem()
            assertEquals("Push day", repository.workouts.getValue(openedId).notes)
        }
    }
}
