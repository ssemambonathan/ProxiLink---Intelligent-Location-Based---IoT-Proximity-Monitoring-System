package com.proxilink.android.data.repository

import com.proxilink.android.data.local.AppSettings
import com.proxilink.android.data.local.SettingsDataStore
import com.proxilink.android.data.remote.ApiConfig
import com.proxilink.android.di.ApplicationScope
import com.proxilink.android.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: SettingsDataStore,
    private val apiConfig: ApiConfig,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    init {
        scope.launch {
            store.settings.collect { current ->
                apiConfig.baseUrl = current.apiBaseUrl
            }
        }
    }

    suspend fun setApiBaseUrl(candidate: String): Result<String> {
        val trimmed = candidate.trim().let { if (it.endsWith("/")) it else "$it/" }
        if (!apiConfig.isValidCandidate(trimmed)) {
            return Result.failure(IllegalArgumentException("Enter a full http:// or https:// URL"))
        }
        store.setApiBaseUrl(trimmed)
        apiConfig.baseUrl = trimmed
        return Result.success(trimmed)
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.setThemeMode(mode)
    suspend fun setNotificationsEnabled(enabled: Boolean) = store.setNotificationsEnabled(enabled)
    suspend fun setSimulationMode(enabled: Boolean) = store.setSimulationMode(enabled)
    suspend fun setScanIntervalSeconds(seconds: Int) = store.setScanIntervalSeconds(seconds)
    suspend fun setOnboardingCompleted(completed: Boolean) = store.setOnboardingCompleted(completed)

    suspend fun setLocationSharingEnabled(enabled: Boolean) = store.setLocationSharingEnabled(enabled)
    suspend fun setBackgroundMonitoringEnabled(enabled: Boolean) = store.setBackgroundMonitoringEnabled(enabled)
}
