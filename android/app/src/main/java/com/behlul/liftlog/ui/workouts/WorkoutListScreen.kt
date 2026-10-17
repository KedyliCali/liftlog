package com.behlul.liftlog.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behlul.liftlog.R
import com.behlul.liftlog.domain.model.WorkoutSummary
import com.behlul.liftlog.ui.components.ErrorView
import com.behlul.liftlog.ui.components.LoadingView
import com.behlul.liftlog.ui.components.MessageView
import com.behlul.liftlog.ui.components.UiState
import com.behlul.liftlog.ui.components.formatKg
import com.behlul.liftlog.ui.components.message
import com.behlul.liftlog.ui.components.shortMonth
import com.behlul.liftlog.ui.components.shortWeekday
import com.behlul.liftlog.ui.theme.NumberStyle
import java.time.LocalDate

@Composable
fun WorkoutListScreen(
    onOpenWorkout: (Long) -> Unit,
    viewModel: WorkoutListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.openWorkout.collect(onOpenWorkout) }

    // This composable is disposed while a detail screen is shown, so the ViewModel decides
    // whether a resume needs a refresh rather than local remembered state
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    WorkoutListContent(
        state = state,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpenWorkout = onOpenWorkout,
        onCreateWorkout = viewModel::createWorkout,
        onActionErrorShown = viewModel::actionErrorShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListContent(
    state: WorkoutListUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onCreateWorkout: (LocalDate, String) -> Unit,
    onActionErrorShown: () -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val actionErrorText = state.actionError?.message()

    LaunchedEffect(actionErrorText) {
        if (actionErrorText != null) {
            snackbarHost.showSnackbar(actionErrorText)
            onActionErrorShown()
        }
    }
    // The dialog closes once the ViewModel has navigated away or reported an error
    LaunchedEffect(state.isCreating) { if (!state.isCreating) showCreateDialog = false }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.workout_new)) },
                modifier = Modifier.testTag("fab_new_workout"),
            )
        },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            ListHeader()
            when (val workouts = state.workouts) {
                UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(workouts.error, onRetry)
                is UiState.Success -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (workouts.data.isEmpty()) {
                        MessageView(
                            icon = Icons.Outlined.FitnessCenter,
                            title = stringResource(R.string.workouts_empty_title),
                            body = stringResource(R.string.workouts_empty_body),
                        )
                    } else {
                        WorkoutList(workouts.data, state.isLoadingMore, onLoadMore, onOpenWorkout)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        WorkoutFormDialog(
            title = stringResource(R.string.workout_new),
            confirmLabel = stringResource(R.string.action_create),
            initialDate = LocalDate.now(),
            initialNotes = "",
            isSaving = state.isCreating,
            onConfirm = onCreateWorkout,
            onDismiss = { showCreateDialog = false },
        )
    }
}

/** Large title with a small accent block offset behind it. */
@Composable
private fun ListHeader() {
    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(width = 44.dp, height = 10.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
        )
        Column(Modifier.padding(top = 14.dp)) {
            Text(stringResource(R.string.workouts_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.workouts_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkoutList(
    workouts: List<WorkoutSummary>,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, workouts.size) { if (nearEnd) onLoadMore() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("workout_list"),
    ) {
        items(workouts, key = { it.id }) { workout ->
            WorkoutCard(workout, onClick = { onOpenWorkout(workout.id) })
        }
        if (isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
fun WorkoutCard(workout: WorkoutSummary, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        // Base card, inset from the left so the date block can overhang its edge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .padding(start = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
        ) {
            Row {
                Spacer(Modifier.width(62.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(top = 16.dp, bottom = 16.dp, end = 16.dp),
                ) {
                    Text(
                        workout.notes ?: stringResource(R.string.workout_untitled),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatChip(pluralStringResource(R.plurals.sets_count, workout.setCount, workout.setCount))
                        StatChip(pluralStringResource(R.plurals.exercises_count, workout.exerciseCount, workout.exerciseCount))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.volume_kg, formatKg(workout.totalVolumeKg)),
                        style = NumberStyle.merge(MaterialTheme.typography.labelLarge),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        DateBlock(workout.date, Modifier.padding(top = 14.dp))
    }
}

/** Stacked weekday / day / month tile with a darker backing tile peeking out below. */
@Composable
private fun DateBlock(date: LocalDate, modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(
            Modifier
                .padding(start = 6.dp, top = 8.dp)
                .size(width = 64.dp, height = 76.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(width = 64.dp, height = 76.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        ) {
            Text(
                date.shortWeekday(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            )
            Text(
                date.dayOfMonth.toString(),
                style = NumberStyle.merge(MaterialTheme.typography.headlineMedium),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(date.shortMonth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
