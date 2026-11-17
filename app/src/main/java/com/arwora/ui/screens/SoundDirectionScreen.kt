package com.arwora.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.*

// Acoustic blip representing detected sound direction and distance
data class SoundBlip(
    val id: Long,
    val angleDegrees: Float,
    val distanceFraction: Float, // 0.2 to 0.95
    val intensity: Float,        // 0.0 to 1.0
    val timestamp: Long
)

// Smooth angle wrapping calculation without 360 degree spin artifacts
fun smoothAngleTransition(current: Float, target: Float, factor: Float): Float {
    var diff = (target - current) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    var result = current + diff * factor
    if (result < 0f) result += 360f
    if (result >= 360f) result -= 360f
    return result
}

// Convert angle into 8 cardinal/intercardinal direction sectors
fun getSectorName(angle: Float, isCompassMode: Boolean = false): String {
    val norm = ((angle % 360f) + 360f) % 360f
    val cardinal = when {
        norm in 337.5f..360.0f || norm in 0.0f..22.5f -> if (isCompassMode) "NORTH (N)" else "FRONT (N)"
        norm in 22.5f..67.5f -> if (isCompassMode) "NORTH-EAST (NE)" else "FRONT-RIGHT (NE)"
        norm in 67.5f..112.5f -> if (isCompassMode) "EAST (E)" else "RIGHT (E)"
        norm in 112.5f..157.5f -> if (isCompassMode) "SOUTH-EAST (SE)" else "REAR-RIGHT (SE)"
        norm in 157.5f..202.5f -> if (isCompassMode) "SOUTH (S)" else "BACK (S)"
        norm in 202.5f..247.5f -> if (isCompassMode) "SOUTH-WEST (SW)" else "REAR-LEFT (SW)"
        norm in 247.5f..292.5f -> if (isCompassMode) "WEST (W)" else "LEFT (W)"
        else -> if (isCompassMode) "NORTH-WEST (NW)" else "FRONT-LEFT (NW)"
    }
    return cardinal
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundDirectionScreen(navController: NavController) {
    val context = LocalContext.current

    // Permission tracking
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            SoundEffectManager.playCoinToss(context)
        }
    }

    // Audio & Processing state
    var isListening by remember { mutableStateOf(true) }
    var currentDecibels by remember { mutableFloatStateOf(30f) }
    var targetAngle by remember { mutableFloatStateOf(0f) }
    var detectedSector by remember { mutableStateOf("FRONT (N)") }
    var leftRmsPercent by remember { mutableFloatStateOf(50f) }
    var rightRmsPercent by remember { mutableFloatStateOf(50f) }
    var isStereoSupported by remember { mutableStateOf(false) }
    var sensitivityMultiplier by remember { mutableFloatStateOf(1.5f) } // 1.0f, 1.5f, 2.5f
    var noiseFloorDb by remember { mutableFloatStateOf(32f) }
    var peakFrequencyHz by remember { mutableIntStateOf(440) }
    var confidenceScorePercent by remember { mutableIntStateOf(85) }
    var spectrumBands by remember { mutableStateOf(List(12) { 0.1f }) }

    // Compass / Device Orientation Tracking
    var compassModeEnabled by remember { mutableStateOf(false) }
    var deviceAzimuth by remember { mutableFloatStateOf(0f) }

    // Recent sound blips for radar phosphor persistence
    var blipList by remember { mutableStateOf<List<SoundBlip>>(emptyList()) }

    // Pulse wave trigger state for animated sound ripples
    var pulseTrigger by remember { mutableLongStateOf(0L) }

    // Device Rotation / Compass Listener
    DisposableEffect(isListening, compassModeEnabled) {
        if (!isListening || !compassModeEnabled) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientationAngles = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    if (azimuthDeg < 0) azimuthDeg += 360f
                    deviceAzimuth = azimuthDeg
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensorManager != null && rotSensor != null) {
            sensorManager.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // Effective display angle adjusted for compass orientation if enabled
    val effectiveAngle = remember(targetAngle, deviceAzimuth, compassModeEnabled) {
        if (compassModeEnabled) {
            (targetAngle + deviceAzimuth) % 360f
        } else {
            targetAngle
        }
    }

    // Smooth compass angle animation
    val animatedAngle by animateFloatAsState(
        targetValue = effectiveAngle,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "radar_angle"
    )

    // Continuous 360° radar sweep animation
    val sweepTransition = rememberInfiniteTransition(label = "sweep")
    val sweepAngle by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    // Clean up stale blips over time
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(200)
            val now = System.currentTimeMillis()
            blipList = blipList.filter { now - it.timestamp < 3500 }
        }
    }

    // Low-Latency Audio Stream Processing Coroutine
    LaunchedEffect(hasPermission, isListening, sensitivityMultiplier, noiseFloorDb) {
        if (!hasPermission || !isListening) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            var isStereo = false

            val sampleRate = 44100
            val encoding = AudioFormat.ENCODING_PCM_16BIT

            val sourcesToTry = listOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )

            for (source in sourcesToTry) {
                if (audioRecord != null) break

                // 1. Try Stereo format first to capture dual-channel acoustic phase/level differences
                val minBufStereo = try {
                    AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, encoding)
                } catch (e: Exception) { -1 }

                if (minBufStereo > 0) {
                    try {
                        val record = AudioRecord(
                            source,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_STEREO,
                            encoding,
                            maxOf(minBufStereo * 2, 4096)
                        )
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecord = record
                            isStereo = true
                            break
                        } else {
                            record.release()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 2. Fallback to Mono if stereo channel input is not provided by hardware HAL
                val minBufMono = try {
                    AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, encoding)
                } catch (e: Exception) { -1 }

                if (minBufMono > 0) {
                    try {
                        val record = AudioRecord(
                            source,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            encoding,
                            maxOf(minBufMono * 2, 2048)
                        )
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecord = record
                            isStereo = false
                            break
                        } else {
                            record.release()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val activeRecord = audioRecord ?: return@withContext
            isStereoSupported = isStereo

            try {
                activeRecord.startRecording()
                val bufferSize = if (isStereo) 1024 else 512
                val buffer = ShortArray(bufferSize)
                var lastPeakTime = 0L

                while (isActive && isListening) {
                    // Blocking read naturally synchronizes with audio sample hardware clock (~11-23ms)
                    val read = activeRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        if (isStereo) {
                            var sumL = 0.0
                            var sumR = 0.0
                            var hfSumL = 0.0
                            var hfSumR = 0.0
                            var zeroCrossings = 0
                            var prevL = 0
                            var prevR = 0
                            val pairs = read / 2

                            for (i in 0 until pairs) {
                                val sL = buffer[i * 2].toInt()
                                val sR = buffer[i * 2 + 1].toInt()
                                sumL += sL * sL
                                sumR += sR * sR
                                hfSumL += abs(sL - prevL)
                                hfSumR += abs(sR - prevR)

                                if (i > 0 && ((sL >= 0 && prevL < 0) || (sL < 0 && prevL >= 0))) {
                                    zeroCrossings++
                                }
                                prevL = sL
                                prevR = sR
                            }

                            val rmsL = sqrt(sumL / maxOf(pairs, 1))
                            val rmsR = sqrt(sumR / maxOf(pairs, 1))
                            val totalRms = (rmsL + rmsR) / 2.0

                            // Calculate decibel sound pressure level
                            val db = (20 * log10(maxOf(totalRms * sensitivityMultiplier, 1.0))).toFloat()
                                .coerceIn(20f, 105f)
                            currentDecibels = db

                            // Estimate peak frequency (Hz)
                            val hz = ((zeroCrossings.toFloat() * sampleRate) / (2f * maxOf(pairs, 1))).coerceIn(80f, 8000f)
                            peakFrequencyHz = hz.toInt()

                            // Estimate confidence score percentage
                            val snr = currentDecibels - noiseFloorDb
                            confidenceScorePercent = ((snr / 25f) * 100f).coerceIn(15f, 98f).toInt()

                            // Left vs Right power ratio
                            val totalPower = rmsL + rmsR
                            if (totalPower > 25) {
                                val leftRatio = (rmsL / totalPower).toFloat()
                                leftRmsPercent = (leftRatio * 100f).coerceIn(5f, 95f)
                                rightRmsPercent = (100f - leftRmsPercent)

                                // Cross-channel amplitude difference (-1.0 to +1.0)
                                val diffX = ((rmsR - rmsL) / totalPower).toFloat()

                                // High-pass frequency tilt for front vs back depth
                                val hfL = (hfSumL / maxOf(rmsL * pairs, 1.0)).toFloat()
                                val diffY = ((hfL - 0.45f) * 2.2f).coerceIn(-1f, 1f)

                                // Convert vector to angle (0° = Front/North, 90° = Right/East, 180° = Back/South, 270° = Left/West)
                                var rawAngle = (atan2(diffX.toDouble(), diffY.toDouble()) * 180.0 / PI).toFloat()
                                if (rawAngle < 0) rawAngle += 360f

                                if (currentDecibels > noiseFloorDb + 5f) {
                                    val smoothAngle = smoothAngleTransition(targetAngle, rawAngle, 0.35f)
                                    targetAngle = smoothAngle
                                    detectedSector = getSectorName(smoothAngle, compassModeEnabled)

                                    val now = System.currentTimeMillis()
                                    if (now - lastPeakTime > 220 && currentDecibels > noiseFloorDb + 5f) {
                                        lastPeakTime = now
                                        pulseTrigger = now
                                        val dist = ((currentDecibels - 30f) / 70f).coerceIn(0.25f, 0.92f)
                                        val newBlip = SoundBlip(
                                            id = now,
                                            angleDegrees = smoothAngle,
                                            distanceFraction = dist,
                                            intensity = (currentDecibels / 100f).coerceIn(0.4f, 1.0f),
                                            timestamp = now
                                        )
                                        blipList = (blipList + newBlip).takeLast(10)
                                    }
                                }
                            }

                            // 12-band equalizer spectrum visualization
                            val chunk = maxOf(pairs / 12, 1)
                            spectrumBands = List(12) { band ->
                                var bandSum = 0.0
                                for (k in 0 until chunk) {
                                    val idx = (band * chunk + k) * 2
                                    if (idx < read) {
                                        bandSum += abs(buffer[idx].toDouble())
                                    }
                                }
                                ((bandSum / chunk) / 1600.0).toFloat().coerceIn(0.08f, 1.0f)
                            }
                        } else {
                            // Mono audio calculations
                            var sum = 0.0
                            var hfSum = 0.0
                            var prev = 0
                            var zeroCrossings = 0

                            for (i in 0 until read) {
                                val s = buffer[i].toInt()
                                sum += s * s
                                hfSum += abs(s - prev)
                                if (i > 0 && ((s >= 0 && prev < 0) || (s < 0 && prev >= 0))) {
                                    zeroCrossings++
                                }
                                prev = s
                            }

                            val rms = sqrt(sum / maxOf(read, 1))
                            val db = (20 * log10(maxOf(rms * sensitivityMultiplier, 1.0))).toFloat()
                                .coerceIn(20f, 105f)
                            currentDecibels = db

                            val hz = ((zeroCrossings.toFloat() * sampleRate) / (2f * read)).coerceIn(80f, 8000f)
                            peakFrequencyHz = hz.toInt()

                            val snr = currentDecibels - noiseFloorDb
                            confidenceScorePercent = ((snr / 25f) * 100f).coerceIn(15f, 95f).toInt()

                            val hfRatio = (hfSum / maxOf(rms * read, 1.0)).toFloat()
                            val pseudoAngle = (abs(hfRatio * 360f) % 360f)

                            leftRmsPercent = 50f
                            rightRmsPercent = 50f

                            if (currentDecibels > noiseFloorDb + 2f) {
                                val smoothAngle = smoothAngleTransition(targetAngle, pseudoAngle, 0.35f)
                                targetAngle = smoothAngle
                                detectedSector = getSectorName(smoothAngle, compassModeEnabled)

                                val now = System.currentTimeMillis()
                                if (now - lastPeakTime > 220 && currentDecibels > noiseFloorDb + 5f) {
                                    lastPeakTime = now
                                    pulseTrigger = now
                                    val dist = ((currentDecibels - 30f) / 70f).coerceIn(0.25f, 0.92f)
                                    val newBlip = SoundBlip(
                                        id = now,
                                        angleDegrees = smoothAngle,
                                        distanceFraction = dist,
                                        intensity = (currentDecibels / 100f).coerceIn(0.4f, 1.0f),
                                        timestamp = now
                                    )
                                    blipList = (blipList + newBlip).takeLast(10)
                                }
                            }

                            val chunk = maxOf(read / 12, 1)
                            spectrumBands = List(12) { band ->
                                var bandSum = 0.0
                                for (k in 0 until chunk) {
                                    val idx = band * chunk + k
                                    if (idx < read) {
                                        bandSum += abs(buffer[idx].toDouble())
                                    }
                                }
                                ((bandSum / chunk) / 1600.0).toFloat().coerceIn(0.08f, 1.0f)
                            }
                        }
                    } else {
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    activeRecord.stop()
                    activeRecord.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sound Direction Detector", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(
                            text = if (isStereoSupported) "Low-Latency Dual-Mic Radar" else "Acoustic Signal Vector Radar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                actions = {
                    // Compass True North mode toggle
                    IconButton(onClick = {
                        SoundEffectManager.playClick(context)
                        compassModeEnabled = !compassModeEnabled
                    }) {
                        Icon(
                            imageVector = if (compassModeEnabled) Icons.Default.Explore else Icons.Default.CompassCalibration,
                            contentDescription = "Compass Mode",
                            tint = if (compassModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Toggle Pause / Play listening
                    IconButton(onClick = {
                        SoundEffectManager.playClick(context)
                        isListening = !isListening
                    }) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Toggle Listening",
                            tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (!hasPermission) {
            // Permission Request Card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = "Microphone Access Required",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "To locate sound origins, measure decibels, and render the real-time acoustic radar, this tool requires microphone input.\n\nAll audio stream processing is performed 100% locally on your device with zero cloud dependency.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = {
                                SoundEffectManager.playClick(context)
                                launcher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Microphone Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Main Radar Dashboard
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. HIGH-TECH 360-DEGREE ACOUSTIC RADAR DISPLAY CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Top Header with Live Telemetry Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isListening) Color(0xFF10B981) else Color(0xFFEF4444))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isListening) "RADAR ACTIVE" else "PAUSED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isListening) Color(0xFF10B981) else Color(0xFFEF4444),
                                    letterSpacing = 1.2.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E293B)
                                ) {
                                    Text(
                                        text = if (compassModeEnabled) "COMPASS NORTH" else "HEADER RELATIVE",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "${noiseFloorDb.toInt()} dB FLOOR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Central Interactive 360° Radar Canvas
                        Box(
                            modifier = Modifier
                                .size(290.dp)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val isSoundActive = currentDecibels > noiseFloorDb + 2f
                            val isFrontActive = detectedSector.contains("FRONT") || detectedSector.contains("NORTH") && isSoundActive
                            val isRightActive = (detectedSector.contains("RIGHT") || detectedSector.contains("EAST")) && !detectedSector.contains("WEST") && isSoundActive
                            val isBackActive = (detectedSector.contains("BACK") || detectedSector.contains("SOUTH")) && !detectedSector.contains("NORTH") && isSoundActive
                            val isLeftActive = (detectedSector.contains("LEFT") || detectedSector.contains("WEST")) && !detectedSector.contains("EAST") && isSoundActive

                            RadarCanvas(
                                sweepAngle = sweepAngle,
                                detectedAngle = animatedAngle,
                                decibels = currentDecibels,
                                blips = blipList,
                                pulseTrigger = pulseTrigger,
                                isListening = isListening,
                                isSoundActive = isSoundActive,
                                isCompassMode = compassModeEnabled
                            )

                            // Cardinal Marker Badges
                            CardinalMarkerBadge(
                                label = if (compassModeEnabled) "N" else "FRONT",
                                angleLabel = "0°",
                                isActive = isFrontActive,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )

                            CardinalMarkerBadge(
                                label = if (compassModeEnabled) "E" else "RIGHT",
                                angleLabel = "90°",
                                isActive = isRightActive,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )

                            CardinalMarkerBadge(
                                label = if (compassModeEnabled) "S" else "BACK",
                                angleLabel = "180°",
                                isActive = isBackActive,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )

                            CardinalMarkerBadge(
                                label = if (compassModeEnabled) "W" else "LEFT",
                                angleLabel = "270°",
                                isActive = isLeftActive,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )

                            // Central Acoustic Beacon
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A))
                                    .border(1.5.dp, if (isSoundActive) Color(0xFF38BDF8) else Color(0xFF334155), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Microphone Core",
                                    tint = if (isSoundActive) Color(0xFF38BDF8) else Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Bottom Radar Telemetry Status Grid
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF131C2E), RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ACOUSTIC SOURCE DIRECTION",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "$detectedSector (${animatedAngle.toInt()}°)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "PEAK FREQUENCY & INTENSITY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B),
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${currentDecibels.toInt()} dB SPL • $peakFrequencyHz Hz",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = when {
                                            currentDecibels > 75 -> Color(0xFFEF4444)
                                            currentDecibels > 55 -> Color(0xFFF59E0B)
                                            else -> Color(0xFF10B981)
                                        }
                                    )
                                }
                            }

                            // Signal Confidence Meter
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Detection Confidence",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = "$confidenceScorePercent%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = Color(0xFF10B981)
                                    )
                                }

                                ExpressiveWavyLinearProgressIndicator(
                                    progress = (confidenceScorePercent / 100f).coerceIn(0.05f, 1f),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF10B981),
                                    trackColor = Color(0xFF1E293B),
                                    height = 9.dp
                                )
                            }
                        }
                    }
                }

                // 2. DUAL-CHANNEL BALANCE & FREQUENCY SPECTRUM MONITOR CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                    border = ArworaDesignTokens.cardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Channel Balance & Frequency Spectrum",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Left vs Right Channel Balance
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Left Mic: ${leftRmsPercent.toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Right Mic: ${rightRmsPercent.toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(leftRmsPercent.coerceAtLeast(1f))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(rightRmsPercent.coerceAtLeast(1f))
                                        .background(MaterialTheme.colorScheme.tertiary)
                                )
                            }
                        }

                        // Real-time 12-Band Equalizer Spectrum Visualizer
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Real-Time Spectrum Envelope (12-Bands)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                spectrumBands.forEachIndexed { index, heightFraction ->
                                    val barHeight by animateFloatAsState(
                                        targetValue = heightFraction,
                                        animationSpec = tween(durationMillis = 40, easing = LinearEasing),
                                        label = "bar_$index"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(barHeight.coerceIn(0.08f, 1f))
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.tertiary
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. RADAR TUNING & CONTROLS CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Acoustic Tuning & Sensitivity",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Sensitivity Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Microphone Gain Sensitivity",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (sensitivityMultiplier) {
                                        1.0f -> "Normal (1.0x)"
                                        1.5f -> "High (1.5x)"
                                        else -> "Ultra Boost (2.5x)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1.0f to "Normal", 1.5f to "High", 2.5f to "Ultra").forEach { (multiplier, label) ->
                                    val isSelected = sensitivityMultiplier == multiplier
                                    OutlinedButton(
                                        onClick = {
                                            SoundEffectManager.playClick(context)
                                            sensitivityMultiplier = multiplier
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Interactive Action Buttons (Clean & minimal bold labels according to system rules)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    SoundEffectManager.playCoinLand(context)
                                    noiseFloorDb = currentDecibels.coerceIn(24f, 70f)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Calibrate Noise Floor", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    SoundEffectManager.playCoinToss(context)
                                    val randomAngle = (0..359).random().toFloat()
                                    targetAngle = randomAngle
                                    detectedSector = getSectorName(randomAngle, compassModeEnabled)
                                    val now = System.currentTimeMillis()
                                    pulseTrigger = now
                                    currentDecibels = 78f
                                    blipList = (blipList + SoundBlip(now, randomAngle, 0.75f, 0.9f, now)).takeLast(10)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Text("Test Acoustic Ping", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun CardinalMarkerBadge(
    label: String,
    angleLabel: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isActive) Color(0xFF0284C7) else Color(0xFF0F172A).copy(alpha = 0.85f),
        animationSpec = tween(180),
        label = "bg_$label"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (isActive) Color(0xFF38BDF8) else Color(0xFF334155).copy(alpha = 0.6f),
        animationSpec = tween(180),
        label = "border_$label"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color(0xFF94A3B8),
        animationSpec = tween(180),
        label = "text_$label"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(animatedBg)
            .border(1.dp, animatedBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = animatedTextColor,
            letterSpacing = 0.8.sp
        )
        Text(
            text = angleLabel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Normal,
            color = if (isActive) Color.White.copy(alpha = 0.85f) else Color(0xFF64748B)
        )
    }
}

@Composable
fun RadarCanvas(
    sweepAngle: Float,
    detectedAngle: Float,
    decibels: Float,
    blips: List<SoundBlip>,
    pulseTrigger: Long,
    isListening: Boolean,
    isSoundActive: Boolean,
    isCompassMode: Boolean = false
) {
    // Pulse wave expanding animation upon sound trigger
    val pulseProgress = remember(pulseTrigger) { Animatable(0f) }
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger > 0L) {
            pulseProgress.snapTo(0f)
            pulseProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2 - 12.dp.toPx()

        // 1. Concentric Range Rings (30dB, 50dB, 70dB, 90dB)
        val rings = listOf(0.25f, 0.5f, 0.75f, 1.0f)
        rings.forEach { fraction ->
            drawCircle(
                color = Color(0xFF1E293B).copy(alpha = 0.8f),
                radius = maxRadius * fraction,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // 2. Polar Grid Crosshairs (North, South, East, West)
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1.dp.toPx()
        )

        // Diagonal Guideline Dashes (45°, 135°, 225°, 315°)
        val diagAngles = listOf(45.0, 135.0, 225.0, 315.0)
        diagAngles.forEach { deg ->
            val rad = deg * PI / 180.0
            val startR = maxRadius * 0.22f
            val endR = maxRadius * 0.95f
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.45f),
                start = Offset((center.x + startR * sin(rad)).toFloat(), (center.y - startR * cos(rad)).toFloat()),
                end = Offset((center.x + endR * sin(rad)).toFloat(), (center.y - endR * cos(rad)).toFloat()),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Perimeter Compass Degree Ticks (Every 15° around the outer circle)
        for (degree in 0 until 360 step 15) {
            val rad = degree * PI / 180.0
            val isMajor = (degree % 30 == 0)
            val tickLen = if (isMajor) 7.dp.toPx() else 4.dp.toPx()
            val startR = maxRadius - tickLen
            val endR = maxRadius

            drawLine(
                color = if (isMajor) Color(0xFF38BDF8).copy(alpha = 0.6f) else Color(0xFF334155),
                start = Offset((center.x + startR * sin(rad)).toFloat(), (center.y - startR * cos(rad)).toFloat()),
                end = Offset((center.x + endR * sin(rad)).toFloat(), (center.y - endR * cos(rad)).toFloat()),
                strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
            )
        }

        // Active Acoustic Sector Glow Wedge when sound is active
        if (isSoundActive) {
            val sectorPath = Path().apply {
                moveTo(center.x, center.y)
                val halfWedge = 22.0
                val steps = 14
                for (i in 0..steps) {
                    val deg = (detectedAngle - halfWedge + (2 * halfWedge * i / steps)) * PI / 180.0
                    val x = (center.x + maxRadius * sin(deg)).toFloat()
                    val y = (center.y - maxRadius * cos(deg)).toFloat()
                    lineTo(x, y)
                }
                close()
            }
            drawPath(
                path = sectorPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.32f),
                        Color(0xFF0284C7).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                )
            )
        }

        // 3. Rotating Acoustic Radar Sweep Beam (with trailing gradient phosphor fade)
        if (isListening) {
            val sweepRad = sweepAngle * PI / 180.0
            val sweepEnd = Offset(
                x = (center.x + maxRadius * sin(sweepRad)).toFloat(),
                y = (center.y - maxRadius * cos(sweepRad)).toFloat()
            )

            // Trailing phosphor sweep arc (65-degree fading wedge)
            val sweepArcPath = Path().apply {
                moveTo(center.x, center.y)
                val trailDegrees = 65f
                val steps = 22
                for (i in 0..steps) {
                    val a = (sweepAngle - trailDegrees * (1f - i.toFloat() / steps)) * PI / 180.0
                    val x = (center.x + maxRadius * sin(a)).toFloat()
                    val y = (center.y - maxRadius * cos(a)).toFloat()
                    lineTo(x, y)
                }
                close()
            }

            drawPath(
                path = sweepArcPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0284C7).copy(alpha = 0.38f),
                        Color(0xFF0284C7).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                )
            )

            // Leading bright sweep line
            drawLine(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7).copy(alpha = 0.3f)),
                    center = center,
                    radius = maxRadius
                ),
                start = center,
                end = sweepEnd,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 4. Historical Sound Blips (fading phosphor dots of recent sound detections)
        val now = System.currentTimeMillis()
        blips.forEach { blip ->
            val age = now - blip.timestamp
            val alpha = (1.0f - (age / 3500f)).coerceIn(0.1f, 1.0f) * blip.intensity
            val blipRad = blip.angleDegrees * PI / 180.0
            val blipDist = maxRadius * blip.distanceFraction
            val blipPos = Offset(
                x = (center.x + blipDist * sin(blipRad)).toFloat(),
                y = (center.y - blipDist * cos(blipRad)).toFloat()
            )

            // Glow Aura
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = alpha * 0.35f),
                radius = 12.dp.toPx(),
                center = blipPos
            )
            // Core Dot
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = alpha),
                radius = 4.dp.toPx(),
                center = blipPos
            )
        }

        // 5. Active Sound Wave Ripples (expanding concentric arcs originating towards detected sound angle)
        if (pulseProgress.value in 0.01f..0.99f) {
            val waveVal = pulseProgress.value
            val waveRad = detectedAngle * PI / 180.0
            val targetDist = maxRadius * ((decibels - 30f) / 70f).coerceIn(0.35f, 0.92f)

            for (ring in 0..2) {
                val delayOffset = ring * 0.12f
                val effectiveVal = (waveVal - delayOffset).coerceIn(0f, 1f)
                if (effectiveVal > 0f) {
                    val currentR = targetDist * effectiveVal
                    val alpha = (1f - effectiveVal).coerceIn(0f, 1f) * 0.85f
                    val wavePos = Offset(
                        x = (center.x + currentR * sin(waveRad)).toFloat(),
                        y = (center.y - currentR * cos(waveRad)).toFloat()
                    )

                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = alpha),
                        radius = (16.dp.toPx() * (1f + effectiveVal * 1.2f)),
                        center = wavePos,
                        style = Stroke(width = maxOf(1.dp.toPx(), (2.5 - ring * 0.6).dp.toPx()))
                    )
                }
            }
        }

        // 6. Active Live Target Marker & Needle Pointer
        val angleRad = detectedAngle * PI / 180.0
        val targetDistance = (maxRadius * ((decibels - 30f) / 70f).coerceIn(0.35f, 0.92f))
        val targetPos = Offset(
            x = (center.x + targetDistance * sin(angleRad)).toFloat(),
            y = (center.y - targetDistance * cos(angleRad)).toFloat()
        )

        // Direction Vector Needle (glowing line from center to target)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.2f), Color(0xFF38BDF8)),
                start = center,
                end = targetPos
            ),
            start = center,
            end = targetPos,
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Target Reticle Box / Crosshair
        val reticleSize = 14.dp.toPx()
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.25f),
            radius = reticleSize,
            center = targetPos
        )
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = 5.dp.toPx(),
            center = targetPos
        )

        // Draw Reticle Brackets
        val cornerLen = 5.dp.toPx()
        // Top-left corner
        drawLine(Color(0xFF38BDF8), Offset(targetPos.x - reticleSize, targetPos.y - reticleSize), Offset(targetPos.x - reticleSize + cornerLen, targetPos.y - reticleSize), 1.5.dp.toPx())
        drawLine(Color(0xFF38BDF8), Offset(targetPos.x - reticleSize, targetPos.y - reticleSize), Offset(targetPos.x - reticleSize, targetPos.y - reticleSize + cornerLen), 1.5.dp.toPx())
        // Top-right corner
        drawLine(Color(0xFF38BDF8), Offset(targetPos.x + reticleSize, targetPos.y - reticleSize), Offset(targetPos.x + reticleSize - cornerLen, targetPos.y - reticleSize), 1.5.dp.toPx())
        drawLine(Color(0xFF38BDF8), Offset(targetPos.x + reticleSize, targetPos.y - reticleSize), Offset(targetPos.x + reticleSize, targetPos.y - reticleSize + cornerLen), 1.5.dp.toPx())
        // Bottom-left corner
        drawLine(Color(0xFF38BDF8), Offset(targetPos.x - reticleSize, targetPos.y + reticleSize), Offset(targetPos.x - reticleSize + cornerLen, targetPos.y + reticleSize), 1.5.dp.toPx())
        drawLine(Color(0xFF38BDF8), Offset(targetPos.x - reticleSize, targetPos.y + reticleSize), Offset(targetPos.x - reticleSize, targetPos.y + reticleSize - cornerLen), 1.5.dp.toPx())
        // Bottom-right corner
        drawLine(Color(0xFF38BDF8), Offset(targetPos.x + reticleSize, targetPos.y + reticleSize), Offset(targetPos.x + reticleSize - cornerLen, targetPos.y + reticleSize), 1.5.dp.toPx())
        drawLine(Color(0xFF38BDF8), Offset(targetPos.x + reticleSize, targetPos.y + reticleSize), Offset(targetPos.x + reticleSize, targetPos.y + reticleSize - cornerLen), 1.5.dp.toPx())
    }
}
