package com.proxilink.android.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.remote.toUserMessage
import com.proxilink.android.data.repository.DeviceRepository
import com.proxilink.android.data.repository.EventRepository
import com.proxilink.android.domain.model.DeviceCategory
import com.proxilink.android.domain.model.ProximityEvent
import com.proxilink.android.domain.model.RegisteredDevice
import com.proxilink.android.ui.design.AppTextField
import com.proxilink.android.ui.design.EmptyState
import com.proxilink.android.ui.design.ErrorBanner
import com.proxilink.android.ui.design.InfoBanner
import com.proxilink.android.ui.design.KeyValueRow
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.PrimaryButton
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ProximityBadge
import com.proxilink.android.ui.design.ProximityGauge
import com.proxilink.android.ui.design.ScreenScaffold
import com.proxilink.android.ui.design.SecondaryButton
import com.proxilink.android.ui.design.SectionHeader
import com.proxilink.android.ui.design.SelectablePillRow
import com.proxilink.android.ui.design.SkeletonCard
import com.proxilink.android.ui.design.StatusChip
import com.proxilink.android.ui.design.SuccessBanner
import com.proxilink.android.ui.design.accent
import com.proxilink.android.ui.design.coordinateLabel
import com.proxilink.android.ui.design.dateTimeLabel
import com.proxilink.android.ui.design.relativeLabel
import com.proxilink.android.ui.design.rssiLabel
import com.proxilink.android.ui.navigation.Routes
import com.proxilink.android.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceRepository: DeviceRepository,
    private val eventRepository: EventRepository,
) : ViewModel() {

    private val deviceId: String = URLDecoder.decode(
        savedStateHandle.get<String>(Routes.DEVICE_ID_ARG).orEmpty(),
        "UTF-8",
    )

    data class UiState(
        val device: RegisteredDevice? = null,
        val events: List<ProximityEvent> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val busy: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cached = deviceRepository.cachedDevice(deviceId)
            if (cached != null) _state.update { it.copy(device = cached, loading = false) }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.device == null, error = null) }
            val errors = mutableListOf<String>()
            val device = deviceRepository.get(deviceId)
                .onFailure { errors += it.toUserMessage() }
                .getOrNull()
            val events = eventRepository.proximityEvents(deviceId = deviceId, limit = 25)
                .onFailure { errors += it.toUserMessage() }
                .getOrNull()
            _state.update {
                it.copy(
                    device = device ?: it.device,
                    events = events ?: it.events,
                    loading = false,
                    error = if (device == null && it.device == null) errors.firstOrNull() else null,
                )
            }
        }
    }

    fun setActive(active: Boolean) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            deviceRepository.setActive(deviceId, active)
                .onSuccess { device ->
                    _state.update {
                        it.copy(
                            device = device,
                            busy = false,
                            message = if (active) "Device activated." else "Device deactivated.",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = error.toUserMessage()) }
                }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

