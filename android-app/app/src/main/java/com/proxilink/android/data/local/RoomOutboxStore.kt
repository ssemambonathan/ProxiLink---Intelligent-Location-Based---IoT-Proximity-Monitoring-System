package com.proxilink.android.data.local

import androidx.room.withTransaction
import com.proxilink.android.domain.outbox.OutboxCounts
import com.proxilink.android.domain.outbox.OutboxKind
import com.proxilink.android.domain.outbox.OutboxRecord
import com.proxilink.android.domain.outbox.OutboxStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomOutboxStore @Inject constructor(
    private val database: ProxiLinkDatabase,
) : OutboxStore {

    private val dao = database.outboxDao()

    override suspend fun pending(kind: OutboxKind, limit: Int): List<OutboxRecord> =
        dao.pending(kind.name, limit).map { it.toRecord() }

    override suspend fun markAcknowledged(eventIds: List<String>) {
        if (eventIds.isEmpty()) return
        dao.deleteByIds(eventIds)
    }

    override suspend fun markRejected(eventId: String, reason: String) {
        dao.markRejected(eventId, reason)
    }

    override suspend fun markAttempt(eventIds: List<String>) {
        if (eventIds.isEmpty()) return
        dao.incrementAttempts(eventIds)
    }

    override suspend fun counts(): OutboxCounts =
        OutboxCounts(pending = dao.pendingCount(), rejected = dao.rejectedCount())

    override suspend fun recentRejected(limit: Int): List<OutboxRecord> =
        dao.recentRejected(limit).map { it.toRecord() }
}
