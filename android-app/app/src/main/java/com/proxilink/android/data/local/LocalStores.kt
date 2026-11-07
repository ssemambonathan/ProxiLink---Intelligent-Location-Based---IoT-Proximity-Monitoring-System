package com.proxilink.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.proxilink.android.BuildConfig
import com.proxilink.android.domain.model.AuthTokens
import com.proxilink.android.domain.model.ThemeMode
import com.proxilink.android.domain.model.UserProfile
import com.proxilink.android.data.remote.dto.UserDto
import com.proxilink.android.data.remote.toDomain
import com.proxilink.android.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "proxilink_session")
private val Context.settingsDataStore by preferencesDataStore(name = "proxilink_settings")

/**
 * Token persistence abstraction. The DataStore implementation is plaintext inside the app sandbox.
 * Production builds should back this with EncryptedSharedPreferences or a Keystore-wrapped cipher;
 * the interface exists so that swap stays local.
 */
interface TokenStore {
    val tokens: Flow<AuthTokens?>

    /** Non-suspending snapshot for OkHttp interceptors; may be momentarily null right after launch. */
    fun cached(): AuthTokens?
    suspend fun current(): AuthTokens?
    suspend fun save(accessToken: String, refreshToken: String, expiresAt: Instant)
    suspend fun clear()
}

@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) : TokenStore {

    private val store = context.sessionDataStore
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedTokens: AuthTokens? = null

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES = longPreferencesKey("expires_at_epoch_seconds")
        val USER = stringPreferencesKey("user_json")
    }

    override val tokens: Flow<AuthTokens?> = store.data.map { prefs -> prefs.toTokens() }

    val user: Flow<UserProfile?> = store.data.map { prefs ->
        prefs[Keys.USER]?.let { raw ->
            runCatching { json.decodeFromString(UserDto.serializer(), raw).toDomain() }.getOrNull()
        }
    }

    init {
        scope.launch { tokens.collect { cachedTokens = it } }
    }

    override fun cached(): AuthTokens? = cachedTokens

    override suspend fun current(): AuthTokens? = tokens.first()

    override suspend fun save(accessToken: String, refreshToken: String, expiresAt: Instant) {
        cachedTokens = AuthTokens(accessToken, refreshToken, expiresAt)
        store.edit { prefs ->
            prefs[Keys.ACCESS] = accessToken
            prefs[Keys.REFRESH] = refreshToken
            prefs[Keys.EXPIRES] = expiresAt.epochSecond
        }
    }

    suspend fun saveUser(userJson: String) {
        store.edit { it[Keys.USER] = userJson }
    }

    suspend fun currentUser(): UserProfile? = user.first()

    override suspend fun clear() {
        cachedTokens = null
        store.edit { it.clear() }
    }

    private fun Preferences.toTokens(): AuthTokens? {
        val access = this[Keys.ACCESS] ?: return null
        val refresh = this[Keys.REFRESH] ?: return null
        return AuthTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = Instant.ofEpochSecond(this[Keys.EXPIRES] ?: 0L),
        )
    }
}

data class AppSettings(
    val apiBaseUrl: String = BuildConfig.DEFAULT_API_BASE_URL,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val notificationsEnabled: Boolean = true,
    val locationSharingEnabled: Boolean = false,
    val backgroundMonitoringEnabled: Boolean = false,
    val simulationMode: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val scanIntervalSeconds: Int = 15,
) {
    val isDefaultBaseUrl: Boolean get() = apiBaseUrl == BuildConfig.DEFAULT_API_BASE_URL
}

