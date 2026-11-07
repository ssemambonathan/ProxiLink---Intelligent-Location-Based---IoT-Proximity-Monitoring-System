package com.proxilink.backend.bootstrap

import com.proxilink.backend.admin.AuditService
import com.proxilink.backend.admin.SettingsService
import com.proxilink.backend.auth.UserSessionRepository
import com.proxilink.backend.config.AppProperties
import com.proxilink.backend.event.LocationEventRepository
import com.proxilink.backend.event.ProximityEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class RetentionJob(
    private val props: AppProperties,
    private val settings: SettingsService,
    private val proximityEvents: ProximityEventRepository,
    private val locationEvents: LocationEventRepository,
    private val sessions: UserSessionRepository,
    private val audit: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    fun purgeExpiredData() {
        if (!props.retention.cleanupEnabled) return
        val retentionDays = settings.current().dataRetentionDays.toLong()
        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
        val removedProximity = proximityEvents.deleteOlderThan(cutoff)
        val removedLocations = locationEvents.deleteOlderThan(cutoff)
        val removedSessions = sessions.deleteExpired(Instant.now().minus(30, ChronoUnit.DAYS))

        if (removedProximity + removedLocations + removedSessions > 0) {
            log.info(
                "Retention purge: {} proximity, {} location events, {} sessions",
                removedProximity,
                removedLocations,
                removedSessions
            )
            audit.record(
                actorId = null,
                action = "RETENTION_PURGE",
                category = "SYSTEM",
                entityType = "EVENTS",
                details = "retentionDays=$retentionDays, proximity=$removedProximity, locations=$removedLocations, sessions=$removedSessions"
            )
        }
    }
}
