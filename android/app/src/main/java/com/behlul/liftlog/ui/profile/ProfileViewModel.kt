package com.behlul.liftlog.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behlul.liftlog.data.local.AvatarEncoder
import com.behlul.liftlog.data.repository.ProfileRepository
import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.domain.model.Profile
import com.behlul.liftlog.ui.components.UiState
import com.behlul.liftlog.ui.components.formatKg
import com.behlul.liftlog.ui.components.toAppError
import com.behlul.liftlog.ui.workouts.WorkoutDetailViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class ProfileField { HEIGHT, WEIGHT }

/** Text inputs of the edit sheet; empty means "not set". */
data class ProfileEditorState(
    val name: String,
    val height: String,
    val weight: String,
    val errors: Set<ProfileField> = emptySet(),
    val isSaving: Boolean = false,
)

data class ProfileUiState(
    val profile: UiState<Profile> = UiState.Loading,
    val avatarJpeg: ByteArray? = null,
    val isUploadingAvatar: Boolean = false,
    val editor: ProfileEditorState? = null,
    val actionError: AppError? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val avatarEncoder: AvatarEncoder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_uiState.value.profile !is UiState.Success) _uiState.update { it.copy(profile = UiState.Loading) }
            repository.getProfile()
                .onSuccess { profile -> showProfile(profile) }
                .onFailure { error -> _uiState.update { it.copy(profile = UiState.Error(error.toAppError())) } }
        }
    }

    fun onPhotoPicked(uri: Uri) {
        if (_uiState.value.isUploadingAvatar) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true) }
            val jpeg = try {
                avatarEncoder.toAvatarJpeg(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unreadable or unsupported file picked from the gallery
                _uiState.update { it.copy(isUploadingAvatar = false, actionError = AppError.Validation(null)) }
                return@launch
            }
            repository.uploadAvatar(jpeg)
                .onSuccess { profile ->
                    _uiState.update { it.copy(isUploadingAvatar = false, avatarJpeg = jpeg, profile = UiState.Success(profile)) }
                }
                .onFailure { error -> _uiState.update { it.copy(isUploadingAvatar = false, actionError = error.toAppError()) } }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            repository.deleteAvatar()
                .onSuccess {
                    _uiState.update { state ->
                        val profile = (state.profile as? UiState.Success)?.data?.copy(avatarVersion = null)
                        state.copy(avatarJpeg = null, profile = profile?.let { UiState.Success(it) } ?: state.profile)
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.toAppError()) } }
        }
    }

    fun openEditor() {
        val profile = (_uiState.value.profile as? UiState.Success)?.data ?: return
        _uiState.update {
            it.copy(
                editor = ProfileEditorState(
                    name = profile.displayName.orEmpty(),
                    height = profile.heightCm?.let(::plain).orEmpty(),
                    weight = profile.bodyWeightKg?.let(::plain).orEmpty(),
                ),
            )
        }
    }

    fun closeEditor() = _uiState.update { it.copy(editor = null) }

    fun onNameChange(value: String) = updateEditor { it.copy(name = value.take(100)) }

    fun onHeightChange(value: String) =
        updateEditor { it.copy(height = WorkoutDetailViewModel.sanitizeDecimal(value).take(5), errors = it.errors - ProfileField.HEIGHT) }

    fun onWeightChange(value: String) =
        updateEditor { it.copy(weight = WorkoutDetailViewModel.sanitizeDecimal(value).take(6), errors = it.errors - ProfileField.WEIGHT) }

    fun saveProfile() {
        val editor = _uiState.value.editor ?: return
        if (editor.isSaving) return
        val validation = validateProfile(editor)
        if (validation.errors.isNotEmpty()) {
            updateEditor { it.copy(errors = validation.errors) }
            return
        }
        viewModelScope.launch {
            updateEditor { it.copy(isSaving = true) }
            repository.updateProfile(editor.name.trim().ifBlank { null }, validation.heightCm, validation.weightKg)
                .onSuccess { profile -> _uiState.update { it.copy(editor = null, profile = UiState.Success(profile)) } }
                .onFailure { error ->
                    _uiState.update { it.copy(editor = it.editor?.copy(isSaving = false), actionError = error.toAppError()) }
                }
        }
    }

    fun actionErrorShown() = _uiState.update { it.copy(actionError = null) }

    private suspend fun showProfile(profile: Profile) {
        _uiState.update { it.copy(profile = UiState.Success(profile)) }
        val version = profile.avatarVersion
        if (version == null) {
            _uiState.update { it.copy(avatarJpeg = null) }
            return
        }
        // A failed photo download keeps the initials; the rest of the profile still shows
        repository.getAvatar(version).onSuccess { bytes -> _uiState.update { it.copy(avatarJpeg = bytes) } }
    }

    private inline fun updateEditor(transform: (ProfileEditorState) -> ProfileEditorState) =
        _uiState.update { state -> state.copy(editor = state.editor?.let(transform)) }

    private fun plain(value: Double) = formatKg(value, Locale.ROOT)
}

data class ProfileValidation(val heightCm: Double?, val weightKg: Double?, val errors: Set<ProfileField>)

/** Empty fields are allowed (they clear the value); otherwise the backend's ranges apply. */
internal fun validateProfile(editor: ProfileEditorState): ProfileValidation {
    val errors = mutableSetOf<ProfileField>()
    val height = editor.height.takeIf { it.isNotBlank() }?.let { text ->
        text.toDoubleOrNull()?.takeIf { it in 50.0..272.0 } ?: run { errors += ProfileField.HEIGHT; null }
    }
    val weight = editor.weight.takeIf { it.isNotBlank() }?.let { text ->
        text.toDoubleOrNull()?.takeIf { it in 20.0..500.0 } ?: run { errors += ProfileField.WEIGHT; null }
    }
    return ProfileValidation(height, weight, errors)
}
