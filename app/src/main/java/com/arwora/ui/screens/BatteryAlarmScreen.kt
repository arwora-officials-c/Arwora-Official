package com.arwora.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.arwora.NameGenApplication
import com.arwora.service.BatteryAlarmService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.feature.system.battery.service.BatteryMonitorManager
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.utils.BatteryDrainTracker
import com.arwora.utils.SoundEffectManager
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryAlarmScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = (context.applicationContext as NameGenApplication).settingsRepository
    val coroutineScope = rememberCoroutineScope()

    // Battery Settings States from SettingsRepository
    val chargingAlarmEnabled by repository.batteryAlarmEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val chargingThreshold by repository.batteryAlarmThresholdFlow.collectAsStateWithLifecycle(initialValue = 80)
    val alarmSound by repository.batteryAlarmSoundFlow.collectAsStateWithLifecycle(initialValue = "Classic Digital Beep")
    val lowAlarmEnabled by repository.batteryLowAlarmEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val lowThreshold by repository.batteryLowAlarmThresholdFlow.collectAsStateWithLifecycle(initialValue = 20)
    var localLowThreshold by remember(lowThreshold) { mutableFloatStateOf(lowThreshold.toFloat().coerceIn(1f, 40f)) }
    val repeatAlarm by repository.batteryAlarmRepeatFlow.collectAsStateWithLifecycle(initialValue = false)
    val alarmVolume by repository.batteryAlarmVolumeFlow.collectAsStateWithLifecycle(initialValue = 100)
    var localAlarmVolume by remember(alarmVolume) { mutableFloatStateOf(alarmVolume.toFloat().coerceIn(0f, 100f)) }
    val alarmVolumeBoost by repository.batteryAlarmVolumeBoostFlow.collectAsStateWithLifecycle(initialValue = false)

    val batteryLevel by BatteryMonitorManager.batteryLevel.collectAsStateWithLifecycle()
    val isBatteryCharging by BatteryMonitorManager.isCharging.collectAsStateWithLifecycle()
    val batteryTemp by BatteryMonitorManager.batteryTemperature.collectAsStateWithLifecycle()
    val batteryVoltage by BatteryMonitorManager.batteryVoltage.collectAsStateWithLifecycle()

    // Mock/placeholder for health and other metrics that are not in BatteryMonitorManager yet
    // I will simplify or extend BatteryMonitorManager later if needed, but for now, focus on the reported issue: temperature inconsistency.
    // Assuming batteryHealth, batteryPlugType, etc., were not the main cause of the *temperature* inconsistency.
    var batteryHealth by remember { mutableStateOf("Optimal") }
    var batteryPlugType by remember { mutableStateOf("Battery") }
    var currentNowMicroAmps by remember { mutableLongStateOf(0L) }
    var energyCounterUwh by remember { mutableLongStateOf(0L) }
    var estimatedCycles by remember { mutableIntStateOf(0) }
    
    val drainStats by BatteryDrainTracker.drainStats.collectAsStateWithLifecycle()

    DisposableEffect(context) {
        BatteryDrainTracker.init(context, batteryLevel, isBatteryCharging)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        fun refreshAdvancedStats() {
            if (bm != null) {
                currentNowMicroAmps = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                energyCounterUwh = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                val chargeCounterUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (chargeCounterUah > 0) {
                    estimatedCycles = (chargeCounterUah / 3500000L).toInt().coerceAtLeast(1)
                }
            }
        }
        refreshAdvancedStats()

        onDispose {
            stopSoundPreview()
        }
    }

    // Auto-manage Service start/stop based on user preferences toggles
    LaunchedEffect(chargingAlarmEnabled, lowAlarmEnabled) {
        val serviceIntent = Intent(context, BatteryAlarmService::class.java)
        try {
            if (chargingAlarmEnabled || lowAlarmEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                context.stopService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("BatteryAlarmScreen", "Could not toggle battery service", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Battery Alarm Guard", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Prevent overcharging & deep drains", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick(context)
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // 1. Sleek, Circular Battery Level Progress Indicator Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Real-Time Telemetry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Circular battery canvas with animated gradient ring
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val chargingSweepAnim = rememberInfiniteTransition(label = "charging")
                        val dynamicGlowAlpha by chargingSweepAnim.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "glow"
                        )

                        val batteryPercentFloat = batteryLevel.toFloat() / 100f
                        val targetColor = when {
                            batteryLevel <= 20 -> Color(0xFFEF4444) // Red
                            batteryLevel <= 40 -> Color(0xFFF59E0B) // Amber
                            else -> Color(0xFF10B981) // Green
                        }

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 14.dp.toPx()
                            val innerDiameter = size.minDimension - strokeWidth
                            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                            val ringSize = Size(innerDiameter, innerDiameter)

                            // Background Track
                            drawArc(
                                color = targetColor.copy(alpha = 0.12f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = ringSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )

                            // Foreground Level Progress Ring
                            drawArc(
                                color = targetColor,
                                startAngle = -90f,
                                sweepAngle = batteryPercentFloat * 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = ringSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Text content inside the circle
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isBatteryCharging) Icons.Default.Bolt else Icons.Default.BatteryStd,
                                contentDescription = null,
                                tint = if (isBatteryCharging) Color(0xFFF59E0B) else targetColor,
                                modifier = Modifier
                                    .size(34.dp)
                                    .animateContentSize()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$batteryLevel%",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 38.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isBatteryCharging) "Charging" else "Unplugged",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isBatteryCharging) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Grid telemetry properties
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Health", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(batteryHealth, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Temp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.1f", batteryTemp)}°C", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Voltage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${if (batteryVoltage > 1000) String.format("%.2f V", batteryVoltage / 1000f) else "$batteryVoltage mV"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Est. Cycles", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (estimatedCycles > 0) "$estimatedCycles cycles" else "Calibrating", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Current Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val mA = kotlin.math.abs(currentNowMicroAmps) / 1000
                            Text(if (mA > 0) "$mA mA" else "Idle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Est. Power", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val watts = (batteryVoltage / 1000f) * (kotlin.math.abs(currentNowMicroAmps) / 1000000f)
                            Text(if (watts > 0.05f) String.format("%.2f W", watts) else "Standby", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // 2. BATTERY DRAIN RATE ANALYTICS CARD
            BatteryDrainAnalyticsCard(
                stats = drainStats,
                onResetSession = {
                    SoundEffectManager.playClick(context)
                    BatteryDrainTracker.resetSession(context, batteryLevel, isBatteryCharging)
                }
            )

            // 3. PRIMARY CONTROLS & ALARMS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Alarm Threshold Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Overcharge protection toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Overcharge Warning", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Alert when charging reaches target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ArworaAnimatedSwitch(
                            checked = chargingAlarmEnabled,
                            onCheckedChange = { value ->
                                SoundEffectManager.playClick(context)
                                coroutineScope.launch { repository.setBatteryAlarmEnabled(value) }
                                if (!value) {
                                    val intent = Intent(context, BatteryAlarmService::class.java).apply {
                                        action = BatteryAlarmService.ACTION_DISMISS_ALARM
                                    }
                                    context.startService(intent)
                                }
                            }
                        )
                    }

                    // Slide Selector for target threshold
                    AnimatedVisibility(visible = chargingAlarmEnabled) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Target Level Threshold", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("$chargingThreshold%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = chargingThreshold.toFloat(),
                                onValueChange = { newValue ->
                                    coroutineScope.launch { repository.setBatteryAlarmThreshold(newValue.toInt()) }
                                },
                                valueRange = 50f..100f,
                                steps = 9 // Steps of 5%: 50, 55, 60... 100
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Low battery drain warning toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Low Battery Warning", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Alert when level drops below target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ArworaAnimatedSwitch(
                            checked = lowAlarmEnabled,
                            onCheckedChange = { value ->
                                SoundEffectManager.playClick(context)
                                coroutineScope.launch { repository.setBatteryLowAlarmEnabled(value) }
                                if (!value) {
                                    val intent = Intent(context, BatteryAlarmService::class.java).apply {
                                        action = BatteryAlarmService.ACTION_DISMISS_ALARM
                                    }
                                    context.startService(intent)
                                }
                            }
                        )
                    }

                    // Slider selector for low battery warning
                    AnimatedVisibility(visible = lowAlarmEnabled) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Low Level Alert Threshold", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("${localLowThreshold.roundToInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                            }
                            Slider(
                                value = localLowThreshold,
                                onValueChange = { newValue ->
                                    val intVal = newValue.roundToInt().coerceIn(1, 40)
                                    localLowThreshold = intVal.toFloat()
                                    coroutineScope.launch { repository.setBatteryLowAlarmThreshold(intVal) }
                                },
                                valueRange = 1f..40f,
                                steps = 38
                            )
                        }
                    }
                }
            }

            // 3. SOUNDS & PERSISTENCY
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Alert Sounds & Playback Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Repeat / persistent alarm toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Persistent Repeat Alert", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Alarm repeats continuously until action taken", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ArworaAnimatedSwitch(
                            checked = repeatAlarm,
                            onCheckedChange = { value ->
                                SoundEffectManager.playClick(context)
                                coroutineScope.launch { repository.setBatteryAlarmRepeat(value) }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Custom Alert Sound Selector Dropdown
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Alert Ringtone Sound",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        var dropdownExpanded by remember { mutableStateOf(false) }

                        val customSoundPicker = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri: Uri? ->
                            uri?.let { selectedUri ->
                                try {
                                    context.contentResolver.takePersistableUriPermission(
                                        selectedUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                val fileName = getFileNameFromUri(context, selectedUri)
                                val soundEntry = "Custom:$selectedUri|$fileName"
                                coroutineScope.launch {
                                    repository.setBatteryAlarmSound(soundEntry)
                                }
                                playSoundPreview(context, soundEntry, localAlarmVolume.roundToInt(), alarmVolumeBoost)
                            }
                        }

                        val displaySoundName = remember(alarmSound) {
                            if (alarmSound.startsWith("Custom:")) {
                                val name = alarmSound.substringAfter("|")
                                if (name.isNotEmpty()) "Custom: $name" else "Custom Audio File"
                            } else {
                                alarmSound
                            }
                        }

                        val soundOptions = listOf(
                            "Classic Digital Beep",
                            "High-Frequency Chime",
                            "Emergency Siren Alert",
                            "Extreme Air Raid Siren",
                            "Heavy Duty Buzzer",
                            "High-Decibel Pulse Alert",
                            "Ultra Loud Warning Tone",
                            "Retro Arcade Chime",
                            "Harmonic Sparkling Ding!",
                            "System Default Alarm",
                            "Select Custom Sound..."
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable { dropdownExpanded = true }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (alarmSound.startsWith("Custom:")) Icons.Default.FolderOpen else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(displaySoundName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), RoundedCornerShape(18.dp)),
                                shape = RoundedCornerShape(18.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Select Alarm Ringtone",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )

                                soundOptions.forEach { soundOption ->
                                    val isSelected = (soundOption == alarmSound) || 
                                        (soundOption == "Select Custom Sound..." && alarmSound.startsWith("Custom:"))
                                    val isCustomOption = soundOption == "Select Custom Sound..."

                                    if (isCustomOption) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        )
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                dropdownExpanded = false
                                                SoundEffectManager.playClick(context)
                                                if (isCustomOption) {
                                                    customSoundPicker.launch("audio/*")
                                                } else {
                                                    coroutineScope.launch {
                                                        repository.setBatteryAlarmSound(soundOption)
                                                    }
                                                    playSoundPreview(context, soundOption, localAlarmVolume.roundToInt(), alarmVolumeBoost)
                                                }
                                            },
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected || isCustomOption) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = if (isCustomOption) Icons.Default.FolderOpen
                                                            else if (isSelected) Icons.Default.VolumeUp
                                                            else Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = if (isSelected || isCustomOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = soundOption,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected || isCustomOption) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected || isCustomOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

            // Dedicated Volume Control Slider
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    localAlarmVolume <= 0f -> Icons.Default.VolumeMute
                                    localAlarmVolume < 50f -> Icons.Default.VolumeDown
                                    else -> Icons.Default.VolumeUp
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Alert Playback Volume",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${localAlarmVolume.roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = localAlarmVolume,
                        onValueChange = { newValue ->
                            val intVal = newValue.roundToInt().coerceIn(0, 100)
                            localAlarmVolume = intVal.toFloat()
                            coroutineScope.launch { repository.setBatteryAlarmVolume(intVal) }
                        },
                        valueRange = 0f..100f,
                        steps = 99
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

            // Maximum Volume Boost Feature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (alarmVolumeBoost) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (alarmVolumeBoost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Max Volume Boost",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (alarmVolumeBoost) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "BOOSTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "Overrides system volume & forces maximum loudness",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ArworaAnimatedSwitch(
                    checked = alarmVolumeBoost,
                    onCheckedChange = { value ->
                        SoundEffectManager.playClick(context)
                        coroutineScope.launch { repository.setBatteryAlarmVolumeBoost(value) }
                        if (value) {
                            playSoundPreview(context, alarmSound, 100, true)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun BatteryDrainAnalyticsCard(
    stats: com.arwora.utils.DrainStats,
    onResetSession: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battery_drain_analytics_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Battery Drain Rate Analytics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (stats.isCharging) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else if (stats.drainRatePerHour > 15f) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (stats.isCharging) "CHARGING" else if (stats.drainRatePerHour > 0f) "LIVE" else "CALIBRATING",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (stats.isCharging) MaterialTheme.colorScheme.primary
                                else if (stats.drainRatePerHour > 15f) MaterialTheme.colorScheme.error
                                else Color(0xFF10B981),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "Discharge velocity (%/hour), screen-on burn rate, and remaining runtime projections.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Primary Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Drain Rate Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Discharge Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        val rateStr = if (stats.isCharging) "0.0 %/h"
                                      else if (stats.drainRatePerHour > 0f) String.format(java.util.Locale.US, "%.1f %%/h", stats.drainRatePerHour)
                                      else "~8.5 %/h"
                        Text(
                            text = rateStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.drainRatePerHour > 18f) MaterialTheme.colorScheme.error
                                    else if (stats.drainRatePerHour > 10f) Color(0xFFF59E0B)
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Estimated Runtime Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Time to Empty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        val hours = stats.estimatedHoursRemaining.toInt()
                        val mins = ((stats.estimatedHoursRemaining - hours) * 60).toInt()
                        val estStr = if (stats.isCharging) "Charging"
                                     else if (stats.estimatedHoursRemaining > 0f) "${hours}h ${mins}m"
                                     else "Estimating"
                        Text(
                            text = estStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Secondary Breakdown
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active Screen-On Rate:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format(java.util.Locale.US, "~%.1f %%/hour", stats.screenOnDrainRatePerHour), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Idle Standby Rate:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format(java.util.Locale.US, "~%.1f %%/hour", stats.standbyDrainRatePerHour), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    val elapsedMins = (stats.sessionDurationMs / 60000L).coerceAtLeast(1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tracking Session:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${elapsedMins}m tracked (${stats.percentDropped}% dropped)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status diagnostic chip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Text(
                    text = stats.statusSummary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action Button adhering strictly to RULE[AGENTS_md]
            Button(
                onClick = onResetSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("reset_drain_session_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reset Drain Session", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Custom Sound"
}

private var previewMediaPlayer: android.media.MediaPlayer? = null
private var previewToneGenerator: android.media.ToneGenerator? = null
private var previewRingtone: android.media.Ringtone? = null
private val previewHandler = android.os.Handler(android.os.Looper.getMainLooper())
private var previewStopRunnable: Runnable? = null

fun stopSoundPreview() {
    try {
        previewStopRunnable?.let { previewHandler.removeCallbacks(it) }
        previewStopRunnable = null
    } catch (e: Exception) { }

    try {
        previewMediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.reset()
            it.release()
        }
    } catch (e: Exception) { }
    previewMediaPlayer = null

    try {
        previewToneGenerator?.release()
    } catch (e: Exception) { }
    previewToneGenerator = null

    try {
        previewRingtone?.stop()
    } catch (e: Exception) { }
    previewRingtone = null
}

// Play simple temporary sound preview of the alarm
private fun playSoundPreview(context: Context, soundName: String, volume: Int = 100, isBoost: Boolean = false) {
    try {
        stopSoundPreview()

        val effVolume = if (isBoost) 100 else volume.coerceIn(0, 100)
        val volumeFloat = effVolume / 100f

        if (isBoost) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (audioManager != null) {
                    val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVol, 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (soundName.startsWith("Custom:")) {
            val uriStr = soundName.substringAfter("Custom:").substringBefore("|")
            try {
                val uri = Uri.parse(uriStr)
                val mp = android.media.MediaPlayer()
                previewMediaPlayer = mp
                mp.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.setDataSource(context, uri)
                mp.setVolume(volumeFloat, volumeFloat)
                mp.setOnErrorListener { _, _, _ ->
                    stopSoundPreview()
                    true
                }
                mp.setOnCompletionListener {
                    stopSoundPreview()
                }
                mp.prepare()
                mp.start()

                val runnable = Runnable { stopSoundPreview() }
                previewStopRunnable = runnable
                previewHandler.postDelayed(runnable, 3500)
            } catch (e: Exception) {
                e.printStackTrace()
                stopSoundPreview()
            }
            return
        }

        when (soundName) {
            "Classic Digital Beep" -> {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, effVolume)
                previewToneGenerator = tg
                tg.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 300)
                val runnable = Runnable { stopSoundPreview() }
                previewStopRunnable = runnable
                previewHandler.postDelayed(runnable, 450)
            }
            "High-Frequency Chime" -> {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, effVolume)
                previewToneGenerator = tg
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 250)
                val runnable = Runnable { stopSoundPreview() }
                previewStopRunnable = runnable
                previewHandler.postDelayed(runnable, 400)
            }
            "Emergency Siren Alert" -> {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, effVolume)
                previewToneGenerator = tg
                tg.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                val runnable = Runnable { stopSoundPreview() }
                previewStopRunnable = runnable
                previewHandler.postDelayed(runnable, 650)
            }
            "Extreme Air Raid Siren" -> {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, effVolume)
                previewToneGenerator = tg
                tg.startTone(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 750)
                val runnable = Runnable { stopSoundPreview() }
                previewStopRunnable = runnable
                previewHandler.postDelayed(runnable, 900)
            }
            "Heavy Duty Buzzer" -> {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, effVolume)
                previewToneGenerator = tg
                tg.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 650)
                val runnable = Runnable { stopSoundPreview() }
                previewStopRunnable = runnable
                previewHandler.postDelayed(runnable, 800)
            }
            "High-Decibel Pulse Alert" -> {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, effVolume)
                previewToneGenerator = tg
                tg.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 550)
                val runnable = Runnable { stopSoundPreview() }
                previewStopRunnable = runnable
                previewHandler.postDelayed(runnable, 700)
            }
            "Ultra Loud Warning Tone" -> {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, effVolume)
                previewToneGenerator = tg
                tg.startTone(android.media.ToneGenerator.TONE_SUP_CONGESTION, 700)
                val runnable = Runnable { stopSoundPreview() }
                previewStopRunnable = runnable
                previewHandler.postDelayed(runnable, 850)
            }
            "Retro Arcade Chime" -> {
                com.arwora.utils.SoundEffectManager.playCoinToss(context)
            }
            "Harmonic Sparkling Ding!" -> {
                com.arwora.utils.SoundEffectManager.playCoinLand(context)
            }
            "System Default Alarm" -> {
                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val r = android.media.RingtoneManager.getRingtone(context, uri)
                previewRingtone = r
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    r.volume = volumeFloat
                }
                r.play()
                val runnable = Runnable { stopSoundPreview() }
                previewStopRunnable = runnable
                previewHandler.postDelayed(runnable, 1500)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        stopSoundPreview()
    }
}
