package com.proxilink.backend.common

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

object JsonMetadata {
    private val type = object : TypeReference<Map<String, Any?>>() {}

    fun write(mapper: ObjectMapper, metadata: Map<String, Any?>?): String? {
        if (metadata == null || metadata.isEmpty()) return null
        return mapper.writeValueAsString(metadata).take(4000)
    }

    fun read(mapper: ObjectMapper, raw: String?): Map<String, Any?>? {
        if (raw.isNullOrBlank()) return null
        return runCatching { mapper.readValue(raw, type) }.getOrNull()
    }
}
