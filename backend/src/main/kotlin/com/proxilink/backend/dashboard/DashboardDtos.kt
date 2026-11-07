package com.proxilink.backend.dashboard

import com.proxilink.backend.device.DeviceDto
import com.proxilink.backend.event.LocationEventDto
import com.proxilink.backend.event.ProximityEventDto
import java.time.Instant
import java.util.UUID

data class CategoryCountDto(val category: String, val count: Long)

data class OverviewDto(
    val totalDevices: Long,
    val onlineDevices: Long,
    val offlineDevices: Long,
    val activeProximityEvents: Long,
    val recentLocationEvents24h: Long,
    val unreadNotifications: Long,
    val devicesByCategory: List<CategoryCountDto>,
    val discoveredToday: Long
)

data class LiveDto(
    val devices: List<DeviceDto>,
    val recentProximity: List<ProximityEventDto>,
    val recentLocations: List<LocationEventDto>,
    val serverTime: Instant
)

data class TrendPointDto(
    val date: String,
    val proximityEvents: Long,
    val locationEvents: Long,
    val uniqueDevices: Long
)

data class TrendsDto(val points: List<TrendPointDto>)

data class MostActiveDeviceDto(val deviceId: UUID, val name: String, val eventCount: Long)

data class LevelCountDto(val level: String, val count: Long)

data class OnlineRatioDto(val online: Long, val offline: Long)

data class DeviceAnalyticsDto(
    val mostActive: List<MostActiveDeviceDto>,
    val proximityDistribution: List<LevelCountDto>,
    val onlineRatio: OnlineRatioDto
)
