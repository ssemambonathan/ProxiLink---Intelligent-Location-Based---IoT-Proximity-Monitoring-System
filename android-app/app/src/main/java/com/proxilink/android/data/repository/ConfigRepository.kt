package com.proxilink.android.data.repository

import com.proxilink.android.data.local.SettingsDataStore
import com.proxilink.android.data.remote.ProxiLinkApi
import com.proxilink.android.di.ApplicationScope
import com.proxilink.android.domain.model.ServerConfig
import com.proxilink.android.domain.proximity.ProximityThresholds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public runtime configuration. Server values win when reachable; the last successful payload is
 * cached in DataStore so thresholds still work offline. Invalid payloads fall back to defaults
 * instead of crashing the classifier.
 */
@Singleton
class ConfigRepository @Inject constructor(
    private val api: ProxiLinkApi,
    private val store: SettingsDataStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    init {
        scope.launch {
            store.cachedConfig.collect { cached ->
                if (cached != null) {
                    val thresholds = runCatching {
                        ProximityThresholds(cached.immediate, cached.veryClose, cached.nearby, cached.far)
                    }.getOrDefault(ProximityThresholds.DEFAULT)
                    _config.value = ServerConfig(
                        thresholds = thresholds,
                        locationIntervalSeconds = cached.locationIntervalSeconds ?: 30,
                        scanIntervalSeconds = cached.scanIntervalSeconds ?: 15,
                        remoteSynced = cached.syncedAt != null,
                    )
                }
            }
        }
    }

    suspend fun refresh(): Result<ServerConfig> {
        val result = runCatching { api.publicConfig() }
        result.onSuccess { dto ->
            val t = dto.proximityThresholds
            val thresholds = runCatching {
                ProximityThresholds(
                    immediate = t?.immediate ?: ProximityThresholds.DEFAULT.immediate,
                    veryClose = t?.veryClose ?: ProximityThresholds.DEFAULT.veryClose,
                    nearby = t?.nearby ?: ProximityThresholds.DEFAULT.nearby,
                    far = t?.far ?: ProximityThresholds.DEFAULT.far,
                )
            }.getOrDefault(ProximityThresholds.DEFAULT)

            val config = ServerConfig(
                thresholds = thresholds,
                locationIntervalSeconds = (dto.locationIntervalSeconds ?: 30).coerceIn(5, 3600),
                scanIntervalSeconds = (dto.scanIntervalSeconds ?: 15).coerceIn(5, 600),
                remoteSynced = true,
            )
            _config.value = config
            store.saveConfig(
                immediate = config.thresholds.immediate,
                veryClose = config.thresholds.veryClose,
                nearby = config.thresholds.nearby,
                far = config.thresholds.far,
                locationIntervalSeconds = config.locationIntervalSeconds,
                scanIntervalSeconds = config.scanIntervalSeconds,
                syncedAt = Instant.now(),
            )
        }
        return result.map {
            ServerConfig(
                thresholds = _config.value.thresholds,
                locationIntervalSeconds = _config.value.locationIntervalSeconds,
                scanIntervalSeconds = _config.value.scanIntervalSeconds,
                remoteSynced = true,
            )
        }
    }
}
