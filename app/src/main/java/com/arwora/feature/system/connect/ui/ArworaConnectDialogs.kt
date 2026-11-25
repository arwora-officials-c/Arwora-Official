package com.arwora.feature.system.connect.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arwora.feature.system.connect.data.ConnectSecurityStore
import com.arwora.feature.system.connect.data.QrMatrixEncoder
import com.arwora.feature.system.connect.model.PairingQrPayload
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.utils.SoundEffectManager
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrCodeMatrixCanvas(
    payloadText: String,
    modifier: Modifier = Modifier
) {
    val matrix = remember(payloadText) {
        runCatching { QrMatrixEncoder.encode(payloadText) }.getOrNull()
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (matrix != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("connect_qr_matrix_canvas")
                ) {
                    val count = matrix.size
                    val quiet = 2
                    val total = count + quiet * 2
                    val cell = minOf(size.width, size.height) / total.toFloat()
                    for (y in 0 until count) {
                        for (x in 0 until count) {
                            if (matrix.get(x, y)) {
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset((x + quiet) * cell, (y + quiet) * cell),
                                    size = Size(cell + 0.5f, cell + 0.5f)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "QR generation unavailable",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun ShowPairingQrDialog(
    payload: PairingQrPayload,
    onRegenerateCode: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(vertical = 16.dp),
            shape = ArworaDesignTokens.CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = ArworaDesignTokens.cardBorder(),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Scan to Pair Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${payload.deviceName} • ${payload.hostIp}:${payload.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                QrCodeMatrixCanvas(
                    payloadText = payload.toUriString(),
                    modifier = Modifier.size(210.dp)
                )
                Surface(
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Code: ${payload.pairingCode}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                onRegenerateCode()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Code", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(
                    text = "Once paired, certificates stay trusted for silent auto-accept transfers.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PairByCodeDialog(
    initialIpHint: String,
    defaultPort: Int,
    discoveredHosts: List<String>,
    isScanningLan: Boolean,
    isBusy: Boolean,
    onScanLan: () -> Unit,
    onPair: (hostIp: String, port: Int, code: String) -> Unit,
    onDismiss: () -> Unit
) {
    val subnetDefault = remember(initialIpHint) {
        val parts = initialIpHint.split(".")
        if (parts.size == 4 && initialIpHint != "127.0.0.1") "${parts[0]}.${parts[1]}.${parts[2]}." else ""
    }
    var ipText by remember { mutableStateOf(subnetDefault) }
    var portText by remember { mutableStateOf(defaultPort.toString()) }
    var codeText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
            shape = ArworaDesignTokens.CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = ArworaDesignTokens.cardBorder(),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Pair with 6-Digit Code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("Peer IP Address") },
                    placeholder = { Text("192.168.1.42") },
                    singleLine = true,
                    shape = ArworaDesignTokens.InnerTileShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_pair_ip_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { next ->
                            if (next.length <= 6 && next.all { it.isDigit() }) codeText = next
                        },
                        label = { Text("6-Digit Code") },
                        placeholder = { Text("482910") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = ArworaDesignTokens.InnerTileShape,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("connect_pair_code_input")
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { next ->
                            if (next.length <= 5 && next.all { it.isDigit() }) portText = next
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = ArworaDesignTokens.InnerTileShape,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                OutlinedButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        onScanLan()
                    },
                    enabled = !isScanningLan,
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isScanningLan) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning Wi-Fi...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Find Peers on Wi-Fi", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (discoveredHosts.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        discoveredHosts.forEach { host ->
                            AssistChip(
                                onClick = { ipText = host },
                                label = { Text(host) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull() ?: ConnectSecurityStore.DEFAULT_PORT
                            onPair(ipText.trim(), port, codeText.trim())
                        },
                        enabled = !isBusy && ipText.isNotBlank() && codeText.length == 6,
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier.testTag("connect_pair_submit_button")
                    ) {
                        Text(if (isBusy) "Pairing..." else "Connect & Trust", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RenameLocalDeviceDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember(currentName) { mutableStateOf(currentName) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(vertical = 16.dp),
            shape = ArworaDesignTokens.CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = ArworaDesignTokens.cardBorder(),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Rename This Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Connected peers receive this updated name automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 36) nameInput = it },
                    label = { Text("Broadcast Device Name") },
                    singleLine = true,
                    shape = ArworaDesignTokens.InnerTileShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_rename_device_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(nameInput.trim()) },
                        enabled = nameInput.isNotBlank(),
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier.testTag("connect_rename_confirm_button")
                    ) {
                        Text("Save Name", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

internal fun createConnectScannerPreviewView(context: Context): PreviewView {
    return PreviewView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // COMPATIBLE (TextureView) avoids SurfaceView Z-order/black-frame bugs and honors 20dp corner clipping inside Dialogs
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
        setBackgroundColor(android.graphics.Color.BLACK)
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun ConnectQrScannerDialog(
    onQrDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPerm = granted }

    val handled = remember { AtomicBoolean(false) }
    var retryNonce by remember { mutableIntStateOf(0) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .wrapContentHeight()
                .testTag("connect_qr_scanner_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = ArworaDesignTokens.cardBorder(),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Scan Arwora Connect QR",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("connect_scanner_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Scanner")
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(300.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .testTag("connect_scanner_camera_frame"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPerm) {
                            val barcodeScanner: BarcodeScanner? = remember(context) {
                                runCatching {
                                    MlKitContext.initializeIfNeeded(context.applicationContext)
                                }
                                runCatching {
                                    BarcodeScanning.getClient()
                                }.onFailure { err ->
                                    Log.w("ConnectQrScanner", "BarcodeScanning client unavailable: ${err.message}")
                                }.getOrNull()
                            }
                            val analysisExecutor = remember { Dispatchers.Default.asExecutor() }
                            val previewView = remember(context) { createConnectScannerPreviewView(context) }

                            DisposableEffect(lifecycleOwner, previewView, barcodeScanner, retryNonce) {
                                var disposed = false
                                cameraError = null
                                val providerFuture = ProcessCameraProvider.getInstance(context)
                                val mainExec = ContextCompat.getMainExecutor(context)
                                var providerRef: ProcessCameraProvider? = null
                                val isProcessing = AtomicBoolean(false)

                                previewView.post {
                                    if (disposed) return@post
                                    providerFuture.addListener({
                                        if (disposed) return@addListener
                                        runCatching {
                                            val provider = providerFuture.get()
                                            providerRef = provider
                                            val preview = Preview.Builder().build().apply {
                                                surfaceProvider = previewView.surfaceProvider
                                            }
                                            val analysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                                .apply {
                                                    setAnalyzer(analysisExecutor) { proxy ->
                                                        val media = proxy.image
                                                        val scanner = barcodeScanner
                                                        if (media == null || scanner == null || handled.get() || !isProcessing.compareAndSet(false, true)) {
                                                            proxy.close()
                                                            return@setAnalyzer
                                                        }
                                                        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                                        scanner.process(input)
                                                            .addOnSuccessListener { barcodes ->
                                                                val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
                                                                if (raw != null && PairingQrPayload.parse(raw) != null && handled.compareAndSet(false, true)) {
                                                                    mainExec.execute { onQrDetected(raw) }
                                                                }
                                                            }
                                                            .addOnFailureListener { e ->
                                                                Log.d("ConnectQrScanner", "Scan frame error: ${e.message}")
                                                            }
                                                            .addOnCompleteListener {
                                                                isProcessing.set(false)
                                                                proxy.close()
                                                            }
                                                    }
                                                }
                                            provider.unbindAll()
                                            provider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                analysis
                                            )
                                        }.onFailure { err ->
                                            Log.e("ConnectQrScanner", "Camera preview bind failed", err)
                                            cameraError = err.localizedMessage ?: "Unable to initialize camera preview"
                                        }
                                    }, mainExec)
                                }

                                onDispose {
                                    disposed = true
                                    runCatching { providerRef?.unbindAll() }
                                    runCatching { barcodeScanner?.close() }
                                }
                            }

                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("connect_scanner_preview_view")
                            )

                            ConnectScannerReticleOverlay(modifier = Modifier.fillMaxSize())

                            val activeErr = cameraError
                            if (activeErr != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.78f))
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                                ) {
                                    Text(
                                        text = activeErr,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                    OutlinedButton(
                                        onClick = { retryNonce++ },
                                        shape = ArworaDesignTokens.PillShape
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry Camera", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Camera permission is required to scan the peer QR pairing code.",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    shape = ArworaDesignTokens.PillShape,
                                    modifier = Modifier.testTag("connect_scanner_grant_camera_btn")
                                ) {
                                    Text("Grant Camera Permission", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Align the peer's Arwora Connect QR inside the viewfinder frame",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectScannerReticleOverlay(modifier: Modifier = Modifier) {
    val bracketColor = Color(0xFF38BDF8)
    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height) * 0.62f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val right = left + side
        val bottom = top + side
        val arm = side * 0.18f
        val strokeWidth = 3.5.dp.toPx()

        // Top-left
        drawLine(bracketColor, Offset(left, top + arm), Offset(left, top), strokeWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(left, top), Offset(left + arm, top), strokeWidth, StrokeCap.Round)
        // Top-right
        drawLine(bracketColor, Offset(right - arm, top), Offset(right, top), strokeWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, top), Offset(right, top + arm), strokeWidth, StrokeCap.Round)
        // Bottom-left
        drawLine(bracketColor, Offset(left, bottom - arm), Offset(left, bottom), strokeWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(left, bottom), Offset(left + arm, bottom), strokeWidth, StrokeCap.Round)
        // Bottom-right
        drawLine(bracketColor, Offset(right - arm, bottom), Offset(right, bottom), strokeWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - arm), strokeWidth, StrokeCap.Round)
    }
}

@Composable
fun DisconnectPeerConfirmationDialog(
    peerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ArworaDesignTokens.CardShape,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Default.LinkOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Disconnect Device",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Are you sure you want to disconnect from this peer?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (peerName.isNotBlank()) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    SoundEffectManager.playClick()
                    onDismiss()
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("connect_disconnect_cancel_btn")
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    SoundEffectManager.playClick()
                    onConfirm()
                },
                shape = ArworaDesignTokens.PillShape,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("connect_disconnect_confirm_btn")
            ) {
                Text(
                    text = "Disconnect",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        modifier = Modifier.testTag("connect_disconnect_confirm_dialog")
    )
}

