package com.behlul.liftlog.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behlul.liftlog.data.repository.WorkoutRepository
import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.domain.model.WorkoutSummary
import com.behlul.liftlog.ui.components.UiState
import com.behlul.liftlog.ui.components.toAppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class WorkoutListUiState(
    val workouts: UiState<List<WorkoutSummary>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val isCreating: Boolean = false,
    /** Transient error for actions (create, load more) shown as a snackbar, not a full-screen error. */
    val actionError: AppError? = null,
)

@HiltViewModel
class WorkoutListViewModel @Inject constructor(
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutListUiState())
    val uiState: StateFlow<WorkoutListUiState> = _uiState.asStateFlow()

    // One-off navigation events; a Channel delivers each one exactly once, unlike StateFlow
    private val _openWorkout = Channel<Long>(Channel.BUFFERED)
    val openWorkout = _openWorkout.receiveAsFlow()

    private var nextPage = 0

    init {
        loadFirstPage(showFullScreenLoading = true)
    }

    fun refresh() = loadFirstPage(showFullScreenLoading = false)

    /**
     * Called whenever the list becomes visible again (e.g. back from a detail screen, where sets
     * may have changed). Skipped while the first load is still running.
     */
    fun onScreenResumed() {
        if (_uiState.value.workouts is UiState.Success) refresh()
    }

    fun retry() = loadFirstPage(showFullScreenLoading = true)

    private fun loadFirstPage(showFullScreenLoading: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (showFullScreenLoading) it.copy(workouts = UiState.Loading) else it.copy(isRefreshing = true)
            }
            repository.getWorkouts(page = 0)
                .onSuccess { page ->
                    nextPage = 1
                    _uiState.update {
                        it.copy(workouts = UiState.Success(page.items), isRefreshing = false, endReached = page.isLast)
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        // Keep showing old data on a failed pull-to-refresh
                        if (state.workouts is UiState.Success && !showFullScreenLoading) {
                            state.copy(isRefreshing = false, actionError = error.toAppError())
                        } else {
                            state.copy(workouts = UiState.Error(error.toAppError()), isRefreshing = false)
                        }
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val current = (state.workouts as? UiState.Success)?.data ?: return
        if (state.isLoadingMore || state.endReached || state.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            repository.getWorkouts(page = nextPage)
                .onSuccess { page ->
                    nextPage++
                    // De-duplicate in case a workout shifted pages after a create/delete
                    val merged = (current + page.items).distinctBy { it.id }
                    _uiState.update {
                        it.copy(workouts = UiState.Success(merged), isLoadingMore = false, endReached = page.isLast)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoadingMore = false, actionError = error.toAppError()) }
                }
        }
    }

    fun createWorkout(date: LocalDate, notes: String) {
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }
            repository.createWorkout(date, notes.trim().ifBlank { null })
                .onSuccess { created ->
                    _uiState.update { it.copy(isCreating = false) }
                    _openWorkout.send(created.id)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isCreating = false, actionError = error.toAppError()) }
                }
        }
    }

    fun actionErrorShown() = _uiState.update { it.copy(actionError = null) }
}
