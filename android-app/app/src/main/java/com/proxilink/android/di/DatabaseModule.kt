package com.proxilink.android.di

import android.content.Context
import androidx.room.Room
import com.proxilink.android.data.ble.AndroidBleScanner
import com.proxilink.android.data.ble.BleScanner
import com.proxilink.android.data.local.ProxiLinkDatabase
import com.proxilink.android.data.local.RoomOutboxStore
import com.proxilink.android.data.local.SessionDataStore
import com.proxilink.android.data.local.TokenStore
import com.proxilink.android.domain.outbox.OutboxStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ProxiLinkDatabase =
        Room.databaseBuilder(context, ProxiLinkDatabase::class.java, "proxilink.db")
            .build()

    @Provides
    fun provideOutboxDao(database: ProxiLinkDatabase) = database.outboxDao()

    @Provides
    fun provideDeviceDao(database: ProxiLinkDatabase) = database.deviceDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: SessionDataStore): TokenStore

    @Binds
    @Singleton
    abstract fun bindOutboxStore(impl: RoomOutboxStore): OutboxStore

    @Binds
    @Singleton
    abstract fun bindBleScanner(impl: AndroidBleScanner): BleScanner
}
