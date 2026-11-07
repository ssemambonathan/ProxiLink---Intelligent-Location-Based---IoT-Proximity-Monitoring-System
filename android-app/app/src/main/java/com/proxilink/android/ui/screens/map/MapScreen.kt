package com.proxilink.android.ui.screens.map

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.proxilink.android.R
import com.proxilink.android.data.remote.toUserMessage
import com.proxilink.android.data.repository.DeviceRepository
import com.proxilink.android.data.repository.LocationRepository
import com.proxilink.android.domain.model.RegisteredDevice
import com.proxilink.android.domain.model.UserFix
import com.proxilink.android.ui.design.MainBottomBarInset
import com.proxilink.android.ui.design.ProxiCard
import com.proxilink.android.ui.design.StatusChip
import com.proxilink.android.ui.design.accent
import com.proxilink.android.ui.design.coordinateLabel
import com.proxilink.android.ui.theme.Palette
import com.proxilink.android.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val locationRepository: LocationRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val devices: List<RegisteredDevice> = emptyList(),
        val fix: UserFix? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val locationPermission: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(locationPermission = PermissionUtils.hasLocationPermission(context)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepository.cachedDevices.collect { devices -> _state.update { it.copy(devices = devices) } }
        }
        viewModelScope.launch {
            locationRepository.current.collect { fix -> _state.update { it.copy(fix = fix) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    locationPermission = PermissionUtils.hasLocationPermission(context),
                )
            }
            val devices = deviceRepository.refresh()
                .onFailure { error -> _state.update { it.copy(error = error.toUserMessage()) } }
                .getOrNull()
            locationRepository.refreshLastKnown()
            _state.update {
                it.copy(
                    devices = devices ?: it.devices,
                    loading = false,
                    locationPermission = PermissionUtils.hasLocationPermission(context),
                )
            }
        }
    }

    fun refreshLocation() {
        viewModelScope.launch {
            _state.update { it.copy(locationPermission = PermissionUtils.hasLocationPermission(context)) }
            locationRepository.refreshLastKnown()
        }
    }

    fun locationPermissionDenied() {
        _state.update { it.copy(locationPermission = false, error = "Location permission is denied; only server-reported device locations are shown.") }
    }
}

@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it } || PermissionUtils.hasLocationPermission(context)) {
            viewModel.refreshLocation()
        } else {
            viewModel.locationPermissionDenied()
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            minZoomLevel = 3.0
            controller.setZoom(14.0)
            controller.setCenter(
                GeoPoint(
                    state.devices.firstOrNull { it.lastLocation != null }?.lastLocation?.latitude ?: 20.0,
                    state.devices.firstOrNull { it.lastLocation != null }?.lastLocation?.longitude ?: 0.0,
                ),
            )
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(state.devices, state.fix) {
        val markers = state.devices.mapNotNull { device -> device.lastLocation?.let { device to it } }
        if (markers.isNotEmpty() || state.fix != null) {
            val mapViewCurrent = mapView
            mapViewCurrent.overlays.removeAll { it is Marker }
            markers.forEach { (device, location) ->
                val marker = Marker(mapViewCurrent).apply {
                    position = GeoPoint(location.latitude, location.longitude)
                    icon = tintedPin(context, device.status.accent())
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = device.name
                    snippet = "${device.status.label} · ${coordinateLabel(location.latitude, location.longitude)}"
                    setOnMarkerClickListener { clicked, _ ->
                        clicked.showInfoWindow()
                        true
                    }
                }
                mapViewCurrent.overlays.add(marker)
            }
            state.fix?.let { fix ->
                val marker = Marker(mapViewCurrent).apply {
                    position = GeoPoint(fix.latitude, fix.longitude)
                    icon = tintedPin(context, Palette.Cyan)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "You"
                    snippet = "${coordinateLabel(fix.latitude, fix.longitude)} · ${fix.source}"
                }
                mapViewCurrent.overlays.add(marker)
            }
            mapViewCurrent.invalidate()
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Proximity map", style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary)
                    Text(
                        "${state.devices.count { it.lastLocation != null }} device locations · ${if (state.fix != null) "your fix available" else "no own fix"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextSecondary,
                    )
                }
                IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Palette.SurfaceHigh.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Palette.Cyan, modifier = Modifier.size(19.dp))
                    }
                }
            }
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                InfoRow(state.error!!, Palette.Coral)
            }
            if (!state.locationPermission) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.SurfaceHigh.copy(alpha = 0.94f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Palette.Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Location permission is off. Show your position?",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Enable",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Cyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { permissionLauncher.launch(PermissionUtils.locationPermissions()) }
                            .padding(4.dp),
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = MainBottomBarInset),
        ) {
            ProxiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Status colours", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendDot("Online", Palette.Mint)
                            LegendDot("Unregistered", Palette.Violet)
                            LegendDot("Offline", Palette.TextTertiary)
                            LegendDot("You", Palette.Cyan)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(text = "OSM", color = Palette.Cyan, icon = Icons.Filled.Map)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.Cyan.copy(alpha = 0.12f))
                            .clickable {
                                mapView.controller.setZoom(16.0)
                                state.fix?.let { fix ->
                                    mapView.controller.animateTo(GeoPoint(fix.latitude, fix.longitude))
                                } ?: permissionLauncher.launch(PermissionUtils.locationPermissions())
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "My location",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.Cyan,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "OpenStreetMap tiles require connectivity. GPS is most accurate outdoors (roughly 3–10 m); indoors accuracy degrades sharply. Device pins come from the last location reported to the server, not live triangulation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
    }
}

@Composable
private fun InfoRow(text: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.SurfaceHigh.copy(alpha = 0.94f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
    }
}

private fun tintedPin(context: Context, color: Color): Drawable? =
    ContextCompat.getDrawable(context, R.drawable.marker_pin)?.mutate()?.let { drawable ->
        DrawableCompat.wrap(drawable)
        DrawableCompat.setTint(drawable, color.toArgb())
        drawable
    }
