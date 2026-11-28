package com.arwora.feature.home.weather

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.MapTileManager
import com.arwora.utils.SoundEffectManager
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

@Composable
fun WeatherRadarMapCard(
    latitude: Double,
    longitude: Double,
    localityName: String,
    radarPath: String?
) {
    val context = LocalContext.current
    var showRadarLayer by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_hub_radar_card"),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.Radar,
                        tint = Color(0xFF38BDF8),
                        size = 38.dp,
                        iconSize = 20.dp,
                        contentDescription = "Rain Radar"
                    )
                    Column {
                        Text(
                            text = "Live Rain Radar Map",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (radarPath != null) "RainViewer live cloud & precipitation overlay"
                            else "Interactive map • Sync online for radar tiles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = showRadarLayer,
                    onCheckedChange = { showRadarLayer = it }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0xFF0F172A), ArworaDesignTokens.InnerTileShape)
            ) {
                var mapRef by remember { mutableStateOf<MapView?>(null) }

                DisposableEffect(Unit) {
                    MapTileManager.configureOsmdroid(context)
                    onDispose {
                        runCatching { mapRef?.onDetach() }
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(MapTileManager.CartoDarkTileSource)
                            setMultiTouchControls(true)
                            controller.setZoom(8.5)
                            controller.setCenter(GeoPoint(latitude, longitude))
                            mapRef = this
                        }
                    },
                    update = { map ->
                        map.controller.setCenter(GeoPoint(latitude, longitude))
                        map.overlays.clear()
                        if (showRadarLayer && !radarPath.isNullOrBlank()) {
                            val radarSource = object : OnlineTileSourceBase(
                                "RainViewerRadar",
                                1,
                                12,
                                256,
                                ".png",
                                arrayOf("https://tilecache.rainviewer.com$radarPath")
                            ) {
                                override fun getTileURLString(pMapTileIndex: Long): String {
                                    val z = MapTileIndex.getZoom(pMapTileIndex)
                                    val x = MapTileIndex.getX(pMapTileIndex)
                                    val y = MapTileIndex.getY(pMapTileIndex)
                                    return "$baseUrl/256/$z/$x/$y/2/1_1.png"
                                }
                            }
                            val radarOverlay = TilesOverlay(MapTileProviderBasic(context, radarSource), context).apply {
                                loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                                loadingLineColor = android.graphics.Color.TRANSPARENT
                            }
                            map.overlays.add(radarOverlay)
                        }
                        val marker = Marker(map).apply {
                            position = GeoPoint(latitude, longitude)
                            title = localityName
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(marker)
                        map.invalidate()
                    },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
        }
    }
}

