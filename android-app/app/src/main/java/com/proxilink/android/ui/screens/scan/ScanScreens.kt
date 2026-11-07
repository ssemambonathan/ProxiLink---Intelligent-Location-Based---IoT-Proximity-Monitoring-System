package com.proxilink.android.ui.screens.scan

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.ble.ScanCoordinator
import com.proxilink.android.data.repository.ConfigRepository
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.domain.model.DeviceObservation
import com.proxilink.android.domain.model.RecognitionState
import com.proxilink.android.domain.proximity.ProximityLevel
import com.proxilink.android.ui.design.EmptyState
import com.proxilink.android.ui.design.ErrorBanner
import com.proxilink.android.ui.design.InfoBanner
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.PrimaryButton
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ProximityBadge
import com.proxilink.android.ui.design.ScreenScaffold
import com.proxilink.android.ui.design.SecondaryButton
import com.proxilink.android.ui.design.SectionHeader
import com.proxilink.android.ui.design.SelectablePillRow
import com.proxilink.android.ui.design.SignalBars
import com.proxilink.android.ui.design.SimulationBanner
import com.proxilink.android.ui.design.StatusChip
import com.proxilink.android.ui.design.accent
import com.proxilink.android.ui.design.distanceLabel
import com.proxilink.android.ui.design.relativeLabel
import com.proxilink.android.ui.design.rssiLabel
import com.proxilink.android.ui.design.smoothedLabel
import com.proxilink.android.ui.theme.Palette
import com.proxilink.android.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanCoordinator: ScanCoordinator,
    settingsRepository: SettingsRepository,
    configRepository: ConfigRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val scan: ScanCoordinator.ScanState = ScanCoordinator.ScanState(),
        val simulation: Boolean = false,
        val scanPermission: Boolean = false,
        val connectPermission: Boolean = false,
        val notificationPermission: Boolean = false,
        val locationPermission: Boolean = false,
        val bluetoothEnabled: Boolean = false,
        val bluetoothLeSupported: Boolean = true,
        val thresholds: String = "",
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(
        UiState(
            scanPermission = PermissionUtils.hasScanPermission(context),
            connectPermission = PermissionUtils.hasConnectPermission(context),
            notificationPermission = PermissionUtils.hasNotificationPermission(context),
            locationPermission = PermissionUtils.hasLocationPermission(context),
            bluetoothEnabled = PermissionUtils.bluetoothEnabled(context),
            bluetoothLeSupported = PermissionUtils.supportsBluetoothLe(context),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            scanCoordinator.state.collect { scan -> _state.update { it.copy(scan = scan) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(simulation = settings.simulationMode) }
            }
        }
        viewModelScope.launch {
            configRepository.config.collect { config ->
                _state.update { it.copy(thresholds = ScanCoordinator.thresholdsForDisplay(config.thresholds)) }
            }
        }
    }

    fun refreshPermissions() {
        _state.update {
            it.copy(
                scanPermission = PermissionUtils.hasScanPermission(context),
                connectPermission = PermissionUtils.hasConnectPermission(context),
                notificationPermission = PermissionUtils.hasNotificationPermission(context),
                locationPermission = PermissionUtils.hasLocationPermission(context),
                bluetoothEnabled = PermissionUtils.bluetoothEnabled(context),
                bluetoothLeSupported = PermissionUtils.supportsBluetoothLe(context),
            )
        }
    }

    fun start() {
        refreshPermissions()
        val current = _state.value
        if (!current.simulation) {
            if (!current.bluetoothLeSupported) {
                _state.update { it.copy(notice = "This device does not expose a Bluetooth LE radio.") }
                return
            }
            if (!current.scanPermission) {
                _state.update { it.copy(notice = "Bluetooth scan permission is required. Use the permission button above.") }
                return
            }
            if (!current.bluetoothEnabled) {
                _state.update { it.copy(notice = "Turn Bluetooth on, then try again.") }
                return
            }
        }
        _state.update { it.copy(notice = null) }
        scanCoordinator.start()
    }

    fun stop() = scanCoordinator.stop()

    fun permissionDenied() {
        _state.update {
            it.copy(
                notice = if (it.simulation) null else "Scanning needs Bluetooth permission. You can grant it in Settings.",
                scanPermission = PermissionUtils.hasScanPermission(context),
            )
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun clearScanError() = scanCoordinator.clearError()

    fun retryIdentification() = scanCoordinator.retryIdentification()

    fun onScanPermissionResult() {
        refreshPermissions()
        if (PermissionUtils.hasScanPermission(context) || _state.value.simulation) start()
    }
}

@Composable
fun ScanScreen(
    onOpenNearby: () -> Unit,
    onDevice: (String) -> Unit,
    onRegisterDevice: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onScanPermissionResult()
    }

    ScreenScaffold(
        title = "BLE scan",
        subtitle = "Live signal processing with local smoothing and classification",
        actions = {
            Text(
                when {
                    state.scan.isScanning -> "LIVE"
                    state.simulation -> "SIM"
                    else -> "IDLE"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    state.scan.isScanning -> Palette.Mint
                    state.simulation -> Palette.Violet
                    else -> Palette.TextTertiary
                },
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (state.simulation) {
                SimulationBanner()
                Spacer(Modifier.height(10.dp))
            }
            if (state.scan.error != null) {
                ErrorBanner(
                    message = state.scan.error!!,
                    onRetry = {
                        viewModel.clearScanError()
                        viewModel.start()
                    },
                )
                Spacer(Modifier.height(10.dp))
            }
            if (state.notice != null) {
                InfoBanner(text = state.notice!!, accent = Palette.Amber, icon = Icons.Filled.Warning)
                Spacer(Modifier.height(10.dp))
            }
            if (!state.simulation) {
                MissingPermissionsBanner(
                    scan = state.scanPermission,
                    connect = state.connectPermission,
                    location = state.locationPermission,
                    notifications = state.notificationPermission,
                    onRequest = { launcher.launch(PermissionUtils.scanPermissions()) },
                )
            }

            ProxiCard(accent = if (state.scan.isScanning) Palette.Cyan else Palette.Violet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScanPulse(active = state.scan.isScanning)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.scan.isScanning) "Scanning" else "Scanner stopped",
                            style = MaterialTheme.typography.titleLarge,
                            color = Palette.TextPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (state.scan.isScanning) "Receiving advertisements in low-latency mode" else "Start a scan to discover nearby devices",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(text = "${state.scan.nearbyCount} nearby", color = Palette.Cyan)
                            StatusChip(text = "${state.scan.uniqueCount} unique", color = Palette.Violet)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.scan.isScanning) {
                        PrimaryButton(
                            text = "Stop scan",
                            onClick = viewModel::stop,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryButton(
                            text = "Start scan",
                            onClick = {
                                if (!state.simulation && !state.scanPermission) {
                                    launcher.launch(PermissionUtils.scanPermissions())
                                } else {
                                    viewModel.start()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SecondaryButton(
                        text = "Nearby",
                        onClick = onOpenNearby,
                        modifier = Modifier.weight(0.65f),
                        icon = Icons.Filled.BluetoothSearching,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Thresholds: ${state.thresholds}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScanMetric("Packets", state.scan.packetsSeen.toString(), Modifier.weight(1f))
                ScanMetric("Recorded", state.scan.eventsRecorded.toString(), Modifier.weight(1f))
                ScanMetric("Out of range", state.scan.outOfRangeCount.toString(), Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(
                title = "Detected devices",
                actionLabel = if (state.scan.observations.any { !it.isRegistered }) "Retry identify" else null,
                onAction = viewModel::retryIdentification,
            )
            Spacer(Modifier.height(10.dp))
            if (state.scan.observations.isEmpty()) {
                ProxiCard {
                    EmptyState(
                        icon = Icons.Filled.BluetoothSearching,
                        title = if (state.scan.isScanning) "Listening for advertisements" else "No devices yet",
                        message = if (state.scan.isScanning) {
                            "Move closer to a BLE device. Advertisements usually arrive within a few seconds."
                        } else {
                            "Start a scan to populate this list. Simulation mode injects synthetic devices through the same pipeline."
                        },
                        actionLabel = if (!state.scan.isScanning) "Start scan" else null,
                        onAction = {
                            if (!state.simulation && !state.scanPermission) {
                                launcher.launch(PermissionUtils.scanPermissions())
                            } else {
                                viewModel.start()
                            }
                        },
                    )
                }
            } else {
                state.scan.observations.forEach { observation ->
                    ObservationCard(
                        observation = observation,
                        onDevice = onDevice,
                        onRegisterDevice = onRegisterDevice,
                        onRetryIdentify = viewModel::retryIdentification,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            if (!state.simulation) {
                InfoBanner(
                    "Android 12+ randomizes BLE MAC addresses for privacy. Matching by service UUID or advertised name is more reliable than by MAC.",
                    icon = Icons.Filled.Warning,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Events are queued locally and uploaded only when accepted by the server. Raw RSSI is never shown as a distance measurement.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}

@Composable
fun ObservationCard(
    observation: DeviceObservation,
    onDevice: (String) -> Unit,
    onRegisterDevice: () -> Unit,
    onRetryIdentify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val registered = observation.registeredDevice
    ProxiCard(
        modifier = modifier,
        accent = observation.level.accent(),
        onClick = registered?.let { { onDevice(it.id) } },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(observation.level.accent().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = observation.level.accent(), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    observation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    observation.bleMac ?: observation.serviceUuid ?: "No MAC advertised",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SignalBars(rssi = observation.rssi, color = observation.level.accent())
                Spacer(Modifier.height(2.dp))
                Text(rssiLabel(observation.rssi), style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProximityBadge(level = observation.level, simulated = observation.simulated)
            Spacer(Modifier.weight(1f))
            Text(
                "${smoothedLabel(observation.smoothedRssi)} · ${distanceLabel(observation.estimatedDistanceM)}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                registered != null -> StatusChip(text = "REGISTERED", color = Palette.Mint, icon = Icons.Filled.CheckCircle)
                observation.recognition == RecognitionState.CHECKING -> StatusChip(text = "CHECKING", color = Palette.Amber)
                observation.recognition == RecognitionState.FAILED -> StatusChip(text = "IDENTIFY FAILED", color = Palette.Coral, icon = Icons.Filled.Error)
                else -> StatusChip(text = "UNKNOWN", color = Palette.Violet)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "seen ${observation.lastSeenAt.relativeLabel()} · ${observation.sampleCount} samples",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
                maxLines = 1,
            )
        }
        if (registered == null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Register device",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Cyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onRegisterDevice)
                        .padding(vertical = 2.dp),
                )
                if (observation.recognition == RecognitionState.FAILED) {
                    Text(
                        "Retry identify",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Amber,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onRetryIdentify)
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanPulse(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "scan-pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2
            for (i in 0..2) {
                val phase = ((progress + i * 0.33f) % 1f)
                val radius = maxRadius * (0.3f + phase * 0.7f)
                drawCircle(
                    color = Palette.Cyan.copy(alpha = (1f - phase) * 0.35f),
                    radius = radius,
                    center = Offset(size.width / 2, size.height / 2),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            drawCircle(Palette.Cyan.copy(alpha = 0.16f), radius = maxRadius * 0.5f)
            drawCircle(if (active) Palette.Cyan else Palette.TextTertiary, radius = maxRadius * 0.18f)
        }
    }
}

@Composable
private fun ScanMetric(label: String, value: String, modifier: Modifier = Modifier) {
    ProxiCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
    }
}

@Composable
private fun MissingPermissionsBanner(
    scan: Boolean,
    connect: Boolean,
    location: Boolean,
    notifications: Boolean,
    onRequest: () -> Unit,
) {
    val missing = buildList {
        if (!scan) add("BLE scan")
        if (!connect) add("BLE connect")
        if (!location) add("Location")
        if (!notifications) add("Notifications")
    }
    if (missing.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Amber.copy(alpha = 0.10f))
            .clickable(onClick = onRequest)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Palette.Amber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Runtime permissions needed", style = MaterialTheme.typography.labelMedium, color = Palette.TextPrimary)
            Text(
                "Missing: ${missing.joinToString(", ")}. Android will show the system prompt.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
            )
        }
        Text(
            "Grant",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Amber,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.Amber.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val scanCoordinator: ScanCoordinator,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    enum class Filter(val label: String) {
        ALL("All"),
        NEARBY("Nearby"),
        REGISTERED("Registered"),
        UNKNOWN("Unknown"),
    }

    data class UiState(
        val scan: ScanCoordinator.ScanState = ScanCoordinator.ScanState(),
        val filter: Filter = Filter.ALL,
        val simulation: Boolean = false,
    ) {
        val visible: List<DeviceObservation>
            get() = when (filter) {
                Filter.ALL -> scan.observations
                Filter.NEARBY -> scan.observations.filter {
                    it.level == ProximityLevel.IMMEDIATE ||
                        it.level == ProximityLevel.VERY_CLOSE ||
                        it.level == ProximityLevel.NEARBY
                }
                Filter.REGISTERED -> scan.observations.filter { it.isRegistered }
                Filter.UNKNOWN -> scan.observations.filter { !it.isRegistered }
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            scanCoordinator.state.collect { scan -> _state.update { it.copy(scan = scan) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(simulation = settings.simulationMode) }
            }
        }
    }

    fun setFilter(filter: Filter) = _state.update { it.copy(filter = filter) }
    fun start() = scanCoordinator.start()
    fun stop() = scanCoordinator.stop()
    fun retryIdentification() = scanCoordinator.retryIdentification()
}

@Composable
fun NearbyScreen(
    onBack: () -> Unit,
    onDevice: (String) -> Unit,
    onRegisterDevice: () -> Unit,
    onOpenScan: () -> Unit,
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Nearby devices",
        subtitle = "Devices seen in this scan session",
        onBack = onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (state.simulation) {
                SimulationBanner()
                Spacer(Modifier.height(10.dp))
            }
            SelectablePillRow(
                options = NearbyViewModel.Filter.entries.map { it.label },
                selected = state.filter.label,
                onSelect = { label ->
                    NearbyViewModel.Filter.entries.firstOrNull { it.label == label }?.let(viewModel::setFilter)
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.visible.size} of ${state.scan.uniqueCount} devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                if (!state.scan.isScanning) {
                    Text(
                        "Start scan",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Cyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenScan)
                            .padding(4.dp),
                    )
                } else {
                    StatusChip(text = "SCANNING", color = Palette.Mint)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (state.visible.isEmpty()) {
                ProxiCard {
                    EmptyState(
                        icon = Icons.Filled.Bluetooth,
                        title = if (state.scan.uniqueCount == 0) "No devices discovered" else "No devices in this filter",
                        message = if (state.scan.uniqueCount == 0) {
                            "Make sure Bluetooth is on and start a scan. Nearby devices are added as their advertisements arrive."
                        } else {
                            "Try a different filter, or move closer to the device you are looking for."
                        },
                        actionLabel = if (!state.scan.isScanning) "Open scanner" else null,
                        onAction = onOpenScan,
                    )
                }
            } else {
                state.visible.forEach { observation ->
                    ObservationCard(
                        observation = observation,
                        onDevice = onDevice,
                        onRegisterDevice = onRegisterDevice,
                        onRetryIdentify = viewModel::retryIdentification,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                "Proximity bands use a 5-sample median and EMA smoothing. The displayed distance is a log-distance estimate, not a measurement.",
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}
