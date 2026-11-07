package com.proxilink.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox_events WHERE state = 'PENDING' AND kind = :kind ORDER BY createdAtEpochMs ASC LIMIT :limit")
    suspend fun pending(kind: String, limit: Int): List<OutboxEventEntity>

    @Query("DELETE FROM outbox_events WHERE eventId IN (:eventIds)")
    suspend fun deleteByIds(eventIds: List<String>)

    @Query("UPDATE outbox_events SET state = 'REJECTED', rejectionReason = :reason WHERE eventId = :eventId")
    suspend fun markRejected(eventId: String, reason: String)

    @Query("UPDATE outbox_events SET attempts = attempts + 1 WHERE eventId IN (:eventIds)")
    suspend fun incrementAttempts(eventIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: OutboxEventEntity): Long

    @Query("SELECT COUNT(*) FROM outbox_events WHERE state = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_events WHERE state = 'REJECTED'")
    fun observeRejectedCount(): Flow<Int>

    @Query("SELECT * FROM outbox_events WHERE state = 'REJECTED' ORDER BY recordedAtEpochMs DESC LIMIT :limit")
    suspend fun recentRejected(limit: Int): List<OutboxEventEntity>

    @Query("SELECT COUNT(*) FROM outbox_events WHERE state = 'PENDING'")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM outbox_events WHERE state = 'REJECTED'")
    suspend fun rejectedCount(): Int

    @Query("DELETE FROM outbox_events WHERE state = 'REJECTED'")
    suspend fun clearRejected()
}

@Dao
interface DeviceDao {

    @Query("SELECT * FROM cached_devices ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CachedDeviceEntity>>

    @Query("SELECT * FROM cached_devices ORDER BY name COLLATE NOCASE ASC")
    suspend fun all(): List<CachedDeviceEntity>

    @Query("SELECT * FROM cached_devices WHERE id = :id")
    suspend fun byId(id: String): CachedDeviceEntity?

    @Upsert
    suspend fun upsertAll(devices: List<CachedDeviceEntity>)

    @Query("DELETE FROM cached_devices WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<String>)
}
