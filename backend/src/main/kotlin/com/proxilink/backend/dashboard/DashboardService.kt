package com.proxilink.backend.dashboard

import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.device.DeviceRepository
import com.proxilink.backend.device.DeviceStatus
import com.proxilink.backend.device.toDto
import com.proxilink.backend.event.LocationEventRepository
import com.proxilink.backend.event.ProximityEventRepository
import com.proxilink.backend.event.ProximityLevel
import com.proxilink.backend.event.toDto
import com.proxilink.backend.notification.NotificationRepository
import com.proxilink.backend.security.UserPrincipal
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class DashboardService(
    private val devices: DeviceRepository,
    private val proximityEvents: ProximityEventRepository,
    private val locationEvents: LocationEventRepository,
    private val notifications: NotificationRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional(readOnly = true)
    fun overview(principal: UserPrincipal): OverviewDto {
        val org = if (principal.isAdmin) null else principal.organization
        val now = Instant.now()
        val startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant()
        val onlineStatuses = listOf(DeviceStatus.ONLINE, DeviceStatus.NEARBY)
        val offlineStatuses = listOf(DeviceStatus.OFFLINE)

        val total = if (org == null) devices.countByActiveTrue() else devices.countByActiveTrueAndOrganization(org)
        val online = if (org == null) {
            devices.countByStatusIn(onlineStatuses)
        } else {
            devices.countByStatusInAndOrganization(onlineStatuses, org)
        }
        val offline = if (org == null) {
            devices.countByStatusIn(offlineStatuses)
        } else {
            devices.countByStatusInAndOrganization(offlineStatuses, org)
        }
        val discoveredToday = if (org == null) {
            devices.countByCreatedAtAfter(startOfDay)
        } else {
            devices.countByOrganizationAndCreatedAtAfter(org, startOfDay)
        }

        return OverviewDto(
            totalDevices = total,
            onlineDevices = online,
            offlineDevices = offline,
            activeProximityEvents = proximityEvents.countActive(
                org,
                now.minusSeconds(900),
                ProximityLevel.OUT_OF_RANGE
            ),
            recentLocationEvents24h = if (org == null) {
                locationEvents.countByRecordedAtAfter(now.minusSeconds(86_400))
            } else {
                locationEvents.countByOrganizationAndRecordedAtAfter(org, now.minusSeconds(86_400))
            },
            unreadNotifications = notifications.countByUserIdAndReadFalse(principal.id),
            devicesByCategory = devices.countByCategory(org).map { CategoryCountDto(it.category, it.count) },
            discoveredToday = discoveredToday
        )
    }

    @Transactional(readOnly = true)
    fun live(principal: UserPrincipal): LiveDto {
        val org = if (principal.isAdmin) null else principal.organization
        val page = PageRequest.of(0, 20)
        return LiveDto(
            devices = devices.findRecent(org, PageRequest.of(0, 12)).map { it.toDto(objectMapper) },
            recentProximity = proximityEvents.search(org, null, null, null, null, page)
                .content.map { it.toDto(objectMapper) },
            recentLocations = locationEvents.search(org, null, null, null, page)
                .content.map { it.toDto(objectMapper) },
            serverTime = Instant.now()
        )
    }

    @Transactional(readOnly = true)
    fun trends(principal: UserPrincipal, daysRequested: Int): TrendsDto {
        val days = daysRequested.coerceIn(1, 30)
        val org = if (principal.isAdmin) null else principal.organization
        val today = LocalDate.now(ZoneOffset.UTC)
        val firstDay = today.minusDays(days.toLong() - 1)
        val from = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant()

        val proximity = if (org == null) {
            proximityEvents.findByRecordedAtAfter(from)
        } else {
            proximityEvents.findByOrganizationAndRecordedAtAfter(org, from)
        }
        val locations = if (org == null) {
            locationEvents.findByRecordedAtAfter(from)
        } else {
            locationEvents.findByOrganizationAndRecordedAtAfter(org, from)
        }

        val proximityByDay = proximity.groupBy { it.recordedAt.atZone(ZoneOffset.UTC).toLocalDate() }
        val locationByDay = locations.groupBy { it.recordedAt.atZone(ZoneOffset.UTC).toLocalDate() }
        val devicesByDay = proximity.groupBy { it.recordedAt.atZone(ZoneOffset.UTC).toLocalDate() }

        val points = (0 until days).map { offset ->
            val day = firstDay.plusDays(offset.toLong())
            TrendPointDto(
                date = day.toString(),
                proximityEvents = proximityByDay[day]?.size?.toLong() ?: 0L,
                locationEvents = locationByDay[day]?.size?.toLong() ?: 0L,
                uniqueDevices = devicesByDay[day]?.mapNotNull { it.deviceId }?.distinct()?.size?.toLong() ?: 0L
            )
        }
        return TrendsDto(points)
    }

    @Transactional(readOnly = true)
    fun deviceAnalytics(principal: UserPrincipal, daysRequested: Int): DeviceAnalyticsDto {
        val days = daysRequested.coerceIn(1, 30)
        val org = if (principal.isAdmin) null else principal.organization
        val from = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)

        val active = proximityEvents.mostActiveDevices(org, from, PageRequest.of(0, 5))
        val deviceNames = if (active.isEmpty()) {
            emptyMap()
        } else {
            devices.findAllById(active.map { it.deviceId }).associate { it.id to it.name }
        }
        val mostActive = active.map {
            MostActiveDeviceDto(
                deviceId = it.deviceId,
                name = deviceNames[it.deviceId] ?: "Deleted device",
                eventCount = it.count
            )
        }

        val levels = proximityEvents.countByLevel(org, from).associate { it.level to it.count }
        val distribution = ProximityLevel.entries.map {
            LevelCountDto(it.name, levels[it] ?: 0L)
        }

        val onlineStatuses = listOf(DeviceStatus.ONLINE, DeviceStatus.NEARBY)
        val online = if (org == null) {
            devices.countByStatusIn(onlineStatuses)
        } else {
            devices.countByStatusInAndOrganization(onlineStatuses, org)
        }
        val offline = if (org == null) {
            devices.countByStatusIn(listOf(DeviceStatus.OFFLINE))
        } else {
            devices.countByStatusInAndOrganization(listOf(DeviceStatus.OFFLINE), org)
        }

        return DeviceAnalyticsDto(
            mostActive = mostActive,
            proximityDistribution = distribution,
            onlineRatio = OnlineRatioDto(online, offline)
        )
    }
}
