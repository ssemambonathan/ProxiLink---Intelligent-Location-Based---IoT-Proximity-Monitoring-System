package com.proxilink.android.ui.screens.notifications

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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.remote.toUserMessage
import com.proxilink.android.data.repository.NotificationRepository
import com.proxilink.android.domain.model.AppNotification
import com.proxilink.android.ui.design.EmptyState
import com.proxilink.android.ui.design.ErrorBanner
import com.proxilink.android.ui.design.InfoBanner
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ScreenScaffold
import com.proxilink.android.ui.design.SkeletonCard
import com.proxilink.android.ui.design.StatusChip
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
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {

    data class UiState(
        val items: List<AppNotification> = emptyList(),
        val unread: Int = 0,
        val loading: Boolean = true,
        val error: String? = null,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.items.collect { items ->
                _state.update { it.copy(items = items, unread = items.count { item -> !item.read }) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.refresh()
                .onSuccess { _state.update { it.copy(loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.toUserMessage()) } }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            repository.markRead(id)
                .onFailure { error -> _state.update { it.copy(notice = error.toUserMessage()) } }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repository.markAllRead()
                .onSuccess { _state.update { it.copy(notice = "All notifications marked read.") } }
                .onFailure { error -> _state.update { it.copy(notice = error.toUserMessage()) } }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }
}

@Composable
fun NotificationsScreen(
    onBack: (() -> Unit)? = null,
    onOpenScan: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    ScreenScaffold(
        title = "Notifications",
        subtitle = if (state.unread > 0) "${state.unread} unread" else "You are up to date",
        onBack = onBack,
        actions = {
            if (state.unread > 0) {
                Text(
                    "Mark all read",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Cyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::markAllRead)
                        .padding(6.dp),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (state.notice != null) {
                InfoBanner(state.notice!!)
                Spacer(Modifier.height(10.dp))
            }
            if (state.error != null) {
                ErrorBanner(message = state.error!!, onRetry = viewModel::refresh)
                Spacer(Modifier.height(10.dp))
            }
            if (state.loading && state.items.isEmpty()) {
                SkeletonCard()
                Spacer(Modifier.height(10.dp))
                SkeletonCard()
            } else if (state.items.isEmpty()) {
                ProxiCard {
                    EmptyState(
                        icon = Icons.Filled.Notifications,
                        title = "No notifications",
                        message = "Proximity alerts and device status changes appear here. Real-time updates arrive over the WebSocket when connected.",
                        actionLabel = "Open scanner",
                        onAction = onOpenScan,
                    )
                }
            } else {
                state.items.forEach { item ->
                    NotificationCard(item = item, onRead = { if (!item.read) viewModel.markRead(item.id) })
                    Spacer(Modifier.height(9.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            InfoBanner(
                "Alerts respect the notification toggle in Settings. In-app messages are also shown as a transient banner while the app is open.",
                icon = Icons.Filled.Notifications,
            )
            Spacer(Modifier.height(MainBottomBarInset))
        }
    }
}

@Composable
private fun NotificationCard(item: AppNotification, onRead: () -> Unit) {
    val accent = notificationAccent(item.type)
    ProxiCard(
        onClick = onRead,
        accent = if (item.read) null else accent,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.type.contains("LOCATION", ignoreCase = true)) Icons.Filled.LocationOn else Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title.ifBlank { "ProxiLink" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                        fontWeight = if (item.read) FontWeight.Medium else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!item.read) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(item.createdAt.relativeLabel(), style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(text = item.type.uppercase(), color = accent)
                    if (!item.read) StatusChip(text = "UNREAD", color = Palette.Cyan)
                }
            }
        }
    }
}

private fun notificationAccent(type: String): Color = when {
    type.contains("PROXIMITY", ignoreCase = true) -> Palette.Coral
    type.contains("LOCATION", ignoreCase = true) -> Palette.Cyan
    type.contains("DEVICE", ignoreCase = true) -> Palette.Violet
    else -> Palette.Amber
}
