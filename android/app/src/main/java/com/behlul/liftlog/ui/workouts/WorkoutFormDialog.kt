package com.behlul.liftlog.ui.workouts

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.behlul.liftlog.R
import com.behlul.liftlog.ui.components.formatLong
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutFormDialog(
    title: String,
    confirmLabel: String,
    initialDate: LocalDate,
    initialNotes: String,
    isSaving: Boolean,
    onConfirm: (LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var showPicker by remember { mutableStateOf(false) }

    // A read-only TextField swallows clicks, so open the picker from its press interaction
    val dateInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(dateInteraction) {
        dateInteraction.interactions.collect { if (it is PressInteraction.Release) showPicker = true }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = date.formatLong(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_date)) },
                    trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    interactionSource = dateInteraction,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(2000) },
                    label = { Text(stringResource(R.string.field_notes)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workout_notes"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(date, notes) }, enabled = !isSaving) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        // DatePicker reports midnight UTC of the chosen day
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
