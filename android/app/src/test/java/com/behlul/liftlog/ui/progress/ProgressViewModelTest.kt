package com.behlul.liftlog.ui.progress

import com.behlul.liftlog.testing.FakeStatsRepository
import com.behlul.liftlog.testing.MainDispatcherRule
import com.behlul.liftlog.ui.components.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakeStatsRepository()

    @Test
    fun `selects the most trained exercise and loads its progress`() = runTest(mainRule.dispatcher) {
        val viewModel = ProgressViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(9L, state.selectedExerciseId) // Bench Press has the most sets
        assertEquals(listOf(9L), repository.progressRequests)
        assertTrue(state.progress is UiState.Success)
        assertEquals(ProgressViewModel.WEEKS, (state.overview as UiState.Success).data.weeklyVolume.size)
    }

    @Test
    fun `reloading keeps the user's selection`() = runTest(mainRule.dispatcher) {
        val viewModel = ProgressViewModel(repository)
        viewModel.load()
        advanceUntilIdle()
        viewModel.selectExercise(1L)
        advanceUntilIdle()

        viewModel.load()
        advanceUntilIdle()

        assertEquals(1L, viewModel.uiState.value.selectedExerciseId)
        assertEquals(listOf(9L, 1L, 1L), repository.progressRequests)
    }

    @Test
    fun `no records means no progress request`() = runTest(mainRule.dispatcher) {
        repository.records = emptyList()
        val viewModel = ProgressViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.selectedExerciseId)
        assertTrue(repository.progressRequests.isEmpty())
    }
}
