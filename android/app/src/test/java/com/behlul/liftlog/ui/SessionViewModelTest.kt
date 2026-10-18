package com.behlul.liftlog.ui

import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.domain.model.SessionState
import com.behlul.liftlog.testing.FakeAuthRepository
import com.behlul.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()

    @Test
    fun `signs in automatically when there is no session`() = runTest(mainRule.dispatcher) {
        val viewModel = SessionViewModel(repository)
        advanceUntilIdle()

        assertEquals(AppStartState.Ready(42), viewModel.state.value)
        assertEquals(1, repository.signInCalls)
    }

    @Test
    fun `failed sign-in shows an error and retry recovers`() = runTest(mainRule.dispatcher) {
        repository.failures += AppError.Network
        val viewModel = SessionViewModel(repository)
        advanceUntilIdle()
        assertEquals(AppStartState.Failed(AppError.Network), viewModel.state.value)

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(AppStartState.Ready(42), viewModel.state.value)
        assertEquals(2, repository.signInCalls)
    }

    @Test
    fun `losing the session (expired token) signs in again`() = runTest(mainRule.dispatcher) {
        val viewModel = SessionViewModel(repository)
        advanceUntilIdle()

        repository.session.value = SessionState.LoggedOut // what a 401 does
        advanceUntilIdle()

        assertEquals(AppStartState.Ready(42), viewModel.state.value)
        assertEquals(2, repository.signInCalls)
    }
}
