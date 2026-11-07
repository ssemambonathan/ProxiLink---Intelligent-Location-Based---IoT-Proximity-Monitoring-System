package com.proxilink.backend.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.common.ApiException
import com.proxilink.backend.event.ProximityThresholds
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SettingsService(
    private val repository: SystemSettingsRepository,
    private val objectMapper: ObjectMapper,
    private val audit: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable("settings")
    @Transactional(readOnly = true)
    fun current(): SettingsDto {
        val values = repository.findAll().associate { it.key to it.value }
        val thresholds = values[KEY_THRESHOLDS]?.let {
            runCatching { objectMapper.readValue(it, ProximityThresholds::class.java) }
                .onFailure { error -> log.warn("Invalid stored proximity thresholds, using defaults", error) }
                .getOrDefault(ProximityThresholds())
        } ?: ProximityThresholds()
        return SettingsDto(
            proximityThresholds = thresholds,
            locationIntervalSeconds = intValue(values, KEY_LOCATION_INTERVAL, 30),
            scanIntervalSeconds = intValue(values, KEY_SCAN_INTERVAL, 10),
            offlineTimeoutSeconds = intValue(values, KEY_OFFLINE_TIMEOUT, 300),
            dataRetentionDays = intValue(values, KEY_RETENTION_DAYS, 90)
        )
    }

    @CacheEvict(cacheNames = ["settings"], allEntries = true)
    @Transactional
    fun update(request: UpdateSettingsRequest, actorId: UUID): SettingsDto {
        val updated = SettingsDto(
            proximityThresholds = request.proximityThresholds ?: current().proximityThresholds,
            locationIntervalSeconds = request.locationIntervalSeconds ?: current().locationIntervalSeconds,
            scanIntervalSeconds = request.scanIntervalSeconds ?: current().scanIntervalSeconds,
            offlineTimeoutSeconds = request.offlineTimeoutSeconds ?: current().offlineTimeoutSeconds,
            dataRetentionDays = request.dataRetentionDays ?: current().dataRetentionDays
        )
        try {
            updated.proximityThresholds.validate()
        } catch (ex: IllegalArgumentException) {
            throw ApiException.unprocessable("INVALID_THRESHOLDS", ex.message ?: "Invalid proximity thresholds")
        }

        val now = Instant.now()
        save(KEY_THRESHOLDS, objectMapper.writeValueAsString(updated.proximityThresholds), actorId, now)
        save(KEY_LOCATION_INTERVAL, updated.locationIntervalSeconds.toString(), actorId, now)
        save(KEY_SCAN_INTERVAL, updated.scanIntervalSeconds.toString(), actorId, now)
        save(KEY_OFFLINE_TIMEOUT, updated.offlineTimeoutSeconds.toString(), actorId, now)
        save(KEY_RETENTION_DAYS, updated.dataRetentionDays.toString(), actorId, now)

        audit.record(
            actorId = actorId,
            action = "SETTINGS_UPDATE",
            category = "ADMIN",
            entityType = "SETTINGS",
            details = "thresholds=${updated.proximityThresholds}, offlineTimeout=${updated.offlineTimeoutSeconds}s"
        )
        return updated
    }

    @Transactional(readOnly = true)
    fun publicConfig(): PublicConfigDto {
        val settings = current()
        return PublicConfigDto(
            proximityThresholds = settings.proximityThresholds,
            locationIntervalSeconds = settings.locationIntervalSeconds,
            scanIntervalSeconds = settings.scanIntervalSeconds
        )
    }

    private fun save(key: String, value: String, actorId: UUID, now: Instant) {
        val entity = repository.findById(key).orElse(SystemSettings(key = key))
        entity.value = value
        entity.updatedBy = actorId
        entity.updatedAt = now
        repository.save(entity)
    }

    private fun intValue(values: Map<String, String>, key: String, default: Int): Int =
        values[key]?.toIntOrNull()?.takeIf { it > 0 } ?: default

    companion object {
        private const val KEY_THRESHOLDS = "proximity.thresholds"
        private const val KEY_LOCATION_INTERVAL = "location.intervalSeconds"
        private const val KEY_SCAN_INTERVAL = "scan.intervalSeconds"
        private const val KEY_OFFLINE_TIMEOUT = "device.offlineTimeoutSeconds"
        private const val KEY_RETENTION_DAYS = "data.retentionDays"
    }
}