@Composable
fun WeatherSmartAlertsSection(
    settings: SmartWeatherAlertSettings,
    liveAlerts: List<TriggeredWeatherAlert>,
    onUpdateSettings: ((SmartWeatherAlertSettings) -> SmartWeatherAlertSettings) -> Unit,
    onRunCheckNow: () -> Int,
    onOpenAlertSettings: () -> Unit = {}
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_hub_alerts_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.NotificationsActive,
                        tint = Color(0xFFF59E0B),
                        size = 40.dp,
                        iconSize = 20.dp,
                        contentDescription = "Smart Alerts"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart Local Alert System",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automated WorkManager local checks every 30 minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AlertToggleRow(
                    title = "Rain (Brishti)",
                    subtitle = "Notify 30–60 mins before rain reaches your area",
                    checked = settings.brishtiAlertEnabled,
                    tag = "alert_toggle_brishti",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(brishtiAlertEnabled = checked) } }
                )
                AlertToggleRow(
                    title = "Laundry (Kapor Shukano)",
                    subtitle = "Sunny drying windows & sudden rain warnings",
                    checked = settings.kaporShukanoAlertEnabled,
                    tag = "alert_toggle_kapor",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(kaporShukanoAlertEnabled = checked) } }
                )
                AlertToggleRow(
                    title = "Market (Bazar)",
                    subtitle = "Morning 7:00 AM – 10:00 AM clear errand alert",
                    checked = settings.bazarAlertEnabled,
                    tag = "alert_toggle_bazar",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(bazarAlertEnabled = checked) } }
                )
                AlertToggleRow(
                    title = "Commuting (Bike Ride)",
                    subtitle = "Next 2-hour rain & wind commute warning",
                    checked = settings.bikeRideAlertEnabled,
                    tag = "alert_toggle_bike",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(bikeRideAlertEnabled = checked) } }
                )
                AlertToggleRow(
                    title = "Extreme Weather Warning",
                    subtitle = "High-priority storm, heatwave & severe cold alerts",
                    checked = settings.extremeWarningEnabled,
                    tag = "alert_toggle_extreme",
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(extremeWarningEnabled = checked) } }
                )

                OutlinedButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        onOpenAlertSettings()
                    },
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("weather_hub_open_alert_settings_btn")
                ) {
                    Text("Configure Weather Alerts", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        val sent = onRunCheckNow()
                        val msg = if (sent > 0) "Dispatched $sent local weather alert(s)"
                        else "Alert conditions checked (${liveAlerts.size} active advisories)"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("weather_run_alert_check_btn")
                ) {
                    Text("Run Alert Check Now", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (liveAlerts.isNotEmpty()) {
            Text(
                text = "Active Advisories (${liveAlerts.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            liveAlerts.forEach { alert ->
                Card(
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                    border = ArworaDesignTokens.cardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            ArworaPillBadge(
                                text = if (alert.isHighPriority) "HIGH" else "LOCAL",
                                tint = if (alert.isHighPriority) Color(0xFFEF4444) else Color(0xFF38BDF8)
                            )
                        }
                        Text(
                            text = alert.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = ArworaDesignTokens.InnerTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = ArworaDesignTokens.innerTileBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag)
            )
        }
    }
}

@Composable
fun WeatherPlannerFarmSection(bundle: ProWeatherBundle) {
    val activities = remember(bundle) {
        WeatherAdvisoryEngine.computeBestTimeActivities(bundle.current, bundle.hourly24)
    }
    val next12Rain = remember(bundle) {
        bundle.hourly24.take(12).maxOfOrNull { it.precipitationProbabilityPercent } ?: 0
    }
    val outfit = remember(bundle, next12Rain) {
        WeatherAdvisoryEngine.computeOutfitAdvice(bundle.current, next12Rain)
    }
    val farming = remember(bundle) {
        WeatherAdvisoryEngine.computeFarmingAdvice(bundle.current, bundle.hourly24, bundle.daily14)
    }
    val sports = remember(bundle) {
        WeatherAdvisoryEngine.computeSportsAdvice(bundle.current, bundle.hourly24)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_hub_planner_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Outfit Advisor ("Aj Ki Porbo?")
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.Checkroom,
                        tint = Color(0xFFA855F7),
                        size = 38.dp,
                        iconSize = 20.dp,
                        contentDescription = "Outfit Advisor"
                    )
                    Column {
                        Text(
                            text = "Aj Ki Porbo? (Outfit Advisor)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = outfit.headline,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                outfit.clothingItems.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = outfit.carryNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Daily Life Planner ("Best Time Suggestions")
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Best Time Suggestions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                activities.forEach { act ->
                    val badgeColor = advisoryColor(act.level)
                    Surface(
                        shape = ArworaDesignTokens.InnerTileShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        border = ArworaDesignTokens.innerTileBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = act.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                ArworaPillBadge(text = act.ratingLabel, tint = badgeColor)
                            }
                            Text(
                                text = "${act.bestWindow} • ${act.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Farming / Chasabad Mode
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ArworaTonalIconBadge(
                            icon = Icons.Default.Agriculture,
                            tint = Color(0xFF10B981),
                            size = 38.dp,
                            iconSize = 20.dp,
                            contentDescription = "Farming Mode"
                        )
                        Text(
                            text = "🌾 Farming / Chasabad Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ArworaPillBadge(text = "${farming.maxRainProbNext48h}% Rain 48h", tint = advisoryColor(farming.level))
                }
                Text(
                    text = "Irrigation: ${farming.irrigationAction}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Fertilizer: ${farming.fertilizerAction}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = farming.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Cricket / Football Mode
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ArworaTonalIconBadge(
                            icon = Icons.Default.SportsCricket,
                            tint = Color(0xFF38BDF8),
                            size = 38.dp,
                            iconSize = 20.dp,
                            contentDescription = "Sports Mode"
                        )
                        Text(
                            text = "🏏 Cricket / Football Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ArworaPillBadge(text = sports.rainInterruptionRisk, tint = advisoryColor(sports.level))
                }
                Text(
                    text = "Pitch / Ground: ${sports.pitchDryness} • ${sports.lightingCondition}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = sports.verdict,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WeatherHealthComfortSection(bundle: ProWeatherBundle) {
    val meters = remember(bundle) {
        val todayLow = bundle.daily14.firstOrNull()?.lowTempCelsius ?: bundle.current.lowTempCelsius
        listOf(
            WeatherAdvisoryEngine.computeHeatStrokeRisk(bundle.current),
            WeatherAdvisoryEngine.computeColdSkinDryness(bundle.current),
            WeatherAdvisoryEngine.computeDustAllergyMeter(bundle.airQuality, bundle.current.windSpeedKmh),
            WeatherAdvisoryEngine.computeSleepComfortAdvice(bundle.current, todayLow)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_hub_health_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        meters.forEach { meter ->
            val tint = advisoryColor(meter.level)
            Card(
                shape = ArworaDesignTokens.CardShape,
                colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                border = ArworaDesignTokens.cardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = meter.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        ArworaPillBadge(text = meter.levelLabel, tint = tint)
                    }
                    Text(
                        text = meter.primaryMetric,
                        style = MaterialTheme.typography.labelLarge,
                        color = tint,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { (meter.scorePercent / 100f).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = tint,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                    Text(
                        text = meter.advice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherProUtilitiesSection(
    bundle: ProWeatherBundle,
    isSpeaking: Boolean,
    onToggleBanglaVoice: () -> Unit,
    onOpenWeatherCamera: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_hub_utilities_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Weather History ("On This Day")
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.History,
                        tint = Color(0xFF38BDF8),
                        size = 38.dp,
                        iconSize = 20.dp,
                        contentDescription = "Weather History"
                    )
                    Column {
                        Text(
                            text = "📅 Weather History (On This Day)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = bundle.lastYearHistory?.dateLabel ?: "1 year ago archive comparison",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val hist = bundle.lastYearHistory
                if (hist != null) {
                    Text(
                        text = "Last Year: ${hist.conditionLabel} • High ${hist.highTempCelsius}°C / Low ${hist.lowTempCelsius}°C • Rain ${String.format("%.1f", hist.precipitationSumMm)} mm",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val deltaHigh = bundle.current.highTempCelsius - hist.highTempCelsius
                    val deltaText = if (deltaHigh >= 0) "+${deltaHigh}°C warmer than last year" else "${deltaHigh}°C cooler than last year"
                    Text(
                        text = "Today (${bundle.current.highTempCelsius}° / ${bundle.current.lowTempCelsius}°): $deltaText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Historical archive data will load when online connection is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Weather Camera + 3. Bangla Voice Weather
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.CameraAlt,
                        tint = Color(0xFF10B981),
                        size = 38.dp,
                        iconSize = 20.dp,
                        contentDescription = "Weather Camera"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📸 Weather Camera",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Capture photos stamped with live temp, condition & city",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        onOpenWeatherCamera()
                    },
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("weather_open_camera_btn")
                ) {
                    Text("Open Weather Camera", fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.RecordVoiceOver,
                        tint = Color(0xFFA855F7),
                        size = 38.dp,
                        iconSize = 20.dp,
                        contentDescription = "Bangla Voice Weather"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🗣️ Bangla Voice Weather",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Spoken weather summary in Bangla (bn-BD) with English fallback",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        onToggleBanglaVoice()
                    },
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("weather_voice_summary_btn")
                ) {
                    Text(
                        text = if (isSpeaking) "Stop Voice Forecast" else "Listen Forecast",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Home Widget & Dynamic Live Wallpaper
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.Wallpaper,
                        tint = Color(0xFFF59E0B),
                        size = 38.dp,
                        iconSize = 20.dp,
                        contentDescription = "Widget & Live Wallpaper"
                    )
                    Column {
                        Text(
                            text = "🖼️ Home Widget & Live Wallpaper",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sync home widget and condition-reactive animated sky wallpaper",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            WeatherAppWidgetProvider.requestWidgetUpdate(context)
                            Toast.makeText(
                                context,
                                "Widget synced! Add 'Arwora Live Weather' from your home launcher widgets.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("weather_sync_widget_btn")
                    ) {
                        Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Widget", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = {
                            SoundEffectManager.playClick()
                            val launched = runCatching {
                                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(context, WeatherLiveWallpaperService::class.java)
                                    )
                                }
                                context.startActivity(intent)
                                true
                            }.getOrDefault(false)
                            if (!launched) {
                                Toast.makeText(context, "Open system Wallpaper Picker to select Arwora Live Weather Sky", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("weather_live_wallpaper_btn")
                    ) {
                        Text("Set Wallpaper", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // 5. Emergency SOS & Shelter Finder
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.Emergency,
                        tint = Color(0xFFEF4444),
                        size = 38.dp,
                        iconSize = 20.dp,
                        contentDescription = "Emergency SOS"
                    )
                    Column {
                        Text(
                            text = "🆘 Emergency SOS & Shelter Finder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "National Emergency 999, Weather Desk 1090 & nearby shelters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:999")))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("weather_sos_999_btn")
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call 999", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val lat = bundle.current.latitude
                                val lon = bundle.current.longitude
                                val uri = Uri.parse("geo:$lat,$lon?q=cyclone+shelter+or+hospital")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }.onFailure {
                                Toast.makeText(context, "No Maps app available to open shelters", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("weather_find_shelter_btn")
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shelters", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun advisoryColor(level: AdvisoryLevel): Color = when (level) {
    AdvisoryLevel.GOOD -> Color(0xFF10B981)
    AdvisoryLevel.MODERATE -> Color(0xFF38BDF8)
    AdvisoryLevel.CAUTION -> Color(0xFFF59E0B)
    AdvisoryLevel.DANGER -> Color(0xFFEF4444)
}
