package com.proxilink.android.ui.screens.privacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.ui.design.InfoBanner
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ScreenScaffold
import com.proxilink.android.ui.design.SecondaryButton
import com.proxilink.android.ui.design.SectionHeader
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
class PrivacyViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val locationSharing: Boolean = false,
        val notificationsEnabled: Boolean = true,
        val scanPermission: Boolean = false,
        val locationPermission: Boolean = false,
        val notificationPermission: Boolean = false,
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
                        locationSharing = settings.locationSharingEnabled,
                        notificationsEnabled = settings.notificationsEnabled,
                    )
                }
            }
        }
    }

    fun refreshPermissions() {
        _state.update {
            it.copy(
                scanPermission = PermissionUtils.hasScanPermission(context),
                locationPermission = PermissionUtils.hasLocationPermission(context),
                notificationPermission = PermissionUtils.hasNotificationPermission(context),
            )
        }
    }

    fun enableLocationSharing() {
        if (!PermissionUtils.hasLocationPermission(context)) {
            _state.update { it.copy(notice = "Location permission is required before sharing can start.") }
            return
        }
        viewModelScope.launch { settingsRepository.setLocationSharingEnabled(true) }
    }

    fun disableLocationSharing() {
        viewModelScope.launch { settingsRepository.setLocationSharingEnabled(false) }
    }

    fun setNotifications(enabled: Boolean) {
        if (enabled && !PermissionUtils.hasNotificationPermission(context)) {
            _state.update { it.copy(notice = "Notification permission is denied; enable it in system settings.") }
            return
        }
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }
}

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onMonitoring: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    ScreenScaffold(
        title = "Privacy & permissions",
        subtitle = "What ProxiLink collects and how to control it",
        onBack = onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (state.notice != null) {
                InfoBanner(state.notice!!, accent = Palette.Amber, icon = Icons.Filled.Warning)
                Spacer(Modifier.height(10.dp))
            }

            SectionHeader(title = "Permissions")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                PermissionRow("Bluetooth scan", state.scanPermission, "Needed to receive BLE advertisements.") {
                    permissionLauncher.launch(PermissionUtils.scanPermissions())
                }
                PermissionRow("Location", state.locationPermission, "Only for location sharing and legacy BLE scanning on Android 11 and below.") {
                    permissionLauncher.launch(PermissionUtils.locationPermissions())
                }
                PermissionRow("Notifications", state.notificationPermission, "Used for the monitoring foreground notification and alerts.") {
                    permissionLauncher.launch(arrayOf(PermissionUtils.notificationPermission()))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "The app never bypasses OS consent. Denying a permission disables the dependent feature instead of working around it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "Controls")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                ToggleRow(
                    title = "Share location",
                    subtitle = "Off by default. When enabled, GPS fixes are attached to proximity events and queued in the outbox.",
                    checked = state.locationSharing,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!PermissionUtils.hasLocationPermission(context)) {
                                permissionLauncher.launch(PermissionUtils.locationPermissions())
                            } else {
                                viewModel.enableLocationSharing()
                            }
                        } else {
                            viewModel.disableLocationSharing()
                        }
                    },
                )
                ToggleRow(
                    title = "In-app notifications",
                    subtitle = "Controls immediate-proximity banners and WebSocket notification display.",
                    checked = state.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !PermissionUtils.hasNotificationPermission(context)) {
                            permissionLauncher.launch(arrayOf(PermissionUtils.notificationPermission()))
                        } else {
                            viewModel.setNotifications(enabled)
                        }
                    },
                )
                SecondaryButton(text = "Open monitoring controls", onClick = onMonitoring)
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "Data we handle")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                PrivacyLine("BLE observations", "MAC (if advertised), name, service UUID, RSSI, smoothed RSSI, proximity band and a log-distance estimate.")
                PrivacyLine("Event metadata", "A client-generated UUID per event, timestamps, scan mode and a simulated flag. The UUID makes retries idempotent.")
                PrivacyLine("Location (optional)", "Latitude, longitude, accuracy, speed and altitude only while sharing is on.")
                PrivacyLine("Account", "Email, display name, organization and roles returned by /auth/me.")
                PrivacyLine("Local storage", "Room outbox, DataStore preferences and tokens inside the app sandbox. Acknowledged outbox rows are deleted.")
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "Retention")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                Text(
                    "Accepted and de-duplicated events are removed from the local outbox immediately after the server acknowledges them. Rejected rows are kept so you can inspect them on the Sync & network screen, and are deleted only when you clear them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Server-side retention is controlled by your organization's ProxiLink deployment, not by the app. Signing out clears stored tokens; queued events stay until a signed-in sync or a manual clear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "System settings")
            Spacer(Modifier.height(8.dp))
            ProxiCard {
                SystemLink("App permissions & notifications", {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                })
                SystemLink("Notification channel settings", {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                })
                SystemLink("Device location settings", {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }, divider = false)
            }

            Spacer(Modifier.height(14.dp))
            InfoBanner(
                "Android 12+ rotates BLE MAC addresses, so a MAC may not identify a device across sessions. Prefer service UUID or advertised name matching.",
                icon = Icons.Filled.Warning,
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    description: String,
    onRequest: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (granted) Palette.Mint else Palette.Coral,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
            Text(description, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        }
        if (!granted) {
            Text(
                "Request",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Cyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onRequest)
                    .padding(4.dp),
            )
        } else {
            StatusChip(text = "ON", color = Palette.Mint)
        }
    }
}

@Composable
private fun PrivacyLine(title: String, body: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
        Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
    }
}

@Composable
private fun SystemLink(title: String, onClick: () -> Unit, divider: Boolean = true) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(16.dp))
        }
        if (divider) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.Outline.copy(alpha = 0.45f)),
            )
        }
    }
}
