package com.arwora.ui.screens

import android.location.Geocoder
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.service.MockLocationService
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.ui.components.GooglePlayCompactLinearProgress
import com.arwora.utils.FakeGpsManager
import com.arwora.utils.LocationPreset
import com.arwora.utils.MapTileManager
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.util.Locale

/**
 * Map style choices available in the Map Type Switcher bottom sheet.
 */
enum class AppMapType(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String,
    val previewBg: Color
) {
    DEFAULT(
        title = "Default",
        subtitle = "Standard vector street map",
        icon = Icons.Default.Map,
        badge = "Vector",
        previewBg = Color(0xFFE2E8F0)
    ),
    SATELLITE(
        title = "Satellite Hybrid",
        subtitle = "High-res aerial satellite imagery",
        icon = Icons.Default.Satellite,
        badge = "Aerial",
        previewBg = Color(0xFF1E293B)
    ),
    TERRAIN(
        title = "Terrain",
        subtitle = "Topographical elevations & contours",
        icon = Icons.Default.Terrain,
        badge = "Topo",
        previewBg = Color(0xFFD1FAE5)
    ),
    DARK(
        title = "Dark Cyber",
        subtitle = "High contrast dark night mode",
        icon = Icons.Default.DarkMode,
        badge = "Night",
        previewBg = Color(0xFF0F172A)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeGpsMapScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Initialize Fake GPS manager and osmdroid config with high-performance cache and custom User-Agent
    LaunchedEffect(Unit) {
        FakeGpsManager.init(context)
        MapTileManager.configureOsmdroid(context)
    }

    val isMockActive by FakeGpsManager.isMockingActive.collectAsStateWithLifecycle()
    val savedLat by FakeGpsManager.currentLat.collectAsStateWithLifecycle()
    val savedLng by FakeGpsManager.currentLng.collectAsStateWithLifecycle()
    val mockUpdateCount by FakeGpsManager.updateCounter.collectAsStateWithLifecycle()
    val mockError by FakeGpsManager.lastError.collectAsStateWithLifecycle()

    var centerLat by remember { mutableStateOf(savedLat) }
    var centerLng by remember { mutableStateOf(savedLng) }
    var locationName by remember { mutableStateOf(FakeGpsManager.getSavedPresetName(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showFineTuneControls by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showMapTypeSheet by remember { mutableStateOf(false) }
    var currentMapType by remember { mutableStateOf(AppMapType.DEFAULT) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    fun getTileSourceForType(type: AppMapType): ITileSource {
        return when (type) {
            AppMapType.DEFAULT -> MapTileManager.StandardOsmTileSource
            AppMapType.SATELLITE -> MapTileManager.GoogleHybridSatelliteTileSource
            AppMapType.TERRAIN -> MapTileManager.OpenTopoTileSource
            AppMapType.DARK -> MapTileManager.CartoDarkTileSource
        }
    }

    fun applyMapType(type: AppMapType) {
        SoundEffectManager.playClick()
        currentMapType = type
        mapViewRef?.let { map ->
            map.setTileSource(getTileSourceForType(type))
            map.invalidate()
        }
        showMapTypeSheet = false
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    fun reverseGeocode(lat: Double, lng: Double) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(lat, lng, 1) { addresses ->
                            if (addresses.isNotEmpty()) {
                                val addr = addresses[0]
                                val title = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName ?: "Custom Point"
                                val sub = addr.countryName ?: ""
                                locationName = if (sub.isNotEmpty() && !title.contains(sub)) "$title, $sub" else title
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lng, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            val title = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName ?: "Custom Point"
                            val sub = addr.countryName ?: ""
                            withContext(Dispatchers.Main) {
                                locationName = if (sub.isNotEmpty() && !title.contains(sub)) "$title, $sub" else title
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore network errors in geocoding
            }
        }
    }

    fun searchLocation(query: String) {
        if (query.isBlank()) return
        isSearching = true
        focusManager.clearFocus()
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val match = FakeGpsManager.popularPresets.find {
                    it.name.contains(query, ignoreCase = true) || it.country.contains(query, ignoreCase = true)
                }
                if (match != null) {
                    withContext(Dispatchers.Main) {
                        centerLat = match.latitude
                        centerLng = match.longitude
                        locationName = "${match.name}, ${match.country}"
                        mapViewRef?.controller?.animateTo(GeoPoint(match.latitude, match.longitude), 16.0, 1000L)
                        isSearching = false
                    }
                    return@launch
                }

                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocationName(query, 1)
                    if (!results.isNullOrEmpty()) {
                        val addr = results[0]
                        withContext(Dispatchers.Main) {
                            centerLat = addr.latitude
                            centerLng = addr.longitude
                            val title = addr.locality ?: addr.featureName ?: query
                            val country = addr.countryName ?: ""
                            locationName = if (country.isNotEmpty()) "$title, $country" else title
                            mapViewRef?.controller?.animateTo(GeoPoint(addr.latitude, addr.longitude), 16.0, 1000L)
                            isSearching = false
                        }
                        return@launch
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Location not found for: '$query'", Toast.LENGTH_SHORT).show()
                    isSearching = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Search error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    isSearching = false
                }
            }
        }
    }

    fun moveToPreset(preset: LocationPreset) {
        SoundEffectManager.playClick()
        centerLat = preset.latitude
        centerLng = preset.longitude
        locationName = "${preset.name}, ${preset.country}"
        mapViewRef?.controller?.animateTo(GeoPoint(preset.latitude, preset.longitude), 16.0, 800L)
        FakeGpsManager.saveCoordinates(context, preset.latitude, preset.longitude, preset.name)
        if (isMockActive) {
            MockLocationService.updateLocation(context, preset.latitude, preset.longitude, preset.name)
        }
    }

    fun nudge(deltaLat: Double, deltaLng: Double) {
        SoundEffectManager.playClick()
        val newLat = (centerLat + deltaLat).coerceIn(-90.0, 90.0)
        val newLng = (centerLng + deltaLng).coerceIn(-180.0, 180.0)
        centerLat = newLat
        centerLng = newLng
        mapViewRef?.controller?.animateTo(GeoPoint(newLat, newLng))
        if (isMockActive) {
            FakeGpsManager.saveCoordinates(context, newLat, newLng, locationName)
            MockLocationService.updateLocation(context, newLat, newLng, locationName)
        }
    }

    val activeGreen = Color(0xFF10B981)
    val stopRed = Color(0xFFEF4444)
    val chipAccent = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Fake GPS Map Picker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ArworaPillBadge(
                            text = if (isMockActive) {
                                "Live OS Injection: Active ($mockUpdateCount fixes)"
                            } else {
                                "Drag map to target coordinates"
                            },
                            tint = if (isMockActive) activeGreen else MaterialTheme.colorScheme.primary,
                            showDot = isMockActive,
                            modifier = Modifier.testTag("map_picker_status_pill")
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("location_history") }) {
                        Icon(Icons.Default.History, contentDescription = "Location History")
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Mock Setup Help")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. FULL SCREEN OSMDROID MAP VIEW
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(getTileSourceForType(currentMapType))
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(15.0)
                        controller.setCenter(GeoPoint(centerLat, centerLng))
                        minZoomLevel = 1.0
                        maxZoomLevel = 20.0

                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                val center = mapCenter
                                if (center != null) {
                                    centerLat = center.latitude
                                    centerLng = center.longitude
                                    reverseGeocode(center.latitude, center.longitude)
                                }
                                return true
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                val center = mapCenter
                                if (center != null) {
                                    centerLat = center.latitude
                                    centerLng = center.longitude
                                }
                                return true
                            }
                        })
                        mapViewRef = this
                    }
                },
                update = { view ->
                    mapViewRef = view
                }
            )

            // 2. CENTER TARGET MARKER PIN
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = (-24).dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMockActive) Color(0xFF065F46) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                        shadowElevation = 6.dp,
                        border = BorderStroke(
                            1.dp,
                            if (isMockActive) activeGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isMockActive) activeGreen else Color(0xFFF59E0B), CircleShape)
                            )
                            Text(
                                text = locationName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMockActive) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(contentAlignment = Alignment.Center) {
                        if (isMockActive) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .scale(pulseScale)
                                    .background(activeGreen.copy(alpha = 0.22f), CircleShape)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Center Target Marker",
                            tint = if (isMockActive) activeGreen else stopRed,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp, 4.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    )
                }
            }

            // 3. FLOATING 28dp SEARCH CARD & TONAL PRESET CHIPS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .zIndex(20f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_picker_search_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search city, address, or landmark...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("map_picker_search_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { searchLocation(searchQuery) })
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (isSearching) {
                            GooglePlayCompactLinearProgress(
                                width = 28.dp,
                                height = 4.dp,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        } else {
                            IconButton(
                                onClick = { searchLocation(searchQuery) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Search Location",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Tonal Preset Chips aligned cleanly below search bar
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_picker_presets_row")
                ) {
                    items(FakeGpsManager.popularPresets) { preset ->
                        val isSelected = centerLat == preset.latitude && centerLng == preset.longitude
                        Surface(
                            shape = ArworaDesignTokens.PillShape,
                            color = if (isSelected) {
                                chipAccent.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f)
                            },
                            shadowElevation = 2.dp,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) {
                                    chipAccent.copy(alpha = 0.72f)
                                } else {
                                    chipAccent.copy(alpha = 0.28f)
                                }
                            ),
                            modifier = Modifier
                                .clip(ArworaDesignTokens.PillShape)
                                .clickable { moveToPreset(preset) }
                                .testTag("map_preset_chip_${preset.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = chipAccent
                                )
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 4. UNIFIED VERTICAL SEGMENTED PILL CONTAINER FOR RIGHT-SIDE MAP CONTROLS
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .zIndex(20f)
                    .testTag("map_picker_segmented_controls")
            ) {
                Column(
                    modifier = Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SegmentedMapControlButton(
                        icon = Icons.Default.Layers,
                        contentDescription = "Select Map Type",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            SoundEffectManager.playClick()
                            showMapTypeSheet = true
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                    SegmentedMapControlButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            SoundEffectManager.playClick()
                            mapViewRef?.controller?.zoomIn()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                    SegmentedMapControlButton(
                        icon = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            SoundEffectManager.playClick()
                            mapViewRef?.controller?.zoomOut()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                    SegmentedMapControlButton(
                        icon = Icons.Default.Explore,
                        contentDescription = "Compass & Fine Tune D-Pad",
                        tint = if (showFineTuneControls) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        containerColor = if (showFineTuneControls) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                        onClick = {
                            SoundEffectManager.playClick()
                            showFineTuneControls = !showFineTuneControls
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                    SegmentedMapControlButton(
                        icon = Icons.Default.MyLocation,
                        contentDescription = "Recenter My Location",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            SoundEffectManager.playClick()
                            mapViewRef?.controller?.animateTo(GeoPoint(savedLat, savedLng), 16.0, 600L)
                        }
                    )
                }
            }

            // 5. D-PAD / FINE TUNING CONTROLLER
            AnimatedVisibility(
                visible = showFineTuneControls,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 152.dp)
                    .zIndex(25f)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Fine Nudge (~50m)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { nudge(0.0005, 0.0) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "North", tint = MaterialTheme.colorScheme.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { nudge(0.0, -0.0005) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "West", tint = MaterialTheme.colorScheme.primary)
                            }
                            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Adjust, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(onClick = { nudge(0.0, 0.0005) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "East", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { nudge(-0.0005, 0.0) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "South", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // 6. PRIMARY START / STOP SPOOFING FAB (PADDED CLEANLY ABOVE FLOATING BOTTOM CARD)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 148.dp)
                    .zIndex(30f)
            ) {
                if (isMockActive) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .align(Alignment.Center)
                            .scale(pulseScale)
                            .background(stopRed.copy(alpha = 0.20f), CircleShape)
                    )
                }

                FloatingActionButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        if (!isMockActive) {
                            FakeGpsManager.saveCoordinates(context, centerLat, centerLng, locationName)
                            val started = FakeGpsManager.startMockLocation(context, centerLat, centerLng)
                            if (started) {
                                MockLocationService.start(context, centerLat, centerLng, locationName)
                                Toast.makeText(context, "Spoofing active at ($centerLat, $centerLng)!", Toast.LENGTH_SHORT).show()
                            } else {
                                showHelpDialog = true
                            }
                        } else {
                            MockLocationService.stop(context)
                            FakeGpsManager.stopMockLocation(context)
                            Toast.makeText(context, "Mock Location Stopped", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .testTag("map_picker_action_fab"),
                    shape = CircleShape,
                    containerColor = if (isMockActive) stopRed else activeGreen,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
                ) {
                    AnimatedContent(
                        targetState = isMockActive,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) + scaleIn() togetherWith
                                fadeOut(animationSpec = tween(220)) + scaleOut()
                        },
                        label = "fabIcon"
                    ) { active ->
                        if (active) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Spoofing",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start Spoofing",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // 7. FLOATING ROUNDED BOTTOM SPOOFING STATUS & COORDINATE SHEET
            Surface(
                shape = ArworaDesignTokens.CardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                shadowElevation = 10.dp,
                border = ArworaDesignTokens.cardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .zIndex(25f)
                    .testTag("map_picker_bottom_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Minimal drag handle
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                            .align(Alignment.CenterHorizontally)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ArworaPillBadge(
                                text = if (isMockActive) "SPOOFING ACTIVE" else "READY TO BROADCAST",
                                tint = if (isMockActive) activeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                showDot = true,
                                modifier = Modifier.testTag("map_picker_spoof_badge")
                            )

                            Text(
                                text = String.format(Locale.US, "Lat: %.5f  •  Lng: %.5f", centerLat, centerLng),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = locationName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                FakeGpsManager.openDeveloperSettings(context)
                            },
                            shape = ArworaDesignTokens.PillShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("map_picker_dev_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Dev Settings",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    if (isMockActive) {
                        ExpressiveWavyLinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("map_picker_active_wave"),
                            progress = null,
                            color = activeGreen,
                            trackColor = activeGreen.copy(alpha = 0.22f),
                            height = 6.dp
                        )
                    }

                    if (!mockError.isNullOrBlank()) {
                        Text(
                            text = mockError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // 8. MAP TYPE SELECTOR BOTTOM SHEET
    if (showMapTypeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMapTypeSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Map Type & Layers",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showMapTypeSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Select map visualization style and tile renderer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppMapType.entries.forEach { mapType ->
                        val isSelected = currentMapType == mapType
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                2.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { applyMapType(mapType) }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else mapType.previewBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = mapType.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = mapType.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = mapType.badge,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Tiles are automatically cached locally for offline and low-bandwidth use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 9. SETUP & PERMISSIONS HELP DIALOG
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("How to Enable Mock Location", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "To broadcast mock coordinates across Google Maps, WhatsApp, and the entire Android OS:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("1. Open Developer Options on your device.")
                    Text("2. Locate 'Select mock location app'.")
                    Text("3. Choose '${context.applicationInfo.loadLabel(context.packageManager)}'.")
                    Text("4. Return here and tap the Green Play button!")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHelpDialog = false
                        FakeGpsManager.openDeveloperSettings(context)
                    }
                ) {
                    Text("Open Developer Options")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
private fun SegmentedMapControlButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    containerColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
