package com.arwora.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.arwora.ui.MainViewModel
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.ui.components.GooglePlayCompactLinearProgress
import com.arwora.ui.components.GoogleStyleLinearLoadingCard
import com.arwora.ui.navigation.Screen
import com.arwora.utils.ExifEditorUtils
import com.arwora.utils.ExifMetadata
import com.arwora.utils.ExportFormat
import com.arwora.utils.MediaType
import com.arwora.utils.SaveResult
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMetadataEditorScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var metadata by remember { mutableStateOf<ExifMetadata?>(null) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalFileName by remember { mutableStateOf("") }
    var currentMediaType by remember { mutableStateOf(MediaType.IMAGE) }

    // Export & file settings
    var customFileName by remember { mutableStateOf("") }
    var subFolderName by remember { mutableStateOf("Media Metadata Editor") }
    var selectedFormat by remember { mutableStateOf(ExportFormat.ORIGINAL) }
    var exportQuality by remember { mutableFloatStateOf(95f) }
    var preserveLossless by remember { mutableStateOf(true) }

    // Metadata modifications
    var removeAllMetadata by remember { mutableStateOf(false) }
    var removeLocationData by remember { mutableStateOf(false) }
    var removeDeviceInfo by remember { mutableStateOf(false) }
    var makeInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf("") }
    var dateTimeInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var artistInput by remember { mutableStateOf("") }

    var showPreviewDialog by remember { mutableStateOf(false) }
    var saveResult by remember { mutableStateOf<SaveResult?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isStripping by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    fun performCompleteMetadataWipe() {
        val source = selectedMediaUri ?: return
        if (isStripping || isSaving) return
        SoundEffectManager.playClick()
        isStripping = true
        coroutineScope.launch(Dispatchers.IO) {
            val baseName = originalFileName.substringBeforeLast(".")
            val targetName = customFileName.ifBlank { "clean_no_exif_$baseName" }
            val result = ExifEditorUtils.stripAllMetadata(
                context = context,
                sourceUri = source,
                customFileName = targetName,
                subFolder = subFolderName.ifBlank { "Cleaned Photos" }
            )

            withContext(Dispatchers.Main) {
                isStripping = false
                if (result != null) {
                    saveResult = result
                    removeAllMetadata = true
                    removeLocationData = true
                    removeDeviceInfo = true
                    makeInput = ""
                    modelInput = ""
                    dateTimeInput = ""
                    titleInput = ""
                    artistInput = ""
                    metadata = metadata?.cleared() ?: ExifMetadata(null, null, null, null, null)
                    SoundEffectManager.playSuccess()
                    snackbarHostState.showSnackbar(
                        message = "All metadata stripped & saved to Gallery! (${result.displayName})",
                        duration = SnackbarDuration.Long
                    )
                } else {
                    SoundEffectManager.playError()
                    snackbarHostState.showSnackbar(
                        message = "Failed to strip metadata and export image.",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    val pickAndStripLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            saveResult = null
            isStripping = true
            coroutineScope.launch(Dispatchers.IO) {
                val detectedType = ExifEditorUtils.detectMediaType(context, uri)
                val origName = ExifEditorUtils.getOriginalFileName(context, uri)
                val meta = ExifEditorUtils.readUnifiedMetadata(context, uri)
                val bmp = if (detectedType == MediaType.VIDEO) {
                    ExifEditorUtils.getVideoThumbnail(context, uri)
                } else {
                    decodeSampledPreviewBitmap(context, uri, maxDim = 1024)
                }

                val baseName = origName.substringBeforeLast(".")
                val ext = origName.substringAfterLast(".", if (detectedType == MediaType.VIDEO) "mp4" else "jpg")
                val cleanName = "clean_no_exif_${baseName}.$ext"

                withContext(Dispatchers.Main) {
                    currentMediaType = detectedType
                    originalFileName = origName
                    customFileName = cleanName
                    metadata = meta
                    thumbnailBitmap = bmp
                }

                val result = ExifEditorUtils.stripAllMetadata(
                    context = context,
                    sourceUri = uri,
                    customFileName = cleanName,
                    subFolder = subFolderName.ifBlank { "Cleaned Photos" }
                )

                withContext(Dispatchers.Main) {
                    isStripping = false
                    if (result != null) {
                        saveResult = result
                        removeAllMetadata = true
                        removeLocationData = true
                        removeDeviceInfo = true
                        makeInput = ""
                        modelInput = ""
                        dateTimeInput = ""
                        titleInput = ""
                        artistInput = ""
                        metadata = meta.cleared()
                        SoundEffectManager.playSuccess()
                        snackbarHostState.showSnackbar(
                            message = "All metadata stripped & saved to Gallery! (${result.displayName})",
                            duration = SnackbarDuration.Long
                        )
                    } else {
                        SoundEffectManager.playError()
                        snackbarHostState.showSnackbar(
                            message = "Failed to strip metadata and export image.",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            saveResult = null
            isLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                val detectedType = ExifEditorUtils.detectMediaType(context, uri)
                val origName = ExifEditorUtils.getOriginalFileName(context, uri)
                val meta = ExifEditorUtils.readUnifiedMetadata(context, uri)
                val bmp = if (detectedType == MediaType.VIDEO) {
                    ExifEditorUtils.getVideoThumbnail(context, uri)
                } else {
                    decodeSampledPreviewBitmap(context, uri, maxDim = 1024)
                }

                withContext(Dispatchers.Main) {
                    currentMediaType = detectedType
                    originalFileName = origName
                    val baseName = origName.substringBeforeLast(".")
                    val ext = origName.substringAfterLast(".", if (detectedType == MediaType.VIDEO) "mp4" else "jpg")
                    customFileName = "clean_${baseName}.$ext"
                    metadata = meta
                    thumbnailBitmap = bmp
                    makeInput = meta.make ?: ""
                    modelInput = meta.model ?: ""
                    dateTimeInput = meta.dateTimeOriginal ?: ""
                    titleInput = meta.title ?: ""
                    artistInput = meta.artist ?: ""
                    removeAllMetadata = false
                    removeLocationData = false
                    removeDeviceInfo = false
                    preserveLossless = true
                    selectedFormat = ExportFormat.ORIGINAL
                    exportQuality = 95f
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Media Metadata Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        viewModel.setMetadataTargetUri(selectedMediaUri)
                        navController.navigate(Screen.MetadataViewer.route)
                    }) {
                        Icon(Icons.Default.Visibility, contentDescription = "Metadata Viewer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (selectedMediaUri != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                makeInput = metadata?.make ?: ""
                                modelInput = metadata?.model ?: ""
                                dateTimeInput = metadata?.dateTimeOriginal ?: ""
                                titleInput = metadata?.title ?: ""
                                artistInput = metadata?.artist ?: ""
                                val baseName = originalFileName.substringBeforeLast(".")
                                val ext = originalFileName.substringAfterLast(".", if (currentMediaType == MediaType.VIDEO) "mp4" else "jpg")
                                customFileName = "clean_${baseName}.$ext"
                                removeAllMetadata = false
                                removeLocationData = false
                                removeDeviceInfo = false
                                preserveLossless = true
                                selectedFormat = ExportFormat.ORIGINAL
                                exportQuality = 95f
                                Toast.makeText(context, "Reset to original metadata", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                performCompleteMetadataWipe()
                            },
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isStripping && !isSaving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            if (isStripping) {
                                GooglePlayCompactLinearProgress(
                                    width = 24.dp,
                                    height = 3.5.dp,
                                    color = MaterialTheme.colorScheme.onError
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wiping...", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            } else {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Strip All EXIF", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = {
                                if (isSaving || isStripping) return@Button
                                SoundEffectManager.playClick()
                                isSaving = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val source = selectedMediaUri
                                    if (source != null) {
                                        val result = if (currentMediaType == MediaType.VIDEO) {
                                            ExifEditorUtils.saveEditedVideoToGallery(
                                                context = context,
                                                sourceUri = source,
                                                customFileName = customFileName,
                                                subFolder = subFolderName,
                                                removeAllMetadata = removeAllMetadata,
                                                removeLocation = removeLocationData
                                            )
                                        } else {
                                            ExifEditorUtils.saveEditedImageToGallery(
                                                context = context,
                                                sourceUri = source,
                                                customFileName = customFileName,
                                                subFolder = subFolderName,
                                                format = selectedFormat,
                                                quality = exportQuality.toInt(),
                                                keepLossless = preserveLossless,
                                                removeAllMetadata = removeAllMetadata,
                                                removeLocation = removeLocationData,
                                                removeDevice = removeDeviceInfo,
                                                newDateTime = dateTimeInput,
                                                newMake = makeInput,
                                                newModel = modelInput
                                            )
                                        }

                                        withContext(Dispatchers.Main) {
                                            isSaving = false
                                            if (result != null) {
                                                saveResult = result
                                                snackbarHostState.showSnackbar(
                                                    message = "Saved to Gallery! (${result.displayName})",
                                                    duration = SnackbarDuration.Short
                                                )
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    message = "Failed to export media to Gallery.",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isSaving && !isStripping,
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            if (isSaving) {
                                GooglePlayCompactLinearProgress(
                                    width = 24.dp,
                                    height = 3.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Saving...", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save As", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1) Top Hero Section
                MediaHeroSection()

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GoogleStyleLinearLoadingCard(
                            title = "Loading media & EXIF metadata...",
                            subtitle = "Inspecting image and video headers",
                            icon = Icons.Default.PhotoLibrary
                        )
                    }
                } else if (selectedMediaUri == null) {
                    // Empty State Card with Media Options
                    MediaEmptyStateCard(
                        onSelectPhoto = {
                            SoundEffectManager.playClick()
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSelectVideo = {
                            SoundEffectManager.playClick()
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onSelectAny = {
                            SoundEffectManager.playClick()
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onPickAndStrip = {
                            SoundEffectManager.playClick()
                            pickAndStripLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                } else {
                    // 2) Selected Media Preview Card with Unified Badges
                    SelectedMediaPreviewCard(
                        thumbnail = thumbnailBitmap,
                        metadata = metadata,
                        fileName = originalFileName,
                        mediaType = currentMediaType,
                        onChangeMediaClick = {
                            SoundEffectManager.playClick()
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    )

                    // 3) Technical Metadata Inspector Breakdown
                    MediaTechnicalInspectorCard(
                        metadata = metadata,
                        mediaType = currentMediaType,
                        onOpenViewer = {
                            SoundEffectManager.playClick()
                            viewModel.setMetadataTargetUri(selectedMediaUri)
                            navController.navigate(Screen.MetadataViewer.route)
                        }
                    )

                    // 4) Complete Metadata Wipe Card (Primary 1-Tap Action)
                    StripAllExifCard(
                        isStripping = isStripping,
                        onStripAll = {
                            performCompleteMetadataWipe()
                        }
                    )

                    // 5) Quick Privacy Wipe Card
                    QuickPrivacyWipeCard(
                        removeAll = removeAllMetadata,
                        removeLocation = removeLocationData,
                        removeDevice = removeDeviceInfo,
                        onQuickWipe = {
                            removeAllMetadata = true
                            removeLocationData = true
                            removeDeviceInfo = true
                            makeInput = ""
                            modelInput = ""
                            Toast.makeText(context, "Privacy sanitized: GPS, device specs & EXIF stripped", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 5) Export Destination & Filename Card
                    ExportDestinationCard(
                        fileName = customFileName,
                        onFileNameChange = { customFileName = it },
                        subFolder = subFolderName,
                        onSubFolderChange = { subFolderName = it },
                        mediaType = currentMediaType
                    )

                    // 6) Format & Quality Card (For images) or Video Encoding Info (For videos)
                    if (currentMediaType == MediaType.IMAGE) {
                        ExportFormatAndQualityCard(
                            selectedFormat = selectedFormat,
                            onFormatSelected = { selectedFormat = it },
                            exportQuality = exportQuality,
                            onQualityChange = { exportQuality = it },
                            preserveLossless = preserveLossless,
                            onPreserveLosslessChange = { preserveLossless = it }
                        )
                    }

                    // 7) Date & Time Editor Card
                    DateTimeEditorCard(
                        dateTimeInput = dateTimeInput,
                        onDateTimeChanged = { dateTimeInput = it },
                        enabled = !removeAllMetadata,
                        onCurrentTimeClick = {
                            SoundEffectManager.playClick()
                            val currentTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date())
                            dateTimeInput = currentTime
                        },
                        onClearClick = {
                            SoundEffectManager.playClick()
                            dateTimeInput = ""
                        }
                    )

                    // 8) Device & Metadata Tags Card
                    if (currentMediaType == MediaType.IMAGE) {
                        DeviceInfoEditorCard(
                            makeInput = makeInput,
                            onMakeChanged = { makeInput = it },
                            modelInput = modelInput,
                            onModelChanged = { modelInput = it },
                            enabled = !removeAllMetadata && !removeDeviceInfo
                        )
                    } else {
                        VideoTagsEditorCard(
                            titleInput = titleInput,
                            onTitleChanged = { titleInput = it },
                            artistInput = artistInput,
                            onArtistChanged = { artistInput = it },
                            enabled = !removeAllMetadata
                        )
                    }

                    // 9) Privacy Toggles
                    RemoveLocationDataCard(
                        removeLocation = removeLocationData,
                        onRemoveLocationChanged = { removeLocationData = it },
                        hasGps = metadata?.hasGps == true,
                        enabled = !removeAllMetadata
                    )

                    if (currentMediaType == MediaType.IMAGE) {
                        RemoveDeviceInfoCard(
                            removeDevice = removeDeviceInfo,
                            onRemoveDeviceChanged = { removeDeviceInfo = it },
                            enabled = !removeAllMetadata
                        )
                    }

                    RemoveAllMetadataCard(
                        removeAll = removeAllMetadata,
                        onRemoveAllChanged = { removeAllMetadata = it },
                        onStripNow = { performCompleteMetadataWipe() },
                        isStripping = isStripping
                    )

                    // 10) Saved Result Success Card
                    val currentResult = saveResult
                    if (currentResult != null) {
                        SavedSuccessCard(
                            saveResult = currentResult,
                            onOpenGallery = { ExifEditorUtils.openInGallery(context, currentResult.uri, currentResult.mimeType) },
                            onShare = { ExifEditorUtils.shareMedia(context, currentResult.uri, currentResult.mimeType) }
                        )
                    }

                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        }
    }

    if (showPreviewDialog) {
        PreviewChangesDialog(
            originalFileName = originalFileName,
            customFileName = customFileName,
            subFolder = subFolderName,
            format = selectedFormat,
            quality = exportQuality.toInt(),
            removeAllMetadata = removeAllMetadata,
            removeLocationData = removeLocationData,
            removeDeviceInfo = removeDeviceInfo,
            makeInput = makeInput,
            modelInput = modelInput,
            dateTimeInput = dateTimeInput,
            metadata = metadata,
            mediaType = currentMediaType,
            onDismiss = { showPreviewDialog = false }
        )
    }
}

@Composable
fun MediaHeroSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Media Metadata & Privacy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Inspect, edit, or wipe EXIF & video tags, GPS coordinates, and camera specs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MediaEmptyStateCard(
    onSelectPhoto: () -> Unit,
    onSelectVideo: () -> Unit,
    onSelectAny: () -> Unit,
    onPickAndStrip: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        CircleShape
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PermMedia,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Select Photo or Video",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Pick any image or video from your device to inspect hidden metadata, strip GPS coordinates, sanitize camera identifiers, and export directly to your Gallery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Direct 1-tap Strip EXIF button
            Button(
                onClick = onPickAndStrip,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pick Photo & Strip All EXIF", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSelectPhoto,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Photo", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSelectVideo,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Video", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = onSelectAny,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse All Media (Photos & Videos)")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectedMediaPreviewCard(
    thumbnail: Bitmap?,
    metadata: ExifMetadata?,
    fileName: String,
    mediaType: MediaType,
    onChangeMediaClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = "Selected Media Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (mediaType == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    if (mediaType == MediaType.VIDEO) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Text(
                                text = metadata?.durationFormatted ?: "Video",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (mediaType == MediaType.VIDEO) "VIDEO" else "PHOTO",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = fileName.ifBlank { "Selected Media" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Resolution: ${metadata?.width ?: "?"} × ${metadata?.height ?: "?"} px",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!metadata?.dateTimeOriginal.isNullOrBlank()) {
                        Text(
                            text = "Date: ${metadata?.dateTimeOriginal}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Summary Chips Flow
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (mediaType == MediaType.VIDEO) {
                    if (!metadata?.durationFormatted.isNullOrBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("⏱ ${metadata.durationFormatted}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (!metadata?.bitrate.isNullOrBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("⚡ ${metadata.bitrate}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (!metadata?.frameRate.isNullOrBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("🎬 ${metadata.frameRate}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                } else {
                    val hasExif = !metadata?.make.isNullOrBlank() || !metadata?.model.isNullOrBlank() || !metadata?.dateTimeOriginal.isNullOrBlank()
                    AssistChip(
                        onClick = {},
                        label = { Text(if (hasExif) "EXIF Found" else "No EXIF", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                if (hasExif) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (hasExif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    if (!metadata?.make.isNullOrBlank() || !metadata?.model.isNullOrBlank()) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = "${metadata?.make ?: ""} ${metadata?.model ?: ""}".trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }

                // GPS Status Chip
                AssistChip(
                    onClick = {},
                    label = { Text(if (metadata?.hasGps == true) "📍 GPS Coordinates" else "No GPS Geotag", style = MaterialTheme.typography.labelSmall) }
                )
            }

            OutlinedButton(
                onClick = onChangeMediaClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Change Photo or Video")
            }
        }
    }
}

@Composable
fun MediaTechnicalInspectorCard(
    metadata: ExifMetadata?,
    mediaType: MediaType,
    onOpenViewer: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (mediaType == MediaType.VIDEO) "Video Metadata Breakdown" else "EXIF & Image Specs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (onOpenViewer != null) {
                    FilledTonalButton(
                        onClick = onOpenViewer,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Viewer", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (mediaType == MediaType.VIDEO) {
                    MetadataRowItem("Container MIME", metadata?.mimeType ?: "video/mp4")
                    MetadataRowItem("Resolution", "${metadata?.width ?: "?"} × ${metadata?.height ?: "?"} px")
                    MetadataRowItem("Duration", metadata?.durationFormatted ?: "Unknown")
                    MetadataRowItem("Bitrate", metadata?.bitrate ?: "Standard")
                    MetadataRowItem("Frame Rate", metadata?.frameRate ?: "Variable / Auto")
                    MetadataRowItem("Orientation / Rotation", metadata?.rotation ?: "0°")
                    if (!metadata?.title.isNullOrBlank()) MetadataRowItem("Title Tag", metadata.title)
                    if (!metadata?.artist.isNullOrBlank()) MetadataRowItem("Artist / Author", metadata.artist)
                    if (!metadata?.album.isNullOrBlank()) MetadataRowItem("Album / Show", metadata.album)
                } else {
                    MetadataRowItem("Camera Make", metadata?.make ?: "Not specified")
                    MetadataRowItem("Device Model", metadata?.model ?: "Not specified")
                    MetadataRowItem("ISO Speed", metadata?.iso ?: "Auto")
                    MetadataRowItem("F-Number", metadata?.fNumber ?: "Standard")
                    MetadataRowItem("Exposure Time", metadata?.exposureTime ?: "Auto")
                    MetadataRowItem("Focal Length", metadata?.focalLength ?: "Standard")
                    MetadataRowItem("Software / App", metadata?.software ?: "Standard Camera")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                if (metadata?.hasGps == true) {
                    MetadataRowItem("GPS Latitude", "${metadata.latitude}")
                    MetadataRowItem("GPS Longitude", "${metadata.longitude}")
                } else {
                    MetadataRowItem("GPS Geotag", "None (Safe from location tracking)")
                }
            }
        }
    }
}

@Composable
fun MetadataRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun StripAllExifCard(
    isStripping: Boolean,
    onStripAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Strip All EXIF",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Strip All EXIF Data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "ZERO METADATA",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Permanently strips GPS geotags, device identifiers, timestamps, and all hidden EXIF headers while keeping 100% visual image quality.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )

            // Features checklist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• GPS coordinates wiped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Device model & camera removed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• Timestamps & dates cleared", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Zero loss in visual quality", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Primary Action Button
            Button(
                onClick = onStripAll,
                enabled = !isStripping,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                if (isStripping) {
                    GooglePlayCompactLinearProgress(
                        width = 28.dp,
                        height = 4.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stripping All Metadata...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remove All Metadata",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun QuickPrivacyWipeCard(
    removeAll: Boolean,
    removeLocation: Boolean,
    removeDevice: Boolean,
    onQuickWipe: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick Privacy Sanitize",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "One-tap wipe of GPS coordinates, device identifiers, and hidden tags before social sharing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onQuickWipe,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Sanitize", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ExportDestinationCard(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    subFolder: String,
    onSubFolderChange: (String) -> Unit,
    mediaType: MediaType
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Gallery Save Location & Filename",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (mediaType == MediaType.VIDEO) Icons.Default.VideoLibrary else Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (mediaType == MediaType.VIDEO) "Save to Movies (Gallery)" else "Save to Pictures (Gallery)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (mediaType == MediaType.VIDEO) "Movies/Arwora Hyper Toolkit/${subFolder.ifBlank { "Media Metadata Editor" }}" else "Pictures/Arwora Hyper Toolkit/${subFolder.ifBlank { "Media Metadata Editor" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = fileName,
                onValueChange = onFileNameChange,
                label = { Text("Output Filename") },
                placeholder = { Text(if (mediaType == MediaType.VIDEO) "e.g. clean_video.mp4" else "e.g. edited_photo.jpg") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) }
            )

            OutlinedTextField(
                value = subFolder,
                onValueChange = onSubFolderChange,
                label = { Text("Album Subfolder") },
                placeholder = { Text("Media Metadata Editor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) }
            )
        }
    }
}

@Composable
fun ExportFormatAndQualityCard(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
    exportQuality: Float,
    onQualityChange: (Float) -> Unit,
    preserveLossless: Boolean,
    onPreserveLosslessChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Export Format & Quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Export format",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportFormat.values().forEach { fmt ->
                    FilterChip(
                        selected = selectedFormat == fmt,
                        onClick = { onFormatSelected(fmt) },
                        label = {
                            Text(
                                text = when (fmt) {
                                    ExportFormat.ORIGINAL -> "Original"
                                    ExportFormat.JPEG -> "JPEG"
                                    ExportFormat.PNG -> "PNG"
                                    ExportFormat.WEBP -> "WebP"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (selectedFormat == ExportFormat.ORIGINAL || selectedFormat == ExportFormat.JPEG) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Preserve Original Quality", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Save without recompression loss",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ArworaAnimatedSwitch(
                        checked = preserveLossless,
                        onCheckedChange = onPreserveLosslessChange
                    )
                }
            }

            if (!preserveLossless || (selectedFormat != ExportFormat.ORIGINAL && selectedFormat != ExportFormat.PNG)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Image quality",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${exportQuality.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = exportQuality,
                        onValueChange = onQualityChange,
                        valueRange = 10f..100f,
                        steps = 18
                    )
                }
            }
        }
    }
}

@Composable
fun DateTimeEditorCard(
    dateTimeInput: String,
    onDateTimeChanged: (String) -> Unit,
    enabled: Boolean,
    onCurrentTimeClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Date & Time Editor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onClearClick) {
                            Text("Clear")
                        }
                        TextButton(onClick = onCurrentTimeClick) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Use Now")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = dateTimeInput,
                onValueChange = onDateTimeChanged,
                label = { Text("Creation Date & Time") },
                placeholder = { Text("YYYY:MM:DD HH:MM:SS") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
            )

            Text(
                text = "Updates creation timestamp in media metadata.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DeviceInfoEditorCard(
    makeInput: String,
    onMakeChanged: (String) -> Unit,
    modelInput: String,
    onModelChanged: (String) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Device & Camera Info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = makeInput,
                onValueChange = onMakeChanged,
                label = { Text("Manufacturer (Make)") },
                placeholder = { Text("e.g. Apple, Google, Samsung") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) }
            )

            OutlinedTextField(
                value = modelInput,
                onValueChange = onModelChanged,
                label = { Text("Device Model") },
                placeholder = { Text("e.g. iPhone 15 Pro, Pixel 8") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }
            )
        }
    }
}

@Composable
fun VideoTagsEditorCard(
    titleInput: String,
    onTitleChanged: (String) -> Unit,
    artistInput: String,
    onArtistChanged: (String) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Video Metadata Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = titleInput,
                onValueChange = onTitleChanged,
                label = { Text("Video Title") },
                placeholder = { Text("e.g. Vacation Highlight") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) }
            )

            OutlinedTextField(
                value = artistInput,
                onValueChange = onArtistChanged,
                label = { Text("Creator / Author Tag") },
                placeholder = { Text("e.g. Anonymous / Content Creator") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )
        }
    }
}

@Composable
fun RemoveLocationDataCard(
    removeLocation: Boolean,
    onRemoveLocationChanged: (Boolean) -> Unit,
    hasGps: Boolean,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remove Location Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasGps) "📍 GPS geotags detected in media" else "Strip GPS / Geo Coordinates",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Strips coordinates and location tags from media metadata",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArworaAnimatedSwitch(
                    checked = removeLocation,
                    onCheckedChange = onRemoveLocationChanged,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
fun RemoveDeviceInfoCard(
    removeDevice: Boolean,
    onRemoveDeviceChanged: (Boolean) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remove Device Info",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Strip Make & Model",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Removes camera or phone hardware identifiers from metadata",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArworaAnimatedSwitch(
                    checked = removeDevice,
                    onCheckedChange = onRemoveDeviceChanged,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
fun RemoveAllMetadataCard(
    removeAll: Boolean,
    onRemoveAllChanged: (Boolean) -> Unit,
    onStripNow: (() -> Unit)? = null,
    isStripping: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remove All Metadata",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Strips EXIF, GPS, device info, and timestamps completely",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArworaAnimatedSwitch(
                    checked = removeAll,
                    onCheckedChange = onRemoveAllChanged
                )
            }

            if (onStripNow != null) {
                Button(
                    onClick = onStripNow,
                    enabled = !isStripping,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (isStripping) {
                        GooglePlayCompactLinearProgress(
                            width = 26.dp,
                            height = 4.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Removing All Metadata...")
                    } else {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove All Metadata Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SavedSuccessCard(
    saveResult: SaveResult,
    onOpenGallery: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saved to Gallery!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = saveResult.relativePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenGallery,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        if (saveResult.mediaType == MediaType.VIDEO) Icons.Default.VideoLibrary else Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open in Gallery")
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
fun PreviewChangesDialog(
    originalFileName: String,
    customFileName: String,
    subFolder: String,
    format: ExportFormat,
    quality: Int,
    removeAllMetadata: Boolean,
    removeLocationData: Boolean,
    removeDeviceInfo: Boolean,
    makeInput: String,
    modelInput: String,
    dateTimeInput: String,
    metadata: ExifMetadata?,
    mediaType: MediaType,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Export & Metadata Preview")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = when {
                        removeAllMetadata -> "• All metadata will be stripped"
                        removeLocationData && removeDeviceInfo -> "• GPS location & Device info will be stripped"
                        removeLocationData -> "• GPS location data will be stripped"
                        removeDeviceInfo -> "• Device make and model will be stripped"
                        else -> "• Custom metadata changes applied"
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "📁 Destination: ${if (mediaType == MediaType.VIDEO) "Movies" else "Pictures"}/Arwora Hyper Toolkit/${subFolder.ifBlank { "Media Metadata Editor" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "📄 Filename: $customFileName",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (mediaType == MediaType.IMAGE) {
                    Text(
                        text = "🎨 Format: ${format.displayName} (${quality}% Quality)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (removeAllMetadata) {
                    Text("• All EXIF metadata, GPS geotags, make, model, tags, and timestamps will be completely removed.")
                } else {
                    if (removeLocationData) {
                        Text("• GPS coordinates: (Removed)")
                    } else if (metadata?.hasGps == true) {
                        Text("• GPS coordinates: Preserved (${metadata.latitude}, ${metadata.longitude})")
                    }

                    if (mediaType == MediaType.IMAGE) {
                        if (removeDeviceInfo) {
                            Text("• Camera Make/Model: (Removed)")
                        } else {
                            Text("• Make: ${metadata?.make ?: "None"} ➔ ${if (makeInput.isBlank()) "(Cleared)" else makeInput}")
                            Text("• Model: ${metadata?.model ?: "None"} ➔ ${if (modelInput.isBlank()) "(Cleared)" else modelInput}")
                        }
                    }
                    Text("• Date/Time: ${metadata?.dateTimeOriginal ?: "None"} ➔ ${if (dateTimeInput.isBlank()) "(Cleared)" else dateTimeInput}")
                }

                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🛡️ Non-Destructive Guarantee", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Original file will stay untouched. The sanitized media is saved to a new file in your public Gallery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun decodeSampledPreviewBitmap(
    context: android.content.Context,
    uri: Uri,
    maxDim: Int = 1024
): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        var sampleSize = 1
        if (bounds.outWidth > maxDim || bounds.outHeight > maxDim) {
            val halfW = bounds.outWidth / 2
            val halfH = bounds.outHeight / 2
            while ((halfW / sampleSize) >= maxDim && (halfH / sampleSize) >= maxDim) {
                sampleSize *= 2
            }
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    } catch (_: Exception) {
        null
    }
}
