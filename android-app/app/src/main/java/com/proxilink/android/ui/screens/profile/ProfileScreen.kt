package com.proxilink.android.ui.screens.profile

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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.remote.toUserMessage
import com.proxilink.android.data.repository.AuthRepository
import com.proxilink.android.data.repository.DeviceRepository
import com.proxilink.android.domain.model.UserProfile
import com.proxilink.android.ui.design.ErrorBanner
import com.proxilink.android.ui.design.InfoBanner
import com.proxilink.android.ui.design.KeyValueRow
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ScreenScaffold
import com.proxilink.android.ui.design.SecondaryButton
import com.proxilink.android.ui.design.StatusChip
import com.proxilink.android.ui.design.dateTimeLabel
import com.proxilink.android.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    deviceRepository: DeviceRepository,
) : ViewModel() {

    data class UiState(
        val user: UserProfile? = null,
        val deviceCount: Int = 0,
        val refreshing: Boolean = false,
        val signingOut: Boolean = false,
        val error: String? = null,
        val signedOut: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.user.collect { user -> _state.update { it.copy(user = user) } }
        }
        viewModelScope.launch {
            deviceRepository.cachedCount.collect { count -> _state.update { it.copy(deviceCount = count) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            authRepository.refreshProfile()
                .onFailure { error -> _state.update { it.copy(error = error.toUserMessage()) } }
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(signingOut = true) }
            runCatching { authRepository.logout() }
            _state.update { it.copy(signingOut = false, signedOut = true) }
        }
    }
}

@Composable
fun ProfileScreen(
    onSettings: () -> Unit,
    onPrivacy: () -> Unit,
    onMonitoring: () -> Unit,
    onNetwork: () -> Unit,
    onRegisterDevice: () -> Unit,
    onProximityHistory: () -> Unit,
    onLocationHistory: () -> Unit,
    onNotifications: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onLoggedOut()
    }

    ScreenScaffold(
        title = "Profile",
        subtitle = "Account, roles and shortcuts",
        actions = {
            Text(
                "Refresh",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Cyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = viewModel::refresh)
                    .padding(6.dp),
            )
        },
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
            val user = state.user
            ProxiCard(accent = Palette.Cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Palette.Cyan, Palette.Violet))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initials(user?.displayName ?: user?.email ?: "?"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Palette.Background,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            user?.displayName?.ifBlank { "ProxiLink operator" } ?: "ProxiLink operator",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Palette.TextPrimary,
                        )
                        Text(
                            user?.email ?: "Profile not loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (user != null) {
                                StatusChip(text = user.roleLabel.uppercase(), color = if (user.isAdmin) Palette.Violet else Palette.Cyan)
                                if (user.enabled) {
                                    StatusChip(text = "ENABLED", color = Palette.Mint, icon = Icons.Filled.CheckCircle)
                                } else {
                                    StatusChip(text = "DISABLED", color = Palette.Coral)
                                }
                            }
                        }
                    }
                }
                if (user != null) {
                    Spacer(Modifier.height(14.dp))
                    KeyValueRow("Organization", user.organization.ifBlank { "—" })
                    KeyValueRow("Roles", user.roles.joinToString(", ").ifBlank { "member" })
                    KeyValueRow("Member since", user.createdAt?.dateTimeLabel() ?: "—")
                    KeyValueRow("Registered devices (cached)", state.deviceCount.toString())
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Shortcuts", style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
            Spacer(Modifier.height(8.dp))
            ProxiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MenuRow(Icons.Filled.Settings, "App settings", "Theme, API endpoint, simulation, intervals", onSettings)
                MenuRow(Icons.Filled.Shield, "Privacy & permissions", "What is collected and how to revoke it", onPrivacy)
                MenuRow(Icons.Filled.Bluetooth, "Monitoring", "Continuous BLE and location sharing", onMonitoring)
                MenuRow(Icons.Filled.Wifi, "Sync & network", "Outbox, rejected events, manual sync", onNetwork)
                MenuRow(Icons.Filled.Devices, "Register device", "Add hardware to your organization", onRegisterDevice)
                MenuRow(Icons.Filled.History, "Proximity events", "Server-classified history", onProximityHistory)
                MenuRow(Icons.Filled.LocationOn, "Location history", "Opt-in location fixes", onLocationHistory)
                MenuRow(Icons.Filled.Notifications, "Notifications", "Alerts and unread messages", onNotifications, divider = false)
            }

            Spacer(Modifier.height(18.dp))
            SecondaryButton(
                text = if (state.signingOut) "Signing out…" else "Sign out",
                onClick = { confirmLogout = true },
                enabled = !state.signingOut,
                icon = Icons.Filled.Logout,
            )
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                "Signing out clears the locally stored tokens and stops the realtime socket. Queued events remain in the outbox until the next signed-in sync.",
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sign out?") },
            text = { Text("You will need your credentials to sign in again. Local monitoring stops.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout()
                }) { Text("Sign out", color = Palette.Coral) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            },
            containerColor = Palette.SurfaceHigh,
            titleContentColor = Palette.TextPrimary,
            textContentColor = Palette.TextSecondary,
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    divider: Boolean = true,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
            }
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(15.dp))
        }
        if (divider) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline.copy(alpha = 0.45f)))
        }
    }
}

private fun initials(value: String): String {
    val parts = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "PL"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
