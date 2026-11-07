package com.proxilink.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.local.TokenStore
import com.proxilink.android.data.repository.AuthRepository
import com.proxilink.android.data.repository.ConfigRepository
import com.proxilink.android.data.repository.LocationRepository
import com.proxilink.android.data.repository.NotificationRepository
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.data.repository.SyncScheduler
import com.proxilink.android.service.ConnectivityObserver
import com.proxilink.android.service.InAppNotificationCenter
import com.proxilink.android.service.InAppMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level state: theme, onboarding gate, session presence, realtime notifications and the
 * one-time startup work (public config, last known location, periodic outbox sync).
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    tokenStore: TokenStore,
    private val authRepository: AuthRepository,
    val settingsRepository: SettingsRepository,
    notificationRepository: NotificationRepository,
    private val inAppNotificationCenter: InAppNotificationCenter,
    syncScheduler: SyncScheduler,
    private val configRepository: ConfigRepository,
    private val locationRepository: LocationRepository,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    val settings = settingsRepository.settings

    /** `null` means the token store has not emitted yet, so the splash must wait. */
    val signedIn: StateFlow<Boolean?> = tokenStore.tokens
        .map { it != null }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            tokenStore.cached()?.let { true },
        )

    val user = authRepository.user
    val connectivity = connectivityObserver.state
    val messages = inAppNotificationCenter.messages

    init {
        authRepository.connectRealtimeIfSignedIn()
        notificationRepository.observeRealtime()
        syncScheduler.ensurePeriodicSync()
        viewModelScope.launch {
            configRepository.refresh()
            locationRepository.refreshLastKnown()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }
}
