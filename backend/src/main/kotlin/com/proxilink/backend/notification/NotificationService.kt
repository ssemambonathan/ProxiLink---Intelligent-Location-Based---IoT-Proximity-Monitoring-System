package com.proxilink.backend.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.common.JsonMetadata
import com.proxilink.backend.common.PageResponse
import com.proxilink.backend.realtime.RealtimeService
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable as SpringPageable

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var type: String = "",
    @Column(nullable = false)
    var title: String = "",
    @Column(nullable = false)
    var body: String = "",
    @Column(name = "is_read", nullable = false)
    var read: Boolean = false,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    var metadata: String? = null
)

data class NotificationDto(
    val id: UUID,
    val type: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: Instant,
    val metadata: Map<String, Any?>?
)

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: SpringPageable): org.springframework.data.domain.Page<Notification>
    fun findByUserIdAndReadFalseOrderByCreatedAtDesc(userId: UUID, pageable: SpringPageable): org.springframework.data.domain.Page<Notification>
    fun countByUserIdAndReadFalse(userId: UUID): Long
    fun findByIdAndUserId(id: UUID, userId: UUID): Notification?

    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    fun markAllRead(@Param("userId") userId: UUID): Int
}

@Service
class NotificationService(
    private val repository: NotificationRepository,
    private val realtime: RealtimeService,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun create(
        userId: UUID?,
        type: String,
        title: String,
        body: String,
        metadata: Map<String, Any?>? = null
    ): NotificationDto? {
        if (userId == null) return null
        val notification = repository.save(
            Notification(
                userId = userId,
                type = type,
                title = title.take(160),
                body = body.take(500),
                metadata = JsonMetadata.write(objectMapper, metadata)
            )
        )
        val dto = notification.toDto(objectMapper)
        realtime.broadcast("notification", dto, organization = null, userId = userId)
        return dto
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID, unreadOnly: Boolean, page: Int, size: Int): PageResponse<NotificationDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val result = if (unreadOnly) {
            repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
        } else {
            repository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        }
        return PageResponse(
            items = result.content.map { it.toDto(objectMapper) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional
    fun markRead(userId: UUID, id: UUID) {
        val notification = repository.findByIdAndUserId(id, userId)
            ?: throw com.proxilink.backend.common.ApiException.notFound(
                "NOTIFICATION_NOT_FOUND",
                "Notification not found"
            )
        notification.read = true
    }

    @Transactional
    fun markAllRead(userId: UUID): Int = repository.markAllRead(userId)
}

fun Notification.toDto(mapper: ObjectMapper): NotificationDto = NotificationDto(
    id = requireNotNull(id),
    type = type,
    title = title,
    body = body,
    read = read,
    createdAt = createdAt,
    metadata = JsonMetadata.read(mapper, metadata)
)
