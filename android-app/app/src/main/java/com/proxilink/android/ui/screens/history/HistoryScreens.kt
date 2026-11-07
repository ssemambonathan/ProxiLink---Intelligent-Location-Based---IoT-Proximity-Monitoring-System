package com.proxilink.android.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.remote.toUserMessage
import com.proxilink.android.data.repository.EventRepository
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.domain.model.LocationEventRecord
import com.proxilink.android.domain.model.ProximityEvent
import com.proxilink.android.domain.proximity.DistanceEstimator
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
import com.proxilink.android.ui.design.SkeletonCard
import com.proxilink.android.ui.design.StatusChip
import com.proxilink.android.ui.design.accuracyLabel
import com.proxilink.android.ui.design.coordinateLabel
import com.proxilink.android.ui.design.dateTimeLabel
import com.proxilink.android.ui.design.relativeLabel
import com.proxilink.android.ui.design.rssiLabel
import com.proxilink.android.ui.design.smoothedLabel
import com.proxilink.android.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationHistoryViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    data class UiState(
        val events: List<LocationEventRecord> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val sharingEnabled: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(sharingEnabled = settings.locationSharingEnabled) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            eventRepository.locationEvents(limit = 100)
                .onSuccess { events -> _state.update { it.copy(events = events, loading = false) } }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.toUserMessage()) }
                }
        }
    }
}

@Composable
fun LocationHistoryScreen(
    onBack: () -> Unit,
    onMonitoring: () -> Unit,
    viewModel: LocationHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    ScreenScaffold(
        title = "Location history",
        subtitle = "Fixes you chose to share, newest first",
        onBack = onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (state.error != null) {
                ErrorBanner(message = state.error!!, onRetry = viewModel::refresh)
                Spacer(Modifier.height(10.dp))
            }
            ProxiCard(accent = if (state.sharingEnabled) Palette.Cyan else Palette.Amber) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.sharingEnabled) "Location sharing is on" else "Location sharing is off",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.TextPrimary,
                        )
                        Text(
                            if (state.sharingEnabled) {
                                "New fixes are queued offline at the server-configured interval."
                            } else {
                                "No new fixes are collected. Past events remain visible below."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary,
                        )
                    }
                    StatusChip(
                        text = if (state.sharingEnabled) "ON" else "OFF",
                        color = if (state.sharingEnabled) Palette.Mint else Palette.TextTertiary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton(text = "Manage sharing", onClick = onMonitoring, icon = Icons.Filled.LocationOn)
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProxiCard(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    Text(state.events.size.toString(), style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
                    Text("events loaded", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                }
                ProxiCard(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    val latest = state.events.firstOrNull()
                    Text(
                        latest?.recordedAt?.relativeLabel() ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                    )
                    Text("most recent", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "Recorded fixes", actionLabel = "Refresh", onAction = viewModel::refresh)
            Spacer(Modifier.height(8.dp))
            if (state.loading && state.events.isEmpty()) {
                SkeletonCard()
                Spacer(Modifier.height(10.dp))
                SkeletonCard()
            } else if (state.events.isEmpty()) {
                ProxiCard {
                    EmptyState(
                        icon = Icons.Filled.LocationOn,
                        title = "No location events",
                        message = "Enable location sharing in monitoring, then keep the app running. Accepted fixes appear here after sync.",
                        actionLabel = "Open monitoring",
                        onAction = onMonitoring,
                    )
                }
            } else {
                state.events.forEach { event ->
                    ProxiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    coordinateLabel(event.latitude, event.longitude),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Palette.TextPrimary,
                                )
                                Text(
                                    "${accuracyLabel(event.accuracyM)} · ${event.source}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.TextSecondary,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(event.recordedAt.relativeLabel(), style = MaterialTheme.typography.labelSmall, color = Palette.Cyan)
                                Text(event.recordedAt.dateTimeLabel(), style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                            }
                        }
                        val extras = buildList {
                            event.speedMps?.let { add("speed ${"%.1f".format(it)} m/s") }
                            event.altitudeM?.let { add("altitude ${"%.0f".format(it)} m") }
                        }
                        if (extras.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                extras.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                "Outbox rows are deleted once the server accepts or de-duplicates them. Server-side retention is controlled by your organization's policy.",
                icon = Icons.Filled.Warning,
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}

@HiltViewModel
class ProximityHistoryViewModel @Inject constructor(
    private val eventRepository: EventRepository,
) : ViewModel() {

    data class UiState(
        val events: List<ProximityEvent> = emptyList(),
        val level: ProximityLevel? = null,
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun setLevel(label: String) {
        val level = ProximityLevel.entries.firstOrNull { it.label == label }
        _state.update { it.copy(level = level) }
        refresh()
    }

    fun refresh() {
        val level = _state.value.level
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            eventRepository.proximityEvents(level = level, limit = 100)
                .onSuccess { events -> _state.update { it.copy(events = events, loading = false) } }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.toUserMessage()) }
                }
        }
    }
}

@Composable
fun ProximityHistoryScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
    viewModel: ProximityHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val options = listOf("All") + ProximityLevel.entries.map { it.label }

    ScreenScaffold(
        title = "Proximity events",
        subtitle = "Server history of classified BLE sightings",
        onBack = onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SelectablePillRow(
                options = options,
                selected = state.level?.label ?: "All",
                onSelect = viewModel::setLevel,
            )
            Spacer(Modifier.height(12.dp))
            if (state.error != null) {
                ErrorBanner(message = state.error!!, onRetry = viewModel::refresh)
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.events.size} events",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Refresh",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Cyan,
                    modifier = Modifier
                        .clickable(onClick = viewModel::refresh)
                        .padding(4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (state.loading && state.events.isEmpty()) {
                SkeletonCard()
                Spacer(Modifier.height(10.dp))
                SkeletonCard()
            } else if (state.events.isEmpty()) {
                ProxiCard {
                    EmptyState(
                        icon = Icons.Filled.History,
                        title = "No proximity events",
                        message = "Start a scan; recorded events sync through the outbox and then appear in this feed.",
                        actionLabel = "Open scanner",
                        onAction = onScan,
                    )
                }
            } else {
                state.events.forEach { event ->
                    ProxiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    event.deviceName ?: event.bleMac ?: "Unknown device",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Palette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${rssiLabel(event.rssi)} · ${smoothedLabel(event.smoothedRssi)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.TextSecondary,
                                )
                                Text(
                                    event.estimatedDistanceM?.let { DistanceEstimator.format(it) } ?: "distance unavailable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.TextTertiary,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                ProximityBadge(level = event.level, simulated = event.simulated)
                                Spacer(Modifier.height(4.dp))
                                Text(event.recordedAt.relativeLabel(), style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                "Proximity bands are computed locally from smoothed RSSI using the server's public thresholds. Metre values are log-distance estimates.",
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}
