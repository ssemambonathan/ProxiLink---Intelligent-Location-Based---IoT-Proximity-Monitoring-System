package com.proxilink.android.ui.screens.settings

import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.BuildConfig
import com.proxilink.android.data.local.AppSettings
import com.proxilink.android.data.repository.ConfigRepository
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.domain.model.ServerConfig
import com.proxilink.android.domain.model.ThemeMode
import com.proxilink.android.ui.design.AppTextField
import com.proxilink.android.ui.design.InfoBanner
import com.proxilink.android.ui.design.KeyValueRow
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.PrimaryButton
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ScreenScaffold
import com.proxilink.android.ui.design.SecondaryButton
import com.proxilink.android.ui.design.SectionHeader
import com.proxilink.android.ui.design.SelectablePillRow
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
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val configRepository: ConfigRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val settings: AppSettings = AppSettings(),
        val config: ServerConfig = ServerConfig(),
        val baseUrlDraft: String = "",
        val baseUrlBusy: Boolean = false,
        val baseUrlError: String? = null,
        val notice: String? = null,
        val refreshingConfig: Boolean = false,
        val notificationPermission: Boolean = false,
    )

    private val _state = MutableStateFlow(
        UiState(
            settings = settingsRepository.settings.value,
            baseUrlDraft = settingsRepository.settings.value.apiBaseUrl,
            notificationPermission = PermissionUtils.hasNotificationPermission(context),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update {
                    it.copy(
                        settings = settings,
                        baseUrlDraft = if (it.baseUrlBusy) it.baseUrlDraft else settings.apiBaseUrl,
                    )
                }
            }
        }
        viewModelScope.launch {
            configRepository.config.collect { config -> _state.update { it.copy(config = config) } }
        }
        refreshConfig()
    }

    fun onBaseUrlChange(value: String) = _state.update {
        it.copy(baseUrlDraft = value, baseUrlError = null, notice = null)
    }

    fun saveBaseUrl() {
        viewModelScope.launch {
            _state.update { it.copy(baseUrlBusy = true, baseUrlError = null, notice = null) }
            settingsRepository.setApiBaseUrl(_state.value.baseUrlDraft)
                .onSuccess { normalized ->
                    _state.update {
                        it.copy(
                            baseUrlBusy = false,
                            baseUrlDraft = normalized,
                            notice = "API base URL saved. Refreshing public config…",
                        )
                    }
                    refreshConfig()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            baseUrlBusy = false,
                            baseUrlError = error.message ?: "Enter a full http:// or https:// URL.",
                        )
                    }
                }
        }
    }

    fun refreshConfig() {
        viewModelScope.launch {
            _state.update { it.copy(refreshingConfig = true) }
            configRepository.refresh()
                .onSuccess {
                    _state.update { it.copy(refreshingConfig = false, notice = "Server configuration refreshed.") }
                }
                .onFailure {
                    _state.update { it.copy(refreshingConfig = false) }
                }
        }
    }

    fun setTheme(label: String) {
        val mode = ThemeMode.entries.firstOrNull { it.label == label } ?: return
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
            _state.update { it.copy(notificationPermission = PermissionUtils.hasNotificationPermission(context)) }
        }
    }

    fun setSimulation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSimulationMode(enabled)
            _state.update {
                it.copy(
                    notice = if (enabled) {
                        "Simulation mode is on. Scans now inject synthetic BLE devices through the normal pipeline."
                    } else {
                        "Simulation mode is off. The next scan uses the real BLE radio."
                    },
                )
            }
        }
    }

    fun changeScanInterval(deltaSeconds: Int) {
        val next = (_state.value.settings.scanIntervalSeconds + deltaSeconds).coerceIn(5, 120)
        viewModelScope.launch { settingsRepository.setScanIntervalSeconds(next) }
    }

    fun refreshPermission() {
        _state.update { it.copy(notificationPermission = PermissionUtils.hasNotificationPermission(context)) }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun deviceSummary(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onMonitoring: () -> Unit,
    onPrivacy: () -> Unit,
    onNetwork: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.refreshPermission()
        if (granted) viewModel.setNotifications(true)
    }

    ScreenScaffold(
        title = "Settings",
        subtitle = "Local preferences and server connection",
        onBack = onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (state.settings.simulationMode) {
                SimulationBanner()
                Spacer(Modifier.height(10.dp))
            }
            if (state.notice != null) {
                InfoBanner(state.notice!!)
                Spacer(Modifier.height(10.dp))
            }

            SectionHeader(title = "API connection")
            Spacer(Modifier.height(8.dp))
            ProxiCard(accent = Palette.Cyan) {
                AppTextField(
                    value = state.baseUrlDraft,
                    onValueChange = viewModel::onBaseUrlChange,
                    label = "API base URL",
                    placeholder = "http://10.0.2.2:8080/api/v1/",
                    keyboardType = KeyboardType.Uri,
                    error = state.baseUrlError,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Default emulator URL is http://10.0.2.2:8080/api/v1/. Cleartext HTTP is permitted only in debug builds; release builds require HTTPS.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Save URL",
                        onClick = viewModel::saveBaseUrl,
                        modifier = Modifier.weight(1f),
                        loading = state.baseUrlBusy,
                    )
                    SecondaryButton(
                        text = "Refresh config",
                        onClick = viewModel::refreshConfig,
                        modifier = Modifier.weight(1f),
                        enabled = !state.refreshingConfig,
                        icon = Icons.Filled.Refresh,
                    )
                }
                Spacer(Modifier.height(10.dp))
                StatusChip(
                    text = if (state.config.remoteSynced) "SERVER CONFIG SYNCED" else "USING CACHED CONFIG",
                    color = if (state.config.remoteSynced) Palette.Mint else Palette.Amber,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(title = "Appearance")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                SelectablePillRow(
                    options = ThemeMode.entries.map { it.label },
                    selected = state.settings.themeMode.label,
                    onSelect = viewModel::setTheme,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Dark is the default. System follows the device setting.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(title = "Scanning & classification")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Record interval", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
                        Text(
                            "Minimum time between persisted proximity events for the same device. Band changes always record immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepButton("−") { viewModel.changeScanInterval(-5) }
                        Text(
                            "${state.settings.scanIntervalSeconds}s",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.Cyan,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                        StepButton("+") { viewModel.changeScanInterval(5) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                StatusChip(
                    text = "SERVER DEFAULT ${state.config.scanIntervalSeconds}s",
                    color = Palette.Violet,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Bands (from GET /config/public): IMMEDIATE ≥ ${state.config.thresholds.immediate} dBm, " +
                        "VERY_CLOSE ≥ ${state.config.thresholds.veryClose}, NEARBY ≥ ${state.config.thresholds.nearby}, " +
                        "FAR ≥ ${state.config.thresholds.far}, else OUT_OF_RANGE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Smoothing: sliding-window median of 5 samples, then EMA with α = 0.3.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Location interval", "${state.config.locationIntervalSeconds}s (read-only, server)")
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(title = "Notifications")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                ToggleRow(
                    title = "In-app proximity alerts",
                    subtitle = "Show an in-app banner when a device enters IMMEDIATE. Also gates foreground alerts.",
                    checked = state.settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !PermissionUtils.hasNotificationPermission(context)) {
                            notificationLauncher.launch(PermissionUtils.notificationPermission())
                        } else {
                            viewModel.setNotifications(enabled)
                        }
                    },
                )
                StatusChip(
                    text = if (state.notificationPermission) "SYSTEM PERMISSION GRANTED" else "SYSTEM PERMISSION NOT GRANTED",
                    color = if (state.notificationPermission) Palette.Mint else Palette.Amber,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(title = "Simulation")
            Spacer(Modifier.height(8.dp))
            ProxiCard(accent = Palette.Violet) {
                ToggleRow(
                    title = "Simulation mode",
                    subtitle = "Injects three synthetic BLE devices through the same smoothing, classification, persistence and upload pipeline. Never represents real hardware.",
                    checked = state.settings.simulationMode,
                    onCheckedChange = viewModel::setSimulation,
                )
                if (state.settings.simulationMode) {
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(
                        "Simulated observations are badged SIMULATED across scan, history and device views, and metadata records simulated=true.",
                        accent = Palette.Violet,
                        icon = Icons.Filled.Warning,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(title = "Monitoring & data")
            Spacer(Modifier.height(8.dp))
            ProxiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                LinkRow("Proximity monitoring", "Foreground service and location sharing", onMonitoring)
                LinkRow("Privacy & permissions", "What is collected, and how to revoke it", onPrivacy)
                LinkRow("Sync & network", "Outbox status and manual sync", onNetwork, divider = false)
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(title = "About")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                KeyValueRow("App", "ProxiLink for Android")
                KeyValueRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                KeyValueRow("Build", if (BuildConfig.DEBUG) "debug" else "release")
                KeyValueRow("Device", viewModel.deviceSummary())
                KeyValueRow("API base", state.settings.apiBaseUrl)
                KeyValueRow("Smoothing", "Median 5 · EMA 0.3")
                Spacer(Modifier.height(10.dp))
                Text(
                    "Distance values are log-distance estimates, not measured distances. Token storage uses DataStore in this build; production should wrap it with Android Keystore/EncryptedSharedPreferences.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Palette.Cyan)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit, divider: Boolean = true) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        if (divider) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline.copy(alpha = 0.45f)))
        }
    }
}
