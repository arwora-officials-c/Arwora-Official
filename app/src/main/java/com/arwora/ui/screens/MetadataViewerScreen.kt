package com.arwora.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.ui.MainViewModel
import com.arwora.ui.components.GooglePlayCompactLinearProgress
import com.arwora.ui.components.GoogleStyleLinearLoadingCard
import com.arwora.ui.navigation.Screen
import com.arwora.utils.DetailedTag
import com.arwora.utils.ExifEditorUtils
import com.arwora.utils.FullMetadataReport
import com.arwora.utils.MediaType
import com.arwora.utils.SaveResult
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataViewerScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val sharedUri by viewModel.metadataTargetUri.collectAsStateWithLifecycle()
    var selectedMediaUri by remember { mutableStateOf<Uri?>(sharedUri) }
    var report by remember { mutableStateOf<FullMetadataReport?>(null) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isStripping by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var saveResult by remember { mutableStateOf<SaveResult?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        SoundEffectManager.playClick()
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    fun loadMedia(uri: Uri) {
        selectedMediaUri = uri
        viewModel.setMetadataTargetUri(uri)
        isLoading = true
        saveResult = null
        coroutineScope.launch(Dispatchers.IO) {
            val rep = ExifEditorUtils.readFullMetadataReport(context, uri)
            val bmp = if (rep.mediaType == MediaType.VIDEO) {
                ExifEditorUtils.getVideoThumbnail(context, uri)
            } else {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                } catch (e: Exception) {
                    null
                }
            }

            withContext(Dispatchers.Main) {
                report = rep
                thumbnailBitmap = bmp
                isLoading = false
            }
        }
    }

    LaunchedEffect(sharedUri) {
        sharedUri?.let { uri ->
            if (report == null || selectedMediaUri != uri) {
                loadMedia(uri)
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            loadMedia(uri)
        }
    }

    fun stripAllMetadataAction() {
        val source = selectedMediaUri ?: return
        if (isStripping) return
        SoundEffectManager.playClick()
        isStripping = true
        coroutineScope.launch(Dispatchers.IO) {
            val origName = report?.fileName ?: "photo.jpg"
            val cleanName = "clean_no_exif_${origName.substringBeforeLast(".")}"
            val res = ExifEditorUtils.stripAllMetadata(
                context = context,
                sourceUri = source,
                customFileName = cleanName,
                subFolder = "Cleaned Photos"
            )

            withContext(Dispatchers.Main) {
                isStripping = false
                if (res != null) {
                    saveResult = res
                    SoundEffectManager.playSuccess()
                    snackbarHostState.showSnackbar(
                        message = "All EXIF & GPS stripped! Saved to Gallery (${res.displayName})",
                        duration = SnackbarDuration.Long
                    )
                    // Reload report on the sanitized file
                    loadMedia(res.uri)
                } else {
                    SoundEffectManager.playError()
                    snackbarHostState.showSnackbar(
                        message = "Failed to strip metadata.",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Metadata Viewer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (report != null) report!!.fileName else "EXIF & Media Inspector",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
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
                        mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Pick Media")
                    }

                    if (report != null) {
                        IconButton(onClick = {
                            SoundEffectManager.playClick()
                            val text = ExifEditorUtils.generateMetadataTextReport(report!!)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Metadata Report"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Report")
                        }

                        IconButton(onClick = {
                            SoundEffectManager.playClick()
                            navController.navigate(Screen.ImageMetadataEditor.route)
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Metadata")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GoogleStyleLinearLoadingCard(
                        title = "Extracting full EXIF & media metadata...",
                        subtitle = "Scanning camera, GPS, and embedded file headers",
                        icon = Icons.Default.ImageSearch
                    )
                }
            } else if (report == null) {
                // Empty state to pick photo or video
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetadataViewerHeroCard(
                        onSelectPhoto = {
                            SoundEffectManager.playClick()
                            mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSelectVideo = {
                            SoundEffectManager.playClick()
                            mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        onSelectAny = {
                            SoundEffectManager.playClick()
                            mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    )

                    MetadataCapabilitiesCard()
                }
            } else {
                val rep = report!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Media Preview Hero & Quick Badges
                    MediaOverviewCard(
                        report = rep,
                        thumbnail = thumbnailBitmap,
                        onChangeMedia = {
                            SoundEffectManager.playClick()
                            mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        onCopyReport = {
                            val text = ExifEditorUtils.generateMetadataTextReport(rep)
                            copyToClipboard("Metadata Report", text)
                        },
                        onOpenEditor = {
                            SoundEffectManager.playClick()
                            navController.navigate(Screen.ImageMetadataEditor.route)
                        }
                    )

                    // 2. Privacy & Security Alert Banner
                    PrivacyStatusCard(
                        report = rep,
                        isStripping = isStripping,
                        onStripNow = { stripAllMetadataAction() }
                    )

                    // 3. Search and Category Filter Chips
                    MetadataSearchAndFilterSection(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it }
                    )

                    // 4. GPS & Location Section
                    if ((selectedCategory == "All" || selectedCategory == "Location (GPS)") && (rep.hasGps || searchQuery.isNotBlank())) {
                        GpsLocationCard(
                            report = rep,
                            onOpenMaps = {
                                if (rep.latitude != null && rep.longitude != null) {
                                    SoundEffectManager.playClick()
                                    try {
                                        val geoUri = Uri.parse("geo:${rep.latitude},${rep.longitude}?q=${rep.latitude},${rep.longitude}(Photo+Location)")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                        context.startActivity(Intent.createChooser(mapIntent, "Open Location in Maps"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No map application found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onCopyCoordinates = {
                                if (rep.latitude != null && rep.longitude != null) {
                                    copyToClipboard("GPS Coordinates", "${rep.latitude}, ${rep.longitude}")
                                }
                            }
                        )
                    }

                    // 5. Camera & Lens Specifications
                    if (rep.mediaType == MediaType.IMAGE && (selectedCategory == "All" || selectedCategory == "Camera & Lens")) {
                        CameraHardwareCard(report = rep, onCopy = { l, v -> copyToClipboard(l, v) })
                    }

                    // 6. Exposure & Capture Settings
                    if (rep.mediaType == MediaType.IMAGE && (selectedCategory == "All" || selectedCategory == "Exposure")) {
                        ExposureSettingsCard(report = rep, onCopy = { l, v -> copyToClipboard(l, v) })
                    }

                    // 7. Video Codec & Stream Specs (If video)
                    if (rep.mediaType == MediaType.VIDEO && (selectedCategory == "All" || selectedCategory == "Video Codec")) {
                        VideoMetadataCard(report = rep, onCopy = { l, v -> copyToClipboard(l, v) })
                    }

                    // 8. File & Image Dimensions
                    if (selectedCategory == "All" || selectedCategory == "File Specs") {
                        FileDimensionsCard(report = rep, onCopy = { l, v -> copyToClipboard(l, v) })
                    }

                    // 9. IPTC, Description & Copyright
                    if (selectedCategory == "All" || selectedCategory == "IPTC & Tags") {
                        IptcAndTagsCard(report = rep, onCopy = { l, v -> copyToClipboard(l, v) })
                    }

                    // 10. Raw EXIF Tag Explorer
                    val filteredTags = rep.allTags.filter { tag ->
                        val matchesCategory = selectedCategory == "All" || selectedCategory == "Raw Tags" || tag.category.equals(selectedCategory, ignoreCase = true)
                        val matchesSearch = searchQuery.isBlank() ||
                                tag.displayName.contains(searchQuery, ignoreCase = true) ||
                                tag.tagKey.contains(searchQuery, ignoreCase = true) ||
                                tag.value.contains(searchQuery, ignoreCase = true)
                        matchesCategory && matchesSearch
                    }

                    if (selectedCategory == "All" || selectedCategory == "Raw Tags" || searchQuery.isNotBlank()) {
                        RawTagExplorerCard(
                            tags = filteredTags,
                            totalCount = rep.allTags.size,
                            onCopyTag = { tag -> copyToClipboard(tag.displayName, tag.value) }
                        )
                    }

                    // 11. Bottom Action Card (Strip Metadata or Open Editor)
                    MetadataBottomActionCard(
                        isStripping = isStripping,
                        onStripAll = { stripAllMetadataAction() },
                        onStripAiMetadata = { stripAllMetadataAction() },
                        onOpenEditor = {
                            SoundEffectManager.playClick()
                            navController.navigate(Screen.ImageMetadataEditor.route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MetadataViewerHeroCard(
    onSelectPhoto: () -> Unit,
    onSelectVideo: () -> Unit,
    onSelectAny: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Metadata & EXIF Viewer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Examine hidden tags, GPS location, camera optics & container details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Text(
                text = "Select any picture or video from your device to perform a deep technical scan of its EXIF tags, GPS geotags, camera lens hardware, aperture, shutter speed, and timestamps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSelectPhoto,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Photo", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSelectVideo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Video", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MetadataCapabilitiesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "What You Can Inspect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val features = listOf(
                Triple(Icons.Default.LocationOn, "GPS Coordinates & Altitude", "Inspect exact latitude, longitude, and elevation. Open directly in Google Maps."),
                Triple(Icons.Default.CameraAlt, "Camera & Lens Optics", "Detect device make, model, lens profile, focal length, ISO, and aperture."),
                Triple(Icons.Default.AccessTime, "Dates & Timestamps", "Analyze date taken, digitization timestamp, UTC timezone, and file modifications."),
                Triple(Icons.Default.Security, "Privacy & Stripping", "Check if social media photos leak your address or device serial numbers, and wipe them with 1 tap.")
            )

            features.forEach { (icon, title, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun MediaOverviewCard(
    report: FullMetadataReport,
    thumbnail: Bitmap?,
    onChangeMedia: () -> Unit,
    onCopyReport: () -> Unit,
    onOpenEditor: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                // Thumbnail Box
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = "Media Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (report.mediaType == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // File Info
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = report.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${report.fileSizeFormatted} • ${report.mimeType.substringAfterLast("/")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (report.width != null && report.height != null) {
                        Text(
                            text = "${report.width} × ${report.height} px ${report.megapixels?.let { "• $it" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quick Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ViewerBadge(label = if (report.mediaType == MediaType.VIDEO) "VIDEO" else "IMAGE", color = MaterialTheme.colorScheme.primary)
                if (report.hasGps) {
                    ViewerBadge(label = "GPS TAGGED", color = MaterialTheme.colorScheme.error)
                } else {
                    ViewerBadge(label = "NO GPS", color = MaterialTheme.colorScheme.secondary)
                }
                report.aspectRatio?.let { ViewerBadge(label = "RATIO: $it") }
                report.iso?.let { ViewerBadge(label = "ISO: $it") }
                report.aperture?.let { ViewerBadge(label = it) }
                report.shutterSpeed?.let { ViewerBadge(label = it) }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeMedia,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onCopyReport,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy All", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = onOpenEditor,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Tags", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ViewerBadge(label: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = color
        )
    }
}

@Composable
fun PrivacyStatusCard(
    report: FullMetadataReport,
    isStripping: Boolean,
    onStripNow: () -> Unit
) {
    val hasSensitive = report.hasGps || !report.model.isNullOrBlank() || !report.serialNumber.isNullOrBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasSensitive) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (hasSensitive) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        )
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
                Icon(
                    imageVector = if (hasSensitive) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (hasSensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (hasSensitive) "Privacy Alert: Sensitive Data Detected" else "Privacy Status: Clean & Safe",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hasSensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = if (hasSensitive) {
                    val reasons = mutableListOf<String>()
                    if (report.hasGps) reasons.add("GPS Location (${report.latitude?.let { String.format("%.4f", it) }}, ${report.longitude?.let { String.format("%.4f", it) }})")
                    if (!report.model.isNullOrBlank()) reasons.add("Device Model (${report.make ?: ""} ${report.model})")
                    if (!report.serialNumber.isNullOrBlank()) reasons.add("Camera Serial Number")
                    "This file contains: ${reasons.joinToString(", ")}. Anyone downloading this photo can view your exact location or device info."
                } else {
                    "No GPS location tags or hardware serial numbers were found in this media."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hasSensitive) {
                Button(
                    onClick = onStripNow,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isStripping,
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
                        Text("Stripping EXIF...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Strip All Metadata Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataSearchAndFilterSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            placeholder = { Text("Search metadata tags (e.g. ISO, GPS, Model)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val categories = listOf("All", "Location (GPS)", "Camera & Lens", "Exposure", "File Specs", "IPTC & Tags", "Raw Tags")
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = {
                        SoundEffectManager.playClick()
                        onCategorySelected(cat)
                    },
                    label = { Text(cat) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }
}

@Composable
fun GpsLocationCard(
    report: FullMetadataReport,
    onOpenMaps: () -> Unit,
    onCopyCoordinates: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("GPS & Geotag Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (report.hasGps) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ViewerRowItem("Latitude", "${report.latitude}")
                    ViewerRowItem("Longitude", "${report.longitude}")
                    report.gpsDms?.let { ViewerRowItem("DMS Format", it) }
                    report.altitude?.let { ViewerRowItem("Altitude", "${String.format("%.1f", it)} meters") }
                    report.gpsDateStamp?.let { ViewerRowItem("GPS Date", it) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenMaps,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open in Maps", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onCopyCoordinates,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy GPS", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = "No GPS coordinates or location geotags found in this file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CameraHardwareCard(
    report: FullMetadataReport,
    onCopy: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Camera & Optics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViewerRowItem("Camera Make", report.make ?: "Not specified", onCopy)
                ViewerRowItem("Device Model", report.model ?: "Not specified", onCopy)
                if (!report.lensModel.isNullOrBlank()) ViewerRowItem("Lens Model", report.lensModel, onCopy)
                if (!report.lensMake.isNullOrBlank()) ViewerRowItem("Lens Make", report.lensMake, onCopy)
                if (!report.software.isNullOrBlank()) ViewerRowItem("Software / Firmware", report.software, onCopy)
                if (!report.serialNumber.isNullOrBlank()) ViewerRowItem("Serial Number", report.serialNumber, onCopy)
            }
        }
    }
}

@Composable
fun ExposureSettingsCard(
    report: FullMetadataReport,
    onCopy: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ShutterSpeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Exposure & Shot Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViewerRowItem("ISO Speed", report.iso ?: "Auto", onCopy)
                ViewerRowItem("Shutter Speed", report.shutterSpeed ?: "Auto", onCopy)
                ViewerRowItem("Aperture", report.aperture ?: "Standard", onCopy)
                ViewerRowItem("Focal Length", report.focalLength ?: "Standard", onCopy)
                if (!report.focalLength35mm.isNullOrBlank()) ViewerRowItem("35mm Equivalent", report.focalLength35mm, onCopy)
                if (!report.flash.isNullOrBlank()) ViewerRowItem("Flash", report.flash, onCopy)
                if (!report.whiteBalance.isNullOrBlank()) ViewerRowItem("White Balance", report.whiteBalance, onCopy)
                if (!report.meteringMode.isNullOrBlank()) ViewerRowItem("Metering Mode", report.meteringMode, onCopy)
                if (!report.exposureProgram.isNullOrBlank()) ViewerRowItem("Exposure Program", report.exposureProgram, onCopy)
                if (!report.exposureBias.isNullOrBlank()) ViewerRowItem("Exposure Bias", report.exposureBias, onCopy)
            }
        }
    }
}

@Composable
fun VideoMetadataCard(
    report: FullMetadataReport,
    onCopy: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Video Codec & Stream Specs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViewerRowItem("Duration", report.durationFormatted ?: "Unknown", onCopy)
                ViewerRowItem("Bitrate", report.bitrateFormatted ?: "Auto", onCopy)
                ViewerRowItem("Frame Rate", report.frameRate ?: "Variable / Auto", onCopy)
                ViewerRowItem("Video Rotation", report.videoRotation ?: "0°", onCopy)
                ViewerRowItem("Container MIME", report.mimeType, onCopy)
            }
        }
    }
}

@Composable
fun FileDimensionsCard(
    report: FullMetadataReport,
    onCopy: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AspectRatio, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("File & Geometry Specifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViewerRowItem("File Name", report.fileName, onCopy)
                ViewerRowItem("File Size", "${report.fileSizeFormatted} (${report.fileSizeBytes} B)", onCopy)
                ViewerRowItem("MIME Type", report.mimeType, onCopy)
                if (report.width != null && report.height != null) {
                    ViewerRowItem("Resolution", "${report.width} × ${report.height} px", onCopy)
                }
                if (!report.megapixels.isNullOrBlank()) ViewerRowItem("Megapixels", report.megapixels, onCopy)
                if (!report.aspectRatio.isNullOrBlank()) ViewerRowItem("Aspect Ratio", report.aspectRatio, onCopy)
                if (!report.orientation.isNullOrBlank()) ViewerRowItem("Orientation", report.orientation, onCopy)
                if (!report.colorSpace.isNullOrBlank()) ViewerRowItem("Color Space", report.colorSpace, onCopy)
                if (!report.resolutionDpi.isNullOrBlank()) ViewerRowItem("DPI Density", report.resolutionDpi, onCopy)
                if (!report.compression.isNullOrBlank()) ViewerRowItem("Compression", report.compression, onCopy)
            }
        }
    }
}

@Composable
fun IptcAndTagsCard(
    report: FullMetadataReport,
    onCopy: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("IPTC, Dates & Authorship", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!report.dateTaken.isNullOrBlank()) ViewerRowItem("Date Taken", report.dateTaken, onCopy)
                if (!report.dateDigitized.isNullOrBlank()) ViewerRowItem("Date Digitized", report.dateDigitized, onCopy)
                if (!report.dateModified.isNullOrBlank()) ViewerRowItem("Date Modified", report.dateModified, onCopy)
                if (!report.artist.isNullOrBlank()) ViewerRowItem("Artist / Author", report.artist, onCopy)
                if (!report.copyright.isNullOrBlank()) ViewerRowItem("Copyright", report.copyright, onCopy)
                if (!report.title.isNullOrBlank()) ViewerRowItem("Title / Description", report.title, onCopy)
                if (!report.userComment.isNullOrBlank()) ViewerRowItem("User Comment", report.userComment, onCopy)
            }
        }
    }
}

@Composable
fun RawTagExplorerCard(
    tags: List<DetailedTag>,
    totalCount: Int,
    onCopyTag: (DetailedTag) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Raw EXIF Tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${tags.size} of $totalCount tags",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (tags.isEmpty()) {
                Text(
                    text = "No tags matching search criteria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onCopyTag(tag) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tag.displayName,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (tag.isSensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tag.tagKey,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = tag.value,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { onCopyTag(tag) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Tag", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataBottomActionCard(
    isStripping: Boolean,
    onStripAll: () -> Unit,
    onStripAiMetadata: () -> Unit,
    onOpenEditor: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Privacy & Editing Actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // New AI-Specific Button
            Button(
                onClick = onStripAiMetadata,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isStripping,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Strip AI Generation Data", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStripAll,
                    modifier = Modifier.weight(1.2f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isStripping,
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
                        Text("Stripping...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Strip All EXIF", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onOpenEditor,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit EXIF", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ViewerRowItem(
    label: String,
    value: String,
    onCopy: ((String, String) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = onCopy != null) { onCopy?.invoke(label, value) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onCopy != null) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
