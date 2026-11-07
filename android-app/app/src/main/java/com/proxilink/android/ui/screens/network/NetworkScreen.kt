package com.proxilink.android.ui.screens.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.local.SettingsDataStore
import com.proxilink.android.data.remote.toUserMessage
import com.proxilink.android.data.repository.OutboxRepository
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.domain.outbox.OutboxCounts
import com.proxilink.android.domain.outbox.OutboxRecord
import com.proxilink.android.domain.outbox.SyncSummary
import com.proxilink.android.service.ConnectivityObserver
import com.proxilink.android.service.ConnectivityState
import com.proxilink.android.ui.design.ErrorBanner
import com.proxilink.android.ui.design.InfoBanner
import com.proxilink.android.ui.design.KeyValueRow
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.PrimaryButton
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ScreenScaffold
import com.proxilink.android.ui.design.SecondaryButton
import com.proxilink.android.ui.design.SectionHeader
import com.proxilink.android.ui.design.StatusChip
import com.proxilink.android.ui.design.dateTimeLabel
import com.proxilink.android.ui.design.relativeLabel
import com.proxilink.android.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val outboxRepository: OutboxRepository,
    settingsRepository: SettingsRepository,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    data class UiState(
        val connectivity: ConnectivityState = ConnectivityState(),
        val counts: OutboxCounts = OutboxCounts(0, 0),
        val stats: SettingsDataStore.SyncStats = SettingsDataStore.SyncStats(null, 0, 0),
        val rejected: List<OutboxRecord> = emptyList(),
        val baseUrl: String = "",
        val syncing: Boolean = false,
        val clearing: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityObserver.state.collect { connectivity ->
                _state.update { it.copy(connectivity = connectivity) }
            }
        }
        viewModelScope.launch {
            outboxRepository.counts.collect { counts -> _state.update { it.copy(counts = counts) } }
        }
        viewModelScope.launch {
            outboxRepository.syncStats.collect { stats -> _state.update { it.copy(stats = stats) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings -> _state.update { it.copy(baseUrl = settings.apiBaseUrl) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val rejected = runCatching { outboxRepository.recentRejected() }.getOrDefault(emptyList())
            _state.update { it.copy(rejected = rejected, error = null) }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, error = null, notice = null) }
            try {
                val summary: SyncSummary = outboxRepository.syncNow()
                _state.update {
                    it.copy(
                        syncing = false,
                        notice = summarize(summary),
                        rejected = runCatching { outboxRepository.recentRejected() }.getOrDefault(it.rejected),
                    )
                }
            } catch (error: Throwable) {
                _state.update { it.copy(syncing = false, error = error.toUserMessage()) }
            }
        }
    }

    fun clearRejected() {
        viewModelScope.launch {
            _state.update { it.copy(clearing = true) }
            runCatching { outboxRepository.clearRejected() }
                .onSuccess {
                    _state.update {
                        it.copy(clearing = false, rejected = emptyList(), notice = "Local rejected events cleared.")
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(clearing = false, error = error.toUserMessage()) }
                }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private fun summarize(summary: SyncSummary): String = buildString {
        append("Sync complete: ${summary.accepted} accepted, ${summary.duplicates} duplicates, ${summary.rejected} rejected.")
        if (summary.failed) append(" ${summary.message ?: "Some batches failed and will retry."}")
    }
}

@Composable
fun NetworkScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: NetworkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    ScreenScaffold(
        title = "Sync & network",
        subtitle = "Offline outbox status and manual delivery",
        onBack = onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (state.notice != null) {
                InfoBanner(state.notice!!, accent = Palette.Mint)
                Spacer(Modifier.height(10.dp))
            }
            if (state.error != null) {
                ErrorBanner(
                    message = state.error!!,
                    onRetry = viewModel::syncNow,
                )
                Spacer(Modifier.height(10.dp))
            }

            ProxiCard(accent = if (state.connectivity.isOnline) Palette.Mint else Palette.Coral) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        if (state.connectivity.isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        contentDescription = null,
                        tint = if (state.connectivity.isOnline) Palette.Mint else Palette.Coral,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.connectivity.isOnline) "Network available" else "No network",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.TextPrimary,
                        )
                        Text(
                            "${state.connectivity.transport}${if (state.connectivity.metered) " · metered" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary,
                        )
                    }
                    StatusChip(
                        text = if (state.connectivity.isOnline) "ONLINE" else "OFFLINE",
                        color = if (state.connectivity.isOnline) Palette.Mint else Palette.Coral,
                    )
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow("API base URL", state.baseUrl)
                KeyValueRow("Auto-sync", "WorkManager · network constraint · exponential backoff")
                KeyValueRow("Pending batches", "${state.counts.pending} events")
                KeyValueRow("Last sync", state.stats.lastSyncAt?.let { "${it.relativeLabel()} (${it.dateTimeLabel()})" } ?: "Never")
                Spacer(Modifier.height(10.dp))
                SecondaryButton(text = "Edit API settings", onClick = onSettings, icon = Icons.Filled.Refresh)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Counter("Pending", state.counts.pending.toString(), Palette.Amber, Modifier.weight(1f))
                Counter("Acknowledged", state.stats.acknowledgedTotal.toString(), Palette.Mint, Modifier.weight(1f))
                Counter("Rejected", state.counts.rejected.toString(), Palette.Coral, Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Sync now",
                onClick = viewModel::syncNow,
                loading = state.syncing,
                enabled = state.counts.pending > 0 || state.counts.rejected > 0,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Manual sync drains the local outbox in batches of 50. Accepted and duplicate events are deleted; rejected events stay with the server's reason. Failed HTTP calls are retried by WorkManager with exponential backoff.",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextTertiary,
            )

            Spacer(Modifier.height(20.dp))
            SectionHeader(
                title = "Rejected events",
                actionLabel = if (state.rejected.isEmpty()) null else "Clear",
                onAction = viewModel::clearRejected,
            )
            Spacer(Modifier.height(8.dp))
            if (state.rejected.isEmpty()) {
                ProxiCard {
                    Text(
                        "No rejected events are stored locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextSecondary,
                    )
                }
            } else {
                state.rejected.take(20).forEach { record ->
                    ProxiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${record.kind.name} · ${record.eventId.take(8)}…",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Palette.TextPrimary,
                                )
                                Text(
                                    record.rejectionReason ?: "No reason supplied",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.Coral,
                                )
                                Text(
                                    "recorded ${record.recordedAt.relativeLabel()} · attempts ${record.attempts}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.TextTertiary,
                                )
                            }
                            StatusChip(text = "REJECTED", color = Palette.Coral)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                SecondaryButton(
                    text = if (state.clearing) "Clearing…" else "Clear rejected events",
                    onClick = viewModel::clearRejected,
                    enabled = !state.clearing,
                    icon = Icons.Filled.Warning,
                )
            }

            Spacer(Modifier.height(16.dp))
            InfoBanner(
                "Every event carries a client-generated UUID. Re-sending after a lost response is safe: the server reports it as a duplicate and the local row is still removed.",
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}

@Composable
private fun Counter(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    ProxiCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
    }
}
