package com.arwora.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.arwora.NameGenApplication
import com.arwora.data.db.LocationLogEntity
import com.arwora.data.repository.LocationLogRepository
import androidx.compose.runtime.collectAsState
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationMapScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NameGenApplication
    val repository = remember { LocationLogRepository(app.database.locationLogDao()) }
    val logs by repository.allLogs.collectAsState(initial = emptyList())

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedLog by remember { mutableStateOf<LocationLogEntity?>(null) }

    LaunchedEffect(Unit) {
        try {
            Configuration.getInstance().load(context, context.getSharedPreferences("osm_pref", android.content.Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = context.packageName
            val osmdroidDir = File(context.cacheDir, "osmdroid")
            if (!osmdroidDir.exists()) osmdroidDir.mkdirs()
            Configuration.getInstance().osmdroidBasePath = osmdroidDir
            Configuration.getInstance().osmdroidTileCache = File(osmdroidDir, "tiles")
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS Location Route Map", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (logs.isNotEmpty() && mapViewRef != null) {
                                val last = logs.first()
                                mapViewRef?.controller?.animateTo(GeoPoint(last.latitude, last.longitude), 16.0, 800L)
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Center on Latest")
                    }
                }
            )
        }
    ) { paddingVals ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        if (logs.isNotEmpty()) {
                            controller.setCenter(GeoPoint(logs.first().latitude, logs.first().longitude))
                        } else {
                            controller.setCenter(GeoPoint(23.8103, 90.4125))
                        }
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    mapViewRef = mapView
                    mapView.overlays.clear()

                    val geoPoints = logs.map { GeoPoint(it.latitude, it.longitude) }

                    for (log in logs) {
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(log.latitude, log.longitude)
                            title = "Timestamp: ${log.timestamp}"
                            snippet = "Duration: ${log.duration}\nLat: ${String.format(Locale.US, "%.5f", log.latitude)}, Lng: ${String.format(Locale.US, "%.5f", log.longitude)}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { m, map ->
                                selectedLog = log
                                m.showInfoWindow()
                                true
                            }
                        }
                        mapView.overlays.add(marker)
                    }

                    if (geoPoints.isNotEmpty()) {
                        val polyline = Polyline().apply {
                            setPoints(geoPoints)
                            outlinePaint.color = AndroidColor.parseColor("#3B82F6")
                            outlinePaint.strokeWidth = 8f
                        }
                        mapView.overlays.add(polyline)
                    }

                    mapView.invalidate()
                }
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (selectedLog != null) "Selected Point Details" else "Saved Location Points: ${logs.size}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedLog != null) {
                        Text(
                            text = "Time: ${selectedLog?.timestamp} • Duration: ${selectedLog?.duration}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Coordinates: ${selectedLog?.latitude}, ${selectedLog?.longitude}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = if (logs.isNotEmpty()) "Latest: ${logs.first().timestamp}. Tap any map marker for details." else "No GPS logs recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
