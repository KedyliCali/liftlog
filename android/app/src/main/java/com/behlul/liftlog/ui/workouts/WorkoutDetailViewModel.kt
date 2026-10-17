package com.behlul.liftlog.ui.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behlul.liftlog.data.repository.ExerciseRepository
import com.behlul.liftlog.data.repository.WorkoutRepository
import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.domain.model.Exercise
import com.behlul.liftlog.domain.model.SetInput
import com.behlul.liftlog.domain.model.WorkoutDetail
import com.behlul.liftlog.domain.model.WorkoutSet
import com.behlul.liftlog.ui.components.UiState
import com.behlul.liftlog.ui.components.formatKg
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
import java.util.Locale
import javax.inject.Inject

enum class SetField { EXERCISE, REPS, WEIGHT, RPE }

/** Form state of the add/edit-set bottom sheet. Inputs stay as text so the user can type freely. */
data class SetEditorState(
    val editingSetId: Long? = null,
    val exercise: Exercise? = null,
    val reps: String = "",
    val weight: String = "",
    val rpe: String = "",
    val errors: Set<SetField> = emptySet(),
    val isSaving: Boolean = false,
)

data class WorkoutDetailUiState(
    val workout: UiState<WorkoutDetail> = UiState.Loading,
    val editor: SetEditorState? = null,
    val exercises: UiState<List<Exercise>> = UiState.Loading,
    val actionError: AppError? = null,
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    // Type-safe navigation stores each route property under its own name
    val workoutId: Long = checkNotNull(savedStateHandle.get<Long>("workoutId"))

    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()

    private val _workoutDeleted = Channel<Unit>(Channel.BUFFERED)
    val workoutDeleted = _workoutDeleted.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_uiState.value.workout !is UiState.Success) _uiState.update { it.copy(workout = UiState.Loading) }
            workoutRepository.getWorkout(workoutId)
                .onSuccess { detail -> _uiState.update { it.copy(workout = UiState.Success(detail)) } }
                .onFailure { error -> _uiState.update { it.copy(workout = UiState.Error(error.toAppError())) } }
        }
    }

    fun loadExercises() {
        if (_uiState.value.exercises is UiState.Success) return
        viewModelScope.launch {
            _uiState.update { it.copy(exercises = UiState.Loading) }
            exerciseRepository.getExercises()
                .onSuccess { list -> _uiState.update { it.copy(exercises = UiState.Success(list)) } }
                .onFailure { error -> _uiState.update { it.copy(exercises = UiState.Error(error.toAppError())) } }
        }
    }

    /** Opens the sheet pre-filled from the last set, since lifters usually repeat it. */
    fun openAddSet() {
        val last = currentWorkout()?.sets?.lastOrNull()
        _uiState.update {
            it.copy(
                editor = SetEditorState(
                    exercise = last?.let { set -> Exercise(set.exerciseId, set.exerciseName, null, false) },
                    reps = last?.reps?.toString().orEmpty(),
                    weight = last?.weightKg?.let(::plainNumber).orEmpty(),
                ),
            )
        }
        loadExercises()
    }

    fun openEditSet(set: WorkoutSet) {
        _uiState.update {
            it.copy(
                editor = SetEditorState(
                    editingSetId = set.id,
                    exercise = Exercise(set.exerciseId, set.exerciseName, null, false),
                    reps = set.reps.toString(),
                    weight = plainNumber(set.weightKg),
                    rpe = set.rpe?.let(::plainNumber).orEmpty(),
                ),
            )
        }
        loadExercises()
    }

    fun closeEditor() = _uiState.update { it.copy(editor = null) }

    fun onExerciseSelected(exercise: Exercise) = updateEditor { it.copy(exercise = exercise, errors = it.errors - SetField.EXERCISE) }

    fun onRepsChange(value: String) = updateEditor { it.copy(reps = value.filter(Char::isDigit).take(4), errors = it.errors - SetField.REPS) }

    fun onWeightChange(value: String) = updateEditor { it.copy(weight = sanitizeDecimal(value), errors = it.errors - SetField.WEIGHT) }

    fun onRpeChange(value: String) = updateEditor { it.copy(rpe = sanitizeDecimal(value), errors = it.errors - SetField.RPE) }

    fun saveSet() {
        val editor = _uiState.value.editor ?: return
        if (editor.isSaving) return
        val result = validateSetInput(editor)
        if (result.input == null) {
            updateEditor { it.copy(errors = result.errors) }
            return
        }
        viewModelScope.launch {
            updateEditor { it.copy(isSaving = true) }
            val saved = editor.editingSetId
                ?.let { workoutRepository.updateSet(it, result.input) }
                ?: workoutRepository.addSet(workoutId, result.input)
            saved
                .onSuccess {
                    _uiState.update { it.copy(editor = null) }
                    load()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(editor = it.editor?.copy(isSaving = false), actionError = error.toAppError()) }
                }
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteSet(setId)
                .onSuccess {
                    _uiState.update { it.copy(editor = null) }
                    load()
                }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.toAppError()) } }
        }
    }

    fun updateWorkout(date: LocalDate, notes: String) {
        viewModelScope.launch {
            workoutRepository.updateWorkout(workoutId, date, notes.trim().ifBlank { null })
                .onSuccess { detail -> _uiState.update { it.copy(workout = UiState.Success(detail)) } }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.toAppError()) } }
        }
    }

    fun deleteWorkout() {
        viewModelScope.launch {
            workoutRepository.deleteWorkout(workoutId)
                .onSuccess { _workoutDeleted.send(Unit) }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.toAppError()) } }
        }
    }

    fun createExercise(name: String, muscleGroup: String) {
        viewModelScope.launch {
            exerciseRepository.createExercise(name.trim(), muscleGroup.trim().ifBlank { null })
                .onSuccess { created ->
                    _uiState.update { state ->
                        val list = (state.exercises as? UiState.Success)?.data.orEmpty()
                        state.copy(exercises = UiState.Success((list + created).sortedBy { it.name.lowercase() }))
                    }
                    onExerciseSelected(created)
                }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.toAppError()) } }
        }
    }

    fun actionErrorShown() = _uiState.update { it.copy(actionError = null) }

    private fun currentWorkout() = (_uiState.value.workout as? UiState.Success)?.data

    private inline fun updateEditor(transform: (SetEditorState) -> SetEditorState) =
        _uiState.update { state -> state.copy(editor = state.editor?.let(transform)) }

    companion object {
        private fun plainNumber(value: Double) = formatKg(value, Locale.ROOT)

        /** Accepts both "." and "," as decimal separator and keeps at most one of them. */
        internal fun sanitizeDecimal(value: String): String {
            val normalized = value.replace(',', '.').filter { it.isDigit() || it == '.' }
            val firstDot = normalized.indexOf('.')
            return if (firstDot < 0) normalized.take(7)
            else (normalized.substring(0, firstDot + 1) + normalized.substring(firstDot + 1).replace(".", "")).take(7)
        }
    }
}

data class SetValidationResult(val input: SetInput?, val errors: Set<SetField>)

/** Same limits as the backend's SetRequest, so most bad input never reaches the network. */
internal fun validateSetInput(editor: SetEditorState): SetValidationResult {
    val errors = mutableSetOf<SetField>()
    val reps = editor.reps.toIntOrNull()?.takeIf { it in 1..1000 } ?: run { errors += SetField.REPS; null }
    val weight = editor.weight.toDoubleOrNull()?.takeIf { it in 0.0..9999.99 } ?: run { errors += SetField.WEIGHT; null }
    val rpe = if (editor.rpe.isBlank()) null
    else editor.rpe.toDoubleOrNull()?.takeIf { it in 1.0..10.0 } ?: run { errors += SetField.RPE; null }
    if (editor.exercise == null) errors += SetField.EXERCISE

    val input = if (errors.isEmpty() && editor.exercise != null && reps != null && weight != null) {
        SetInput(exerciseId = editor.exercise.id, reps = reps, weightKg = weight, rpe = rpe)
    } else null
    return SetValidationResult(input, errors)
}
