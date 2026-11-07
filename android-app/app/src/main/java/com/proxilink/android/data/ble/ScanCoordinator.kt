package com.proxilink.android.data.ble

import com.proxilink.android.data.repository.ConfigRepository
import com.proxilink.android.data.repository.DeviceRepository
import com.proxilink.android.data.repository.LocationRepository
import com.proxilink.android.data.repository.OutboxRepository
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.di.ApplicationScope
import com.proxilink.android.domain.model.DeviceObservation
import com.proxilink.android.domain.model.RawBleObservation
import com.proxilink.android.domain.model.RecognitionState
import com.proxilink.android.domain.model.RegisteredDevice
import com.proxilink.android.domain.proximity.DistanceEstimator
import com.proxilink.android.domain.proximity.ProximityClassifier
import com.proxilink.android.domain.proximity.ProximityLevel
import com.proxilink.android.domain.proximity.ProximityThresholds
import com.proxilink.android.domain.proximity.RssiSmoother
import com.proxilink.android.service.InAppMessage
import com.proxilink.android.service.InAppNotificationCenter
import com.proxilink.android.service.InAppSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Owns the scan session: raw advertisements (real or simulated) go through the same smoothing,
 * classification, identification and outbox persistence pipeline exactly once.
 *
 * Business logic deliberately lives here rather than in Composables so it stays testable.
 */
