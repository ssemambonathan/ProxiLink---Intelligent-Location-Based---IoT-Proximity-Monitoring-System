package com.proxilink.backend.device

import com.proxilink.backend.IntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DeviceApiIntegrationTest : IntegrationTestSupport() {

    @Test
    fun `registered device is normalised and starts offline`() {
        val user = registerUser()
        val mac = uniqueMac()
        val response = post(
            "/api/v1/devices",
            mapOf(
                "name" to "Dock Door Beacon",
                "deviceType" to "BLE Beacon",
                "category" to "INFRASTRUCTURE",
                "hardwareId" to "HW-DOCK-1",
                "bleMac" to mac.lowercase(),
                "serviceUuid" to "0000180F-0000-1000-8000-00805F9B34FB"
            ),
            user.accessToken
        )
        assertEquals(201, response.statusCode.value(), response.body)
        val device = json(response)
        assertEquals(mac.uppercase(), device["bleMac"].asText())
        assertEquals("0000180f-0000-1000-8000-00805f9b34fb", device["serviceUuid"].asText())
        assertEquals("OFFLINE", device["status"].asText())
        assertTrue(device["active"].asBoolean())
        assertEquals(user.id.toString(), device["ownerId"].asText())
        assertEquals(user.organization, device["organization"].asText())
    }

    @Test
    fun `device lists are scoped to the caller organization`() {
        val owner = registerUser()
        val outsider = registerUser()
        val device = createDevice(owner.accessToken, name = "Scoped Device")
        createDevice(outsider.accessToken, name = "Other Org Device")

        val ownerList = get("/api/v1/devices", owner.accessToken)
        assertEquals(200, ownerList.statusCode.value(), ownerList.body)
        val ownerItems = json(ownerList)["items"]
        assertEquals(1, ownerItems.size())
        assertEquals("Scoped Device", ownerItems[0]["name"].asText())

        val outsiderList = json(get("/api/v1/devices", outsider.accessToken))["items"]
        assertEquals(1, outsiderList.size())
        assertEquals("Other Org Device", outsiderList[0]["name"].asText())

        assertError(get("/api/v1/devices/${device["id"].asText()}", outsider.accessToken), 404, "DEVICE_NOT_FOUND")
    }

    @Test
    fun `duplicate hardware id is rejected within an organization but allowed across organizations`() {
        val user = registerUser()
        val hardwareId = uniqueHardwareId()
        createDevice(user.accessToken, hardwareId = hardwareId)

        val duplicate = post(
            "/api/v1/devices",
            mapOf("name" to "Duplicate", "deviceType" to "Sensor", "category" to "SENSOR", "hardwareId" to hardwareId),
            user.accessToken
        )
        assertError(duplicate, 409, "HARDWARE_ID_TAKEN")

        val otherOrgUser = registerUser()
        val sameHardwareElsewhere = post(
            "/api/v1/devices",
            mapOf("name" to "Same HW Other Org", "deviceType" to "Sensor", "category" to "SENSOR", "hardwareId" to hardwareId),
            otherOrgUser.accessToken
        )
        assertEquals(201, sameHardwareElsewhere.statusCode.value(), sameHardwareElsewhere.body)
    }

    @Test
    fun `invalid mac address is rejected with field violation`() {
        val user = registerUser()
        val response = post(
            "/api/v1/devices",
            mapOf("name" to "Bad MAC", "deviceType" to "Sensor", "category" to "SENSOR", "bleMac" to "AA:BB"),
            user.accessToken
        )
        assertError(response, 400, "VALIDATION_FAILED")
        assertTrue(response.body!!.contains("bleMac"))
    }

    @Test
    fun `identify recognises registered devices by mac regardless of case`() {
        val user = registerUser()
        val mac = uniqueMac()
        createDevice(user.accessToken, name = "Identify Me", bleMac = mac)

        val recognised = post(
            "/api/v1/devices/identify",
            mapOf("bleMac" to mac.lowercase(), "rssi" to -61),
            user.accessToken
        )
        assertEquals(200, recognised.statusCode.value(), recognised.body)
        assertTrue(json(recognised)["recognized"].asBoolean())
        assertEquals("Identify Me", json(recognised)["device"]["name"].asText())

        val unknown = post(
            "/api/v1/devices/identify",
            mapOf("bleMac" to uniqueMac()),
            user.accessToken
        )
        assertEquals(200, unknown.statusCode.value(), unknown.body)
        assertFalse(json(unknown)["recognized"].asBoolean())

        assertError(post("/api/v1/devices/identify", mapOf<String, Any>(), user.accessToken), 400, "IDENTIFIER_REQUIRED")
    }

    @Test
    fun `identify does not reveal devices owned by another organization`() {
        val owner = registerUser()
        val outsider = registerUser()
        val mac = uniqueMac()
        createDevice(owner.accessToken, name = "Private Beacon", bleMac = mac)

        val response = post("/api/v1/devices/identify", mapOf("bleMac" to mac), outsider.accessToken)
        assertEquals(200, response.statusCode.value(), response.body)
        assertFalse(json(response)["recognized"].asBoolean())
    }

    @Test
    fun `only the owner or an administrator may modify a device`() {
        val owner = registerUser()
        val colleague = registerUser(organization = owner.organization)
        val device = createDevice(owner.accessToken)

        val forbidden = patch(
            "/api/v1/devices/${device["id"].asText()}",
            mapOf("name" to "Renamed by colleague"),
            colleague.accessToken
        )
        assertError(forbidden, 403, "DEVICE_FORBIDDEN")

        val admin = registerUser(admin = true)
        val adminEdit = patch(
            "/api/v1/devices/${device["id"].asText()}",
            mapOf("name" to "Renamed by admin"),
            admin.accessToken
        )
        assertEquals(200, adminEdit.statusCode.value(), adminEdit.body)
        assertEquals("Renamed by admin", json(adminEdit)["name"].asText())
    }

    @Test
    fun `device can be updated deactivated reactivated and soft deleted`() {
        val user = registerUser()
        val device = createDevice(user.accessToken)
        val id = device["id"].asText()

        val updated = patch(
            "/api/v1/devices/$id",
            mapOf("name" to "Updated Beacon", "category" to "WEARABLE"),
            user.accessToken
        )
        assertEquals(200, updated.statusCode.value(), updated.body)
        assertEquals("Updated Beacon", json(updated)["name"].asText())
        assertEquals("WEARABLE", json(updated)["category"].asText())

        val deactivated = post("/api/v1/devices/$id/deactivate", null, user.accessToken)
        assertEquals(200, deactivated.statusCode.value(), deactivated.body)
        assertFalse(json(deactivated)["active"].asBoolean())
        assertEquals("INACTIVE", json(deactivated)["status"].asText())

        val activated = post("/api/v1/devices/$id/activate", null, user.accessToken)
        assertEquals(200, activated.statusCode.value(), activated.body)
        assertTrue(json(activated)["active"].asBoolean())

        assertEquals(204, delete("/api/v1/devices/$id", user.accessToken).statusCode.value())
        val deleted = get("/api/v1/devices/$id", user.accessToken)
        assertEquals(200, deleted.statusCode.value(), deleted.body)
        assertFalse(json(deleted)["active"].asBoolean())
        assertEquals("INACTIVE", json(deleted)["status"].asText())
    }

    @Test
    fun `filters and pagination apply to device lists`() {
        val user = registerUser()
        repeat(3) { index ->
            createDevice(
                user.accessToken,
                name = "Sensor $index",
                category = "SENSOR",
                hardwareId = uniqueHardwareId()
            )
        }
        createDevice(user.accessToken, name = "Gateway", category = "GATEWAY", hardwareId = uniqueHardwareId())

        val paged = json(get("/api/v1/devices?page=0&size=2", user.accessToken))
        assertEquals(4, paged["totalElements"].asInt())
        assertEquals(2, paged["totalPages"].asInt())
        assertEquals(2, paged["items"].size())

        val gateways = json(get("/api/v1/devices?category=GATEWAY", user.accessToken))
        assertEquals(1, gateways["items"].size())
        assertEquals("Gateway", gateways["items"][0]["name"].asText())

        val searched = json(get("/api/v1/devices?q=Sensor", user.accessToken))
        assertEquals(3, searched["totalElements"].asInt())
    }

    @Test
    fun `unknown device id returns not found`() {
        val user = registerUser()
        assertError(get("/api/v1/devices/${UUID.randomUUID()}", user.accessToken), 404, "DEVICE_NOT_FOUND")
    }
}
