package com.proxilink.android.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.proxilink.android.data.local.TokenStore
import com.proxilink.android.data.repository.OutboxRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OutboxSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val outboxRepository: OutboxRepository,
    private val tokenStore: TokenStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (tokenStore.current() == null) {
            // Nothing can be accepted while signed out; keep rows for the next session.
            return Result.success()
        }
        return runCatching { outboxRepository.syncNow() }.fold(
            onSuccess = { summary -> if (summary.failed) Result.retry() else Result.success() },
            onFailure = { if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry() },
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}
