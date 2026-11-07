package com.proxilink.backend.dashboard

import com.proxilink.backend.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class DashboardController(private val dashboardService: DashboardService) {

    @GetMapping("/dashboard/overview")
    fun overview(@AuthenticationPrincipal principal: UserPrincipal): OverviewDto =
        dashboardService.overview(principal)

    @GetMapping("/dashboard/live")
    fun live(@AuthenticationPrincipal principal: UserPrincipal): LiveDto =
        dashboardService.live(principal)

    @GetMapping("/analytics/trends")
    fun trends(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "7") days: Int
    ): TrendsDto = dashboardService.trends(principal, days)

    @GetMapping("/analytics/devices")
    fun devices(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "7") days: Int
    ): DeviceAnalyticsDto = dashboardService.deviceAnalytics(principal, days)
}
