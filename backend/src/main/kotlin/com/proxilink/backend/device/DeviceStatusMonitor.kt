package com.proxilink.backend.device

import com.proxilink.backend.admin.SettingsService
import com.proxilink.backend.event.ProximityLevel
import com.proxilink.backend.realtime.RealtimeService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class DeviceStatusMonitor(
    private val devices: DeviceRepository,
    private val history: DeviceStatusHistoryRepository,
    private val settings: SettingsService,
    private val realtime: RealtimeService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT60S")
    @Transactional
    fun markStaleDevicesOffline() {
        val timeoutSeconds = settings.current().offlineTimeoutSeconds.toLong()
        val now = Instant.now()
        val cutoff = now.minusSeconds(timeoutSeconds)
        val stale = devices.findByActiveTrueAndStatusInAndLastSeenAtBefore(
            listOf(DeviceStatus.ONLINE, DeviceStatus.NEARBY),
            cutoff
        )
        if (stale.isEmpty()) return

        stale.forEach { device ->
            device.status = DeviceStatus.OFFLINE
            device.lastProximity = ProximityLevel.OUT_OF_RANGE
            device.updatedAt = now
            history.save(
                DeviceStatusHistory(
                    deviceId = requireNotNull(device.id),
                    status = DeviceStatus.OFFLINE,
                    reason = "no advertisement for ${timeoutSeconds}s",
                    changedAt = now
                )
            )
            realtime.broadcast(
                type = "device.status",
                payload = mapOf(
                    "deviceId" to device.id,
                    "status" to DeviceStatus.OFFLINE,
                    "lastSeenAt" to device.lastSeenAt,
                    "lastRssi" to device.lastRssi
                ),
                organization = device.organization
            )
        }
        log.info("Marked {} device(s) offline after {}s without advertisements", stale.size, timeoutSeconds)
    }
}
