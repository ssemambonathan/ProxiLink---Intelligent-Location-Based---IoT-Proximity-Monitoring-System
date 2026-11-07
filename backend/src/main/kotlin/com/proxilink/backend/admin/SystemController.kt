package com.proxilink.backend.admin

import com.proxilink.backend.config.AppProperties
import com.proxilink.backend.realtime.RealtimeService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory

@RestController
@RequestMapping("/api/v1/system")
class SystemController(
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
    private val realtime: RealtimeService,
    private val props: AppProperties
) {

    @GetMapping("/status")
    fun status(): SystemStatusDto {
        val database = try {
            jdbcTemplate.queryForObject("select 1", Integer::class.java)
            "UP"
        } catch (ex: Exception) {
            "DOWN"
        }
        val redis = redisStatus()
        return SystemStatusDto(
            status = if (database == "UP") "UP" else "DEGRADED",
            database = database,
            redis = redis,
            websocketSessions = realtime.sessionCount(),
            uptimeSeconds = ManagementFactory.getRuntimeMXBean().uptime / 1000,
            version = "1.0.0"
        )
    }

    private fun redisStatus(): String {
        if (!props.redis.enabled) return "DISABLED"
        val template = redisTemplateProvider.getIfAvailable() ?: return "DISABLED"
        return try {
            template.execute { connection -> connection.ping() }
            "UP"
        } catch (ex: Exception) {
            "DOWN"
        }
    }
}
