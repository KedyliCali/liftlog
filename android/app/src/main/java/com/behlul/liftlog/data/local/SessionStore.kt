package com.behlul.liftlog.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.behlul.liftlog.di.ApplicationScope
import com.behlul.liftlog.domain.model.Session
import com.behlul.liftlog.domain.model.SessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Persists the JWT and basic user info in app-private storage (DataStore). Exposed as a StateFlow
 * so the OkHttp interceptor can read the token synchronously on its own thread.
 */
/**
 * The hidden account this installation signs in with. Generated once on first launch; the user never
 * types or sees it. Android Auto Backup restores app data, so it usually survives a phone change too.
 */
data class DeviceCredentials(val email: String, val password: String) {
    companion object {
        fun generate(random: SecureRandom = SecureRandom()): DeviceCredentials {
            val secret = ByteArray(32).also(random::nextBytes)
            return DeviceCredentials(
                email = "device-${UUID.randomUUID()}@liftlog.device",
                // 256 random bits, 43 URL-safe characters (BCrypt only reads the first 72 bytes)
                password = Base64.getUrlEncoder().withoutPadding().encodeToString(secret),
            )
        }
    }
}

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val dataStore = context.sessionDataStore

    val state: StateFlow<SessionState> = dataStore.data
        .map { prefs -> prefs.toSession()?.let(SessionState::LoggedIn) ?: SessionState.LoggedOut }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    val currentToken: String?
        get() = (state.value as? SessionState.LoggedIn)?.session?.token

    suspend fun save(session: Session) {
        dataStore.edit { prefs ->
            prefs[TOKEN] = session.token
            prefs[USER_ID] = session.userId
            prefs[EMAIL] = session.email
            if (session.displayName != null) prefs[DISPLAY_NAME] = session.displayName else prefs.remove(DISPLAY_NAME)
        }
    }

    /** Forgets the session token but keeps this device's account credentials, so it can sign in again. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(TOKEN)
            prefs.remove(USER_ID)
            prefs.remove(EMAIL)
            prefs.remove(DISPLAY_NAME)
        }
    }

    suspend fun deviceCredentials(): DeviceCredentials? {
        val prefs = dataStore.data.first()
        val email = prefs[DEVICE_EMAIL] ?: return null
        val password = prefs[DEVICE_PASSWORD] ?: return null
        return DeviceCredentials(email, password)
    }

    suspend fun saveDeviceCredentials(credentials: DeviceCredentials) {
        dataStore.edit { prefs ->
            prefs[DEVICE_EMAIL] = credentials.email
            prefs[DEVICE_PASSWORD] = credentials.password
        }
    }

    private fun Preferences.toSession(): Session? {
        val token = this[TOKEN] ?: return null
        val userId = this[USER_ID] ?: return null
        val email = this[EMAIL] ?: return null
        return Session(token, userId, email, this[DISPLAY_NAME])
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = longPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val DEVICE_EMAIL = stringPreferencesKey("device_email")
        val DEVICE_PASSWORD = stringPreferencesKey("device_password")
    }
}
