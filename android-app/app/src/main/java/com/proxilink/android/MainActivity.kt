package com.proxilink.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxilink.android.ui.AppViewModel
import com.proxilink.android.ui.navigation.ProxiLinkNavHost
import com.proxilink.android.ui.theme.Palette
import com.proxilink.android.ui.theme.ProxiLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProxiLinkAppContent()
        }
    }
}

@Composable
private fun ProxiLinkAppContent() {
    val appViewModel: AppViewModel = hiltViewModel()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        appViewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                message = "${message.title}: ${message.body}",
                withDismissAction = true,
            )
        }
    }

    ProxiLinkTheme(themeMode = settings.themeMode) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Background),
        ) {
            ProxiLinkNavHost(
                appViewModel = appViewModel,
                snackbarHostState = snackbarHostState,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 100.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = Palette.SurfaceMuted,
                    contentColor = Palette.TextPrimary,
                    actionColor = Palette.Cyan,
                )
            }
        }
    }
}
