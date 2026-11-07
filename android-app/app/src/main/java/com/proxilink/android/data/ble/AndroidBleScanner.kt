package com.proxilink.android.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import com.proxilink.android.domain.model.RawBleObservation
import com.proxilink.android.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class BleFailureKind { PERMISSION_MISSING, BLUETOOTH_UNAVAILABLE, BLUETOOTH_DISABLED, SCAN_FAILED }

class BleScanException(
    val kind: BleFailureKind,
    message: String,
) : Exception(message)

interface BleScanner {
    fun scan(): Flow<RawBleObservation>
}

@Singleton
class AndroidBleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleScanner {

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<RawBleObservation> = callbackFlow {
        if (!PermissionUtils.hasScanPermission(context)) {
            close(BleScanException(BleFailureKind.PERMISSION_MISSING, "Bluetooth scan permission is not granted"))
            return@callbackFlow
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            close(BleScanException(BleFailureKind.BLUETOOTH_UNAVAILABLE, "This device has no Bluetooth LE radio"))
            return@callbackFlow
        }
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null) {
            close(BleScanException(BleFailureKind.BLUETOOTH_UNAVAILABLE, "Bluetooth is not available"))
            return@callbackFlow
        }
        if (!adapter.isEnabled) {
            close(BleScanException(BleFailureKind.BLUETOOTH_DISABLED, "Bluetooth is turned off"))
            return@callbackFlow
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            close(BleScanException(BleFailureKind.BLUETOOTH_UNAVAILABLE, "BLE scanner is unavailable"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.toObservation()?.let { trySend(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> result.toObservation()?.let { trySend(it) } }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleScanException(BleFailureKind.SCAN_FAILED, "BLE scan failed (code $errorCode)"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        try {
            scanner.startScan(null, settings, callback)
        } catch (security: SecurityException) {
            close(BleScanException(BleFailureKind.PERMISSION_MISSING, "Bluetooth scan permission was revoked"))
            return@callbackFlow
        }

        awaitClose { runCatching { scanner.stopScan(callback) } }
    }.flowOn(Dispatchers.Default)

    private fun ScanResult.toObservation(): RawBleObservation? {
        if (rssi >= 0 || rssi < -127) return null
        // device.name needs BLUETOOTH_CONNECT; the advertisement payload is safe with BLUETOOTH_SCAN.
        val advertisedName = scanRecord?.deviceName
        return RawBleObservation(
            bleMac = device.address?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" },
            name = advertisedName,
            rssi = rssi,
            serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
            observedAt = Instant.now(),
        )
    }
}
