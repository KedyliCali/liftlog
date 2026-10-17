package com.behlul.liftlog.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behlul.liftlog.R
import com.behlul.liftlog.data.repository.AuthRepository
import com.behlul.liftlog.domain.model.SessionState
import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.ui.components.ErrorView
import com.behlul.liftlog.ui.components.LoadingView
import com.behlul.liftlog.ui.components.toAppError
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import com.behlul.liftlog.ui.progress.ProgressScreen
import com.behlul.liftlog.ui.profile.ProfileScreen
import com.behlul.liftlog.ui.workouts.WorkoutDetailScreen
import com.behlul.liftlog.ui.workouts.WorkoutListScreen
import kotlinx.serialization.Serializable

@Serializable data object WorkoutsRoute
@Serializable data class WorkoutDetailRoute(val workoutId: Long)
@Serializable data object ProgressRoute
@Serializable data object ProfileRoute

private data class TopLevelTab(val route: Any, @StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    TopLevelTab(WorkoutsRoute, R.string.nav_workouts, Icons.Outlined.FitnessCenter),
    TopLevelTab(ProgressRoute, R.string.nav_progress, Icons.AutoMirrored.Outlined.ShowChart),
    TopLevelTab(ProfileRoute, R.string.nav_profile, Icons.Outlined.Person),
)

sealed interface AppStartState {
    data object SigningIn : AppStartState
    data class Failed(val error: AppError) : AppStartState
    data class Ready(val userId: Long) : AppStartState
}

/**
 * There is no login screen: whenever there is no session (first launch, or the token expired and a
 * 401 cleared it) this signs in with the device's hidden account automatically.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val signInError = MutableStateFlow<AppError?>(null)
    private val signInMutex = Mutex()

    val state: StateFlow<AppStartState> = combine(authRepository.session, signInError) { session, error ->
        when {
            session is SessionState.LoggedIn -> AppStartState.Ready(session.session.userId)
            error != null -> AppStartState.Failed(error)
            else -> AppStartState.SigningIn
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppStartState.SigningIn)

    init {
        viewModelScope.launch {
            authRepository.session.collect { if (it is SessionState.LoggedOut) signIn() }
        }
    }

    fun retry() {
        viewModelScope.launch { signIn() }
    }

    private suspend fun signIn() {
        // tryLock: a sign-in already in flight covers this request too
        if (!signInMutex.tryLock()) return
        try {
            signInError.value = null
            authRepository.signInDevice().onFailure { signInError.value = it.toAppError() }
        } finally {
            signInMutex.unlock()
        }
    }
}

@Composable
fun LiftLogApp(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val state by sessionViewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        AppStartState.SigningIn -> LoadingView()
        is AppStartState.Failed -> ErrorView(current.error, onRetry = sessionViewModel::retry)
        // Keyed by user so a different account never sees the previous account's screens or ViewModels
        is AppStartState.Ready -> key(current.userId) { MainScaffold() }
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = tabs.any { tab -> destination?.hasRoute(tab.route::class) == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    // Standard bottom-nav behaviour: one copy of each tab, state kept per tab
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            NavHost(navController = navController, startDestination = WorkoutsRoute) {
                composable<WorkoutsRoute> {
                    WorkoutListScreen(onOpenWorkout = { id -> navController.navigate(WorkoutDetailRoute(id)) })
                }
                composable<WorkoutDetailRoute> {
                    // The ViewModel reads workoutId from this entry's SavedStateHandle
                    WorkoutDetailScreen(onBack = { navController.popBackStack() })
                }
                composable<ProgressRoute> { ProgressScreen() }
                composable<ProfileRoute> { ProfileScreen() }
            }
        }
    }
}
