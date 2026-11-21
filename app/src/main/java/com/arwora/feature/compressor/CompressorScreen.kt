package com.arwora.feature.compressor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.ui.components.AppCard
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaPrimaryPillButton
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.components.ExpressiveCompactLoadingIndicator
import com.arwora.ui.components.ExpressiveLoadingIndicator
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.utils.SoundEffectManager
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompressorScreen(
    initialTypeId: String,
    onBack: () -> Unit,
    onOpenCompressionSettings: (() -> Unit)? = null,
    viewModel: CompressorViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialTypeId) {
        viewModel.selectCompressorType(CompressorType.fromId(initialTypeId))
    }

    LaunchedEffect(state.savedSuccessNote) {
        val note = state.savedSuccessNote
        if (!note.isNullOrBlank()) {
            Toast.makeText(context, note, Toast.LENGTH_LONG).show()
            viewModel.clearSavedNote()
        }
    }

    val singlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onFilesSelected(listOf(uri))
        }
    }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(uris)
        }
    }

    fun launchPicker() {
        SoundEffectManager.playClick()
        val mimes = state.activeType.pickerMimeTypes
        val isMulti = state.activeType == CompressorType.ARCHIVE ||
            state.activeType == CompressorType.BATCH
        if (isMulti) {
            multiPicker.launch(mimes)
        } else {
            singlePicker.launch(mimes)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.activeType.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.activeType.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (onOpenCompressionSettings != null) {
                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                onOpenCompressionSettings()
                            },
                            modifier = Modifier.testTag("compressor_global_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Global Compression Settings"
                            )
                        }
                    }
                    ArworaPillBadge(
                        text = state.activeType.badge,
                        tint = state.activeType.accentColor,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Mode Switcher Chips
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("compressor_mode_strip"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CompressorType.entries) { type ->
                            val selected = type == state.activeType
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    SoundEffectManager.playClick()
                                    viewModel.selectCompressorType(type)
                                },
                                label = {
                                    Text(
                                        text = type.title.removeSuffix(" Compressor"),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null,
                                        tint = if (selected) type.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = ArworaDesignTokens.PillShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = type.accentColor.copy(alpha = 0.16f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }

                // 2. File Picker & Selected Queue Card
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ArworaTonalIconBadge(
                                    icon = state.activeType.icon,
                                    tint = state.activeType.accentColor,
                                    size = 46.dp,
                                    iconSize = 24.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (state.selectedFiles.isEmpty()) "Select Input Source" else "${state.selectedFiles.size} File(s) Ready",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Supported: ${state.activeType.subtitle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { launchPicker() },
                                    enabled = !state.isCompressing,
                                    shape = ArworaDesignTokens.PillShape,
                                    modifier = Modifier.testTag("compressor_pick_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (state.selectedFiles.isEmpty()) "Choose" else "Change",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (state.isResolvingFiles) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ExpressiveCompactLoadingIndicator(
                                        size = 20.dp,
                                        color = state.activeType.accentColor
                                    )
                                    Text(
                                        text = "Inspecting selected file metadata...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (state.selectedFiles.isEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ArworaDesignTokens.InnerTileShape)
                                        .clickable(enabled = !state.isCompressing) { launchPicker() },
                                    shape = ArworaDesignTokens.InnerTileShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    border = ArworaDesignTokens.innerTileBorder()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 22.dp, horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.InsertDriveFile,
                                            contentDescription = null,
                                            tint = state.activeType.accentColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = if (state.activeType == CompressorType.BATCH || state.activeType == CompressorType.ARCHIVE) {
                                                "Tap to pick one or multiple files"
                                            } else {
                                                "Tap to pick a file from device storage"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Processed locally on device — zero uploads",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.selectedFiles.forEach { fileItem ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(ArworaDesignTokens.InnerTileShape)
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                                                .border(ArworaDesignTokens.innerTileBorder(), ArworaDesignTokens.InnerTileShape)
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = fileItem.detectedType.icon,
                                                contentDescription = null,
                                                tint = fileItem.detectedType.accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = fileItem.fileName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${formatFileSize(fileItem.originalSizeBytes)} • ${fileItem.detectedType.badge}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!state.isCompressing) {
                                                IconButton(
                                                    onClick = { viewModel.removeSelectedFile(fileItem.uri) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = "Remove ${fileItem.fileName}",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
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

                // 3. Specialized Engine Configuration Card
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ArworaTonalIconBadge(
                                    icon = Icons.Filled.Tune,
                                    tint = state.activeType.accentColor,
                                    size = 38.dp,
                                    iconSize = 19.dp,
                                    cornerRadius = 12.dp
                                )
                                Column {
                                    Text(
                                        text = "Engine Parameters",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = engineSubtitle(state.activeType),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                            SpecializedOptionsContent(state = state, viewModel = viewModel)
                        }
                    }
                }

                // 4. Primary Action CTA Button
                item {
                    ArworaPrimaryPillButton(
                        text = if (state.isCompressing) "Compressing..." else "Compress Now",
                        enabled = state.selectedFiles.isNotEmpty() && !state.isCompressing && !state.isResolvingFiles,
                        containerColor = state.activeType.accentColor,
                        contentColor = Color(0xFF0B0B0F),
                        onClick = {
                            SoundEffectManager.playClick()
                            viewModel.startCompression()
                        },
                        modifier = Modifier.testTag("compressor_action_button")
                    )
                }

                // 5. Expressive Progress Card (Material 3 Expressive Loading + Wavy Progress)
                item {
                    AnimatedVisibility(visible = state.isCompressing) {
                        AppCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("compressor_progress_card")
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
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ExpressiveLoadingIndicator(
                                        size = 36.dp,
                                        color = state.activeType.accentColor,
                                        showContainer = false
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Running Compression Engine",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = state.statusText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    ArworaPillBadge(
                                        text = "${(state.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                                        tint = state.activeType.accentColor
                                    )
                                }

                                ExpressiveWavyLinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    progress = state.progress.coerceIn(0f, 1f),
                                    color = state.activeType.accentColor,
                                    trackColor = state.activeType.accentColor.copy(alpha = 0.22f),
                                    height = 10.dp
                                )

                                OutlinedButton(
                                    onClick = { viewModel.cancelCompression() },
                                    shape = ArworaDesignTokens.PillShape,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }

                // 6. Error Feedback Card
                if (!state.errorMessage.isNullOrBlank()) {
                    item {
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 7. Batch Live Queue Status (when Batch active & has entries)
                if (state.activeType == CompressorType.BATCH && state.batchEntries.isNotEmpty() && (state.isCompressing || state.results.isNotEmpty())) {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Batch Queue Status",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                state.batchEntries.forEach { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.fileName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = when (entry.status) {
                                                    BatchItemStatus.QUEUED -> "Queued (${entry.detectedType.badge})"
                                                    BatchItemStatus.PROCESSING -> "Running ${entry.detectedType.title}..."
                                                    BatchItemStatus.COMPLETED -> "${formatFileSize(entry.originalBytes)} → ${formatFileSize(entry.compressedBytes)}"
                                                    BatchItemStatus.FAILED -> entry.errorNote ?: "Failed"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (entry.status == BatchItemStatus.FAILED) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        when (entry.status) {
                                            BatchItemStatus.PROCESSING -> ExpressiveCompactLoadingIndicator(
                                                size = 18.dp,
                                                color = entry.detectedType.accentColor
                                            )
                                            BatchItemStatus.COMPLETED -> Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Done",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            BatchItemStatus.FAILED -> Icon(
                                                imageVector = Icons.Filled.ErrorOutline,
                                                contentDescription = "Failed",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            BatchItemStatus.QUEUED -> ArworaPillBadge(
                                                text = entry.detectedType.badge,
                                                tint = entry.detectedType.accentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. Compressed Output Results
                state.batchZipResult?.let { zipRes ->
                    item {
                        CompressedResultCard(
                            title = "All Batch Files Bundled (ZIP)",
                            result = zipRes,
                            accent = CompressorType.BATCH.accentColor,
                            onSave = { viewModel.saveResultToDownloads(zipRes) },
                            onShare = {
                                val intent = CompressorEngines.buildShareIntent(context, zipRes)
                                context.startActivity(android.content.Intent.createChooser(intent, "Share ${zipRes.outputName}"))
                            }
                        )
                    }
                }

                items(state.results) { res ->
                    CompressedResultCard(
                        title = res.outputName,
                        result = res,
                        accent = state.activeType.accentColor,
                        onSave = { viewModel.saveResultToDownloads(res) },
                        onShare = {
                            val intent = CompressorEngines.buildShareIntent(context, res)
                            context.startActivity(android.content.Intent.createChooser(intent, "Share ${res.outputName}"))
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

private fun engineSubtitle(type: CompressorType): String = when (type) {
    CompressorType.VIDEO -> "Hardware MediaCodec H.264 video scaling & bitrate cap"
    CompressorType.AUDIO -> "MediaCodec PCM decode + AAC-LC bitrate reduction"
    CompressorType.IMAGE -> "EXIF-safe Bitmap downsampling & format re-encoding"
    CompressorType.PDF -> "PdfRenderer page raster optimization & compact streams"
    CompressorType.ARCHIVE -> "ZipOutputStream DEFLATE / GZIP multi-file packaging"
    CompressorType.FILE -> "Structural HTML/JSON/XML/CSS minifier + optional GZIP"
    CompressorType.GIF -> "GIF89a frame-skipper, scale & adaptive LZW quantizer"
    CompressorType.BATCH -> "Auto-routed parallel Dispatchers.IO compression queue"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecializedOptionsContent(
    state: CompressorUiState,
    viewModel: CompressorViewModel
) {
    val accent = state.activeType.accentColor
    when (state.activeType) {
        CompressorType.VIDEO -> {
            val opts = state.videoOptions
            Text("Target Resolution", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_resolution_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(720 to "720p HD", 480 to "480p SD", 360 to "360p Compact").forEach { (h, label) ->
                    val selected = opts.targetHeight == h
                    Surface(
                        selected = selected,
                        onClick = {
                            SoundEffectManager.playClick()
                            viewModel.updateVideoOptions { it.copy(targetHeight = h) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("video_res_$h"),
                        shape = ArworaDesignTokens.PillShape,
                        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Text("Target Bitrate: ${opts.bitrateKbps} kbps", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Slider(
                value = opts.bitrateKbps.toFloat(),
                onValueChange = { v -> viewModel.updateVideoOptions { it.copy(bitrateKbps = v.roundToInt()) } },
                valueRange = 500f..4000f,
                steps = 6
            )
            OptionSwitchRow(
                label = "Preserve Audio Track",
                checked = opts.keepAudio,
                onCheckedChange = { checked -> viewModel.updateVideoOptions { it.copy(keepAudio = checked) } }
            )
        }

        CompressorType.AUDIO -> {
            val opts = state.audioOptions
            Text("AAC Target Bitrate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(64, 96, 128, 192).forEach { kbps ->
                    FilterChip(
                        selected = opts.bitrateKbps == kbps,
                        onClick = { viewModel.updateAudioOptions { it.copy(bitrateKbps = kbps) } },
                        label = { Text("$kbps kbps") },
                        shape = ArworaDesignTokens.PillShape
                    )
                }
            }
            Text("Sample Rate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(44100 to "44.1 kHz", 22050 to "22.05 kHz").forEach { (hz, label) ->
                    FilterChip(
                        selected = opts.sampleRateHz == hz,
                        onClick = { viewModel.updateAudioOptions { it.copy(sampleRateHz = hz) } },
                        label = { Text(label) },
                        shape = ArworaDesignTokens.PillShape
                    )
                }
            }
            OptionSwitchRow(
                label = "Downmix to Mono",
                checked = opts.mono,
                onCheckedChange = { mono -> viewModel.updateAudioOptions { it.copy(mono = mono) } }
            )
        }

        CompressorType.IMAGE -> {
            val opts = state.imageOptions
            Text("Output Format", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageOutputFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = opts.format == fmt,
                        onClick = { viewModel.updateImageOptions { it.copy(format = fmt) } },
                        label = { Text(fmt.label) },
                        shape = ArworaDesignTokens.PillShape
                    )
                }
            }
            Text("Compression Quality: ${opts.quality}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Slider(
                value = opts.quality.toFloat(),
                onValueChange = { v -> viewModel.updateImageOptions { it.copy(quality = v.roundToInt()) } },
                valueRange = 20f..100f
            )
            Text("Max Dimension", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4096 to "Original", 1600 to "1600px", 1280 to "1280px", 800 to "800px").forEach { (px, label) ->
                    FilterChip(
                        selected = opts.maxDimensionPx == px,
                        onClick = { viewModel.updateImageOptions { it.copy(maxDimensionPx = px) } },
                        label = { Text(label) },
                        shape = ArworaDesignTokens.PillShape
                    )
                }
            }
        }

        CompressorType.PDF -> {
            val opts = state.pdfOptions
            Text("Optimization Level", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompressorQualityPreset.entries.forEach { preset ->
                    val selected = opts.preset == preset
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArworaDesignTokens.InnerTileShape)
                            .clickable { viewModel.updatePdfOptions { it.copy(preset = preset) } },
                        shape = ArworaDesignTokens.InnerTileShape,
                        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(preset.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ArworaPillBadge(text = "Q${PdfCompressionOptions(preset).jpegQuality}%", tint = accent)
                        }
                    }
                }
            }
        }

        CompressorType.ARCHIVE -> {
            val opts = state.archiveOptions
            Text("Deflate Compression Level: ${opts.level}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Slider(
                value = opts.level.toFloat(),
                onValueChange = { v -> viewModel.updateArchiveOptions { it.copy(level = v.roundToInt()) } },
                valueRange = 1f..9f,
                steps = 7
            )
            OptionSwitchRow(
                label = "Use GZIP (.gz) for single file",
                checked = opts.useGzipForSingleFile,
                onCheckedChange = { checked -> viewModel.updateArchiveOptions { it.copy(useGzipForSingleFile = checked) } }
            )
        }

        CompressorType.FILE -> {
            val opts = state.fileOptions
            OptionSwitchRow(
                label = "Minify JSON / HTML / XML / CSS whitespace",
                checked = opts.minifyCode,
                onCheckedChange = { checked -> viewModel.updateFileOptions { it.copy(minifyCode = checked) } }
            )
            OptionSwitchRow(
                label = "Package output with GZIP (.gz)",
                checked = opts.wrapWithGzip,
                onCheckedChange = { checked -> viewModel.updateFileOptions { it.copy(wrapWithGzip = checked) } }
            )
        }

        CompressorType.GIF -> {
            val opts = state.gifOptions
            Text("Output Scale: ${opts.scalePercent}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Slider(
                value = opts.scalePercent.toFloat(),
                onValueChange = { v -> viewModel.updateGifOptions { it.copy(scalePercent = v.roundToInt()) } },
                valueRange = 35f..100f
            )
            Text("Frame Retention", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Keep All", 2 to "1/2 Frames", 3 to "1/3 Frames").forEach { (skip, label) ->
                    FilterChip(
                        selected = opts.frameSkip == skip,
                        onClick = { viewModel.updateGifOptions { it.copy(frameSkip = skip) } },
                        label = { Text(label) },
                        shape = ArworaDesignTokens.PillShape
                    )
                }
            }
            Text("Color Palette Size", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(64, 128, 256).forEach { colors ->
                    FilterChip(
                        selected = opts.maxColors == colors,
                        onClick = { viewModel.updateGifOptions { it.copy(maxColors = colors) } },
                        label = { Text("$colors colors") },
                        shape = ArworaDesignTokens.PillShape
                    )
                }
            }
        }

        CompressorType.BATCH -> {
            Text(
                text = "Files are automatically routed to their specialized engine (Video, Audio, Image, PDF, GIF, Markup, or ZIP) concurrently on background workers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OptionSwitchRow(
                label = "Bundle all compressed outputs into a single ZIP",
                checked = state.batchBundleAsZip,
                onCheckedChange = { bundle -> viewModel.setBatchBundleAsZip(bundle) }
            )
        }
    }
}

@Composable
private fun OptionSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CompressedResultCard(
    title: String,
    result: CompressedOutputResult,
    accent: Color,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val savedPositive = result.savingsPercent > 0f
    val pctText = if (savedPositive) {
        String.format(Locale.US, "-%.1f%% Saved", result.savingsPercent)
    } else {
        "Optimized"
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compressor_result_card"),
        borderColor = Color(0xFF10B981).copy(alpha = 0.45f)
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${result.engineUsed} • ${result.summaryNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                ArworaPillBadge(
                    text = pctText,
                    tint = if (savedPositive) Color(0xFF10B981) else accent
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SizeMetricTile(
                    label = "Original",
                    value = formatFileSize(result.originalBytes),
                    modifier = Modifier.weight(1f)
                )
                SizeMetricTile(
                    label = "Compressed",
                    value = formatFileSize(result.compressedBytes),
                    highlight = true,
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        onSave()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = ArworaDesignTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF0B0B0F)
                    )
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        onShare()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = ArworaDesignTokens.PillShape
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SizeMetricTile(
    label: String,
    value: String,
    highlight: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = ArworaDesignTokens.InnerTileShape,
        color = if (highlight) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = BorderStroke(
            1.dp,
            if (highlight) accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) accent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
