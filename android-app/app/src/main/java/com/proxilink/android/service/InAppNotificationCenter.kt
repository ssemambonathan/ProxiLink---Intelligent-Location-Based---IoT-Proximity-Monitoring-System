package com.proxilink.android.service

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class InAppSeverity { INFO, WARNING, ALERT }

data class InAppMessage(
    val title: String,
    val body: String,
    val severity: InAppSeverity = InAppSeverity.INFO,
)

/** Lightweight in-app notification bus fed by the WebSocket channel. */
@Singleton
class InAppNotificationCenter @Inject constructor() {
    private val _messages = MutableSharedFlow<InAppMessage>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<InAppMessage> = _messages.asSharedFlow()

    fun push(message: InAppMessage) {
        _messages.tryEmit(message)
    }
}
