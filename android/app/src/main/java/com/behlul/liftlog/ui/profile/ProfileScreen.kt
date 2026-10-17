package com.behlul.liftlog.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.behlul.liftlog.BuildConfig
import com.behlul.liftlog.R
import com.behlul.liftlog.data.repository.AuthRepository
import com.behlul.liftlog.domain.model.Session
import com.behlul.liftlog.domain.model.SessionState
import com.behlul.liftlog.ui.theme.NumberStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = authRepository.session
        .map { (it as? SessionState.LoggedIn)?.session }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

}

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    session?.let { ProfileContent(it) }
}

@Composable
fun ProfileContent(session: Session) {
    val name = session.displayName ?: session.email.substringBefore('@')
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(24.dp))

        Box(Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 20.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("profile_name"))
                    Text(session.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Avatar(name, Modifier.padding(start = 20.dp))
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

/** Initials on a primary circle with a smaller accent dot overlapping its edge. */
@Composable
private fun Avatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.split(' ', '.', '_', '-').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase(Locale.getDefault()) }
    Box(modifier.size(80.dp)) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = NumberStyle.merge(MaterialTheme.typography.headlineMedium), color = MaterialTheme.colorScheme.onPrimary)
        }
        Box(
            Modifier
                .size(22.dp)
                .offset(x = 54.dp, y = 52.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
