package com.behlul.liftlog.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behlul.liftlog.R
import com.behlul.liftlog.domain.model.ExerciseGroup
import com.behlul.liftlog.domain.model.WorkoutDetail
import com.behlul.liftlog.domain.model.WorkoutSet
import com.behlul.liftlog.ui.components.ErrorView
import com.behlul.liftlog.ui.components.LoadingView
import com.behlul.liftlog.ui.components.MessageView
import com.behlul.liftlog.ui.components.UiState
import com.behlul.liftlog.ui.components.formatKg
import com.behlul.liftlog.ui.components.formatLong
import com.behlul.liftlog.ui.components.message
import com.behlul.liftlog.ui.theme.NumberStyle

@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.workoutDeleted.collect { onBack() } }

    WorkoutDetailContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onAddSet = viewModel::openAddSet,
        onEditSet = viewModel::openEditSet,
        onUpdateWorkout = viewModel::updateWorkout,
        onDeleteWorkout = viewModel::deleteWorkout,
        onActionErrorShown = viewModel::actionErrorShown,
        onNewRecordShown = viewModel::newRecordShown,
    )

    state.editor?.let { editor ->
        SetEditorSheet(
            editor = editor,
            exercises = state.exercises,
            onExerciseSelected = viewModel::onExerciseSelected,
            onRepsChange = viewModel::onRepsChange,
            onWeightChange = viewModel::onWeightChange,
            onRpeChange = viewModel::onRpeChange,
            onRetryExercises = viewModel::loadExercises,
            onCreateExercise = viewModel::createExercise,
            onSave = viewModel::saveSet,
            onDelete = { editor.editingSetId?.let(viewModel::deleteSet) },
            onDismiss = viewModel::closeEditor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailContent(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddSet: () -> Unit,
    onEditSet: (WorkoutSet) -> Unit,
    onUpdateWorkout: (java.time.LocalDate, String) -> Unit,
    onDeleteWorkout: () -> Unit,
    onActionErrorShown: () -> Unit,
    onNewRecordShown: () -> Unit = {},
) {
    val snackbarHost = remember { SnackbarHostState() }
    val actionErrorText = state.actionError?.message()
    LaunchedEffect(actionErrorText) {
        if (actionErrorText != null) {
            snackbarHost.showSnackbar(actionErrorText)
            onActionErrorShown()
        }
    }
    val newRecordText = state.newRecordExercise?.let { stringResource(R.string.new_pr_message, it) }
    LaunchedEffect(newRecordText) {
        if (newRecordText != null) {
            onNewRecordShown()
            snackbarHost.showSnackbar(newRecordText)
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val workout = (state.workout as? UiState.Success)?.data

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workout_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (workout != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_workout)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menuOpen = false; showEdit = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_workout)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (workout != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddSet,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_set)) },
                    modifier = Modifier.testTag("fab_add_set"),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state.workout) {
                UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(s.error, onRetry)
                is UiState.Success -> WorkoutBody(s.data, onEditSet)
            }
        }
    }

    if (showEdit && workout != null) {
        WorkoutFormDialog(
            title = stringResource(R.string.edit_workout),
            confirmLabel = stringResource(R.string.action_save),
            initialDate = workout.date,
            initialNotes = workout.notes.orEmpty(),
            isSaving = false,
            onConfirm = { date, notes -> onUpdateWorkout(date, notes); showEdit = false },
            onDismiss = { showEdit = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_workout_title)) },
            text = { Text(stringResource(R.string.delete_workout_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteWorkout() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun WorkoutBody(workout: WorkoutDetail, onEditSet: (WorkoutSet) -> Unit) {
    val groups = workout.setsByExercise
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("workout_detail"),
    ) {
        item { SummaryHeader(workout, groups.size) }
        if (groups.isEmpty()) {
            item {
                MessageView(
                    icon = Icons.Outlined.FitnessCenter,
                    title = stringResource(R.string.detail_empty_title),
                    body = stringResource(R.string.detail_empty_body),
                    modifier = Modifier.height(320.dp),
                )
            }
        }
        items(groups, key = { it.exerciseId }) { group -> ExerciseGroupCard(group, onEditSet) }
    }
}

/** Hero block: date and notes on a card, with the volume figure on a raised tile overlapping its corner. */
@Composable
private fun SummaryHeader(workout: WorkoutDetail, exerciseCount: Int) {
    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(end = 28.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    workout.date.formatLong(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    workout.notes ?: stringResource(R.string.workout_untitled),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeaderStat(stringResource(R.string.label_sets), workout.sets.size.toString())
                    HeaderStat(stringResource(R.string.label_exercises), exerciseCount.toString())
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(y = 12.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.total_volume),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
            Text(
                stringResource(R.string.weight_kg, formatKg(workout.totalVolumeKg)),
                style = NumberStyle.merge(MaterialTheme.typography.titleLarge),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String) {
    Column {
        Text(value, style = NumberStyle.merge(MaterialTheme.typography.titleLarge))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseGroupCard(group: ExerciseGroup, onEditSet: (WorkoutSet) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Accent rail on the left edge, thicker at the top
            Column(Modifier.fillMaxHeight().padding(vertical = 14.dp)) {
                Box(Modifier.width(6.dp).height(28.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(3.dp).weight(1f).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
                Text(group.exerciseName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                group.sets.forEachIndexed { index, set -> SetRow(index + 1, set, onClick = { onEditSet(set) }) }
            }
        }
    }
}

@Composable
private fun SetRow(number: Int, set: WorkoutSet, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("set_row_${set.id}"),
    ) {
        Text(
            stringResource(R.string.set_number, number),
            style = NumberStyle.merge(MaterialTheme.typography.labelLarge),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.weight_kg, formatKg(set.weightKg)),
            style = NumberStyle.merge(MaterialTheme.typography.titleMedium),
            modifier = Modifier.weight(1f),
        )
        Text(
            "× ${set.reps}",
            style = NumberStyle.merge(MaterialTheme.typography.titleMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Fixed-width RPE column keeps the "× reps" column aligned whether or not RPE is set
        Text(
            set.rpe?.let { stringResource(R.string.rpe_value, formatKg(it)) }.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
    }
}
