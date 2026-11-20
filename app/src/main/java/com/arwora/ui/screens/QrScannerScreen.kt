package com.arwora.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arwora.feature.scanner.QrScanCategory
import com.arwora.feature.scanner.QrScanParser
import com.arwora.feature.scanner.QrScanResult
import androidx.compose.runtime.rememberCoroutineScope
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaPrimaryPillButton
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.utils.SoundEffectManager
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrScannerScreen"

/**
 * Sleek Home tab card following Wi-Fi Controller Material 3 Expressive UI tokens.
 */
@Composable
fun HomeQrScannerCard(
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBgColor = ArworaDesignTokens.cardContainerColor()
    val cardShape = ArworaDesignTokens.CardShape
    val cardBorder = ArworaDesignTokens.cardBorder()
    val cardElevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_qr_scanner_card"),
        onClick = {
            SoundEffectManager.playClick()
            onOpenScanner()
        },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = cardElevation
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = Icons.Default.QrCodeScanner,
                    contentDescription = "All QR & Barcode Scanner",
                    tint = Color(0xFF38BDF8),
                    containerColor = Color(0xFF38BDF8).copy(alpha = 0.14f),
                    borderColor = Color(0xFF38BDF8).copy(alpha = 0.32f),
                    size = 46.dp,
                    iconSize = 24.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "All QR & Barcode Scanner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Scan Wi-Fi, URLs, contacts & barcodes 100% offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            ArworaPrimaryPillButton(
                text = "Open Scanner",
                onClick = {
                    SoundEffectManager.playClick()
                    onOpenScanner()
                },
                modifier = Modifier.testTag("home_open_qr_scanner_button"),
                height = ArworaDesignTokens.SecondaryButtonHeight
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    initialResult: QrScanResult? = null
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var hasTorchUnit by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var scannedResult by remember { mutableStateOf<QrScanResult?>(initialResult) }

    val coroutineScope = rememberCoroutineScope()
    val analysisPaused = remember { AtomicBoolean(initialResult != null) }
    LaunchedEffect(scannedResult) {
        analysisPaused.set(scannedResult != null)
    }

    val barcodeScanner = remember {
        BarcodeScanning.getClient()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { barcodeScanner.close() }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequestedOnce = true
        hasCameraPermission = granted
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(context, uri)
                }
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val parsed = barcodes.firstNotNullOfOrNull { QrScanParser.fromMlKitBarcode(it) }
                        if (parsed != null) {
                            SoundEffectManager.vibrate(context, 40L)
                            SoundEffectManager.playClick(context)
                            scannedResult = parsed
                        } else {
                            Toast.makeText(context, "No QR code or barcode detected in image", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to scan selected image", Toast.LENGTH_SHORT).show()
                    }
            }.onFailure {
                Toast.makeText(context, "Unable to open selected image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequestedOnce) {
            permissionRequestedOnce = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
    ) {
        if (hasCameraPermission) {
            CameraXBarcodePreview(
                barcodeScanner = barcodeScanner,
                analysisPaused = analysisPaused,
                onCameraReady = { cam ->
                    boundCamera = cam
                    hasTorchUnit = cam.cameraInfo.hasFlashUnit()
                    if (isTorchEnabled && cam.cameraInfo.hasFlashUnit()) {
                        cam.cameraControl.enableTorch(true)
                    }
                },
                onBarcodeDetected = { result ->
                    if (analysisPaused.compareAndSet(false, true)) {
                        SoundEffectManager.vibrate(context, 40L)
                        SoundEffectManager.playClick(context)
                        scannedResult = result
                    }
                }
            )

            ScannerViewfinderOverlay(
                isDetected = scannedResult != null,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("qr_scanner_viewfinder")
            )
        } else {
            CameraPermissionFallbackContent(
                onRequestPermission = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = {
                    runCatching {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                onPickFromGallery = {
                    galleryPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }

        // Top Floating Control Bar (Wi-Fi Controller tonal surface pill style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xCC111622),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                IconButton(
                    onClick = {
                        SoundEffectManager.playClick(context)
                        onBack()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("qr_scanner_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Surface(
                shape = ArworaDesignTokens.PillShape,
                color = Color(0xCC111622),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "All QR Scanner",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    ArworaPillBadge(
                        text = when {
                            scannedResult != null -> "DETECTED"
                            isTorchEnabled -> "TORCH ON"
                            hasCameraPermission -> "OFFLINE • LIVE"
                            else -> "OFFLINE"
                        },
                        tint = when {
                            scannedResult != null -> Color(0xFF10B981)
                            isTorchEnabled -> Color(0xFFF59E0B)
                            else -> Color(0xFF38BDF8)
                        },
                        showDot = hasCameraPermission
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = if (isTorchEnabled) Color(0xFFF59E0B).copy(alpha = 0.25f) else Color(0xCC111622),
                border = BorderStroke(
                    1.dp,
                    if (isTorchEnabled) Color(0xFFF59E0B).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f)
                )
            ) {
                IconButton(
                    onClick = {
                        SoundEffectManager.playClick(context)
                        val cam = boundCamera
                        if (cam != null && hasTorchUnit) {
                            val next = !isTorchEnabled
                            isTorchEnabled = next
                            cam.cameraControl.enableTorch(next)
                        } else {
                            Toast.makeText(
                                context,
                                if (hasCameraPermission) "Flashlight not available on this camera" else "Grant camera permission first",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("qr_scanner_torch_button")
                ) {
                    Icon(
                        imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (isTorchEnabled) "Turn Torch Off" else "Turn Torch On",
                        tint = if (isTorchEnabled) Color(0xFFF59E0B) else Color.White
                    )
                }
            }
        }

        // Bottom Floating Scanner Info & Photo Picker Pill Bar
        if (hasCameraPermission) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 20.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                shape = ArworaDesignTokens.CardShape,
                color = Color(0xD9111622),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (scannedResult != null) "Code Captured Locally" else "100% On-Device Offline Scanner",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Wi-Fi • URLs • Contacts • Barcodes • Works without internet",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = {
                                SoundEffectManager.playClick(context)
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = ArworaDesignTokens.PillShape,
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("qr_scanner_gallery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ImageSearch,
                                contentDescription = "Scan from Gallery",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Gallery",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    ExpressiveWavyLinearProgressIndicator(
                        progress = if (scannedResult != null) 1f else null,
                        color = if (scannedResult != null) Color(0xFF10B981) else Color(0xFF38BDF8),
                        trackColor = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_scanner_wavy_indicator")
                    )
                }
            }
        }
    }

    scannedResult?.let { result ->
        QrResultBottomSheet(
            result = result,
            onDismiss = {
                scannedResult = null
                analysisPaused.set(false)
            },
            onCopy = { QrScanParser.copyToClipboard(context, result.rawValue, result.formatLabel) },
            onShare = { QrScanParser.shareText(context, result.rawValue) },
            onPrimaryAction = {
                when (result.category) {
                    QrScanCategory.WIFI -> result.wifiDetails?.let { QrScanParser.connectWifi(context, it) }
                    QrScanCategory.URL -> QrScanParser.openUrlLocally(context, result.normalizedUrl ?: result.rawValue)
                    QrScanCategory.CONTACT -> result.contactDetails?.let { QrScanParser.saveContactLocally(context, it) }
                    QrScanCategory.PHONE -> QrScanParser.openDialerLocally(context, result.displayValue)
                    QrScanCategory.EMAIL -> QrScanParser.composeEmailLocally(context, result.rawValue)
                    else -> QrScanParser.copyToClipboard(context, result.rawValue, result.formatLabel)
                }
            }
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraXBarcodePreview(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    analysisPaused: AtomicBoolean,
    onCameraReady: (Camera) -> Unit,
    onBarcodeDetected: (QrScanResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analysisExecutor = remember { Dispatchers.Default.asExecutor() }
    val isProcessingFrame = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var providerRef: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                providerRef = cameraProvider
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(analysisExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null || analysisPaused.get() || !isProcessingFrame.compareAndSet(false, true)) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    if (!analysisPaused.get()) {
                                        val match = barcodes.firstNotNullOfOrNull {
                                            QrScanParser.fromMlKitBarcode(it)
                                        }
                                        if (match != null) {
                                            mainExecutor.execute { onBarcodeDetected(match) }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "ML Kit barcode detection error: ${e.message}")
                                }
                                .addOnCompleteListener {
                                    isProcessingFrame.set(false)
                                    imageProxy.close()
                                }
                        }
                    }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                onCameraReady(camera)
            }.onFailure { e ->
                Log.e(TAG, "Failed to bind CameraX use cases", e)
            }
        }, mainExecutor)

        onDispose {
            runCatching { providerRef?.unbindAll() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScannerViewfinderOverlay(
    isDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "qr_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )
    val accentColor = if (isDetected) Color(0xFF10B981) else Color(0xFF38BDF8)

    Canvas(modifier = modifier) {
        val boxSide = (minOf(size.width, size.height) * 0.68f).coerceAtMost(300.dp.toPx())
        val left = (size.width - boxSide) / 2f
        val top = (size.height - boxSide) / 2f - 28.dp.toPx()
        val cornerRadiusPx = 26.dp.toPx()
        val rect = Rect(left, top, left + boxSide, top + boxSide)

        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = rect,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            )
        }

        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(color = Color(0x99080B12))
        }

        drawRoundRect(
            color = accentColor.copy(alpha = 0.25f),
            topLeft = Offset(left, top),
            size = Size(boxSide, boxSide),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Draw expressive rounded corner brackets
        val strokePx = 4.dp.toPx()
        val arm = 34.dp.toPx()

        // Top-left
        drawLine(accentColor, Offset(left, top + arm), Offset(left, top + cornerRadiusPx), strokePx, StrokeCap.Round)
        drawArc(accentColor, 180f, 90f, false, Offset(left, top), Size(cornerRadiusPx * 2, cornerRadiusPx * 2), style = Stroke(strokePx, cap = StrokeCap.Round))
        drawLine(accentColor, Offset(left + cornerRadiusPx, top), Offset(left + arm, top), strokePx, StrokeCap.Round)

        // Top-right
        drawLine(accentColor, Offset(rect.right - arm, top), Offset(rect.right - cornerRadiusPx, top), strokePx, StrokeCap.Round)
        drawArc(accentColor, 270f, 90f, false, Offset(rect.right - cornerRadiusPx * 2, top), Size(cornerRadiusPx * 2, cornerRadiusPx * 2), style = Stroke(strokePx, cap = StrokeCap.Round))
        drawLine(accentColor, Offset(rect.right, top + cornerRadiusPx), Offset(rect.right, top + arm), strokePx, StrokeCap.Round)

        // Bottom-left
        drawLine(accentColor, Offset(left, rect.bottom - arm), Offset(left, rect.bottom - cornerRadiusPx), strokePx, StrokeCap.Round)
        drawArc(accentColor, 90f, 90f, false, Offset(left, rect.bottom - cornerRadiusPx * 2), Size(cornerRadiusPx * 2, cornerRadiusPx * 2), style = Stroke(strokePx, cap = StrokeCap.Round))
        drawLine(accentColor, Offset(left + cornerRadiusPx, rect.bottom), Offset(left + arm, rect.bottom), strokePx, StrokeCap.Round)

        // Bottom-right
        drawLine(accentColor, Offset(rect.right - arm, rect.bottom), Offset(rect.right - cornerRadiusPx, rect.bottom), strokePx, StrokeCap.Round)
        drawArc(accentColor, 0f, 90f, false, Offset(rect.right - cornerRadiusPx * 2, rect.bottom - cornerRadiusPx * 2), Size(cornerRadiusPx * 2, cornerRadiusPx * 2), style = Stroke(strokePx, cap = StrokeCap.Round))
        drawLine(accentColor, Offset(rect.right, rect.bottom - cornerRadiusPx), Offset(rect.right, rect.bottom - arm), strokePx, StrokeCap.Round)

        // Animated laser line inside viewfinder
        val inset = 18.dp.toPx()
        val laserY = (top + inset) + (boxSide - inset * 2) * laserProgress
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, accentColor, Color.Transparent),
                startX = left + inset,
                endX = rect.right - inset
            ),
            start = Offset(left + inset, laserY),
            end = Offset(rect.right - inset, laserY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CameraPermissionFallbackContent(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ArworaTonalIconBadge(
                    icon = Icons.Default.CameraAlt,
                    contentDescription = "Camera Permission",
                    tint = Color(0xFF38BDF8),
                    size = 56.dp,
                    iconSize = 28.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Grant camera access to scan QR codes, Wi-Fi networks, URLs, and 1D/2D barcodes in real time, or pick an image from your gallery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                ArworaPrimaryPillButton(
                    text = "Grant Camera Access",
                    onClick = onRequestPermission,
                    height = ArworaDesignTokens.SecondaryButtonHeight
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickFromGallery,
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Scan Image", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOpenSettings,
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("App Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrResultBottomSheet(
    result: QrScanResult,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onPrimaryAction: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("qr_result_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArworaTonalIconBadge(
                    icon = when (result.category) {
                        QrScanCategory.WIFI -> Icons.Default.Wifi
                        QrScanCategory.URL -> Icons.Default.Link
                        else -> Icons.Default.QrCodeScanner
                    },
                    contentDescription = result.category.badgeLabel,
                    tint = Color(0xFF38BDF8),
                    size = 46.dp,
                    iconSize = 23.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArworaPillBadge(
                            text = "${result.formatLabel.uppercase()} • ${result.category.badgeLabel.uppercase()}",
                            tint = Color(0xFF38BDF8)
                        )
                        ArworaPillBadge(
                            text = "OFFLINE",
                            tint = Color(0xFF10B981)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (result.category) {
                            QrScanCategory.WIFI -> "Wi-Fi Network Detected"
                            QrScanCategory.URL -> "Web Link Detected"
                            QrScanCategory.PRODUCT -> "Product Barcode Detected"
                            QrScanCategory.CONTACT -> "Contact Card Detected"
                            QrScanCategory.EMAIL -> "Email Address Detected"
                            QrScanCategory.PHONE -> "Phone Number Detected"
                            QrScanCategory.SMS -> "SMS Payload Detected"
                            QrScanCategory.GEO -> "Location Coordinates Detected"
                            QrScanCategory.TEXT -> "Plain Text Detected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                border = ArworaDesignTokens.cardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (result.wifiDetails != null) {
                        Text(
                            text = "Network SSID: ${result.wifiDetails.ssid}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Security: ${result.wifiDetails.securityType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (result.wifiDetails.password.isNotEmpty()) {
                            Text(
                                text = "Password: ${result.wifiDetails.password}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else if (result.contactDetails != null) {
                        if (result.contactDetails.name.isNotBlank()) {
                            Text(result.contactDetails.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        if (result.contactDetails.phone.isNotBlank()) {
                            Text("Phone: ${result.contactDetails.phone}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.contactDetails.email.isNotBlank()) {
                            Text("Email: ${result.contactDetails.email}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.contactDetails.organization.isNotBlank()) {
                            Text("Organization: ${result.contactDetails.organization}", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(
                            text = result.displayValue,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = if (result.category == QrScanCategory.PRODUCT) FontFamily.Monospace else FontFamily.Default
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (result.wifiDetails != null || result.contactDetails != null) {
                        Text(
                            text = "Raw: ${result.rawValue}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val hasLocalPrimaryAction = result.category in setOf(
                QrScanCategory.WIFI,
                QrScanCategory.URL,
                QrScanCategory.CONTACT,
                QrScanCategory.PHONE,
                QrScanCategory.EMAIL
            )
            if (hasLocalPrimaryAction) {
                val primaryLabel = when (result.category) {
                    QrScanCategory.WIFI -> "Connect to Wi-Fi"
                    QrScanCategory.URL -> "Open Link Locally"
                    QrScanCategory.CONTACT -> "Save Contact Locally"
                    QrScanCategory.PHONE -> "Open Dialer"
                    QrScanCategory.EMAIL -> "Compose Email"
                    else -> "Copy to Clipboard"
                }
                ArworaPrimaryPillButton(
                    text = primaryLabel,
                    onClick = onPrimaryAction,
                    modifier = Modifier.testTag("qr_result_primary_action_button"),
                    height = ArworaDesignTokens.SecondaryButtonHeight
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCopy,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("qr_result_copy_button"),
                    shape = ArworaDesignTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy to Clipboard", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }

                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(0.85f)
                        .height(46.dp)
                        .testTag("qr_result_share_button"),
                    shape = ArworaDesignTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("qr_result_scan_again_button"),
                shape = ArworaDesignTokens.PillShape
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Again", fontWeight = FontWeight.Bold)
            }
        }
    }
}
