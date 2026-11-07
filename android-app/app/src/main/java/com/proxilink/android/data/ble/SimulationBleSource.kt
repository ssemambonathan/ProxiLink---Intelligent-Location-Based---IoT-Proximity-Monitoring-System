package com.proxilink.android.data.ble

import com.proxilink.android.domain.model.RawBleObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Demo-only synthetic advertisements. They enter the exact same smoothing/classification/outbox
 * pipeline as real BLE packets and are flagged `simulated = true` all the way to the UI and payload.
 */
@Singleton
class SimulationBleSource @Inject constructor() {

    private data class SimulatedDevice(
        val name: String,
        val serviceUuid: String,
        val baseRssi: Int,
    )

    private val devices = listOf(
        SimulatedDevice("SIM Dock Beacon", "6e400001-b5a3-f393-e0a9-e50e24dcca9e", -48),
        SimulatedDevice("SIM Cold Storage Sensor", "6e400002-b5a3-f393-e0a9-e50e24dcca9e", -68),
        SimulatedDevice("SIM Meeting Room Tag", "6e400003-b5a3-f393-e0a9-e50e24dcca9e", -88),
    )

    fun observations(): Flow<RawBleObservation> = flow {
        var step = 0
        while (currentCoroutineContext().isActive) {
            val now = Instant.now()
            devices.forEachIndexed { index, device ->
                // Slow sinusoidal wander plus jitter so the proximity bands are visibly dynamic.
                val wave = sin((step + index * 9) / 4.0) * 7.0
                val jitter = Random.nextInt(-2, 3)
                val rssi = (device.baseRssi + wave + jitter).roundToInt().coerceIn(-100, -30)
                emit(
                    RawBleObservation(
                        bleMac = null,
                        name = device.name,
                        rssi = rssi,
                        serviceUuids = listOf(device.serviceUuid),
                        observedAt = now,
                        simulated = true,
                    ),
                )
            }
            step++
            delay(1_200)
        }
    }.flowOn(Dispatchers.Default)
}
