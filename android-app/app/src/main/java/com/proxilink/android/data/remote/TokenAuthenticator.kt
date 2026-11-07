package com.proxilink.android.data.remote

import com.proxilink.android.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes the access token once per 401. A retried request that fails again is not retried a
 * third time, which prevents refresh loops against a broken backend.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refresher: SessionRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val usedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        // OkHttp invokes this on a worker thread; blocking is the documented pattern here.
        return runBlocking {
            val current = tokenStore.current() ?: return@runBlocking null
            when {
                current.accessToken != usedToken -> retryWith(response.request, current.accessToken)
                else -> refresher.refresh()?.let { retryWith(response.request, it.accessToken) }
            }
        }
    }

    private fun retryWith(request: Request, token: String): Request =
        request.newBuilder().header("Authorization", "Bearer $token").build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
