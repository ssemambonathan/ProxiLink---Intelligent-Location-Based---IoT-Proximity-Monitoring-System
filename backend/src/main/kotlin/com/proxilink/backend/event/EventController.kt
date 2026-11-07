package com.proxilink.backend.event

import com.proxilink.backend.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/events")
class EventController(private val eventService: EventService) {

    @PostMapping("/proximity")
    fun ingestProximity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody @Valid request: ProximityBatchRequest
    ): BatchIngestResponse = eventService.ingestProximity(principal, request)

    @PostMapping("/location")
    fun ingestLocation(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody @Valid request: LocationBatchRequest
    ): BatchIngestResponse = eventService.ingestLocation(principal, request)

    @GetMapping("/proximity")
    fun searchProximity(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) deviceId: UUID?,
        @RequestParam(required = false) proximityLevel: ProximityLevel?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): com.proxilink.backend.common.PageResponse<ProximityEventDto> =
        eventService.searchProximity(principal, deviceId, proximityLevel, from, to, page, size)

    @GetMapping("/location")
    fun searchLocation(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) deviceId: UUID?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): com.proxilink.backend.common.PageResponse<LocationEventDto> =
        eventService.searchLocation(principal, deviceId, from, to, page, size)
}
