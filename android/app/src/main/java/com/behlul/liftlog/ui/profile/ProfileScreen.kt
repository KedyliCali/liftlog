package com.behlul.liftlog.ui.profile

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behlul.liftlog.BuildConfig
import com.behlul.liftlog.R
import com.behlul.liftlog.domain.model.Profile
import com.behlul.liftlog.ui.components.ErrorView
import com.behlul.liftlog.ui.components.LoadingView
import com.behlul.liftlog.ui.components.UiState
import com.behlul.liftlog.ui.components.formatKg
import com.behlul.liftlog.ui.components.message
import com.behlul.liftlog.ui.theme.NumberStyle
import java.util.Locale

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // System photo picker: no storage permission needed, the user only shares the one photo
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPhotoPicked)
    }

    ProfileContent(
        state = state,
        onRetry = viewModel::load,
        onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemovePhoto = viewModel::removePhoto,
        onEdit = viewModel::openEditor,
        onActionErrorShown = viewModel::actionErrorShown,
    )

    state.editor?.let { editor ->
        ProfileEditorSheet(
            editor = editor,
            onNameChange = viewModel::onNameChange,
            onHeightChange = viewModel::onHeightChange,
            onWeightChange = viewModel::onWeightChange,
            onSave = viewModel::saveProfile,
            onDismiss = viewModel::closeEditor,
        )
    }
}

@Composable
fun ProfileContent(
    state: ProfileUiState,
    onRetry: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onEdit: () -> Unit,
    onActionErrorShown: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val errorText = state.actionError?.message()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHost.showSnackbar(errorText)
            onActionErrorShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Box(Modifier.padding(padding)) {
            when (val profile = state.profile) {
                UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(profile.error, onRetry)
                is UiState.Success -> ProfileBody(profile.data, state, onPickPhoto, onRemovePhoto, onEdit)
            }
        }
    }
}

@Composable
private fun ProfileBody(
    profile: Profile,
    state: ProfileUiState,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onEdit: () -> Unit,
) {
    val name = profile.displayName ?: stringResource(R.string.profile_default_name)
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(24.dp))

        // Card with the avatar overlapping its top edge
        Box(Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 72.dp, bottom = 16.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("profile_name"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = onEdit, modifier = Modifier.testTag("edit_profile")) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.profile_edit), modifier = Modifier.padding(start = 6.dp))
                        }
                        if (profile.avatarVersion != null) {
                            TextButton(onClick = onRemovePhoto) { Text(stringResource(R.string.profile_remove_photo)) }
                        }
                    }
                }
            }
            Avatar(
                name = name,
                jpeg = state.avatarJpeg,
                isUploading = state.isUploadingAvatar,
                onClick = onPickPhoto,
                modifier = Modifier.padding(start = 20.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BodyStatTile(
                label = stringResource(R.string.profile_height),
                value = profile.heightCm?.let { stringResource(R.string.unit_cm, formatKg(it)) },
                modifier = Modifier.weight(1f),
            )
            BodyStatTile(
                label = stringResource(R.string.profile_body_weight),
                value = profile.bodyWeightKg?.let { stringResource(R.string.weight_kg, formatKg(it)) },
                modifier = Modifier.weight(1f),
            )
            BodyStatTile(
                label = stringResource(R.string.profile_bmi),
                value = profile.bmi?.let { String.format(Locale.getDefault(), "%.1f", it) },
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(stringResource(R.string.profile_server), BuildConfig.API_BASE_URL.removeSuffix("/"))
                InfoRow(stringResource(R.string.profile_version), BuildConfig.VERSION_NAME)
            }
        }
    }
}

/**
 * Photo (or initials) in a ring, with a camera badge overlapping the lower right to show it is
 * tappable. While uploading, a spinner covers it.
 */
@Composable
private fun Avatar(name: String, jpeg: ByteArray?, isUploading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap = remember(jpeg) { jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(modifier.size(124.dp)) {
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick)
                .testTag("avatar"),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(initials(name), style = NumberStyle.merge(MaterialTheme.typography.displaySmall), color = MaterialTheme.colorScheme.onPrimary)
            }
            if (isUploading) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Box(
            Modifier
                .size(38.dp)
                .offset(x = 80.dp, y = 78.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .padding(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = stringResource(R.string.profile_change_photo),
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BodyStatTile(label: String, value: String?, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val container = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Surface(shape = RoundedCornerShape(18.dp), color = container, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                value ?: "—",
                style = NumberStyle.merge(MaterialTheme.typography.titleLarge),
                color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorSheet(
    editor: ProfileEditorState,
    onNameChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.profile_edit), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("field_name"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.height,
                    onValueChange = onHeightChange,
                    label = { Text(stringResource(R.string.profile_height_cm)) },
                    isError = ProfileField.HEIGHT in editor.errors,
                    supportingText = if (ProfileField.HEIGHT in editor.errors) ({ Text(stringResource(R.string.error_height)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f).testTag("field_height"),
                )
                OutlinedTextField(
                    value = editor.weight,
                    onValueChange = onWeightChange,
                    label = { Text(stringResource(R.string.profile_body_weight_kg)) },
                    isError = ProfileField.WEIGHT in editor.errors,
                    supportingText = if (ProfileField.WEIGHT in editor.errors) ({ Text(stringResource(R.string.error_body_weight)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f).testTag("field_body_weight"),
                )
            }
            Button(
                onClick = onSave,
                enabled = !editor.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_profile"),
            ) {
                if (editor.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

private fun initials(name: String): String =
    name.split(' ', '.', '_', '-').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase(Locale.getDefault()) }
        .ifEmpty { "?" }
