package com.behlul.liftlog.ui.progress

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.behlul.liftlog.R
import com.behlul.liftlog.ui.components.MessageView

@Composable
fun ProgressScreen() {
    MessageView(
        icon = Icons.Outlined.ShowChart,
        title = stringResource(R.string.coming_soon_title),
        body = stringResource(R.string.coming_soon_body),
    )
}
