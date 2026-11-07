package com.proxilink.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
class AppProperties {
    var jwt = Jwt()
    var cors = Cors()
    var websocket = WebSocket()
    var rateLimit = RateLimit()
    var demo = Demo()
    var retention = Retention()
    var redis = Redis()
    var bootstrap = Bootstrap()

    class Jwt {
        var secret: String = ""
        var accessTokenTtlSeconds: Long = 1800
        var refreshTokenTtlDays: Long = 14
    }

    class Cors {
        var allowedOrigins: List<String> = listOf("http://localhost:5173")
    }

    class WebSocket {
        var allowedOrigins: List<String> = listOf("*")
    }

    class RateLimit {
        var generalPerMinute: Int = 240
        var loginPerMinute: Int = 10
    }

    class Demo {
        var seedEnabled: Boolean = true
        var adminEmail: String = "admin@proxilink.dev"
        var adminPassword: String = "ProxiLink!Demo2026"
        var userEmail: String = "demo@proxilink.dev"
        var userPassword: String = "ProxiLink!Demo2026"
    }

    class Retention {
        var cleanupEnabled: Boolean = true
    }

    class Redis {
        var enabled: Boolean = false
    }

    /**
     * One-time production administrator. Only used when the database has no users,
     * so a fresh container can be administered without a manual SQL step.
     */
    class Bootstrap {
        var enabled: Boolean = false
        var adminEmail: String = ""
        var adminPassword: String = ""
        var displayName: String = "ProxiLink Administrator"
        var organization: String = "ProxiLink"
    }
}
