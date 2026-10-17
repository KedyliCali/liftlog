package com.behlul.liftlog.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behlul.liftlog.data.repository.AuthRepository
import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.ui.components.toAppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { LOGIN, REGISTER }

enum class AuthField { EMAIL, PASSWORD }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val fieldErrors: Set<AuthField> = emptySet(),
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
)

/** On success there is nothing to navigate: the saved session flips the root UI to the main app. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun toggleMode() = _uiState.update {
        it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN, fieldErrors = emptySet(), error = null)
    }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value.trim(), fieldErrors = it.fieldErrors - AuthField.EMAIL, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, fieldErrors = it.fieldErrors - AuthField.PASSWORD, error = null) }

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value.take(100)) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val errors = validate(state)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = when (state.mode) {
                AuthMode.LOGIN -> authRepository.login(state.email, state.password)
                AuthMode.REGISTER -> authRepository.register(state.email, state.password, state.displayName)
            }
            _uiState.update { it.copy(isSubmitting = false, error = result.exceptionOrNull()?.toAppError()) }
        }
    }

    companion object {
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        internal fun validate(state: AuthUiState): Set<AuthField> = buildSet {
            if (!EMAIL_PATTERN.matches(state.email)) add(AuthField.EMAIL)
            // Length rule only matters when choosing a password; old accounts may log in with anything
            val passwordOk = if (state.mode == AuthMode.REGISTER) state.password.length in 8..72 else state.password.isNotEmpty()
            if (!passwordOk) add(AuthField.PASSWORD)
        }
    }
}
