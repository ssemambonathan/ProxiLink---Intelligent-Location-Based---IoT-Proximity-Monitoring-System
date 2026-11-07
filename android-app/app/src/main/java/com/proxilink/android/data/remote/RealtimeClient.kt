package com.proxilink.android.data.remote

import com.proxilink.android.data.local.TokenStore
import com.proxilink.android.data.remote.dto.DeviceStatusPayloadDto
import com.proxilink.android.data.remote.dto.HelloPayloadDto
import com.proxilink.android.data.remote.dto.LocationEventDto
import com.proxilink.android.data.remote.dto.NotificationDto
import com.proxilink.android.data.remote.dto.ProximityEventDto
import com.proxilink.android.data.remote.dto.WsEnvelopeDto
import com.proxilink.android.di.ApplicationScope
import com.proxilink.android.di.WebSocketClient
import com.proxilink.android.domain.model.AppNotification
import com.proxilink.android.domain.model.DeviceStatus
import com.proxilink.android.domain.model.LocationEventRecord
import com.proxilink.android.domain.model.ProximityEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class RealtimeState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, UNAUTHENTICATED }

sealed interface RealtimeEvent {
    data class Hello(val sessionId: String, val userId: String, val serverTime: Instant?) : RealtimeEvent
    data class Proximity(val event: ProximityEvent) : RealtimeEvent
    data class Location(val event: LocationEventRecord) : RealtimeEvent
    data class DeviceStatusUpdate(
        val deviceId: String,
        val status: DeviceStatus,
        val lastSeenAt: Instant?,
        val lastRssi: Int?,
    ) : RealtimeEvent
    data class Notification(val notification: AppNotification) : RealtimeEvent
    data class Failure(val message: String) : RealtimeEvent
}

/**
 * OkHttp WebSocket client with exponential-backoff reconnect. Access tokens live 30 minutes, so a
 * 401 close triggers a single-flight refresh before reconnecting with the new token.
 */
@Singleton
class RealtimeClient @Inject constructor(
    @WebSocketClient private val client: OkHttpClient,
    private val apiConfig: ApiConfig,
    private val tokenStore: TokenStore,
    private val refresher: SessionRefresher,
    private val json: Json,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<RealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(RealtimeState.DISCONNECTED)
    val state: StateFlow<RealtimeState> = _state.asStateFlow()

    private var loopJob: Job? = null
    private var socket: WebSocket? = null

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { reconnectLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        socket?.close(NORMAL_CLOSURE, "client stopped")
        socket = null
        _state.value = RealtimeState.DISCONNECTED
    }

    private suspend fun reconnectLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val tokens = tokenStore.current()
            if (tokens == null) {
                _state.value = RealtimeState.UNAUTHENTICATED
                delay(UNAUTHENTICATED_RETRY_MS)
                continue
            }

            _state.value = if (attempt == 0) RealtimeState.CONNECTING else RealtimeState.RECONNECTING
            val closed = CompletableDeferred<Int>()
            var unauthorized = false
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    attempt = 0
                    _state.value = RealtimeState.CONNECTED
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleFrame(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    unauthorized = response?.code == 401
                    _events.tryEmit(RealtimeEvent.Failure(t.message ?: "Realtime channel failed"))
                    if (!closed.isCompleted) closed.complete(response?.code ?: 0)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (code == 401) unauthorized = true
                    if (!closed.isCompleted) closed.complete(code)
                }
            }

            val request = Request.Builder().url(apiConfig.webSocketUrl(tokens.accessToken)).build()
            val ws = client.newWebSocket(request, listener)
            socket = ws
            val heartbeat = scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    ws.send("""{"type":"ping"}""")
                }
            }

            val closeCode = closed.await()
            heartbeat.cancel()
            socket = null

            if (!currentCoroutineContext().isActive) break
            if (closeCode == 401 || unauthorized) {
                val refreshed = refresher.refresh()
                if (refreshed == null) {
                    _state.value = RealtimeState.UNAUTHENTICATED
                    delay(UNAUTHENTICATED_RETRY_MS)
                    continue
                }
            }
            attempt++
            _state.value = RealtimeState.RECONNECTING
            delay(backoffMillis(attempt))
        }
    }

    private fun handleFrame(text: String) {
        val envelope = runCatching { json.decodeFromString(WsEnvelopeDto.serializer(), text) }
            .getOrNull() ?: return
        val payload = envelope.payload ?: return
        val event = runCatching {
            when (envelope.type) {
                "hello" -> {
                    val hello = json.decodeFromJsonElement(HelloPayloadDto.serializer(), payload)
                    RealtimeEvent.Hello(hello.sessionId, hello.userId, hello.serverTime?.let { Instant.parse(it) })
                }
                "proximity.event" ->
                    RealtimeEvent.Proximity(json.decodeFromJsonElement(ProximityEventDto.serializer(), payload).toDomain())
                "location.event" ->
                    RealtimeEvent.Location(json.decodeFromJsonElement(LocationEventDto.serializer(), payload).toDomain())
                "device.status" -> {
                    val update = json.decodeFromJsonElement(DeviceStatusPayloadDto.serializer(), payload)
                    RealtimeEvent.DeviceStatusUpdate(
                        deviceId = update.deviceId,
                        status = DeviceStatus.fromWire(update.status),
                        lastSeenAt = update.lastSeenAt?.let { Instant.parse(it) },
                        lastRssi = update.lastRssi,
                    )
                }
                "notification" ->
                    RealtimeEvent.Notification(json.decodeFromJsonElement(NotificationDto.serializer(), payload).toDomain())
                else -> null
            }
        }.getOrNull()
        if (event != null) _events.tryEmit(event)
    }

    private fun backoffMillis(attempt: Int): Long {
        val exponential = BASE_BACKOFF_MS shl (attempt - 1).coerceIn(0, 6)
        return (exponential + Random.nextLong(0, 400)).coerceAtMost(MAX_BACKOFF_MS)
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val HEARTBEAT_MS = 25_000L
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val UNAUTHENTICATED_RETRY_MS = 30_000L
    }
}
