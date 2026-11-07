package com.proxilink.android.domain.outbox

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

enum class OutboxKind(val wireName: String) {
    PROXIMITY("events/proximity"),
    LOCATION("events/location"),
}

enum class OutboxState { PENDING, REJECTED }

data class OutboxRecord(
    val eventId: String,
    val kind: OutboxKind,
    val payloadJson: String,
    val recordedAt: Instant,
    val state: OutboxState,
    val attempts: Int,
    val rejectionReason: String?,
)

data class OutboxCounts(val pending: Int, val rejected: Int)

data class RejectedEvent(val eventId: String, val reason: String)

data class BatchResult(
    val accepted: Int,
    val duplicates: Int,
    val rejected: List<RejectedEvent> = emptyList(),
) {
    init {
        require(accepted >= 0 && duplicates >= 0) { "counts must be non-negative" }
    }

    val acknowledged: Int get() = accepted + duplicates
}

fun interface BatchSender {
    /** Posts one batch; throws on transport/server failure so the caller can retry later. */
    suspend fun send(kind: OutboxKind, batchJson: String): BatchResult
}

interface OutboxStore {
    suspend fun pending(kind: OutboxKind, limit: Int): List<OutboxRecord>
    suspend fun markAcknowledged(eventIds: List<String>)
    suspend fun markRejected(eventId: String, reason: String)
    suspend fun markAttempt(eventIds: List<String>)
    suspend fun counts(): OutboxCounts
    suspend fun recentRejected(limit: Int): List<OutboxRecord>
}

data class SyncSummary(
    val accepted: Int = 0,
    val duplicates: Int = 0,
    val rejected: Int = 0,
    val batches: Int = 0,
    val completedAt: Instant,
    val failed: Boolean = false,
    val message: String? = null,
)

/**
 * Drains the Room outbox in idempotent batches.
 *
 * Server semantics: accepted + duplicates mean "stored safely, delete locally"; rejected rows are
 * kept with a reason so the network screen can explain why they were not accepted. A failed HTTP
 * call throws out of [sync] so WorkManager applies exponential backoff; the client UUIDs make a
 * replay harmless.
 */
class OutboxSyncer(
    private val store: OutboxStore,
    private val sender: BatchSender,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val now: () -> Instant = { Instant.now() },
) {
    init {
        require(batchSize > 0) { "batchSize must be positive" }
    }

    suspend fun sync(): SyncSummary {
        var accepted = 0
        var duplicates = 0
        var rejected = 0
        var batches = 0

        for (kind in OutboxKind.entries) {
            while (true) {
                val rows = store.pending(kind, batchSize)
                if (rows.isEmpty()) break

                val batchJson = buildBatchJson(rows)
                val result = sender.send(kind, batchJson)
                batches++

                val ids = rows.map { it.eventId }
                val rejectedRows = result.rejected.filter { it.eventId in ids }
                val rejectedIds = rejectedRows.map { it.eventId }.toSet()
                val acknowledgedIds = ids.filterNot { it in rejectedIds }

                accepted += result.accepted
                duplicates += result.duplicates
                rejected += rejectedRows.size

                if (result.acknowledged == 0 && rejectedRows.isEmpty()) {
                    // The server neither stored nor rejected anything: keep every row and let the
                    // caller retry instead of silently dropping data.
                    store.markAttempt(ids)
                    return SyncSummary(
                        accepted = accepted,
                        duplicates = duplicates,
                        rejected = rejected,
                        batches = batches,
                        completedAt = now(),
                        failed = true,
                        message = "Server acknowledged no events; rows kept for retry.",
                    )
                }

                if (acknowledgedIds.isNotEmpty()) {
                    store.markAcknowledged(acknowledgedIds)
                }
                rejectedRows.forEach { store.markRejected(it.eventId, it.reason) }

                if (rows.size < batchSize) break
            }
        }

        return SyncSummary(
            accepted = accepted,
            duplicates = duplicates,
            rejected = rejected,
            batches = batches,
            completedAt = now(),
        )
    }

    private fun buildBatchJson(rows: List<OutboxRecord>): String {
        val events = JsonArray(rows.map { json.parseToJsonElement(it.payloadJson) })
        return buildJsonObject { put("events", events) }.toString()
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 50
        private val json = Json { ignoreUnknownKeys = true }
    }
}