@Singleton
class ScanCoordinator @Inject constructor(
    private val bleScanner: BleScanner,
    private val simulationSource: SimulationBleSource,
    private val settingsRepository: SettingsRepository,
    private val configRepository: ConfigRepository,
    private val locationRepository: LocationRepository,
    private val outboxRepository: OutboxRepository,
    private val deviceRepository: DeviceRepository,
    private val notifications: InAppNotificationCenter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    data class ScanState(
        val isScanning: Boolean = false,
        val simulated: Boolean = false,
        val startedAt: Instant? = null,
        val error: String? = null,
        val observations: List<DeviceObservation> = emptyList(),
        val packetsSeen: Int = 0,
        val eventsRecorded: Int = 0,
        val lastPacketAt: Instant? = null,
    ) {
        val uniqueCount: Int get() = observations.size
        val nearbyCount: Int
            get() = observations.count {
                it.level == ProximityLevel.IMMEDIATE ||
                    it.level == ProximityLevel.VERY_CLOSE ||
                    it.level == ProximityLevel.NEARBY
            }
        val registeredCount: Int get() = observations.count { it.isRegistered }
        val outOfRangeCount: Int get() = observations.count { it.level == ProximityLevel.OUT_OF_RANGE }
    }

    private class Engine(
        val identity: String,
        val firstSeen: Instant,
        val smoother: RssiSmoother,
        val estimator: DistanceEstimator,
    ) {
        var classifier: ProximityClassifier = ProximityClassifier()
        var mac: String? = null
        var serviceUuid: String? = null
        var name: String? = null
        var hardwareId: String? = null
        var rssi: Int = -127
        var smoothed: Double? = null
        var level: ProximityLevel = ProximityLevel.OUT_OF_RANGE
        var distance: Double = 0.0
        var lastSeen: Instant? = null
        var packets: Int = 0
        var lastPersistedAt: Instant? = null
        var lastPersistedLevel: ProximityLevel? = null
        var simulated: Boolean = false
        var recognition: RecognitionState = RecognitionState.UNKNOWN
        var registered: RegisteredDevice? = null
        var lastIdentifyAt: Instant? = null
        var identifyRequested: Boolean = false

        fun toObservation(): DeviceObservation = DeviceObservation(
            identity = identity,
            bleMac = mac,
            serviceUuid = serviceUuid,
            advertisedName = name,
            hardwareId = hardwareId,
            rssi = rssi,
            smoothedRssi = smoothed ?: rssi.toDouble(),
            level = level,
            estimatedDistanceM = distance,
            firstSeenAt = firstSeen,
            lastSeenAt = lastSeen ?: firstSeen,
            sampleCount = packets,
            simulated = simulated,
            recognition = recognition,
            registeredDevice = registered,
        )
    }

    private data class IdentifyRequestData(
        val identity: String,
        val mac: String?,
        val serviceUuid: String?,
    )

    private val mutex = Mutex()
    private val engines = LinkedHashMap<String, Engine>()

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var tickerJob: Job? = null

    fun start() {
        if (_state.value.isScanning) return
        val simulate = settingsRepository.settings.value.simulationMode
        _state.value = ScanState(isScanning = true, simulated = simulate, startedAt = Instant.now())
        seedKnownDevices()

        scanJob = scope.launch {
            val source = if (simulate) simulationSource.observations() else bleScanner.scan()
            source
                .catch { throwable ->
                    scanJob = null
                    _state.update { it.copy(isScanning = false, error = friendlyScanError(throwable)) }
                }
                .collect { raw -> handleRaw(raw) }
        }
        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                tick()
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        tickerJob?.cancel()
        tickerJob = null
        _state.update { it.copy(isScanning = false) }
    }

    fun toggle() {
        if (_state.value.isScanning) stop() else start()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Re-runs identification for every visible unknown device. */
    fun retryIdentification() {
        scope.launch {
            mutex.withLock {
                engines.values.forEach { engine ->
                    if (engine.registered == null) {
                        engine.recognition = RecognitionState.UNKNOWN
                        engine.identifyRequested = false
                    }
                }
                _state.update { it.copy(observations = snapshotLocked()) }
            }
        }
    }

    private suspend fun handleRaw(raw: RawBleObservation) {
        if (raw.rssi >= 0 || raw.rssi < -127) return
        val thresholds = configRepository.config.value.thresholds
        val persistIntervalSeconds = settingsRepository.settings.value.scanIntervalSeconds.coerceAtLeast(5).toLong()

        var observationToPersist: DeviceObservation? = null
        var observationToNotify: DeviceObservation? = null
        var identifyRequest: IdentifyRequestData? = null

        mutex.withLock {
            val engine = engines.getOrPut(raw.identity) {
                Engine(
                    identity = raw.identity,
                    firstSeen = raw.observedAt,
                    smoother = RssiSmoother(),
                    estimator = DistanceEstimator(),
                )
            }
            val previousLevel = engine.level
            val smoothed = runCatching { engine.smoother.accept(raw.rssi) }.getOrNull()
                ?: return@withLock

            engine.classifier = ProximityClassifier(thresholds)
            val level = engine.classifier.classify(smoothed)
            engine.mac = raw.bleMac ?: engine.mac
            engine.serviceUuid = raw.serviceUuids.firstOrNull() ?: engine.serviceUuid
            engine.name = raw.name ?: engine.name
            engine.simulated = raw.simulated
            engine.rssi = raw.rssi
            engine.smoothed = smoothed
            engine.level = level
            engine.distance = engine.estimator.estimateMeters(smoothed)
            engine.lastSeen = raw.observedAt
            engine.packets++

            val now = raw.observedAt
            val dueForRecord = engine.lastPersistedAt == null ||
                Duration.between(engine.lastPersistedAt, now).seconds >= persistIntervalSeconds ||
                level != engine.lastPersistedLevel
            if (dueForRecord) {
                engine.lastPersistedAt = now
                engine.lastPersistedLevel = level
                observationToPersist = engine.toObservation()
            }
            if (level == ProximityLevel.IMMEDIATE && previousLevel != ProximityLevel.IMMEDIATE) {
                observationToNotify = engine.toObservation()
            }
            if (!engine.identifyRequested && engine.registered == null) {
                engine.identifyRequested = true
                engine.recognition = RecognitionState.CHECKING
                engine.lastIdentifyAt = now
                identifyRequest = IdentifyRequestData(engine.identity, engine.mac, engine.serviceUuid)
            }

            _state.update { current ->
                current.copy(
                    observations = snapshotLocked(),
                    packetsSeen = current.packetsSeen + 1,
                    lastPacketAt = now,
                )
            }
        }

        observationToPersist?.let { persistProximity(it) }
        observationToNotify?.let { maybeNotifyImmediate(it) }
        identifyRequest?.let { request -> identify(request) }
    }

    private suspend fun persistProximity(observation: DeviceObservation) {
        val fix = locationRepository.current.value.takeIf { locationRepository.sharingActive.value }
        val recordedAt = observation.lastSeenAt
        val eventId = OutboxRepository.newEventId()
        val payload = OutboxRepository.proximityPayload(
            eventId = eventId,
            deviceId = observation.registeredDevice?.id,
            bleMac = observation.bleMac,
            deviceName = observation.registeredDevice?.name ?: observation.advertisedName,
            rssi = observation.rssi,
            smoothedRssi = observation.smoothedRssi,
            latitude = fix?.latitude,
            longitude = fix?.longitude,
            accuracyM = fix?.accuracyM,
            recordedAt = recordedAt,
            metadata = buildMap {
                put("scanMode", "low_latency")
                put("simulated", observation.simulated.toString())
                put("locationShared", (fix != null).toString())
            },
        )
        runCatching { outboxRepository.enqueueProximity(eventId, payload, recordedAt) }
            .onSuccess {
                _state.update { it.copy(eventsRecorded = it.eventsRecorded + 1) }
            }
    }

    private fun maybeNotifyImmediate(observation: DeviceObservation) {
        if (!settingsRepository.settings.value.notificationsEnabled) return
        val simulatedSuffix = if (observation.simulated) " (simulated)" else ""
        notifications.push(
            InAppMessage(
                title = "${observation.displayName} is immediately nearby",
                body = "Smoothed RSSI ${observation.smoothedRssi.roundToInt()} dBm$simulatedSuffix",
                severity = InAppSeverity.ALERT,
            ),
        )
    }

    private fun identify(request: IdentifyRequestData) {
        scope.launch {
            val result = deviceRepository.identify(
                bleMac = request.mac,
                serviceUuid = request.serviceUuid,
            )
            mutex.withLock {
                val engine = engines[request.identity] ?: return@withLock
                result.fold(
                    onSuccess = { device ->
                        if (device != null) {
                            engine.registered = device
                            engine.hardwareId = device.hardwareId
                            engine.recognition = RecognitionState.RECOGNIZED
                            engine.name = device.name
                        }
                    },
                    onFailure = {
                        // Offline or unauthorized: allow a retry a minute later.
                        engine.recognition = RecognitionState.FAILED
                        engine.identifyRequested = false
                        engine.lastIdentifyAt = Instant.now()
                    },
                )
                _state.update { it.copy(observations = snapshotLocked()) }
            }
        }
    }

    private fun seedKnownDevices() {
        scope.launch {
            val cached = runCatching { deviceRepository.cachedDevices.first() }.getOrDefault(emptyList())
            if (cached.isEmpty()) return@launch
            mutex.withLock {
                engines.values.forEach { engine ->
                    val match = cached.firstOrNull { device ->
                        matches(device, engine)
                    }
                    if (match != null) {
                        engine.registered = match
                        engine.recognition = RecognitionState.RECOGNIZED
                        engine.name = match.name
                    }
                }
                _state.update { it.copy(observations = snapshotLocked()) }
            }
        }
    }

    private fun matches(device: RegisteredDevice, engine: Engine): Boolean {
        val macMatch = !device.bleMac.isNullOrBlank() && !engine.mac.isNullOrBlank() &&
            device.bleMac.equals(engine.mac, ignoreCase = true)
        val uuidMatch = !device.serviceUuid.isNullOrBlank() && !engine.serviceUuid.isNullOrBlank() &&
            device.serviceUuid.equals(engine.serviceUuid, ignoreCase = true)
        val nameMatch = device.name.equals(engine.name, ignoreCase = true)
        return macMatch || uuidMatch || nameMatch
    }

    private suspend fun tick() {
        val now = Instant.now()
        var changed = false
        mutex.withLock {
            val iterator = engines.entries.iterator()
            while (iterator.hasNext()) {
                val engine = iterator.next().value
                val lastSeen = engine.lastSeen
                if (lastSeen == null) continue
                val silentFor = Duration.between(lastSeen, now)
                if (silentFor.toMillis() > DISAPPEARANCE_MS && engine.level != ProximityLevel.OUT_OF_RANGE) {
                    engine.level = ProximityLevel.OUT_OF_RANGE
                    changed = true
                }
                if (engine.recognition == RecognitionState.FAILED &&
                    engine.lastIdentifyAt != null &&
                    Duration.between(engine.lastIdentifyAt, now).seconds >= IDENTIFY_RETRY_SECONDS
                ) {
                    engine.recognition = RecognitionState.UNKNOWN
                    engine.identifyRequested = false
                    changed = true
                }
                if (silentFor.toMinutes() >= FORGET_AFTER_MINUTES) {
                    iterator.remove()
                    changed = true
                }
            }
            if (changed) {
                _state.update { it.copy(observations = snapshotLocked()) }
            }
        }
    }

    private fun snapshotLocked(): List<DeviceObservation> = engines.values
        .map { it.toObservation() }
        .sortedWith(compareBy<DeviceObservation> { it.level.ordinal }.thenByDescending { it.lastSeenAt })

    private fun friendlyScanError(throwable: Throwable): String = when (throwable) {
        is BleScanException -> when (throwable.kind) {
            BleFailureKind.PERMISSION_MISSING -> "Bluetooth scan permission is required."
            BleFailureKind.BLUETOOTH_DISABLED -> "Turn Bluetooth on to scan for devices."
            BleFailureKind.BLUETOOTH_UNAVAILABLE -> "Bluetooth LE is not available on this device."
            BleFailureKind.SCAN_FAILED -> throwable.message ?: "The BLE scanner reported a failure."
        }
        else -> throwable.message ?: "Scanning stopped unexpectedly."
    }

    companion object {
        private const val DISAPPEARANCE_MS = 10_000L
        private const val FORGET_AFTER_MINUTES = 10L
        private const val IDENTIFY_RETRY_SECONDS = 60L

        fun thresholdsForDisplay(thresholds: ProximityThresholds): String =
            "IMMEDIATE >= ${thresholds.immediate} dBm, VERY_CLOSE >= ${thresholds.veryClose}, " +
                "NEARBY >= ${thresholds.nearby}, FAR >= ${thresholds.far}"
    }
}
