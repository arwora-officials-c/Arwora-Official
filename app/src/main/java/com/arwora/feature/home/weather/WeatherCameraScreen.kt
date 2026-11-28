package com.arwora.feature.home.weather

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WeatherCameraScreen(
    onBack: () -> Unit,
    proViewModel: WeatherProViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by proViewModel.uiState.collectAsStateWithLifecycle()

    val fallbackSnapshot = remember {
        WeatherSnapshot(
            temperatureCelsius = 24,
            highTempCelsius = 28,
            lowTempCelsius = 19,
            humidityPercent = 64,
            windSpeedKmh = 10,
            weatherCode = 0,
            conditionLabel = "Clear Sky",
            conditionType = WeatherConditionType.CLEAR,
            isDay = true,
            localityName = "Sunamganj",
            locationSource = WeatherLocationSource.DEFAULT_CITY,
            latitude = 25.0658,
            longitude = 91.3950,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    val activeSnapshot = when (val s = uiState) {
        is ProWeatherUiState.Success -> s.bundle.current
        is ProWeatherUiState.Loading -> s.cached?.current ?: fallbackSnapshot
        is ProWeatherUiState.Error -> fallbackSnapshot
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        proViewModel.ensureInitialized()
        onDispose { }
    }

    Scaffold(
        containerColor = Color.Black,
        modifier = Modifier.testTag("weather_camera_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!hasCameraPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Weather Camera Access",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Allow camera access to stamp live temperature, weather condition, and city onto your photos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            SoundEffectManager.playClick()
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("weather_camera_grant_btn")
                    ) {
                        Text("Allow Camera", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onBack,
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Go Back", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else if (capturedBitmap != null) {
                val bmp = capturedBitmap!!
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Captured Weather Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            capturedBitmap = null
                            savedUri = null
                        },
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Take Another", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Button(
                        onClick = {
                            val uri = savedUri ?: return@Button
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Weather Photo"))
                        },
                        enabled = savedUri != null,
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Photo", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                WeatherCameraPreviewView(
                    lensFacing = lensFacing,
                    onImageCaptureReady = { imageCaptureRef = it }
                )

                // Live Weather Watermark HUD Preview at Bottom
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 110.dp),
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = Color(0xBB0F172A)),
                    border = ArworaDesignTokens.cardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${activeSnapshot.temperatureCelsius}°C • ${activeSnapshot.conditionLabel}",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${activeSnapshot.localityName} • Humidity ${activeSnapshot.humidityPercent}% • Wind ${activeSnapshot.windSpeedKmh} km/h",
                                color = Color(0xFFCBD5E1),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        val visual = resolveWeatherVisual(activeSnapshot.conditionType, activeSnapshot.isDay)
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = activeSnapshot.conditionLabel,
                            tint = visual.tint,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Shutter & Camera Flip Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x88000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = Color.White
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            val capture = imageCaptureRef ?: return@FloatingActionButton
                            if (isCapturing) return@FloatingActionButton
                            isCapturing = true
                            SoundEffectManager.playClick()
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        scope.launch(Dispatchers.Default) {
                                            val rawBmp = imageProxyToBitmap(image)
                                            image.close()
                                            if (rawBmp != null) {
                                                val stamped = WeatherCameraHelper.stampWeatherOverlayOnBitmap(rawBmp, activeSnapshot)
                                                val uri = WeatherCameraHelper.saveStampedBitmapToGallery(context, stamped)
                                                withContext(Dispatchers.Main) {
                                                    capturedBitmap = stamped
                                                    savedUri = uri
                                                    isCapturing = false
                                                    Toast.makeText(
                                                        context,
                                                        if (uri != null) "Saved weather photo to Gallery" else "Stamped preview ready",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) { isCapturing = false }
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("weather_camera_shutter_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture Weather Photo",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.size(52.dp))
                }
            }

            // Top Back Bar
            Surface(
                shape = CircleShape,
                color = Color(0x880F172A),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherCameraPreviewView(
    lensFacing: Int,
    onImageCaptureReady: (ImageCapture) -> Unit
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

    DisposableEffect(lifecycleOwner, lensFacing) {
        val future = ProcessCameraProvider.getInstance(context)
        var providerRef: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val provider = future.get()
                providerRef = provider
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                onImageCaptureReady(capture)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerRef?.unbindAll() }
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rot = image.imageInfo.rotationDegrees
    if (rot == 0) return decoded
    val matrix = Matrix().apply { postRotate(rot.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
