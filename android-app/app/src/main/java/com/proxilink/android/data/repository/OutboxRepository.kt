package com.proxilink.android.data.repository

import com.proxilink.android.data.local.OutboxDao
import com.proxilink.android.data.local.OutboxEventEntity
import com.proxilink.android.data.local.SettingsDataStore
import com.proxilink.android.data.local.toRecord
import com.proxilink.android.data.remote.dto.LocationEventIngest
import com.proxilink.android.data.remote.dto.ProximityEventIngest
import com.proxilink.android.data.remote.toApiString
import com.proxilink.android.domain.outbox.OutboxCounts
import com.proxilink.android.domain.outbox.OutboxKind
import com.proxilink.android.domain.outbox.OutboxRecord
import com.proxilink.android.domain.outbox.OutboxSyncer
import com.proxilink.android.domain.outbox.SyncSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutboxRepository @Inject constructor(
    private val dao: OutboxDao,
    private val settings: SettingsDataStore,
    private val syncer: OutboxSyncer,
    private val scheduler: SyncScheduler,
    private val json: Json,
) {
    val counts: Flow<OutboxCounts> = combine(
        dao.observePendingCount(),
        dao.observeRejectedCount(),
    ) { pending, rejected -> OutboxCounts(pending, rejected) }

    val syncStats: Flow<SettingsDataStore.SyncStats> = settings.syncStats

    suspend fun enqueueProximity(
        eventId: String,
        payload: ProximityEventIngest,
        recordedAt: Instant,
    ) = enqueue(
        eventId = eventId,
        kind = OutboxKind.PROXIMITY,
        payloadJson = json.encodeToString(ProximityEventIngest.serializer(), payload),
        recordedAt = recordedAt,
    )

    suspend fun enqueueLocation(
        eventId: String,
        payload: LocationEventIngest,
        recordedAt: Instant,
    ) = enqueue(
        eventId = eventId,
        kind = OutboxKind.LOCATION,
        payloadJson = json.encodeToString(LocationEventIngest.serializer(), payload),
        recordedAt = recordedAt,
    )

    private suspend fun enqueue(
        eventId: String,
        kind: OutboxKind,
        payloadJson: String,
        recordedAt: Instant,
    ) {
        dao.insert(
            OutboxEventEntity(
                eventId = eventId,
                kind = kind.name,
                payloadJson = payloadJson,
                recordedAtEpochMs = recordedAt.toEpochMilli(),
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        scheduler.requestSync()
    }

    /** Throws on transport/server failure so the WorkManager retry policy applies. */
    suspend fun syncNow(): SyncSummary {
        val summary = syncer.sync()
        settings.recordSync(
            acked = summary.accepted + summary.duplicates,
            rejected = summary.rejected,
            at = summary.completedAt,
        )
        return summary
    }

    suspend fun recentRejected(limit: Int = 25): List<OutboxRecord> =
        dao.recentRejected(limit).map { it.toRecord() }

    suspend fun clearRejected() = dao.clearRejected()

    suspend fun pendingCount(): Int = dao.pendingCount()

    companion object {
        fun newEventId(): String = UUID.randomUUID().toString()

        fun proximityPayload(
            eventId: String,
            deviceId: String?,
            bleMac: String?,
            deviceName: String?,
            rssi: Int,
            smoothedRssi: Double?,
            latitude: Double?,
            longitude: Double?,
            accuracyM: Double?,
            recordedAt: Instant,
            metadata: Map<String, String>,
        ) = ProximityEventIngest(
            eventId = eventId,
            deviceId = deviceId,
            bleMac = bleMac,
            deviceName = deviceName,
            rssi = rssi,
            smoothedRssi = smoothedRssi,
            latitude = latitude,
            longitude = longitude,
            accuracyM = accuracyM,
            recordedAt = recordedAt.toApiString(),
            metadata = metadata,
        )

        fun locationPayload(
            eventId: String,
            latitude: Double,
            longitude: Double,
            accuracyM: Double?,
            speedMps: Double?,
            altitudeM: Double?,
            recordedAt: Instant,
            metadata: Map<String, String> = emptyMap(),
        ) = LocationEventIngest(
            eventId = eventId,
            latitude = latitude,
            longitude = longitude,
            accuracyM = accuracyM,
            speedMps = speedMps,
            altitudeM = altitudeM,
            recordedAt = recordedAt.toApiString(),
            metadata = metadata,
        )
    }
}
