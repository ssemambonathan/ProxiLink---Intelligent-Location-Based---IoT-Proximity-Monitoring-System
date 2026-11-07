package com.proxilink.android.ui.screens.auth

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxilink.android.data.remote.toUserMessage
import com.proxilink.android.data.repository.AuthRepository
import com.proxilink.android.data.repository.SessionState
import com.proxilink.android.ui.design.AppTextField
import com.proxilink.android.ui.design.ErrorBanner
import com.proxilink.android.ui.design.InfoBanner
import com.proxilink.android.ui.design.PrimaryButton
import com.proxilink.android.ui.design.ProxiBackground
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.ScreenHorizontalPadding
import com.proxilink.android.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val email: String = "",
        val password: String = "",
        val displayName: String = "",
        val organization: String = "",
        val confirmPassword: String = "",
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val sessionState: StateFlow<SessionState> = authRepository.sessionState

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onDisplayName(value: String) = _state.update { it.copy(displayName = value, error = null) }
    fun onOrganization(value: String) = _state.update { it.copy(organization = value, error = null) }
    fun onConfirmPassword(value: String) = _state.update { it.copy(confirmPassword = value, error = null) }

    fun login() {
        val current = _state.value
        if (current.loading) return
        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Email and password are required.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.login(current.email, current.password)
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.toUserMessage()) }
                }
                .onSuccess {
                    _state.update { it.copy(loading = false, password = "") }
                }
        }
    }

    fun register() {
        val current = _state.value
        if (current.loading) return
        val validation = validateRegistration(current)
        if (validation != null) {
            _state.update { it.copy(error = validation) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.register(
                email = current.email,
                password = current.password,
                displayName = current.displayName,
                organization = current.organization,
            ).onFailure { error ->
                _state.update { it.copy(loading = false, error = error.toUserMessage()) }
            }.onSuccess {
                _state.update { it.copy(loading = false, password = "", confirmPassword = "") }
            }
        }
    }

    private fun validateRegistration(state: UiState): String? = when {
        !state.email.contains("@") || !state.email.contains(".") -> "Enter a valid email address."
        state.displayName.trim().length < 2 -> "Display name must be at least 2 characters."
        state.password.length < 8 -> "Password must be at least 8 characters."
        state.password != state.confirmPassword -> "Passwords do not match."
        else -> null
    }
}

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        if (session == SessionState.SIGNED_IN) onSignedIn()
    }

    AuthLayout(
        title = "Welcome back",
        subtitle = "Sign in to monitor nearby ProxiLink devices and keep your proximity history in sync.",
    ) {
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = "Email",
            placeholder = "you@company.com",
            keyboardType = KeyboardType.Email,
            leadingIcon = Icons.Filled.Email,
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = "Password",
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible },
            leadingIcon = Icons.Filled.Lock,
        )
        Spacer(Modifier.height(16.dp))
        if (state.error != null) {
            ErrorBanner(message = state.error!!)
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton(
            text = "Sign in",
            onClick = viewModel::login,
            loading = state.loading,
            enabled = state.email.isNotBlank() && state.password.isNotBlank(),
        )
        Spacer(Modifier.height(18.dp))
        ProxiCard(accent = Palette.Violet) {
            Text(
                "Demo environment",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Violet,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "admin@proxilink.dev",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "ProxiLink!Demo2026",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Also available: demo@proxilink.dev with the same password.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New here?", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
            Spacer(Modifier.width(6.dp))
            Text(
                "Create an account",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Cyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onRegister)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        if (session == SessionState.SIGNED_IN) onSignedIn()
    }

    AuthLayout(
        title = "Create account",
        subtitle = "Join your organization's proximity network. You can register hardware after signing in.",
        onBack = onBack,
    ) {
        AppTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayName,
            label = "Display name",
            leadingIcon = Icons.Filled.Person,
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = "Email",
            keyboardType = KeyboardType.Email,
            leadingIcon = Icons.Filled.Email,
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = state.organization,
            onValueChange = viewModel::onOrganization,
            label = "Organization (optional)",
            leadingIcon = Icons.Filled.Business,
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = "Password (min. 8 characters)",
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible },
            leadingIcon = Icons.Filled.Lock,
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPassword,
            label = "Confirm password",
            isPassword = true,
            passwordVisible = passwordVisible,
            leadingIcon = Icons.Filled.Lock,
        )
        Spacer(Modifier.height(16.dp))
        if (state.error != null) {
            ErrorBanner(message = state.error!!)
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton(
            text = "Create account",
            onClick = viewModel::register,
            loading = state.loading,
        )
        Spacer(Modifier.height(14.dp))
        InfoBanner("By registering you agree to keep BLE scanning and location sharing under your control.")
    }
}

@Composable
private fun AuthLayout(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ProxiBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
        ) {
            if (onBack != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Palette.SurfaceHigh)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Palette.TextPrimary, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.height(22.dp))
            } else {
                Spacer(Modifier.height(24.dp))
            }
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(listOf(Palette.Cyan, Palette.CyanDeep)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("P", style = MaterialTheme.typography.headlineMedium, color = Palette.Background, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Palette.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.height(36.dp))
        }
    }
}
