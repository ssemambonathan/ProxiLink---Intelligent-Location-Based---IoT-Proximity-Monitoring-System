package com.proxilink.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.ui.graphics.vector.ImageVector

/** Every destination in the app. All of these are backed by real screens and repositories. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val SCAN = "scan"
    const val NEARBY = "nearby"
    const val DEVICE_DETAILS = "device/{deviceId}"
    const val MONITORING = "monitoring"
    const val MAP = "map"
    const val REGISTER_DEVICE = "register-device"
    const val LOCATION_HISTORY = "location-history"
    const val PROXIMITY_HISTORY = "proximity-history"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val NETWORK = "network"

    const val DEVICE_ID_ARG = "deviceId"

    fun deviceDetails(deviceId: String): String = "device/${java.net.URLEncoder.encode(deviceId, "UTF-8")}"
}

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val BottomDestinations = listOf(
    BottomDestination(Routes.DASHBOARD, "Home", Icons.Filled.Dashboard),
    BottomDestination(Routes.SCAN, "Scan", Icons.Filled.BluetoothSearching),
    BottomDestination(Routes.MAP, "Map", Icons.Filled.Map),
    BottomDestination(Routes.NOTIFICATIONS, "Alerts", Icons.Filled.Notifications),
    BottomDestination(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

val BottomRoutes: Set<String> = BottomDestinations.map { it.route }.toSet()
