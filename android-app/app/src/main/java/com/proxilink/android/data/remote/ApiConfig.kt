package com.proxilink.android.data.remote

import com.proxilink.android.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mutable base URL so the Settings screen can point the app at another backend without rebuilding
 * Retrofit. Interceptors read the volatile value on every call.
 */
@Singleton
class ApiConfig @Inject constructor() {

    @Volatile
    var baseUrl: String = BuildConfig.DEFAULT_API_BASE_URL

    fun normalizedBaseUrl(): HttpUrl {
        val trimmed = baseUrl.trim().ifBlank { BuildConfig.DEFAULT_API_BASE_URL }
        val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        return withSlash.toHttpUrlOrNull() ?: BuildConfig.DEFAULT_API_BASE_URL.toHttpUrl()
    }

    fun isValidCandidate(candidate: String): Boolean {
        val url = candidate.trim().toHttpUrlOrNull() ?: return false
        return url.scheme == "http" || url.scheme == "https"
    }

    /** The WebSocket endpoint lives at the server root: ws(s)://host:port/ws?token=... */
    fun webSocketUrl(token: String): String {
        val base = normalizedBaseUrl()
        val scheme = if (base.isHttps) "wss" else "ws"
        return "$scheme://${base.host}:${base.port}/ws?token=$token"
    }
}
