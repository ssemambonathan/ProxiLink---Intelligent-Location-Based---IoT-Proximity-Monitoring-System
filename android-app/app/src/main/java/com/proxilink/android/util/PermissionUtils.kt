package com.proxilink.android.util

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central permission policy. The app never bypasses OS restrictions: BLE scanning on API 31+ needs
 * BLUETOOTH_SCAN/CONNECT, older versions derive scan results from location permission, and
 * notifications need POST_NOTIFICATIONS from API 33.
 */
object PermissionUtils {

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasScanPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Without neverForLocation, Android requires fine location alongside BLUETOOTH_SCAN.
            isGranted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasConnectPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isGranted(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }

    fun hasLocationPermission(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPreciseLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)

    /** Runtime permissions to request when the user starts a scan. */
    fun scanPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        else -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    fun locationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun notificationPermission(): String = Manifest.permission.POST_NOTIFICATIONS

    fun missingMonitoringPermissions(context: Context): List<String> = buildList {
        if (!hasScanPermission(context)) add("Bluetooth scan")
        if (!hasConnectPermission(context)) add("Bluetooth connect")
        if (!hasNotificationPermission(context)) add("Notifications")
    }

    fun bluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return false
        return manager.adapter?.isEnabled == true
    }

    fun supportsBluetoothLe(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
}
