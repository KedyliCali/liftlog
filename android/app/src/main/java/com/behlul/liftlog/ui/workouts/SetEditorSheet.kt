package com.behlul.liftlog.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.behlul.liftlog.R
import com.behlul.liftlog.domain.model.Exercise
import com.behlul.liftlog.ui.components.ErrorView
import com.behlul.liftlog.ui.components.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetEditorSheet(
    editor: SetEditorState,
    exercises: UiState<List<Exercise>>,
    onExerciseSelected: (Exercise) -> Unit,
    onRepsChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRpeChange: (String) -> Unit,
    onRetryExercises: () -> Unit,
    onCreateExercise: (String, String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingExercise by rememberSaveable { mutableStateOf(editor.exercise == null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (pickingExercise) {
                ExercisePicker(
                    exercises = exercises,
                    onRetry = onRetryExercises,
                    onPick = { onExerciseSelected(it); pickingExercise = false },
                    onCreate = { name, group -> onCreateExercise(name, group); pickingExercise = false },
                    onBack = if (editor.exercise != null) ({ pickingExercise = false }) else null,
                )
            } else {
                SetForm(
                    editor = editor,
                    onPickExercise = { pickingExercise = true },
                    onRepsChange = onRepsChange,
                    onWeightChange = onWeightChange,
                    onRpeChange = onRpeChange,
                    onSave = onSave,
                    onDelete = onDelete,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetForm(
    editor: SetEditorState,
    onPickExercise: () -> Unit,
    onRepsChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRpeChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val isEditing = editor.editingSetId != null
    Text(
        stringResource(if (isEditing) R.string.edit_set else R.string.add_set),
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(16.dp))

    // Exercise selector: a tappable tile with a colored tab sticking out of its left edge
    Box(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onPickExercise)
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
                .testTag("exercise_selector"),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.field_exercise),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (SetField.EXERCISE in editor.errors) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    editor.exercise?.name ?: stringResource(R.string.choose_exercise),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
        Box(
            Modifier
                .padding(top = 14.dp)
                .size(width = 6.dp, height = 24.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)),
        )
    }
    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            value = editor.weight,
            onValueChange = onWeightChange,
            label = stringResource(R.string.field_weight),
            error = stringResource(R.string.error_weight).takeIf { SetField.WEIGHT in editor.errors },
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1.3f).testTag("field_weight"),
        )
        NumberField(
            value = editor.reps,
            onValueChange = onRepsChange,
            label = stringResource(R.string.field_reps),
            error = stringResource(R.string.error_reps).takeIf { SetField.REPS in editor.errors },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f).testTag("field_reps"),
        )
    }
    Spacer(Modifier.height(8.dp))
    NumberField(
        value = editor.rpe,
        onValueChange = onRpeChange,
        label = stringResource(R.string.field_rpe),
        error = stringResource(R.string.error_rpe).takeIf { SetField.RPE in editor.errors },
        keyboardType = KeyboardType.Decimal,
        imeAction = ImeAction.Done,
        modifier = Modifier.fillMaxWidth().testTag("field_rpe"),
    )
    Spacer(Modifier.height(20.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isEditing) {
            OutlinedButton(onClick = onDelete, enabled = !editor.isSaving) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = onSave,
            enabled = !editor.isSaving,
            modifier = Modifier.weight(1f).height(52.dp).testTag("save_set"),
        ) {
            if (editor.isSaving) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        textStyle = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

@Composable
private fun ExercisePicker(
    exercises: UiState<List<Exercise>>,
    onRetry: () -> Unit,
    onPick: (Exercise) -> Unit,
    onCreate: (String, String) -> Unit,
    onBack: (() -> Unit)?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }
        Text(
            stringResource(if (creating) R.string.exercise_new else R.string.choose_exercise),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    Spacer(Modifier.height(12.dp))

    if (creating) {
        NewExerciseForm(initialName = query, onCreate = onCreate, onCancel = { creating = false })
        return
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.exercise_search)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("exercise_search"),
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { creating = true }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.exercise_new))
    }

    when (exercises) {
        UiState.Loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.height(280.dp)) { ErrorView(exercises.error, onRetry) }
        is UiState.Success -> {
            val filtered = exercises.data.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) {
                Text(
                    stringResource(R.string.exercise_none_found, query.trim()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp).testTag("exercise_list")) {
                items(filtered, key = { it.id }) { exercise ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(exercise) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                            exercise.muscleGroup?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (exercise.isCustom) {
                            Text(
                                stringResource(R.string.exercise_custom_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun NewExerciseForm(initialName: String, onCreate: (String, String) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var group by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(100); showError = false },
        label = { Text(stringResource(R.string.exercise_name)) },
        isError = showError,
        supportingText = if (showError) ({ Text(stringResource(R.string.error_name_required)) }) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("new_exercise_name"),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = group,
        onValueChange = { group = it.take(50) },
        label = { Text(stringResource(R.string.exercise_muscle_group)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
        Button(
            onClick = { if (name.isBlank()) showError = true else onCreate(name, group) },
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.action_create)) }
    }
}
