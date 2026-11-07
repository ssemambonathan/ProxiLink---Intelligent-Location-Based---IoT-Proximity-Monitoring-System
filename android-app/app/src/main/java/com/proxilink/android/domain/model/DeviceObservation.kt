package com.proxilink.android.domain.model

import com.proxilink.android.domain.proximity.ProximityLevel
import java.time.Instant

/** A device currently visible to the scanner, already smoothed and classified locally. */
data class DeviceObservation(
    val identity: String,
    val bleMac: String?,
    val serviceUuid: String?,
    val advertisedName: String?,
    val hardwareId: String?,
    val rssi: Int,
    val smoothedRssi: Double,
    val level: ProximityLevel,
    val estimatedDistanceM: Double,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val sampleCount: Int,
    val simulated: Boolean,
    val recognition: RecognitionState = RecognitionState.UNKNOWN,
    val registeredDevice: RegisteredDevice? = null,
) {
    val displayName: String
        get() = registeredDevice?.name
            ?: advertisedName?.takeIf { it.isNotBlank() }
            ?: "Unknown device"

    val isRegistered: Boolean get() = registeredDevice != null
}

enum class RecognitionState { UNKNOWN, CHECKING, RECOGNIZED, FAILED }

/** Raw scan result before smoothing. */
data class RawBleObservation(
    val bleMac: String?,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String>,
    val observedAt: Instant,
    val simulated: Boolean = false,
) {
    val identity: String
        get() = bleMac ?: name?.takeIf { it.isNotBlank() } ?: serviceUuids.firstOrNull() ?: "unknown"
}
