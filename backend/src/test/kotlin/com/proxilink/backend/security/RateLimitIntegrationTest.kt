package com.proxilink.backend.security

import com.proxilink.backend.IntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(
    properties = [
        "app.rate-limit.login-per-minute=3",
        "app.rate-limit.general-per-minute=1000"
    ]
)
class RateLimitIntegrationTest : IntegrationTestSupport() {

    @Test
    fun `login attempts are rate limited per client`() {
        repeat(3) {
            assertError(login("nobody-${it}@proxilink.test", "ProxiLink!Test2026"), 401, "INVALID_CREDENTIALS")
        }
        val limited = login("nobody-final@proxilink.test", "ProxiLink!Test2026")
        assertEquals(429, limited.statusCode.value(), limited.body)
        assertEquals("RATE_LIMITED", json(limited)["code"].asText())
    }
}
