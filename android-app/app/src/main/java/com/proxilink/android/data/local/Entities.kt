package com.proxilink.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox_events",
    indices = [Index("state"), Index("kind"), Index("recordedAtEpochMs")],
)
data class OutboxEventEntity(
    @PrimaryKey val eventId: String,
    val kind: String,
    val payloadJson: String,
    val recordedAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val state: String = "PENDING",
    val attempts: Int = 0,
    val rejectionReason: String? = null,
)

@Entity(tableName = "cached_devices")
data class CachedDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val deviceType: String,
    val category: String,
    val hardwareId: String,
    val bleMac: String?,
    val serviceUuid: String?,
    val ownerName: String?,
    val organization: String?,
    val status: String,
    val active: Boolean,
    val lastSeenAtEpochMs: Long?,
    val lastRssi: Int?,
    val lastProximity: String,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val lastAccuracyM: Double?,
    val lastLocationAtEpochMs: Long?,
    val metadataJson: String,
    val cachedAtEpochMs: Long,
)
