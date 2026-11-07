package com.proxilink.backend.device

import org.springframework.data.jpa.domain.Specification
import java.util.Locale

object DeviceSpecifications {
    fun build(
        organization: String?,
        query: String?,
        status: DeviceStatus?,
        category: DeviceCategory?,
        active: Boolean?
    ): Specification<Device> = Specification { root, _, cb ->
        val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
        organization?.let { predicates += cb.equal(root.get<String>("organization"), it) }
        status?.let { predicates += cb.equal(root.get<DeviceStatus>("status"), it) }
        category?.let { predicates += cb.equal(root.get<DeviceCategory>("category"), it) }
        active?.let { predicates += cb.equal(root.get<Boolean>("active"), it) }
        if (!query.isNullOrBlank()) {
            val like = "%${query.lowercase(Locale.ROOT)}%"
            predicates += cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("hardwareId")), like),
                cb.like(cb.lower(root.get("bleMac")), like)
            )
        }
        cb.and(*predicates.toTypedArray())
    }
}
