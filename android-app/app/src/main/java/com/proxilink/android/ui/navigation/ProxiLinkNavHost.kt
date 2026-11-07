package com.proxilink.android.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.proxilink.android.ui.AppViewModel
import com.proxilink.android.ui.screens.auth.LoginScreen
import com.proxilink.android.ui.screens.auth.RegisterScreen
import com.proxilink.android.ui.screens.dashboard.DashboardScreen
import com.proxilink.android.ui.screens.devices.DeviceDetailsScreen
import com.proxilink.android.ui.screens.devices.RegisterDeviceScreen
import com.proxilink.android.ui.screens.history.LocationHistoryScreen
import com.proxilink.android.ui.screens.history.ProximityHistoryScreen
import com.proxilink.android.ui.screens.map.MapScreen
import com.proxilink.android.ui.screens.monitoring.MonitoringScreen
import com.proxilink.android.ui.screens.network.NetworkScreen
import com.proxilink.android.ui.screens.notifications.NotificationsScreen
import com.proxilink.android.ui.screens.onboarding.OnboardingScreen
import com.proxilink.android.ui.screens.onboarding.SplashScreen
import com.proxilink.android.ui.screens.privacy.PrivacyScreen
import com.proxilink.android.ui.screens.profile.ProfileScreen
import com.proxilink.android.ui.screens.scan.NearbyScreen
import com.proxilink.android.ui.screens.scan.ScanScreen
import com.proxilink.android.ui.screens.settings.SettingsScreen
import com.proxilink.android.ui.theme.Palette

@Composable
fun ProxiLinkNavHost(
    appViewModel: AppViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val signedIn by appViewModel.signedIn.collectAsStateWithLifecycle()

    fun goTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goSignedIn() {
        navController.navigate(Routes.DASHBOARD) {
            popUpTo(Routes.LOGIN) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    signedIn = signedIn,
                    onboardingCompleted = settings.onboardingCompleted,
                    onReady = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    signedIn = signedIn == true,
                    onFinished = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    onSignedIn = { goSignedIn() },
                    onRegister = { navController.navigate(Routes.REGISTER) { launchSingleTop = true } },
                )
            }

            composable(Routes.REGISTER) {
                RegisterScreen(
                    onBack = { navController.popBackStack() },
                    onSignedIn = { goSignedIn() },
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onScan = { goTopLevel(Routes.SCAN) },
                    onMap = { goTopLevel(Routes.MAP) },
                    onNotifications = { goTopLevel(Routes.NOTIFICATIONS) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onMonitoring = { navController.navigate(Routes.MONITORING) },
                    onDevice = { id -> navController.navigate(Routes.deviceDetails(id)) },
                    onProximityHistory = { navController.navigate(Routes.PROXIMITY_HISTORY) },
                    onProfile = { goTopLevel(Routes.PROFILE) },
                )
            }

            composable(Routes.SCAN) {
                ScanScreen(
                    onOpenNearby = { navController.navigate(Routes.NEARBY) },
                    onDevice = { id -> navController.navigate(Routes.deviceDetails(id)) },
                    onRegisterDevice = { navController.navigate(Routes.REGISTER_DEVICE) },
                )
            }

            composable(Routes.NEARBY) {
                NearbyScreen(
                    onBack = { navController.popBackStack() },
                    onDevice = { id -> navController.navigate(Routes.deviceDetails(id)) },
                    onRegisterDevice = { navController.navigate(Routes.REGISTER_DEVICE) },
                    onOpenScan = { goTopLevel(Routes.SCAN) },
                )
            }

            composable(
                route = Routes.DEVICE_DETAILS,
                arguments = listOf(navArgument(Routes.DEVICE_ID_ARG) { type = NavType.StringType }),
            ) {
                DeviceDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onMap = { navController.navigate(Routes.MAP) },
                )
            }

            composable(Routes.MONITORING) {
                MonitoringScreen(
                    onBack = { navController.popBackStack() },
                    onNetwork = { navController.navigate(Routes.NETWORK) },
                )
            }

            composable(Routes.MAP) {
                MapScreen()
            }

            composable(Routes.REGISTER_DEVICE) {
                RegisterDeviceScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDevice = { id ->
                        navController.navigate(Routes.deviceDetails(id)) {
                            popUpTo(Routes.REGISTER_DEVICE) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.LOCATION_HISTORY) {
                LocationHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onMonitoring = { navController.navigate(Routes.MONITORING) },
                )
            }

            composable(Routes.PROXIMITY_HISTORY) {
                ProximityHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onScan = { goTopLevel(Routes.SCAN) },
                )
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = null,
                    onOpenScan = { goTopLevel(Routes.SCAN) },
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onMonitoring = { navController.navigate(Routes.MONITORING) },
                    onNetwork = { navController.navigate(Routes.NETWORK) },
                    onRegisterDevice = { navController.navigate(Routes.REGISTER_DEVICE) },
                    onProximityHistory = { navController.navigate(Routes.PROXIMITY_HISTORY) },
                    onLocationHistory = { navController.navigate(Routes.LOCATION_HISTORY) },
                    onNotifications = { goTopLevel(Routes.NOTIFICATIONS) },
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.DASHBOARD) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onMonitoring = { navController.navigate(Routes.MONITORING) },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onNetwork = { navController.navigate(Routes.NETWORK) },
                )
            }

            composable(Routes.PRIVACY) {
                PrivacyScreen(
                    onBack = { navController.popBackStack() },
                    onMonitoring = { navController.navigate(Routes.MONITORING) },
                )
            }

            composable(Routes.NETWORK) {
                NetworkScreen(
                    onBack = { navController.popBackStack() },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
        }

        if (currentRoute in BottomRoutes) {
            ProxiBottomBar(
                currentRoute = currentRoute,
                onNavigate = { route -> goTopLevel(route) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun ProxiBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Palette.SurfaceHigh.copy(alpha = 0.98f), Palette.Surface.copy(alpha = 0.98f)),
                    ),
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val tint by animateColorAsState(
                    targetValue = if (selected) Palette.Cyan else Palette.TextTertiary,
                    label = "bottom-tint",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Palette.Cyan.copy(alpha = 0.11f) else Color.Transparent)
                        .clickable { if (!selected) onNavigate(destination.route) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        destination.icon,
                        contentDescription = destination.label,
                        tint = tint,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}
