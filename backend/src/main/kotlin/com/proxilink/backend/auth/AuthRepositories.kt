package com.proxilink.backend.auth

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmailIgnoreCase(email: String): Optional<User>
    fun existsByEmailIgnoreCase(email: String): Boolean

    @Query(
        """
        select u from User u
        where :q is null
           or lower(u.email) like lower(concat('%', :q, '%'))
           or lower(u.displayName) like lower(concat('%', :q, '%'))
        order by u.createdAt desc
        """
    )
    fun search(@Param("q") q: String?, pageable: Pageable): Page<User>

    @Modifying
    @Transactional
    @Query("update User u set u.lastLoginAt = :now, u.updatedAt = :now where u.id = :id")
    fun updateLastLogin(@Param("id") id: UUID, @Param("now") now: Instant)
}

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByName(name: String): Role?
}

interface UserSessionRepository : JpaRepository<UserSession, UUID> {
    fun findByRefreshTokenHash(hash: String): Optional<UserSession>
    fun countByExpiresAtBefore(cutoff: Instant): Long

    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :cutoff")
    fun deleteExpired(@Param("cutoff") cutoff: Instant): Int
}
