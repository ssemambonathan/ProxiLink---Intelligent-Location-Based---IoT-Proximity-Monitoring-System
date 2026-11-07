package com.proxilink.android.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebSocketClient
