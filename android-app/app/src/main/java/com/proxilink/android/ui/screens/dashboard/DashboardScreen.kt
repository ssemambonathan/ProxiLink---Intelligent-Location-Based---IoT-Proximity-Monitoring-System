package com.proxilink.android.ui.screens.dashboard

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.ble.ScanCoordinator
import com.proxilink.android.data.remote.toUserMessage
import com.proxilink.android.data.repository.AuthRepository
import com.proxilink.android.data.repository.ConfigRepository
import com.proxilink.android.data.repository.DashboardRepository
import com.proxilink.android.data.repository.DeviceRepository
import com.proxilink.android.data.repository.EventRepository
import com.proxilink.android.data.repository.LocationRepository
import com.proxilink.android.data.repository.NotificationRepository
import com.proxilink.android.data.repository.OutboxRepository
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.domain.model.DashboardOverview
import com.proxilink.android.domain.model.LiveSnapshot
import com.proxilink.android.domain.model.ProximityEvent
import com.proxilink.android.domain.model.RegisteredDevice
import com.proxilink.android.domain.model.UserFix
import com.proxilink.android.service.ConnectivityObserver
import com.proxilink.android.ui.design.EmptyState
import com.proxilink.android.ui.design.ErrorBanner
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.ProxiBackground
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ProximityBadge
import com.proxilink.android.ui.design.ScreenHorizontalPadding
import com.proxilink.android.ui.design.SectionHeader
import com.proxilink.android.ui.design.SimulationBanner
import com.proxilink.android.ui.design.SkeletonCard
import com.proxilink.android.ui.design.StatTile
import com.proxilink.android.ui.design.StatusChip
import com.proxilink.android.ui.design.accent
import com.proxilink.android.ui.design.accuracyLabel
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
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val deviceRepository: DeviceRepository,
    private val eventRepository: EventRepository,
    private val notificationRepository: NotificationRepository,
    private val configRepository: ConfigRepository,
    private val locationRepository: LocationRepository,
    private val outboxRepository: OutboxRepository,
    private val scanCoordinator: ScanCoordinator,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val overview: DashboardOverview? = null,
        val live: LiveSnapshot? = null,
        val recentEvents: List<ProximityEvent> = emptyList(),
        val scanning: Boolean = false,
        val nearbyCount: Int = 0,
        val discovered: Int = 0,
        val registeredCount: Int = 0,
        val pendingEvents: Int = 0,
        val unread: Int = 0,
        val fix: UserFix? = null,
        val locationSharing: Boolean = false,
        val simulation: Boolean = false,
        val online: Boolean = false,
        val userName: String = "",
        val lastUpdated: Instant? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            scanCoordinator.state.collect { scan ->
                _state.update {
                    it.copy(scanning = scan.isScanning, nearbyCount = scan.nearbyCount, discovered = scan.uniqueCount)
                }
            }
        }
        viewModelScope.launch {
            deviceRepository.cachedDevices.collect { devices ->
                _state.update { it.copy(registeredCount = devices.size) }
            }
        }
        viewModelScope.launch {
            outboxRepository.counts.collect { counts ->
                _state.update { it.copy(pendingEvents = counts.pending) }
            }
        }
        viewModelScope.launch {
            notificationRepository.unreadCount.collect { unread ->
                _state.update { it.copy(unread = unread) }
            }
        }
        viewModelScope.launch {
            locationRepository.current.collect { fix -> _state.update { it.copy(fix = fix) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(locationSharing = settings.locationSharingEnabled, simulation = settings.simulationMode) }
            }
        }
        viewModelScope.launch {
            authRepository.user.collect { user ->
                _state.update { it.copy(userName = user?.displayName?.ifBlank { user.email } ?: "Operator") }
            }
        }
        viewModelScope.launch {
            connectivityObserver.state.collect { connectivity ->
                _state.update { it.copy(online = connectivity.isOnline) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            val errors = mutableListOf<String>()
            val overview = dashboardRepository.overview()
                .onFailure { errors += it.toUserMessage() }
                .getOrNull()
            val live = dashboardRepository.live()
                .onFailure { errors += it.toUserMessage() }
                .getOrNull()
            deviceRepository.refresh().onFailure { errors += it.toUserMessage() }
            eventRepository.proximityEvents(limit = 6).onSuccess { events ->
                _state.update { it.copy(recentEvents = events) }
            }
            notificationRepository.refresh().onFailure { errors += it.toUserMessage() }
            configRepository.refresh()
            locationRepository.refreshLastKnown()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    overview = overview ?: it.overview,
                    live = live ?: it.live,
                    error = if (overview == null && live == null) errors.firstOrNull() else null,
                    lastUpdated = Instant.now(),
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    onScan: () -> Unit,
    onMap: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    onMonitoring: () -> Unit,
    onDevice: (String) -> Unit,
    onProximityHistory: () -> Unit,
    onProfile: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    ProxiBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextTertiary,
                    )
                    Text(
                        state.userName.ifBlank { "Operator" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Palette.TextPrimary,
                    )
                }
                StatusChip(
                    text = if (state.online) "ONLINE" else "OFFLINE",
                    color = if (state.online) Palette.Mint else Palette.Coral,
                    icon = if (state.online) Icons.Filled.Bluetooth else Icons.Filled.Warning,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = Palette.Cyan,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Palette.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            if (state.simulation) {
                SimulationBanner()
                Spacer(Modifier.height(12.dp))
            }
            if (state.error != null) {
                ErrorBanner(message = state.error!!, onRetry = viewModel::refresh)
                Spacer(Modifier.height(12.dp))
            }

            ProxiCard(accent = Palette.Cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Current location", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (state.locationSharing) "SHARING ON" else "SHARING OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.locationSharing) Palette.Mint else Palette.Amber,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    coordinateLabel(state.fix?.latitude, state.fix?.longitude),
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.fix == null) {
                        "No fix yet. Enable location sharing in monitoring to attach coordinates to proximity events."
                    } else {
                        "${accuracyLabel(state.fix?.accuracyM)} · updated ${state.fix!!.recordedAt.relativeLabel()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.Cyan.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.fix == null) "Open monitoring" else "Manage sharing",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Cyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .padding(2.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(12.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Nearby",
                    value = state.nearbyCount.toString(),
                    hint = if (state.scanning) "live scan" else "scan idle",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Bluetooth,
                )
                StatTile(
                    label = "Registered",
                    value = state.registeredCount.toString(),
                    hint = "known devices",
                    modifier = Modifier.weight(1f),
                    accent = Palette.Violet,
                    icon = Icons.Filled.Devices,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Pending sync",
                    value = state.pendingEvents.toString(),
                    hint = if (state.pendingEvents == 0) "all delivered" else "local outbox",
                    modifier = Modifier.weight(1f),
                    accent = Palette.Amber,
                    icon = Icons.Filled.Refresh,
                )
                StatTile(
                    label = "Alerts",
                    value = state.unread.toString(),
                    hint = "unread notifications",
                    modifier = Modifier.weight(1f),
                    accent = Palette.Coral,
                    icon = Icons.Filled.Notifications,
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader(
                title = "Discovery",
                actionLabel = "Nearby devices",
                onAction = onScan,
            )
            Spacer(Modifier.height(10.dp))
            ProxiCard(onClick = onScan, accent = if (state.scanning) Palette.Cyan else Palette.Violet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Palette.Cyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.scanning) "Scanning for BLE devices" else "Start a BLE scan",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.TextPrimary,
                        )
                        Text(
                            if (state.scanning) {
                                "${state.discovered} unique devices seen · ${state.nearbyCount} within range"
                            } else {
                                "Detect nearby hardware, smoothed locally before upload"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary,
                        )
                    }
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader(
                title = "Proximity feed",
                actionLabel = "History",
                onAction = onProximityHistory,
            )
            Spacer(Modifier.height(10.dp))
            if (state.loading && state.recentEvents.isEmpty()) {
                SkeletonCard()
                Spacer(Modifier.height(10.dp))
                SkeletonCard()
            } else if (state.recentEvents.isEmpty()) {
                ProxiCard {
                    EmptyState(
                        icon = Icons.Filled.Bluetooth,
                        title = "No proximity events yet",
                        message = "Start scanning. Events appear here after they are queued and synced.",
                    )
                }
            } else {
                state.recentEvents.forEach { event ->
                    EventRow(event = event, onClick = { event.deviceId?.let(onDevice) })
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader(
                title = "Map",
                actionLabel = "Open map",
                onAction = onMap,
            )
            Spacer(Modifier.height(10.dp))
            MapPreview(
                fix = state.fix,
                devices = state.live?.devices.orEmpty(),
                onOpen = onMap,
            )

            Spacer(Modifier.height(22.dp))
            ProxiCard {
                Text("Account & privacy", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Review permissions, sharing toggles and data retention at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Cyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onProfile)
                            .padding(4.dp),
                    )
                    Text(
                        "Monitoring",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Cyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onMonitoring)
                            .padding(4.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Last refreshed ${state.lastUpdated?.let { it.relativeLabel() } ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}

@Composable
private fun EventRow(event: ProximityEvent, onClick: () -> Unit) {
    ProxiCard(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    event.deviceName ?: event.bleMac ?: "Unknown device",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${rssiLabel(event.rssi)} · ${event.recordedAt.relativeLabel()} · ${event.recordedAt.dateTimeLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
                if (event.estimatedDistanceM != null) {
                    Text(
                        com.proxilink.android.domain.proximity.DistanceEstimator.format(event.estimatedDistanceM),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                ProximityBadge(level = event.level, simulated = event.simulated)
                Spacer(Modifier.height(4.dp))
                Text(
                    event.level.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MapPreview(fix: UserFix?, devices: List<RegisteredDevice>, onOpen: () -> Unit) {
    val points = remember(fix, devices) {
        devices.mapNotNull { device ->
            device.lastLocation?.let { location ->
                PreviewPoint(location.latitude, location.longitude, device.status.accent())
            }
        }
    }
    ProxiCard(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Palette.SurfaceHigh, Palette.Surface),
                    ),
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val grid = 6
                val stepX = size.width / grid
                val stepY = size.height / grid
                for (i in 0..grid) {
                    drawLine(Palette.Outline.copy(alpha = 0.35f), Offset(stepX * i, 0f), Offset(stepX * i, size.height), strokeWidth = 1f)
                    drawLine(Palette.Outline.copy(alpha = 0.35f), Offset(0f, stepY * i), Offset(size.width, stepY * i), strokeWidth = 1f)
                }
                val referenceLat = fix?.latitude ?: points.firstOrNull()?.lat ?: 0.0
                val referenceLon = fix?.longitude ?: points.firstOrNull()?.lon ?: 0.0
                val spread = 0.004
                fun project(lat: Double, lon: Double): Offset {
                    val x = ((lon - referenceLon + spread) / (spread * 2)).toFloat().coerceIn(0.06f, 0.94f)
                    val y = ((referenceLat - lat + spread) / (spread * 2)).toFloat().coerceIn(0.08f, 0.92f)
                    return Offset(size.width * x, size.height * y)
                }
                points.forEach { point ->
                    val center = project(point.lat, point.lon)
                    drawCircle(point.color.copy(alpha = 0.22f), radius = 18f, center = center)
                    drawCircle(point.color, radius = 6f, center = center)
                }
                if (fix != null) {
                    val center = project(fix.latitude, fix.longitude)
                    drawCircle(Palette.Cyan.copy(alpha = 0.2f), radius = 22f, center = center)
                    drawCircle(Palette.Cyan, radius = 8f, center = center)
                    drawCircle(Palette.Background, radius = 3f, center = center)
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text("Map preview", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                Text(
                    if (points.isEmpty() && fix == null) "No mappable locations yet" else "${points.size} device location${if (points.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                )
            }
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Palette.Cyan.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Open map", style = MaterialTheme.typography.labelSmall, color = Palette.Cyan)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(12.dp))
            }
        }
    }
}

private data class PreviewPoint(val lat: Double, val lon: Double, val color: Color)

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}
