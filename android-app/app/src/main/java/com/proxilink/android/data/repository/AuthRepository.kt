package com.proxilink.android.data.repository

import com.proxilink.android.data.local.SessionDataStore
import com.proxilink.android.data.local.TokenStore
import com.proxilink.android.data.remote.ProxiLinkApi
import com.proxilink.android.data.remote.RealtimeClient
import com.proxilink.android.data.remote.dto.LoginRequest
import com.proxilink.android.data.remote.dto.RefreshRequest
import com.proxilink.android.data.remote.dto.RegisterRequest
import com.proxilink.android.data.remote.dto.TokenResponse
import com.proxilink.android.data.remote.dto.UserDto
import com.proxilink.android.data.remote.toDomain
import com.proxilink.android.di.ApplicationScope
import com.proxilink.android.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class SessionState { UNKNOWN, SIGNED_OUT, SIGNED_IN }

@Singleton
class AuthRepository @Inject constructor(
    private val api: ProxiLinkApi,
    private val tokenStore: TokenStore,
    private val sessionStore: SessionDataStore,
    private val realtime: RealtimeClient,
    private val configRepository: ConfigRepository,
    private val json: Json,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val sessionState: StateFlow<SessionState> = tokenStore.tokens
        .map { if (it == null) SessionState.SIGNED_OUT else SessionState.SIGNED_IN }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            if (tokenStore.cached() == null) SessionState.UNKNOWN else SessionState.SIGNED_IN,
        )

    val user: StateFlow<UserProfile?> =
        sessionStore.user.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun login(email: String, password: String): Result<UserProfile> = runCatching {
        val response = api.login(LoginRequest(email.trim(), password))
        persist(response)
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        organization: String?,
    ): Result<UserProfile> = runCatching {
        val response = api.register(
            RegisterRequest(
                email = email.trim(),
                password = password,
                displayName = displayName.trim(),
                organization = organization?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
        persist(response)
    }

    suspend fun refreshProfile(): Result<UserProfile> = runCatching {
        val dto = api.me()
        sessionStore.saveUser(json.encodeToString(UserDto.serializer(), dto))
        dto.toDomain()
    }

    suspend fun logout() {
        val tokens = tokenStore.current()
        if (tokens != null) {
            runCatching { api.logout(RefreshRequest(tokens.refreshToken)) }
        }
        realtime.stop()
        tokenStore.clear()
    }

    /** Called from the app shell when a stored session already exists. */
    fun connectRealtimeIfSignedIn() {
        if (tokenStore.cached() != null) realtime.start()
    }

    private suspend fun persist(response: TokenResponse): UserProfile {
        check(response.accessToken.isNotBlank()) { "Server response did not include an access token" }
        check(response.refreshToken.isNotBlank()) { "Server response did not include a refresh token" }
        val expiresAt = Instant.now().plusSeconds(response.expiresInSeconds.coerceAtLeast(60))
        tokenStore.save(response.accessToken, response.refreshToken, expiresAt)

        val userDto = response.user ?: api.me()
        sessionStore.saveUser(json.encodeToString(UserDto.serializer(), userDto))

        realtime.start()
        scope.launch { configRepository.refresh() }
        return userDto.toDomain()
    }
}
