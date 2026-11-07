package com.proxilink.android.ui.screens.monitoring

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.ble.ScanCoordinator
import com.proxilink.android.data.repository.ConfigRepository
import com.proxilink.android.data.repository.LocationRepository
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.domain.model.ServerConfig
import com.proxilink.android.service.BleMonitoringService
import com.proxilink.android.ui.design.InfoBanner
import com.proxilink.android.ui.design.KeyValueRow
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.PrimaryButton
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ScreenScaffold
import com.proxilink.android.ui.design.SecondaryButton
import com.proxilink.android.ui.design.SectionHeader
import com.proxilink.android.ui.design.SimulationBanner
import com.proxilink.android.ui.design.StatusChip
import com.proxilink.android.ui.design.ToggleRow
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

@HiltViewModel
class MonitoringViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scanCoordinator: ScanCoordinator,
    private val locationRepository: LocationRepository,
    configRepository: ConfigRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val backgroundEnabled: Boolean = false,
        val serviceRunning: Boolean = false,
        val locationSharing: Boolean = false,
        val locationActive: Boolean = false,
        val scanning: Boolean = false,
        val nearbyCount: Int = 0,
        val simulation: Boolean = false,
        val scanPermission: Boolean = false,
        val connectPermission: Boolean = false,
        val locationPermission: Boolean = false,
        val notificationPermission: Boolean = false,
        val config: ServerConfig = ServerConfig(),
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshPermissions()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update {
                    it.copy(
                        backgroundEnabled = settings.backgroundMonitoringEnabled,
                        locationSharing = settings.locationSharingEnabled,
                        simulation = settings.simulationMode,
                    )
                }
            }
        }
        viewModelScope.launch {
            BleMonitoringService.running.collect { running ->
                _state.update { it.copy(serviceRunning = running) }
            }
        }
        viewModelScope.launch {
            locationRepository.sharingActive.collect { active ->
                _state.update { it.copy(locationActive = active) }
            }
        }
        viewModelScope.launch {
            scanCoordinator.state.collect { scan ->
                _state.update { it.copy(scanning = scan.isScanning, nearbyCount = scan.nearbyCount) }
            }
        }
        viewModelScope.launch {
            configRepository.config.collect { config -> _state.update { it.copy(config = config) } }
        }
    }

    fun refreshPermissions() {
        _state.update {
            it.copy(
                scanPermission = PermissionUtils.hasScanPermission(context),
                connectPermission = PermissionUtils.hasConnectPermission(context),
                locationPermission = PermissionUtils.hasLocationPermission(context),
                notificationPermission = PermissionUtils.hasNotificationPermission(context),
            )
        }
    }

    fun enableBackground() {
        refreshPermissions()
        val missing = PermissionUtils.missingMonitoringPermissions(context)
        if (!_state.value.simulation && missing.isNotEmpty()) {
            _state.update { it.copy(notice = "Grant ${missing.joinToString(", ")} before starting continuous monitoring.") }
            return
        }
        if (!PermissionUtils.supportsBluetoothLe(context) && !_state.value.simulation) {
            _state.update { it.copy(notice = "This device has no Bluetooth LE radio.") }
            return
        }
        viewModelScope.launch {
            settingsRepository.setBackgroundMonitoringEnabled(true)
            _state.update { it.copy(notice = null) }
            BleMonitoringService.start(context)
        }
    }

    fun disableBackground() {
        viewModelScope.launch {
            settingsRepository.setBackgroundMonitoringEnabled(false)
            BleMonitoringService.stop(context)
            scanCoordinator.stop()
        }
    }

    fun enableLocationSharing() {
        refreshPermissions()
        if (!PermissionUtils.hasLocationPermission(context)) {
            _state.update { it.copy(notice = "Location permission is required to share location events.") }
            return
        }
        viewModelScope.launch {
            settingsRepository.setLocationSharingEnabled(true)
            locationRepository.refreshSharing()
            locationRepository.refreshLastKnown()
            _state.update { it.copy(notice = null) }
        }
    }

    fun disableLocationSharing() {
        viewModelScope.launch {
            settingsRepository.setLocationSharingEnabled(false)
            _state.update { it.copy(notice = null) }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun scanPermissions(): Array<String> = PermissionUtils.missingMonitoringPermissions(context)
        .mapNotNull {
            when (it) {
                "Bluetooth scan" -> android.Manifest.permission.BLUETOOTH_SCAN
                "Bluetooth connect" -> android.Manifest.permission.BLUETOOTH_CONNECT
                "Notifications" -> android.Manifest.permission.POST_NOTIFICATIONS
                else -> null
            }
        }
        .toTypedArray()
}

@Composable
fun MonitoringScreen(
    onBack: () -> Unit,
    onNetwork: () -> Unit,
    viewModel: MonitoringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingBackground by remember { mutableStateOf(false) }
    var pendingLocation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissions()
        if (pendingBackground) {
            pendingBackground = false
            val ready = PermissionUtils.hasScanPermission(context) &&
                PermissionUtils.hasConnectPermission(context) &&
                PermissionUtils.hasNotificationPermission(context)
            if (ready) viewModel.enableBackground()
        }
        if (pendingLocation) {
            pendingLocation = false
            if (PermissionUtils.hasLocationPermission(context)) viewModel.enableLocationSharing()
        }
    }

    ScreenScaffold(
        title = "Proximity monitoring",
        subtitle = "Continuous monitoring is opt-in and always visible",
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
            if (state.notice != null) {
                InfoBanner(state.notice!!, accent = Palette.Amber, icon = Icons.Filled.Warning)
                Spacer(Modifier.height(10.dp))
            }

            ProxiCard(accent = if (state.serviceRunning) Palette.Mint else Palette.Violet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background((if (state.serviceRunning) Palette.Mint else Palette.Violet).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, tint = if (state.serviceRunning) Palette.Mint else Palette.Violet)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.serviceRunning) "Monitoring service active" else "Monitoring is off",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.TextPrimary,
                        )
                        Text(
                            if (state.scanning) "${state.nearbyCount} nearby · scanning in foreground" else "No scan is running",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary,
                        )
                    }
                    StatusChip(
                        text = if (state.serviceRunning) "RUNNING" else "STOPPED",
                        color = if (state.serviceRunning) Palette.Mint else Palette.TextTertiary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                ToggleRow(
                    title = "Continuous BLE monitoring",
                    subtitle = "Runs a foreground service with a persistent, stoppable notification. Requires Bluetooth permissions.",
                    checked = state.backgroundEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val missing = if (state.simulation) emptyList() else PermissionUtils.missingMonitoringPermissions(context)
                            if (missing.isNotEmpty()) {
                                pendingBackground = true
                                permissionLauncher.launch(
                                    (
                                        PermissionUtils.scanPermissions() + PermissionUtils.notificationPermission()
                                        ).distinct().toTypedArray(),
                                )
                            } else {
                                viewModel.enableBackground()
                            }
                        } else {
                            viewModel.disableBackground()
                        }
                    },
                )
                if (state.serviceRunning) {
                    SecondaryButton(
                        text = "Stop monitoring service",
                        onClick = viewModel::disableBackground,
                        icon = Icons.Filled.Error,
                    )
                } else {
                    PrimaryButton(
                        text = "Start monitoring service",
                        onClick = {
                            if (!state.simulation && PermissionUtils.missingMonitoringPermissions(context).isNotEmpty()) {
                                pendingBackground = true
                                permissionLauncher.launch(
                                    (
                                        PermissionUtils.scanPermissions() +
                                            arrayOf(PermissionUtils.notificationPermission())
                                        ).distinct().toTypedArray(),
                                )
                            } else {
                                viewModel.enableBackground()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "The service is started explicitly by you and can be stopped from this screen or from the notification action.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "Permissions")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                PermissionRow("Bluetooth scan", state.scanPermission, onRequest = {
                    permissionLauncher.launch(PermissionUtils.scanPermissions())
                })
                PermissionRow("Bluetooth connect", state.connectPermission, onRequest = {
                    permissionLauncher.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT))
                })
                PermissionRow("Fine location", state.locationPermission, onRequest = {
                    permissionLauncher.launch(PermissionUtils.locationPermissions())
                })
                PermissionRow("Notifications", state.notificationPermission, onRequest = {
                    permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                })
                Spacer(Modifier.height(6.dp))
                Text(
                    "Android 13+ prompts for notifications; Android 12+ requires the Bluetooth runtime permissions. Permissions are never bypassed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "Location sharing")
            Spacer(Modifier.height(8.dp))
            ProxiCard(accent = Palette.Cyan) {
                ToggleRow(
                    title = "Share location with proximity events",
                    subtitle = "Off by default. When on, fixes are queued in the same offline outbox at the server's interval.",
                    checked = state.locationSharing,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!PermissionUtils.hasLocationPermission(context)) {
                                pendingLocation = true
                                permissionLauncher.launch(PermissionUtils.locationPermissions())
                            } else {
                                viewModel.enableLocationSharing()
                            }
                        } else {
                            viewModel.disableLocationSharing()
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        text = if (state.locationSharing) "PREFERENCE ON" else "PREFERENCE OFF",
                        color = if (state.locationSharing) Palette.Mint else Palette.TextTertiary,
                    )
                    StatusChip(
                        text = if (state.locationActive) "COLLECTING" else "IDLE",
                        color = if (state.locationActive) Palette.Cyan else Palette.TextTertiary,
                        icon = Icons.Filled.LocationOn,
                    )
                }
                if (state.locationSharing && !state.locationPermission) {
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(
                        "Sharing is enabled in settings but location permission is denied, so no fixes are being collected.",
                        accent = Palette.Amber,
                        icon = Icons.Filled.Warning,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "Intervals & service")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                KeyValueRow("Location interval", "${state.config.locationIntervalSeconds}s (server)")
                KeyValueRow("Scan record interval", "${state.config.scanIntervalSeconds}s (server)")
                KeyValueRow("Foreground type", "connectedDevice (Android 14+)")
                KeyValueRow("Service notification", "Persistent with Stop action")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Location and scan intervals are read-only here: the server's public config is authoritative and cached for offline use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                )
            }

            Spacer(Modifier.height(16.dp))
            SecondaryButton(text = "Open sync & network", onClick = onNetwork)
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (granted) Palette.Mint else Palette.Coral,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
        if (granted) {
            Text("Granted", style = MaterialTheme.typography.labelSmall, color = Palette.Mint)
        } else {
            Text(
                "Request",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Cyan,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .clickable(onClick = onRequest)
                    .padding(4.dp),
            )
        }
    }
}
