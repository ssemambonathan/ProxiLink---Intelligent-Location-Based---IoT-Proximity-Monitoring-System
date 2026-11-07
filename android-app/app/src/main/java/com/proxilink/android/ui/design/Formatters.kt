package com.proxilink.android.ui.design

import androidx.compose.ui.graphics.Color
import com.proxilink.android.domain.model.DeviceStatus
import com.proxilink.android.domain.proximity.DistanceEstimator
import com.proxilink.android.domain.proximity.ProximityLevel
import com.proxilink.android.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US).withZone(ZoneId.systemDefault())
private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.US).withZone(ZoneId.systemDefault())

fun Instant.relativeLabel(now: Instant = Instant.now()): String {
    val seconds = Duration.between(this, now).seconds
    return when {
        seconds < 0 -> "in the future"
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3_600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3_600}h ago"
        else -> dateTimeFormatter.format(this)
    }
}

fun Instant.clockLabel(): String = clockFormatter.format(this)

fun Instant.dateTimeLabel(): String = dateTimeFormatter.format(this)

fun ProximityLevel.accent(): Color = when (this) {
    ProximityLevel.IMMEDIATE -> Palette.Coral
    ProximityLevel.VERY_CLOSE -> Palette.Amber
    ProximityLevel.NEARBY -> Palette.Cyan
    ProximityLevel.FAR -> Palette.Sky
    ProximityLevel.OUT_OF_RANGE -> Palette.TextTertiary
}

fun DeviceStatus.accent(): Color = when (this) {
    DeviceStatus.ONLINE, DeviceStatus.NEARBY -> Palette.Mint
    DeviceStatus.UNREGISTERED -> Palette.Violet
    DeviceStatus.OFFLINE, DeviceStatus.OUT_OF_RANGE, DeviceStatus.INACTIVE -> Palette.TextTertiary
}

fun rssiLabel(rssi: Int?): String = rssi?.let { "$it dBm" } ?: "no signal"

fun smoothedLabel(value: Double?): String =
    value?.let { String.format(Locale.US, "%.1f dBm", it) } ?: "—"

fun distanceLabel(metres: Double?): String =
    if (metres == null) "—" else DistanceEstimator.format(metres)

fun signalBars(rssi: Int?): Int = when {
    rssi == null -> 0
    rssi >= -55 -> 4
    rssi >= -70 -> 3
    rssi >= -85 -> 2
    rssi >= -100 -> 1
    else -> 0
}

fun coordinateLabel(latitude: Double?, longitude: Double?): String =
    if (latitude == null || longitude == null) {
        "No location fix"
    } else {
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    }

fun accuracyLabel(accuracyM: Double?): String =
    accuracyM?.let { String.format(Locale.US, "±%.0f m", it) } ?: "accuracy unknown"
