package com.behlul.liftlog.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.behlul.liftlog.R
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
    TopLevelTab(ProgressRoute, R.string.nav_progress, Icons.Outlined.ShowChart),
    TopLevelTab(ProfileRoute, R.string.nav_profile, Icons.Outlined.Person),
)

@Composable
fun LiftLogApp() {
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
