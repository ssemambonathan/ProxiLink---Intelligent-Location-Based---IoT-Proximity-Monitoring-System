package com.proxilink.backend.admin

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import java.util.UUID

/**
 * Writes audit rows in their own transaction so failed requests (e.g. rejected logins) leave a trail.
 * When called inside a transaction, the write is deferred until after commit to avoid foreign-key
 * races with brand-new users.
 */
@Component
class AuditWriter(private val repository: AuditLogRepository) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun write(
        actorId: UUID?,
        action: String,
        category: String,
        entityType: String?,
        entityId: String?,
        ip: String?,
        userAgent: String?,
        details: String?
    ) {
        repository.save(
            AuditLog(
                actorId = actorId,
                action = action,
                category = category,
                entityType = entityType,
                entityId = entityId,
                ip = ip,
                userAgent = userAgent,
                details = details,
                createdAt = Instant.now()
            )
        )
    }
}

@Service
class AuditService(private val writer: AuditWriter) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun record(
        actorId: UUID?,
        action: String,
        category: String,
        entityType: String? = null,
        entityId: String? = null,
        details: String? = null
    ) {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val ip = request?.let { clientIp(it) }
        val userAgent = request?.getHeader("User-Agent")?.take(255)
        val safeDetails = details?.take(2000)

        val write = {
            runCatching {
                writer.write(actorId, action, category, entityType, entityId, ip, userAgent, safeDetails)
            }.onFailure { log.warn("Failed to persist audit event {}", action, it) }
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        write()
                    }
                }
            )
        } else {
            write()
        }
    }

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr
}
