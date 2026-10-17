package com.behlul.liftlog.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.behlul.liftlog.R
import com.behlul.liftlog.ui.components.MessageView

@Composable
fun ProfileScreen() {
    MessageView(
        icon = Icons.Outlined.Person,
        title = stringResource(R.string.coming_soon_title),
        body = stringResource(R.string.coming_soon_body),
    )
}