/**
 * User preferences plus the last server-provided public config, so thresholds and intervals keep
 * working offline after the first successful sync.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.settingsDataStore

    private object Keys {
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val THEME = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val LOCATION_SHARING = booleanPreferencesKey("location_sharing_enabled")
        val BACKGROUND_MONITORING = booleanPreferencesKey("background_monitoring_enabled")
        val SIMULATION = booleanPreferencesKey("simulation_mode")
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val SCAN_INTERVAL = intPreferencesKey("scan_interval_seconds")
        val CONFIG_IMMEDIATE = intPreferencesKey("config_immediate")
        val CONFIG_VERY_CLOSE = intPreferencesKey("config_very_close")
        val CONFIG_NEARBY = intPreferencesKey("config_nearby")
        val CONFIG_FAR = intPreferencesKey("config_far")
        val CONFIG_LOCATION_INTERVAL = intPreferencesKey("config_location_interval")
        val CONFIG_SCAN_INTERVAL = intPreferencesKey("config_scan_interval")
        val CONFIG_SYNCED_AT = longPreferencesKey("config_synced_at")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val ACKED_TOTAL = longPreferencesKey("acked_total")
        val REJECTED_TOTAL = longPreferencesKey("rejected_total")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            apiBaseUrl = prefs[Keys.API_BASE_URL] ?: BuildConfig.DEFAULT_API_BASE_URL,
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.DARK.name) }
                .getOrDefault(ThemeMode.DARK),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            locationSharingEnabled = prefs[Keys.LOCATION_SHARING] ?: false,
            backgroundMonitoringEnabled = prefs[Keys.BACKGROUND_MONITORING] ?: false,
            simulationMode = prefs[Keys.SIMULATION] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING] ?: false,
            scanIntervalSeconds = prefs[Keys.SCAN_INTERVAL] ?: 15,
        )
    }

    val settingsSnapshot: AppSettings get() = current

    @Volatile
    private var current: AppSettings = AppSettings()

    suspend fun refreshCache() {
        current = settings.first()
    }

    suspend fun setApiBaseUrl(url: String) = store.edit { it[Keys.API_BASE_URL] = url }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setNotificationsEnabled(enabled: Boolean) = store.edit { it[Keys.NOTIFICATIONS] = enabled }
    suspend fun setLocationSharingEnabled(enabled: Boolean) = store.edit { it[Keys.LOCATION_SHARING] = enabled }
    suspend fun setBackgroundMonitoringEnabled(enabled: Boolean) = store.edit { it[Keys.BACKGROUND_MONITORING] = enabled }
    suspend fun setSimulationMode(enabled: Boolean) = store.edit { it[Keys.SIMULATION] = enabled }
    suspend fun setOnboardingCompleted(completed: Boolean) = store.edit { it[Keys.ONBOARDING] = completed }
    suspend fun setScanIntervalSeconds(seconds: Int) = store.edit { it[Keys.SCAN_INTERVAL] = seconds.coerceIn(5, 120) }

    data class CachedConfig(
        val immediate: Int,
        val veryClose: Int,
        val nearby: Int,
        val far: Int,
        val locationIntervalSeconds: Int?,
        val scanIntervalSeconds: Int?,
        val syncedAt: Instant?,
    )

    val cachedConfig: Flow<CachedConfig?> = store.data.map { prefs ->
        val immediate = prefs[Keys.CONFIG_IMMEDIATE] ?: return@map null
        CachedConfig(
            immediate = immediate,
            veryClose = prefs[Keys.CONFIG_VERY_CLOSE] ?: -65,
            nearby = prefs[Keys.CONFIG_NEARBY] ?: -80,
            far = prefs[Keys.CONFIG_FAR] ?: -95,
            locationIntervalSeconds = prefs[Keys.CONFIG_LOCATION_INTERVAL],
            scanIntervalSeconds = prefs[Keys.CONFIG_SCAN_INTERVAL],
            syncedAt = prefs[Keys.CONFIG_SYNCED_AT]?.let { Instant.ofEpochSecond(it) },
        )
    }

    suspend fun saveConfig(
        immediate: Int,
        veryClose: Int,
        nearby: Int,
        far: Int,
        locationIntervalSeconds: Int?,
        scanIntervalSeconds: Int?,
        syncedAt: Instant,
    ) = store.edit { prefs ->
        prefs[Keys.CONFIG_IMMEDIATE] = immediate
        prefs[Keys.CONFIG_VERY_CLOSE] = veryClose
        prefs[Keys.CONFIG_NEARBY] = nearby
        prefs[Keys.CONFIG_FAR] = far
        locationIntervalSeconds?.let { prefs[Keys.CONFIG_LOCATION_INTERVAL] = it }
        scanIntervalSeconds?.let { prefs[Keys.CONFIG_SCAN_INTERVAL] = it }
        prefs[Keys.CONFIG_SYNCED_AT] = syncedAt.epochSecond
    }

    data class SyncStats(val lastSyncAt: Instant?, val acknowledgedTotal: Long, val rejectedTotal: Long)

    val syncStats: Flow<SyncStats> = store.data.map { prefs ->
        SyncStats(
            lastSyncAt = prefs[Keys.LAST_SYNC_AT]?.let { Instant.ofEpochSecond(it) },
            acknowledgedTotal = prefs[Keys.ACKED_TOTAL] ?: 0L,
            rejectedTotal = prefs[Keys.REJECTED_TOTAL] ?: 0L,
        )
    }

    suspend fun recordSync(acked: Int, rejected: Int, at: Instant) = store.edit { prefs ->
        prefs[Keys.LAST_SYNC_AT] = at.epochSecond
        prefs[Keys.ACKED_TOTAL] = (prefs[Keys.ACKED_TOTAL] ?: 0L) + acked
        prefs[Keys.REJECTED_TOTAL] = (prefs[Keys.REJECTED_TOTAL] ?: 0L) + rejected
    }
}
