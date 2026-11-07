package com.proxilink.android.data.repository

import com.proxilink.android.data.remote.ProxiLinkApi
import com.proxilink.android.data.remote.RealtimeClient
import com.proxilink.android.data.remote.RealtimeEvent
import com.proxilink.android.data.remote.toDomain
import com.proxilink.android.di.ApplicationScope
import com.proxilink.android.domain.model.AppNotification
import com.proxilink.android.service.InAppMessage
import com.proxilink.android.service.InAppNotificationCenter
import com.proxilink.android.service.InAppSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val api: ProxiLinkApi,
    private val realtime: RealtimeClient,
    private val inApp: InAppNotificationCenter,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())
    val items: StateFlow<List<AppNotification>> = _items.asStateFlow()

    val unreadCount: StateFlow<Int> = _items
        .map { list -> list.count { !it.read } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private var observingRealtime = false

    fun observeRealtime() {
        if (observingRealtime) return
        observingRealtime = true
        scope.launch {
            realtime.events.collect { event ->
                if (event is RealtimeEvent.Notification) {
                    val notification = event.notification
                    _items.update { current ->
                        listOf(notification) + current.filterNot { it.id == notification.id }
                    }
                    if (settings.settings.value.notificationsEnabled) {
                        inApp.push(
                            InAppMessage(
                                title = notification.title,
                                body = notification.body,
                                severity = InAppSeverity.WARNING,
                            ),
                        )
                    }
                }
            }
        }
    }

    suspend fun refresh(unreadOnly: Boolean = false): Result<List<AppNotification>> = runCatching {
        api.notifications(unreadOnly = unreadOnly, size = 50).items.map { it.toDomain() }
    }.onSuccess { _items.value = it }

    suspend fun markRead(id: String): Result<Unit> = runCatching {
        api.markNotificationRead(id)
        _items.update { list -> list.map { if (it.id == id) it.copy(read = true) else it } }
    }

    suspend fun markAllRead(): Result<Unit> = runCatching {
        api.markAllNotificationsRead()
        _items.update { list -> list.map { it.copy(read = true) } }
    }
}
