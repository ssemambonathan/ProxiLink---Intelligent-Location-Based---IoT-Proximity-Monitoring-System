package com.proxilink.backend.device

import com.proxilink.backend.common.PageResponse
import com.proxilink.backend.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/devices")
class DeviceController(private val deviceService: DeviceService) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: DeviceStatus?,
        @RequestParam(required = false) category: DeviceCategory?,
        @RequestParam(required = false) organization: String?,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<DeviceDto> =
        deviceService.list(principal, q, status, category, organization, active, page, size)

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ): DeviceDto = deviceService.get(principal, id)

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody @Valid request: CreateDeviceRequest
    ): ResponseEntity<DeviceDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(deviceService.create(principal, request))

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateDeviceRequest
    ): DeviceDto = deviceService.update(principal, id, request)

    @PostMapping("/{id}/activate")
    fun activate(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ): DeviceDto = deviceService.setActive(principal, id, true)

    @PostMapping("/{id}/deactivate")
    fun deactivate(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ): DeviceDto = deviceService.setActive(principal, id, false)

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        deviceService.delete(principal, id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/identify")
    fun identify(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody @Valid request: IdentifyDeviceRequest
    ): IdentifyDeviceResponse = deviceService.identify(principal, request)
}
