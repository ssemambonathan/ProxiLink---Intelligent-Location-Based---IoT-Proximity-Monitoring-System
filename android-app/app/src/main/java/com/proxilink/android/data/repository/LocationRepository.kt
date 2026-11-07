package com.proxilink.android.data.repository

import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.proxilink.android.di.ApplicationScope
import com.proxilink.android.domain.model.UserFix
import com.proxilink.android.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Fused location provider wrapper. Sharing is opt-in and off by default; when enabled, fixes are
 * recorded into the same outbox as proximity events at the server-configured interval.
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val config: ConfigRepository,
    private val outbox: OutboxRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val _current = MutableStateFlow<UserFix?>(null)
    val current: StateFlow<UserFix?> = _current.asStateFlow()

    private val _sharingActive = MutableStateFlow(false)
    val sharingActive: StateFlow<Boolean> = _sharingActive.asStateFlow()

    private var shareJob: Job? = null

    init {
        scope.launch {
            settings.settings.collect { current ->
                syncSharing(current.locationSharingEnabled)
            }
        }
    }

    /** Call after the user grants location permission at runtime. */
    fun refreshSharing() {
        syncSharing(settings.settings.value.locationSharingEnabled)
    }

    suspend fun refreshLastKnown(): UserFix? {
        if (!PermissionUtils.hasLocationPermission(context)) return null
        val fix = suspendCancellableCoroutine { continuation ->
            try {
                client.lastLocation
                    .addOnSuccessListener { location -> continuation.resume(location?.toUserFix()) }
                    .addOnFailureListener { continuation.resume(null) }
            } catch (security: SecurityException) {
                continuation.resume(null)
            }
        }
        if (fix != null) _current.value = fix
        return fix
    }

    fun locationUpdates(intervalMillis: Long): Flow<UserFix> = callbackFlow {
        if (!PermissionUtils.hasLocationPermission(context)) {
            close(IllegalStateException("Location permission is not granted"))
            return@callbackFlow
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis.coerceAtLeast(5_000L),
        )
            .setMinUpdateIntervalMillis((intervalMillis / 2).coerceAtLeast(2_000L))
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toUserFix()) }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (security: SecurityException) {
            close(security)
            return@callbackFlow
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }.conflate()

    private fun syncSharing(enabled: Boolean) {
        if (!enabled) {
            shareJob?.cancel()
            shareJob = null
            _sharingActive.value = false
            // Never let a stale fix be attached to BLE events after sharing is switched off.
            _current.value = null
            return
        }
        if (!PermissionUtils.hasLocationPermission(context)) {
            // Graceful denial: the toggle stays on but no fixes are collected until permission exists.
            _sharingActive.value = false
            _current.value = null
            return
        }
        if (shareJob?.isActive == true) return
        shareJob = scope.launch {
            try {
                _sharingActive.value = true
                config.config.collectLatest { serverConfig ->
                    if (!PermissionUtils.hasLocationPermission(context)) {
                        _sharingActive.value = false
                        return@collectLatest
                    }
                    locationUpdates(serverConfig.locationIntervalSeconds * 1_000L).collect { fix ->
                        _current.value = fix
                        val eventId = OutboxRepository.newEventId()
                        outbox.enqueueLocation(
                            eventId = eventId,
                            payload = OutboxRepository.locationPayload(
                                eventId = eventId,
                                latitude = fix.latitude,
                                longitude = fix.longitude,
                                accuracyM = fix.accuracyM,
                                speedMps = fix.speedMps,
                                altitudeM = fix.altitudeM,
                                recordedAt = fix.recordedAt,
                                metadata = mapOf("source" to fix.source),
                            ),
                            recordedAt = fix.recordedAt,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _sharingActive.value = false
            }
        }
    }

    private fun Location.toUserFix(): UserFix = UserFix(
        latitude = latitude,
        longitude = longitude,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        altitudeM = if (hasAltitude()) altitude else null,
        recordedAt = Instant.ofEpochMilli(time.takeIf { it > 0 } ?: System.currentTimeMillis()),
        source = "FUSED",
    )
}
