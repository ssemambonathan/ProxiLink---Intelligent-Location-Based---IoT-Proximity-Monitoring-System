package com.proxilink.android.di

import com.proxilink.android.data.local.RoomOutboxStore
import com.proxilink.android.data.remote.RemoteBatchSender
import com.proxilink.android.domain.outbox.OutboxSyncer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OutboxModule {
    @Provides
    @Singleton
    fun provideOutboxSyncer(store: RoomOutboxStore, sender: RemoteBatchSender): OutboxSyncer =
        OutboxSyncer(store = store, sender = sender)
}
