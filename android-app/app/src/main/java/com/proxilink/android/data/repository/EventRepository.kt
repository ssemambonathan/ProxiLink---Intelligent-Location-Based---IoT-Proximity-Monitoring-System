package com.proxilink.android.data.repository

import com.proxilink.android.data.remote.ProxiLinkApi
import com.proxilink.android.data.remote.toDomain
import com.proxilink.android.domain.model.LocationEventRecord
import com.proxilink.android.domain.model.ProximityEvent
import com.proxilink.android.domain.proximity.ProximityLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val api: ProxiLinkApi,
) {
    suspend fun proximityEvents(
        deviceId: String? = null,
        level: ProximityLevel? = null,
        limit: Int = 60,
    ): Result<List<ProximityEvent>> = runCatching {
        api.proximityEvents(
            deviceId = deviceId,
            proximityLevel = level?.name,
            size = limit,
        ).items.map { it.toDomain() }
    }

    suspend fun locationEvents(
        deviceId: String? = null,
        limit: Int = 60,
    ): Result<List<LocationEventRecord>> = runCatching {
        api.locationEvents(deviceId = deviceId, size = limit).items.map { it.toDomain() }
    }
}