@Composable
fun DeviceDetailsScreen(
    onBack: () -> Unit,
    onMap: () -> Unit,
    viewModel: DeviceDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device = state.device

    ScreenScaffold(
        title = device?.name ?: "Device details",
        subtitle = device?.let { "${it.deviceType} · ${it.category.label}" },
        onBack = onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (state.message != null) {
                SuccessBanner(state.message!!)
                Spacer(Modifier.height(10.dp))
            }
            if (state.error != null) {
                ErrorBanner(message = state.error!!, onRetry = viewModel::refresh)
                Spacer(Modifier.height(10.dp))
            }
            if (state.loading && device == null) {
                SkeletonCard()
                Spacer(Modifier.height(12.dp))
                SkeletonCard()
            } else if (device == null) {
                ProxiCard {
                    EmptyState(
                        icon = Icons.Filled.Devices,
                        title = "Device unavailable",
                        message = "The device could not be loaded. It may have been removed, or the API is unreachable.",
                        actionLabel = "Retry",
                        onAction = viewModel::refresh,
                    )
                }
            } else {
                ProxiCard(accent = device.status.accent()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
                            Text(
                                "Hardware ${device.hardwareId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.TextSecondary,
                            )
                        }
                        StatusChip(text = device.status.label.uppercase(), color = device.status.accent())
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(
                            text = if (device.active) "ACTIVE" else "INACTIVE",
                            color = if (device.active) Palette.Mint else Palette.TextTertiary,
                            icon = if (device.active) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        )
                        ProximityBadge(level = device.lastProximity)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Last seen ${device.lastSeenAt?.relativeLabel() ?: "never"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextSecondary,
                    )
                }

                Spacer(Modifier.height(14.dp))
                ProximityGauge(
                    level = device.lastProximity,
                    smoothedRssi = device.lastRssi?.toDouble(),
                    distanceM = null,
                )

                Spacer(Modifier.height(18.dp))
                SectionHeader(title = "Identity")
                Spacer(Modifier.height(8.dp))
                ProxiCard {
                    KeyValueRow("Hardware ID", device.hardwareId.ifBlank { "—" })
                    KeyValueRow("BLE MAC", device.bleMac ?: "Not provided")
                    KeyValueRow("Service UUID", device.serviceUuid ?: "Not provided")
                    KeyValueRow("Device type", device.deviceType)
                    KeyValueRow("Category", device.category.label)
                    KeyValueRow("Owner", device.ownerName ?: "—")
                    KeyValueRow("Organization", device.organization ?: "—")
                    KeyValueRow("Last RSSI", rssiLabel(device.lastRssi))
                    KeyValueRow("Last seen", device.lastSeenAt?.dateTimeLabel() ?: "—")
                }

                Spacer(Modifier.height(18.dp))
                SectionHeader(title = "Last known location")
                Spacer(Modifier.height(8.dp))
                ProxiCard(onClick = onMap, accent = Palette.Cyan) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                coordinateLabel(device.lastLocation?.latitude, device.lastLocation?.longitude),
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.TextPrimary,
                            )
                            Text(
                                device.lastLocation?.let {
                                    "±${it.accuracyM?.let { acc -> "%.0f".format(acc) } ?: "?"} m · ${it.recordedAt?.relativeLabel() ?: "time unknown"}"
                                } ?: "No location reported for this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.TextSecondary,
                            )
                        }
                        Icons.Filled.LocationOn.let { icon ->
                            androidx.compose.material3.Icon(icon, contentDescription = null, tint = Palette.Cyan)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (device.active) {
                        SecondaryButton(
                            text = "Deactivate",
                            onClick = { viewModel.setActive(false) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy,
                            icon = Icons.Filled.Warning,
                        )
                    } else {
                        PrimaryButton(
                            text = "Activate device",
                            onClick = { viewModel.setActive(true) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy,
                            loading = state.busy,
                        )
                    }
                    SecondaryButton(
                        text = "Map",
                        onClick = onMap,
                        modifier = Modifier.weight(0.6f),
                        icon = Icons.Filled.LocationOn,
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionHeader(title = "Recent proximity events")
                Spacer(Modifier.height(8.dp))
                if (state.events.isEmpty()) {
                    ProxiCard {
                        EmptyState(
                            icon = Icons.Filled.Devices,
                            title = "No events yet",
                            message = "Events appear after this device is seen by a scan and the outbox syncs.",
                        )
                    }
                } else {
                    state.events.forEach { event ->
                        ProxiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${rssiLabel(event.rssi)} · ${event.recordedAt.dateTimeLabel()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Palette.TextPrimary,
                                    )
                                    Text(
                                        "${event.recordedAt.relativeLabel()} · ${event.samplesLabel()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Palette.TextTertiary,
                                    )
                                }
                                ProximityBadge(level = event.level, simulated = event.simulated)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}

private fun ProximityEvent.samplesLabel(): String =
    if (smoothedRssi != null) "smoothed ${"%.1f".format(smoothedRssi)} dBm" else "raw only"

@HiltViewModel
class RegisterDeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    data class UiState(
        val name: String = "",
        val deviceType: String = "",
        val category: DeviceCategory = DeviceCategory.ASSET,
        val hardwareId: String = "",
        val bleMac: String = "",
        val serviceUuid: String = "",
        val loading: Boolean = false,
        val error: String? = null,
        val created: RegisteredDevice? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onType(value: String) = _state.update { it.copy(deviceType = value, error = null) }
    fun onCategory(value: String) = _state.update {
        it.copy(category = DeviceCategory.entries.firstOrNull { category -> category.label == value } ?: it.category)
    }
    fun onHardwareId(value: String) = _state.update { it.copy(hardwareId = value, error = null) }
    fun onBleMac(value: String) = _state.update { it.copy(bleMac = value, error = null) }
    fun onServiceUuid(value: String) = _state.update { it.copy(serviceUuid = value, error = null) }

    fun reset() {
        _state.value = UiState()
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val validation = validate(current)
        if (validation != null) {
            _state.update { it.copy(error = validation) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            deviceRepository.register(
                name = current.name,
                deviceType = current.deviceType,
                category = current.category,
                hardwareId = current.hardwareId,
                bleMac = current.bleMac,
                serviceUuid = current.serviceUuid,
                metadata = mapOf("registeredFrom" to "android"),
            ).onSuccess { device ->
                _state.update { it.copy(loading = false, created = device) }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.toUserMessage()) }
            }
        }
    }

    private fun validate(state: UiState): String? {
        val macRegex = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        return when {
            state.name.trim().length < 2 -> "Give the device a name of at least 2 characters."
            state.deviceType.isBlank() -> "Device type is required (for example: badge, beacon, sensor)."
            state.hardwareId.isBlank() -> "Hardware ID is required and must match the physical label."
            state.bleMac.isNotBlank() && !macRegex.matches(state.bleMac.trim()) ->
                "BLE MAC must look like AA:BB:CC:DD:EE:FF, or leave it blank."
            else -> null
        }
    }
}

@Composable
fun RegisterDeviceScreen(
    onBack: () -> Unit,
    onOpenDevice: (String) -> Unit,
    viewModel: RegisterDeviceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Register device",
        subtitle = "Register a BLE asset so scans can identify it",
        onBack = onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (state.created != null) {
                ProxiCard(accent = Palette.Mint) {
                    StatusChip(text = "REGISTERED", color = Palette.Mint, icon = Icons.Filled.CheckCircle)
                    Spacer(Modifier.height(10.dp))
                    Text(state.created!!.name, style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary)
                    Text(
                        "Hardware ${state.created!!.hardwareId} · ${state.created!!.category.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = "View device",
                        onClick = { onOpenDevice(state.created!!.id) },
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(text = "Register another", onClick = viewModel::reset)
                }
                Spacer(Modifier.height(MainBottomBarInset))
                return@Column
            }

            if (state.error != null) {
                ErrorBanner(message = state.error!!)
                Spacer(Modifier.height(12.dp))
            }
            AppTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = "Device name",
                placeholder = "Loading bay beacon",
            )
            Spacer(Modifier.height(12.dp))
            AppTextField(
                value = state.deviceType,
                onValueChange = viewModel::onType,
                label = "Device type",
                placeholder = "beacon, badge, sensor…",
            )
            Spacer(Modifier.height(14.dp))
            Text("Category", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
            Spacer(Modifier.height(8.dp))
            SelectablePillRow(
                options = DeviceCategory.entries.map { it.label },
                selected = state.category.label,
                onSelect = viewModel::onCategory,
            )
            Spacer(Modifier.height(14.dp))
            AppTextField(
                value = state.hardwareId,
                onValueChange = viewModel::onHardwareId,
                label = "Hardware ID",
                placeholder = "Printed on the device label",
            )
            Spacer(Modifier.height(12.dp))
            AppTextField(
                value = state.bleMac,
                onValueChange = viewModel::onBleMac,
                label = "BLE MAC (optional)",
                placeholder = "AA:BB:CC:DD:EE:FF",
                keyboardType = KeyboardType.Text,
            )
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                "Android 12+ rotates BLE MAC addresses for privacy, so a MAC alone may not identify a device. A service UUID or advertised name is more durable.",
                icon = Icons.Filled.Warning,
            )
            Spacer(Modifier.height(12.dp))
            AppTextField(
                value = state.serviceUuid,
                onValueChange = viewModel::onServiceUuid,
                label = "Service UUID (optional)",
                placeholder = "0000180f-0000-1000-8000-00805f9b34fb",
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Register device",
                onClick = viewModel::submit,
                loading = state.loading,
                enabled = state.name.isNotBlank() && state.deviceType.isNotBlank() && state.hardwareId.isNotBlank(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Registration is written to the ProxiLink API. Scans then match this device by MAC, service UUID or name.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}
