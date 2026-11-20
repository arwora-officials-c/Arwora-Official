package com.arwora.feature.compressor

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.ui.components.AppCard
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.SoundEffectManager
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompressionSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        CompressionDefaultsStore.ensureLoaded(context)
    }

    val settings by CompressionDefaultsStore.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Compression Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Global defaults for Compressor Hub",
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
                    TextButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            CompressionDefaultsStore.resetToDefaults(context)
                            Toast.makeText(
                                context,
                                "Compression defaults restored",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.testTag("compression_settings_reset_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                    .widthIn(max = 640.dp)
                    .testTag("compression_settings_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Overview card
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ArworaTonalIconBadge(
                                icon = Icons.Default.Tune,
                                tint = MaterialTheme.colorScheme.primary,
                                size = 44.dp,
                                iconSize = 22.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Global Profile",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = settings.summaryLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("compression_settings_summary")
                                )
                            }
                            ArworaPillBadge(text = "AUTO-SAVED")
                        }
                    }
                }

                // 1. IMAGE DEFAULTS
                item {
                    EngineDefaultsCard(
                        title = "Image Defaults",
                        subtitle = "Default output format, JPEG/WebP quality & max size",
                        icon = Icons.Default.Image,
                        accentColor = CompressorType.IMAGE.accentColor,
                        badge = settings.imageFormat.label.uppercase()
                    ) {
                        Text(
                            text = "Default Output Format",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ImageOutputFormat.entries.forEach { fmt ->
                                EngineChip(
                                    label = fmt.label,
                                    selected = settings.imageFormat == fmt,
                                    accent = CompressorType.IMAGE.accentColor,
                                    testTag = "cs_image_format_${fmt.name.lowercase()}",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(imageFormat = fmt)
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Default Quality Level",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${settings.imageQuality}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = CompressorType.IMAGE.accentColor,
                                modifier = Modifier.testTag("cs_image_quality_value")
                            )
                        }
                        Slider(
                            value = settings.imageQuality.toFloat(),
                            onValueChange = { raw ->
                                CompressionDefaultsStore.update(context) {
                                    it.copy(imageQuality = raw.roundToInt().coerceIn(25, 95))
                                }
                            },
                            valueRange = 25f..95f,
                            modifier = Modifier.testTag("cs_image_quality_slider")
                        )

                        Text(
                            text = "Default Max Dimension",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1080, 1600, 2048, 0).forEach { dim ->
                                EngineChip(
                                    label = if (dim == 0) "Original" else "$dim px",
                                    selected = settings.imageMaxDimensionPx == dim,
                                    accent = CompressorType.IMAGE.accentColor,
                                    testTag = "cs_image_dim_$dim",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(imageMaxDimensionPx = dim)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. VIDEO DEFAULTS
                item {
                    EngineDefaultsCard(
                        title = "Video Defaults",
                        subtitle = "Target H.264 bitrate, resolution cap & audio track",
                        icon = Icons.Default.VideoFile,
                        accentColor = CompressorType.VIDEO.accentColor,
                        badge = "${settings.videoTargetHeight}P"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Target Video Bitrate",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${settings.videoBitrateKbps} kbps",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = CompressorType.VIDEO.accentColor,
                                modifier = Modifier.testTag("cs_video_bitrate_value")
                            )
                        }
                        Slider(
                            value = settings.videoBitrateKbps.toFloat(),
                            onValueChange = { raw ->
                                val snapped = ((raw / 100f).roundToInt() * 100).coerceIn(500, 5000)
                                CompressionDefaultsStore.update(context) {
                                    it.copy(videoBitrateKbps = snapped)
                                }
                            },
                            valueRange = 500f..5000f,
                            modifier = Modifier.testTag("cs_video_bitrate_slider")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(800, 1400, 2500, 4000).forEach { kbps ->
                                EngineChip(
                                    label = "$kbps kbps",
                                    selected = settings.videoBitrateKbps == kbps,
                                    accent = CompressorType.VIDEO.accentColor,
                                    testTag = "cs_video_bitrate_$kbps",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(videoBitrateKbps = kbps)
                                        }
                                    }
                                )
                            }
                        }

                        Text(
                            text = "Default Max Resolution",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(480, 720, 1080).forEach { h ->
                                EngineChip(
                                    label = "${h}p",
                                    selected = settings.videoTargetHeight == h,
                                    accent = CompressorType.VIDEO.accentColor,
                                    testTag = "cs_video_height_$h",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(videoTargetHeight = h)
                                        }
                                    }
                                )
                            }
                        }

                        SwitchRow(
                            title = "Keep Audio Track by Default",
                            subtitle = "Pass through audio stream during video transcode",
                            checked = settings.videoKeepAudio,
                            testTag = "cs_video_keep_audio_switch",
                            onCheckedChange = { checked ->
                                CompressionDefaultsStore.update(context) {
                                    it.copy(videoKeepAudio = checked)
                                }
                            }
                        )
                    }
                }

                // 3. AUDIO DEFAULTS
                item {
                    EngineDefaultsCard(
                        title = "Audio Defaults",
                        subtitle = "AAC-LC bitrate, sample rate & channel layout",
                        icon = Icons.Default.Audiotrack,
                        accentColor = CompressorType.AUDIO.accentColor,
                        badge = "${settings.audioBitrateKbps}K"
                    ) {
                        Text(
                            text = "Target AAC Bitrate",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(64, 96, 128, 192).forEach { kbps ->
                                EngineChip(
                                    label = "$kbps kbps",
                                    selected = settings.audioBitrateKbps == kbps,
                                    accent = CompressorType.AUDIO.accentColor,
                                    testTag = "cs_audio_bitrate_$kbps",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(audioBitrateKbps = kbps)
                                        }
                                    }
                                )
                            }
                        }

                        Text(
                            text = "Default Sample Rate",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(22050 to "22.05 kHz", 44100 to "44.1 kHz", 48000 to "48 kHz").forEach { (hz, label) ->
                                EngineChip(
                                    label = label,
                                    selected = settings.audioSampleRateHz == hz,
                                    accent = CompressorType.AUDIO.accentColor,
                                    testTag = "cs_audio_rate_$hz",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(audioSampleRateHz = hz)
                                        }
                                    }
                                )
                            }
                        }

                        SwitchRow(
                            title = "Mono Downmix by Default",
                            subtitle = "Save extra space on voice recordings & podcasts",
                            checked = settings.audioMono,
                            testTag = "cs_audio_mono_switch",
                            onCheckedChange = { checked ->
                                CompressionDefaultsStore.update(context) {
                                    it.copy(audioMono = checked)
                                }
                            }
                        )
                    }
                }

                // 4. PDF DEFAULTS
                item {
                    EngineDefaultsCard(
                        title = "PDF Defaults",
                        subtitle = "Default document optimization preset",
                        icon = Icons.Default.PictureAsPdf,
                        accentColor = CompressorType.PDF.accentColor,
                        badge = settings.pdfPreset.label.uppercase()
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompressorQualityPreset.entries.forEach { preset ->
                                EngineChip(
                                    label = preset.label,
                                    selected = settings.pdfPreset == preset,
                                    accent = CompressorType.PDF.accentColor,
                                    testTag = "cs_pdf_preset_${preset.name.lowercase()}",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(pdfPreset = preset)
                                        }
                                    }
                                )
                            }
                        }
                        Text(
                            text = settings.pdfPreset.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 5. ARCHIVE & FILE DEFAULTS
                item {
                    EngineDefaultsCard(
                        title = "Archive & Code Defaults",
                        subtitle = "ZIP deflate level, GZIP mode & text minification",
                        icon = Icons.Default.Archive,
                        accentColor = CompressorType.ARCHIVE.accentColor,
                        badge = "LVL ${settings.archiveLevel}"
                    ) {
                        Text(
                            text = "ZIP Compression Level",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3 to "Fast (3)", 6 to "Normal (6)", 9 to "Best (9)").forEach { (lvl, label) ->
                                EngineChip(
                                    label = label,
                                    selected = settings.archiveLevel == lvl,
                                    accent = CompressorType.ARCHIVE.accentColor,
                                    testTag = "cs_archive_level_$lvl",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(archiveLevel = lvl)
                                        }
                                    }
                                )
                            }
                        }

                        SwitchRow(
                            title = "Use GZIP for Single Archive Input",
                            subtitle = "Output .gz instead of .zip when compressing 1 file",
                            checked = settings.archiveGzipSingleFile,
                            testTag = "cs_archive_gzip_switch",
                            onCheckedChange = { checked ->
                                CompressionDefaultsStore.update(context) {
                                    it.copy(archiveGzipSingleFile = checked)
                                }
                            }
                        )

                        SwitchRow(
                            title = "Minify JSON / HTML / XML / CSS",
                            subtitle = "Strip whitespace and comments automatically",
                            checked = settings.fileMinifyCode,
                            testTag = "cs_file_minify_switch",
                            onCheckedChange = { checked ->
                                CompressionDefaultsStore.update(context) {
                                    it.copy(fileMinifyCode = checked)
                                }
                            }
                        )

                        SwitchRow(
                            title = "Wrap Minified Files as GZIP",
                            subtitle = "Output .gz alongside code/text minification",
                            checked = settings.fileWrapWithGzip,
                            testTag = "cs_file_gzip_switch",
                            onCheckedChange = { checked ->
                                CompressionDefaultsStore.update(context) {
                                    it.copy(fileWrapWithGzip = checked)
                                }
                            }
                        )
                    }
                }

                // 6. GIF & BATCH DEFAULTS
                item {
                    EngineDefaultsCard(
                        title = "GIF & Batch Defaults",
                        subtitle = "Frame sampling, color quantization & batch bundling",
                        icon = Icons.Default.GifBox,
                        accentColor = CompressorType.GIF.accentColor,
                        badge = "${settings.gifScalePercent}%"
                    ) {
                        Text(
                            text = "Default GIF Scale",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(50, 70, 100).forEach { pct ->
                                EngineChip(
                                    label = "$pct%",
                                    selected = settings.gifScalePercent == pct,
                                    accent = CompressorType.GIF.accentColor,
                                    testTag = "cs_gif_scale_$pct",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(gifScalePercent = pct)
                                        }
                                    }
                                )
                            }
                        }

                        Text(
                            text = "Default Frame Retention & Palette",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1 to "All frames", 2 to "Every 2nd", 3 to "Every 3rd").forEach { (skip, label) ->
                                EngineChip(
                                    label = label,
                                    selected = settings.gifFrameSkip == skip,
                                    accent = CompressorType.GIF.accentColor,
                                    testTag = "cs_gif_skip_$skip",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(gifFrameSkip = skip)
                                        }
                                    }
                                )
                            }
                            listOf(64, 128, 256).forEach { colors ->
                                EngineChip(
                                    label = "$colors colors",
                                    selected = settings.gifMaxColors == colors,
                                    accent = CompressorType.GIF.accentColor,
                                    testTag = "cs_gif_colors_$colors",
                                    onClick = {
                                        CompressionDefaultsStore.update(context) {
                                            it.copy(gifMaxColors = colors)
                                        }
                                    }
                                )
                            }
                        }

                        SwitchRow(
                            title = "Bundle Batch Outputs into ZIP",
                            subtitle = "Create a single shareable archive for batch runs",
                            checked = settings.batchBundleAsZip,
                            testTag = "cs_batch_zip_switch",
                            onCheckedChange = { checked ->
                                CompressionDefaultsStore.update(context) {
                                    it.copy(batchBundleAsZip = checked)
                                }
                            }
                        )
                    }
                }

                // Bottom Reset button card
                item {
                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            CompressionDefaultsStore.resetToDefaults(context)
                            Toast.makeText(
                                context,
                                "All compression defaults reset",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("compression_settings_reset_bottom")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset All Defaults",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun EngineDefaultsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badge: String,
    content: @Composable () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
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
                ArworaTonalIconBadge(
                    icon = icon,
                    tint = accentColor,
                    size = 40.dp,
                    iconSize = 20.dp,
                    cornerRadius = 12.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArworaPillBadge(text = badge, tint = accentColor)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            content()
        }
    }
}

@Composable
private fun EngineChip(
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = {
            SoundEffectManager.playClick()
            onClick()
        },
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = ArworaDesignTokens.PillShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
