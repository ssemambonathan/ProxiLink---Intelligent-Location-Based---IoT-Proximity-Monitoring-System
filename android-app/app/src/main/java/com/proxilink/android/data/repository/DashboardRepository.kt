package com.proxilink.android.data.repository

import com.proxilink.android.data.remote.ProxiLinkApi
import com.proxilink.android.data.remote.dto.SystemStatusDto
import com.proxilink.android.data.remote.parseInstantOrNull
import com.proxilink.android.data.remote.toDomain
import com.proxilink.android.domain.model.CategoryCount
import com.proxilink.android.domain.model.DashboardOverview
import com.proxilink.android.domain.model.DeviceCategory
import com.proxilink.android.domain.model.LiveSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(
    private val api: ProxiLinkApi,
) {
    suspend fun overview(): Result<DashboardOverview> = runCatching {
        val dto = api.dashboardOverview()
        DashboardOverview(
            totalDevices = dto.totalDevices,
            onlineDevices = dto.onlineDevices,
            offlineDevices = dto.offlineDevices,
            activeProximityEvents = dto.activeProximityEvents,
            recentLocationEvents24h = dto.recentLocationEvents24h,
            unreadNotifications = dto.unreadNotifications,
            discoveredToday = dto.discoveredToday,
            devicesByCategory = dto.devicesByCategory.map {
                CategoryCount(DeviceCategory.fromWire(it.category), it.count)
            },
        )
    }

    suspend fun live(): Result<LiveSnapshot> = runCatching {
        val dto = api.dashboardLive()
        LiveSnapshot(
            devices = dto.devices.map { it.toDomain() },
            recentProximity = dto.recentProximity.map { it.toDomain() },
            recentLocations = dto.recentLocations.map { it.toDomain() },
            serverTime = parseInstantOrNull(dto.serverTime),
        )
    }

    suspend fun systemStatus(): Result<SystemStatusDto> = runCatching { api.systemStatus() }
}
