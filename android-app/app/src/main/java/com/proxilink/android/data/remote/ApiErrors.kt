package com.proxilink.android.data.remote

import com.proxilink.android.data.remote.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private val errorJson = Json { ignoreUnknownKeys = true }

fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val apiError = runCatching {
            response()?.errorBody()?.string()?.let {
                errorJson.decodeFromString(ApiErrorDto.serializer(), it)
            }
        }.getOrNull()
        when {
            apiError?.message != null && apiError.violations.isNullOrEmpty() ->
                "${apiError.message} (HTTP ${code()})"
            !apiError?.violations.isNullOrEmpty() ->
                apiError.violations.entries.joinToString("; ") { "${it.key}: ${it.value}" }
            code() == 401 -> "Session expired. Please sign in again."
            code() == 403 -> "You do not have permission for this action."
            code() == 429 -> "Too many requests. Try again shortly."
            code() >= 500 -> "Server error (HTTP ${code()}). Try again later."
            else -> "Request failed (HTTP ${code()})."
        }
    }
    is SocketTimeoutException -> "The server did not answer in time."
    is UnknownHostException -> "Cannot resolve the server address."
    is IOException -> "No connection to the ProxiLink API."
    else -> message ?: "Unexpected error."
}
