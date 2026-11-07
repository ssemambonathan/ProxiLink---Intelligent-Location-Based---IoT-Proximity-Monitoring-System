package com.proxilink.android.ui.screens.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.repository.SettingsRepository
import com.proxilink.android.ui.design.PrimaryButton
import com.proxilink.android.ui.design.ProxiBackground
import com.proxilink.android.ui.design.ScreenHorizontalPadding
import com.proxilink.android.ui.navigation.Routes
import com.proxilink.android.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun SplashScreen(
    signedIn: Boolean?,
    onboardingCompleted: Boolean,
    onReady: (String) -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Reverse),
        label = "splash-pulse",
    )

    LaunchedEffect(signedIn, onboardingCompleted) {
        if (signedIn == null) return@LaunchedEffect
        delay(1_000)
        val next = when {
            !onboardingCompleted -> Routes.ONBOARDING
            signedIn -> Routes.DASHBOARD
            else -> Routes.LOGIN
        }
        onReady(next)
    }

    ProxiBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size((168 * pulse).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Palette.Cyan.copy(alpha = 0.18f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(Palette.Cyan, Palette.CyanDeep))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = Palette.Background,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "ProxiLink",
                style = MaterialTheme.typography.displaySmall,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "BLE proximity intelligence",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(34.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Palette.Cyan,
                strokeWidth = 2.dp,
            )
        }
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    fun finish() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            _completed.value = true
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val bullets: List<String>,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Bluetooth,
        title = "Proximity, decoded",
        body = "Scan for nearby BLE devices and see a stable, smoothed proximity band instead of a jittery raw RSSI number.",
        bullets = listOf(
            "Median + EMA smoothing on every advertisement",
            "Bands: immediate, very close, nearby, far",
            "Distance estimates are always labelled as estimates",
        ),
    ),
    OnboardingPage(
        icon = Icons.Filled.Sync,
        title = "Offline-first by design",
        body = "Every proximity and location event is queued locally with a client UUID, then synced to the ProxiLink API when a network is available.",
        bullets = listOf(
            "Room outbox with exponential backoff",
            "Idempotent batches: replays are safe",
            "Rejected events stay visible with a reason",
        ),
    ),
    OnboardingPage(
        icon = Icons.Filled.Shield,
        title = "You stay in control",
        body = "Bluetooth and location permissions are requested only when needed. Location sharing and background monitoring are off by default.",
        bullets = listOf(
            "Explicit opt-in for location sharing",
            "Foreground service only when you enable it",
            "Every permission can be revoked in system settings",
        ),
    ),
)

@Composable
fun OnboardingScreen(
    onFinished: (String) -> Unit,
    signedIn: Boolean,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.lastIndex

    LaunchedEffect(completed) {
        if (completed) onFinished(if (signedIn) Routes.DASHBOARD else Routes.LOGIN)
    }

    ProxiBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ProxiLink",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Cyan,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::finish) {
                    Text("Skip", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val item = pages[page]
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(118.dp)
                            .clip(RoundedCornerShape(34.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Palette.Cyan.copy(alpha = 0.22f), Palette.Violet.copy(alpha = 0.16f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(54.dp))
                    }
                    Spacer(Modifier.height(34.dp))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Palette.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(26.dp))
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.bullets.forEach { bullet ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Palette.Cyan.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(13.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(bullet, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .width(if (pagerState.currentPage == index) 24.dp else 7.dp)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) Palette.Cyan else Palette.Outline,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = if (lastPage) "Get started" else "Continue",
                onClick = {
                    if (lastPage) {
                        viewModel.finish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}
