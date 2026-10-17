package com.behlul.liftlog.data.remote

import com.behlul.liftlog.data.local.SessionStore
import com.behlul.liftlog.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Adds the bearer token to every API call. A 401 on an authenticated call means the token expired
 * or was revoked, so the session is cleared and the app falls back to the login screen.
 */
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
    @ApplicationScope private val scope: CoroutineScope,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = sessionStore.currentToken
        val isAuthCall = request.url.encodedPath.startsWith("/api/v1/auth/") &&
            !request.url.encodedPath.endsWith("/me")

        val authorized = if (token != null && !isAuthCall) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }

        val response = chain.proceed(authorized)
        if (response.code == 401 && token != null && !isAuthCall) {
            scope.launch { sessionStore.clear() }
        }
        return response
    }
}
