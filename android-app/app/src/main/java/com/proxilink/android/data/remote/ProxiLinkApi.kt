package com.proxilink.android.data.remote

import com.proxilink.android.data.remote.dto.CreateDeviceRequest
import com.proxilink.android.data.remote.dto.DashboardLiveDto
import com.proxilink.android.data.remote.dto.DashboardOverviewDto
import com.proxilink.android.data.remote.dto.DeviceDto
import com.proxilink.android.data.remote.dto.DevicePageDto
import com.proxilink.android.data.remote.dto.IdentifyRequest
import com.proxilink.android.data.remote.dto.IdentifyResponse
import com.proxilink.android.data.remote.dto.IngestResponse
import com.proxilink.android.data.remote.dto.LocationEventPageDto
import com.proxilink.android.data.remote.dto.LoginRequest
import com.proxilink.android.data.remote.dto.NotificationPageDto
import com.proxilink.android.data.remote.dto.ProximityEventPageDto
import com.proxilink.android.data.remote.dto.PublicConfigDto
import com.proxilink.android.data.remote.dto.RefreshRequest
import com.proxilink.android.data.remote.dto.RegisterRequest
import com.proxilink.android.data.remote.dto.SystemStatusDto
import com.proxilink.android.data.remote.dto.TokenResponse
import com.proxilink.android.data.remote.dto.UpdateDeviceRequest
import com.proxilink.android.data.remote.dto.UserDto
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ProxiLinkApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): TokenResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest)

    @GET("auth/me")
    suspend fun me(): UserDto

    @GET("devices")
    suspend fun devices(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
        @Query("q") query: String? = null,
        @Query("status") status: String? = null,
        @Query("category") category: String? = null,
        @Query("active") active: Boolean? = null,
    ): DevicePageDto

    @POST("devices")
    suspend fun createDevice(@Body body: CreateDeviceRequest): DeviceDto

    @GET("devices/{id}")
    suspend fun device(@Path("id") id: String): DeviceDto

    @PATCH("devices/{id}")
    suspend fun updateDevice(@Path("id") id: String, @Body body: UpdateDeviceRequest): DeviceDto

    @POST("devices/{id}/activate")
    suspend fun activateDevice(@Path("id") id: String): DeviceDto

    @POST("devices/{id}/deactivate")
    suspend fun deactivateDevice(@Path("id") id: String): DeviceDto

    @POST("devices/identify")
    suspend fun identify(@Body body: IdentifyRequest): IdentifyResponse

    @POST("events/proximity")
    suspend fun ingestProximity(@Body body: RequestBody): IngestResponse

    @POST("events/location")
    suspend fun ingestLocation(@Body body: RequestBody): IngestResponse

    @GET("events/proximity")
    suspend fun proximityEvents(
        @Query("deviceId") deviceId: String? = null,
        @Query("proximityLevel") proximityLevel: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): ProximityEventPageDto

    @GET("events/location")
    suspend fun locationEvents(
        @Query("deviceId") deviceId: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): LocationEventPageDto

    @GET("notifications")
    suspend fun notifications(
        @Query("unreadOnly") unreadOnly: Boolean = false,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): NotificationPageDto

    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String)

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead()

    @GET("dashboard/overview")
    suspend fun dashboardOverview(): DashboardOverviewDto

    @GET("dashboard/live")
    suspend fun dashboardLive(): DashboardLiveDto

    @GET("config/public")
    suspend fun publicConfig(): PublicConfigDto

    @GET("system/status")
    suspend fun systemStatus(): SystemStatusDto
}

/** Used by the OkHttp Authenticator on a client without the auth interceptor, avoiding recursion. */
interface AuthRefreshApi {
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse
}
