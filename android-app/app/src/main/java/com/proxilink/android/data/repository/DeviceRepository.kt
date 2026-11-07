package com.proxilink.android.data.repository

import com.proxilink.android.data.local.DeviceDao
import com.proxilink.android.data.local.toDomain
import com.proxilink.android.data.local.toEntity
import com.proxilink.android.data.remote.ProxiLinkApi
import com.proxilink.android.data.remote.dto.CreateDeviceRequest
import com.proxilink.android.data.remote.dto.IdentifyRequest
import com.proxilink.android.data.remote.toDomain
import com.proxilink.android.domain.model.DeviceCategory
import com.proxilink.android.domain.model.RegisteredDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val api: ProxiLinkApi,
    private val deviceDao: DeviceDao,
) {
    val cachedDevices: Flow<List<RegisteredDevice>> =
        deviceDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    val cachedCount: Flow<Int> = cachedDevices.map { it.size }

    suspend fun refresh(query: String? = null): Result<List<RegisteredDevice>> = runCatching {
        val page = api.devices(query = query?.takeIf { it.isNotBlank() }, size = 100)
        val devices = page.items.map { it.toDomain() }
        deviceDao.upsertAll(devices.map { it.toEntity(Instant.now()) })
        if (query.isNullOrBlank() && devices.isNotEmpty()) {
            deviceDao.deleteMissing(devices.map { it.id })
        }
        devices
    }

    suspend fun get(id: String): Result<RegisteredDevice> = runCatching {
        api.device(id).toDomain().also { cache(it) }
    }

    suspend fun cachedDevice(id: String): RegisteredDevice? = deviceDao.byId(id)?.toDomain()

    suspend fun register(
        name: String,
        deviceType: String,
        category: DeviceCategory,
        hardwareId: String,
        bleMac: String?,
        serviceUuid: String?,
        metadata: Map<String, String>,
    ): Result<RegisteredDevice> = runCatching {
        api.createDevice(
            CreateDeviceRequest(
                name = name.trim(),
                deviceType = deviceType.trim(),
                category = category.name,
                hardwareId = hardwareId.trim(),
                bleMac = bleMac?.trim()?.takeIf { it.isNotBlank() },
                serviceUuid = serviceUuid?.trim()?.takeIf { it.isNotBlank() },
                metadata = metadata,
            ),
        ).toDomain().also { cache(it) }
    }

    suspend fun setActive(id: String, active: Boolean): Result<RegisteredDevice> = runCatching {
        val dto = if (active) api.activateDevice(id) else api.deactivateDevice(id)
        dto.toDomain().also { cache(it) }
    }

    suspend fun identify(
        bleMac: String?,
        serviceUuid: String?,
        hardwareId: String? = null,
    ): Result<RegisteredDevice?> {
        if (bleMac.isNullOrBlank() && serviceUuid.isNullOrBlank() && hardwareId.isNullOrBlank()) {
            return Result.success(null)
        }
        return runCatching {
            val response = api.identify(
                IdentifyRequest(hardwareId = hardwareId, bleMac = bleMac, serviceUuid = serviceUuid),
            )
            if (response.recognized) response.device?.toDomain()?.also { cache(it) } else null
        }
    }

    private suspend fun cache(device: RegisteredDevice) {
        deviceDao.upsertAll(listOf(device.toEntity(Instant.now())))
    }
}
