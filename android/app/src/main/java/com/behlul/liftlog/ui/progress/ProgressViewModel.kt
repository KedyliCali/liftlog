package com.behlul.liftlog.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behlul.liftlog.data.repository.StatsRepository
import com.behlul.liftlog.domain.model.PersonalRecord
import com.behlul.liftlog.domain.model.ProgressPoint
import com.behlul.liftlog.domain.model.WeeklyVolume
import com.behlul.liftlog.ui.components.UiState
import com.behlul.liftlog.ui.components.toAppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressOverview(val records: List<PersonalRecord>, val weeklyVolume: List<WeeklyVolume>)

data class ProgressUiState(
    val overview: UiState<ProgressOverview> = UiState.Loading,
    val selectedExerciseId: Long? = null,
    val progress: UiState<List<ProgressPoint>> = UiState.Loading,
) {
    val selectedRecord: PersonalRecord?
        get() = (overview as? UiState.Success)?.data?.records?.firstOrNull { it.exerciseId == selectedExerciseId }
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null

    /** Called on every resume, since sets logged on the Workouts tab change all of these numbers. */
    fun load() {
        viewModelScope.launch {
            if (_uiState.value.overview !is UiState.Success) _uiState.update { it.copy(overview = UiState.Loading) }
            // Both requests run in parallel; the screen needs both before it can render
            val records = async { statsRepository.getPersonalRecords() }
            val volume = async { statsRepository.getWeeklyVolume(WEEKS) }
            val recordsResult = records.await()
            val volumeResult = volume.await()

            val error = recordsResult.exceptionOrNull() ?: volumeResult.exceptionOrNull()
            if (error != null) {
                _uiState.update { it.copy(overview = UiState.Error(error.toAppError())) }
                return@launch
            }
            val overview = ProgressOverview(recordsResult.getOrThrow(), volumeResult.getOrThrow())
            // Keep the user's choice if that exercise still has records, else pick the most trained one
            val selected = _uiState.value.selectedExerciseId?.takeIf { id -> overview.records.any { it.exerciseId == id } }
                ?: overview.records.maxByOrNull { it.totalSets }?.exerciseId
            _uiState.update { it.copy(overview = UiState.Success(overview), selectedExerciseId = selected) }
            selected?.let(::loadProgress)
        }
    }

    fun selectExercise(exerciseId: Long) {
        if (exerciseId == _uiState.value.selectedExerciseId) return
        _uiState.update { it.copy(selectedExerciseId = exerciseId) }
        loadProgress(exerciseId)
    }

    private fun loadProgress(exerciseId: Long) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            _uiState.update { it.copy(progress = UiState.Loading) }
            statsRepository.getProgress(exerciseId)
                .onSuccess { points -> _uiState.update { it.copy(progress = UiState.Success(points)) } }
                .onFailure { error -> _uiState.update { it.copy(progress = UiState.Error(error.toAppError())) } }
        }
    }

    companion object {
        const val WEEKS = 8
    }
}
