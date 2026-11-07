package com.proxilink.android.di

import com.proxilink.android.data.remote.ApiConfig
import com.proxilink.android.data.remote.AuthRefreshApi
import com.proxilink.android.data.remote.AuthInterceptor
import com.proxilink.android.data.remote.BaseUrlInterceptor
import com.proxilink.android.data.remote.ProxiLinkApi
import com.proxilink.android.data.remote.TokenAuthenticator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    @MainClient
    fun provideMainClient(
        baseUrlInterceptor: BaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor())
        .authenticator(authenticator)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @RefreshClient
    fun provideRefreshClient(baseUrlInterceptor: BaseUrlInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @WebSocketClient
    fun provideWebSocketClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(@MainClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://proxilink.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @RefreshRetrofit
    fun provideRefreshRetrofit(@RefreshClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://proxilink.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): ProxiLinkApi = retrofit.create(ProxiLinkApi::class.java)

    @Provides
    @Singleton
    fun provideRefreshApi(@RefreshRetrofit retrofit: Retrofit): AuthRefreshApi =
        retrofit.create(AuthRefreshApi::class.java)

    private fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // BASIC only: headers (including bearer tokens) must not reach logcat.
            level = if (com.proxilink.android.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
}
