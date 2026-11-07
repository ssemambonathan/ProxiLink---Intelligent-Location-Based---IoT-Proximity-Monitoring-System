package com.proxilink.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [OutboxEventEntity::class, CachedDeviceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ProxiLinkDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun deviceDao(): DeviceDao
}
