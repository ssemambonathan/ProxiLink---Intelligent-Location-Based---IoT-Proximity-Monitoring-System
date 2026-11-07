package com.proxilink.android.data.remote

import com.proxilink.android.data.remote.dto.RejectedEventDto
import com.proxilink.android.domain.outbox.BatchResult
import com.proxilink.android.domain.outbox.BatchSender
import com.proxilink.android.domain.outbox.OutboxKind
import com.proxilink.android.domain.outbox.RejectedEvent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteBatchSender @Inject constructor(
    private val api: ProxiLinkApi,
) : BatchSender {

    override suspend fun send(kind: OutboxKind, batchJson: String): BatchResult {
        val body = batchJson.toRequestBody(JSON)
        val response = when (kind) {
            OutboxKind.PROXIMITY -> api.ingestProximity(body)
            OutboxKind.LOCATION -> api.ingestLocation(body)
        }
        return BatchResult(
            accepted = response.accepted,
            duplicates = response.duplicates,
            rejected = response.rejected.map { it.toRejected() },
        )
    }

    private fun RejectedEventDto.toRejected() = RejectedEvent(eventId = eventId, reason = reason)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
