package com.proxilink.android.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Retrofit is built against a placeholder host; every request is re-rooted at the configured API. */
@Singleton
class BaseUrlInterceptor @Inject constructor(private val config: ApiConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val relative = request.url.encodedPath.trimStart('/') +
            (request.url.encodedQuery?.let { "?$it" } ?: "")
        val newUrl = config.normalizedBaseUrl().resolve(relative) ?: request.url
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
