package com.behlul.liftlog.ui.auth

import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.domain.model.SessionState
import com.behlul.liftlog.testing.FakeAuthRepository
import com.behlul.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()

    @Test
    fun `invalid input is rejected without calling the server`() = runTest(mainRule.dispatcher) {
        val viewModel = AuthViewModel(repository)
        viewModel.toggleMode() // register mode enforces password length
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("short")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(setOf(AuthField.EMAIL, AuthField.PASSWORD), viewModel.uiState.value.fieldErrors)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `login mode accepts any non-empty password`() {
        val errors = AuthViewModel.validate(AuthUiState(mode = AuthMode.LOGIN, email = "a@b.co", password = "x"))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `successful register stores a session`() = runTest(mainRule.dispatcher) {
        val viewModel = AuthViewModel(repository)
        viewModel.toggleMode()
        viewModel.onDisplayNameChange("Behlul")
        viewModel.onEmailChange(" lifter@example.com ")
        viewModel.onPasswordChange("long-enough")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf("register:lifter@example.com:Behlul"), repository.calls)
        assertTrue(repository.session.value is SessionState.LoggedIn)
        assertNull(viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `wrong credentials surface as an error`() = runTest(mainRule.dispatcher) {
        repository.failWith = AppError.Unauthorized
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChange("lifter@example.com")
        viewModel.onPasswordChange("wrong")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(AppError.Unauthorized, viewModel.uiState.value.error)
        assertEquals(SessionState.LoggedOut, repository.session.value)
    }

    @Test
    fun `switching mode clears previous errors`() = runTest(mainRule.dispatcher) {
        val viewModel = AuthViewModel(repository)
        viewModel.submit()
        assertTrue(viewModel.uiState.value.fieldErrors.isNotEmpty())

        viewModel.toggleMode()

        assertTrue(viewModel.uiState.value.fieldErrors.isEmpty())
        assertEquals(AuthMode.REGISTER, viewModel.uiState.value.mode)
    }
}
