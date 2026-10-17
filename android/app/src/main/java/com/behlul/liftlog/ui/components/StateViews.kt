package com.behlul.liftlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.behlul.liftlog.R
import com.behlul.liftlog.domain.model.AppError
import kotlinx.coroutines.delay

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    // Only shown when loading is unusually slow, which in practice means the server is cold-starting
    var showWakeUpHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(WAKE_UP_HINT_DELAY_MS)
        showWakeUpHint = true
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (showWakeUpHint) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.server_waking_up),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val WAKE_UP_HINT_DELAY_MS = 4_000L

@Composable
fun ErrorView(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageView(
        icon = Icons.Outlined.CloudOff,
        title = error.message(),
        body = null,
        modifier = modifier,
        action = {
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        },
    )
}

/**
 * Icon sits on two offset, overlapping tiles so the empty/error illustration has depth
 * instead of being a single flat circle.
 */
@Composable
fun MessageView(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(72.dp)
                    .offset(x = 14.dp, y = (-10).dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(22.dp)),
            )
            Box(
                Modifier
                    .size(72.dp)
                    .offset(x = (-8).dp, y = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            }
            Box(
                Modifier
                    .size(14.dp)
                    .offset(x = 34.dp, y = 30.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
