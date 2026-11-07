package com.proxilink.android.data.remote

import com.proxilink.android.data.local.TokenStore
import com.proxilink.android.data.remote.dto.RefreshRequest
import com.proxilink.android.domain.model.AuthTokens
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Single-flight token refresh shared by the HTTP Authenticator and the WebSocket reconnect loop.
 * Refresh tokens rotate, so concurrent refreshes must never race.
 */
@Singleton
class SessionRefresher @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApi: Provider<AuthRefreshApi>,
) {
    private val mutex = Mutex()

    suspend fun refresh(): AuthTokens? = mutex.withLock {
        val current = tokenStore.current() ?: return@withLock null
        val response = runCatching {
            refreshApi.get().refresh(RefreshRequest(current.refreshToken))
        }.getOrNull()
        if (response == null || response.accessToken.isBlank() || response.refreshToken.isBlank()) {
            null
        } else {
            val expiresAt = Instant.now().plusSeconds(response.expiresInSeconds.coerceAtLeast(60))
            tokenStore.save(response.accessToken, response.refreshToken, expiresAt)
            AuthTokens(response.accessToken, response.refreshToken, expiresAt)
        }
    }
}
