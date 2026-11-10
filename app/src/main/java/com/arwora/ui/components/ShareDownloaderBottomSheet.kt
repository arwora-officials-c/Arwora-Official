package com.arwora.ui.components

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arwora.ui.MainViewModel
import com.arwora.NameGenApplication
import com.arwora.data.db.DownloadHistoryEntity
import com.arwora.utils.MediaDownloadUtils
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

enum class MediaDownloadCategory {
    VIDEO, AUDIO
}

data class AudioQualityOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val format: String,
    val estimatedSize: String,
    val bitrate: String
)

data class VideoQualityOption(
    val id: String,
    val label: String,
    val resolution: String,
    val format: String,
    val estimatedSize: String,
    val isHighRes: Boolean = false
)

fun calculateDynamicVideoOptions(durationSec: Long): List<VideoQualityOption> {
    val d = if (durationSec > 0) durationSec else 180L
    fun sizeStr(kbps: Int): String {
        val mb = (d * kbps / 8.0) / 1024.0
        return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
    }
    return listOf(
        VideoQualityOption("144", "144P", "256x144", "MP4", sizeStr(150)),
        VideoQualityOption("240", "240P", "426x240", "MP4", sizeStr(300)),
        VideoQualityOption("360", "360P", "640x360", "MP4", sizeStr(600)),
        VideoQualityOption("480", "480P", "854x480", "MP4", sizeStr(1000)),
        VideoQualityOption("720", "720P HD", "1280x720", "MP4", sizeStr(2000)),
        VideoQualityOption("1080", "1080P FHD", "1920x1080", "MP4", sizeStr(4500), isHighRes = true),
        VideoQualityOption("1440", "2K QHD", "2560x1440", "MP4", sizeStr(9000), isHighRes = true),
        VideoQualityOption("2160", "4K UHD", "3840x2160", "MP4", sizeStr(18000), isHighRes = true),
        VideoQualityOption("max", "8K Ultra", "7680x4320", "MP4", sizeStr(35000), isHighRes = true)
    )
}

fun calculateDynamicAudioOptions(durationSec: Long): List<AudioQualityOption> {
    val d = if (durationSec > 0) durationSec else 180L
    fun sizeStr(kbps: Int): String {
        val mb = (d * kbps / 8.0) / 1024.0
        return "%.1f MB".format(mb)
    }
    return listOf(
        AudioQualityOption("mp3_128", "128K MP3", "Standard Quality", "mp3", sizeStr(128), "128 kbps"),
        AudioQualityOption("mp3_256", "256K MP3", "High Definition", "mp3", sizeStr(256), "256 kbps"),
        AudioQualityOption("mp3_320", "320K MP3", "Studio Master", "mp3", sizeStr(320), "320 kbps"),
        AudioQualityOption("wav", "Lossless WAV", "Hi-Res Archive", "wav", sizeStr(1411), "1411 kbps"),
        AudioQualityOption("opus", "HD Opus", "Modern Encoding", "opus", sizeStr(160), "160 kbps")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDownloaderBottomSheet(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    val rawUrl by viewModel.sharedMediaUrl.collectAsState()
    var inputUrl by remember(rawUrl) { mutableStateOf(rawUrl) }
    var mediaTitle by remember(rawUrl) { mutableStateOf("") }
    var mediaDuration by remember { mutableStateOf("00:00") }
    var fetchedThumbnail by remember { mutableStateOf<String?>(null) }
    var isEditingTitle by remember { mutableStateOf(false) }

    val customDownloadPath by viewModel.customDownloadPath.collectAsState()
    var showFolderDialog by remember { mutableStateOf(false) }

    // Dynamic Options State
    var videoOptions by remember { mutableStateOf(calculateDynamicVideoOptions(180L)) }
    var audioOptions by remember { mutableStateOf(calculateDynamicAudioOptions(180L)) }

    // Selection State
    var selectedCategory by remember { mutableStateOf(MediaDownloadCategory.VIDEO) }
    var selectedAudioOption by remember { mutableStateOf(audioOptions[1]) } // 256K MP3 default
    var selectedVideoOption by remember { mutableStateOf(videoOptions[5]) } // 1080P default

    // Download / Processing State
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStageText by remember { mutableStateOf("Ready to download") }
    var isCompleted by remember { mutableStateOf(false) }
    var downloadedFileName by remember { mutableStateOf("") }
    var downloadedFileObj by remember { mutableStateOf<File?>(null) }
    var downloadedMimeType by remember { mutableStateOf("audio/mpeg") }

    // Detected Platform and Thumbnail Logic
    val detectedPlatform = remember(inputUrl) {
        val lower = inputUrl.lowercase()
        when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("instagram.com") -> "Instagram"
            lower.contains("twitter.com") || lower.contains("x.com") -> "X (Twitter)"
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("facebook.com/share") -> "Facebook"
            lower.contains("threads.net") -> "Threads"
            lower.contains("reddit.com") -> "Reddit"
            lower.contains("pinterest.com") || lower.contains("pin.it") -> "Pinterest"
            lower.contains("twitch.tv") -> "Twitch"
            lower.contains("vimeo.com") -> "Vimeo"
            lower.contains("soundcloud.com") -> "SoundCloud"
            inputUrl.isNotBlank() -> "Social Video"
            else -> "Media Downloader"
        }
    }

    val platformIcon = remember(detectedPlatform) {
        when (detectedPlatform) {
            "YouTube" -> Icons.Default.PlayCircle
            "TikTok" -> Icons.Default.MusicVideo
            "Instagram" -> Icons.Default.CameraAlt
            "X (Twitter)" -> Icons.Default.Tag
            "Facebook" -> Icons.Default.ThumbUp
            "Threads" -> Icons.Default.Forum
            "SoundCloud" -> Icons.Default.Audiotrack
            else -> Icons.Default.VideoLibrary
        }
    }

    // Video Thumbnail & Title Extraction
    val videoThumbnailUrl = remember(inputUrl, fetchedThumbnail) {
        if (fetchedThumbnail != null) return@remember fetchedThumbnail
        
        val lower = inputUrl.lowercase()
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
            val videoId = extractYouTubeId(inputUrl)
            if (!videoId.isNullOrBlank()) {
                "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            } else null
        } else null
    }

    LaunchedEffect(inputUrl) {
        if (inputUrl.isNotBlank() && URLUtil.isValidUrl(inputUrl)) {
            mediaTitle = "Resolving media info..."
            
            // Primary: High-fidelity metadata via yt-dlp
            val info = MediaDownloadUtils.fetchDetailedMetadata(inputUrl)
            if (info != null) {
                mediaTitle = info.title ?: ""
                val durSec = info.duration.toLong()
                mediaDuration = if (durSec > 0) {
                    val hr = durSec / 3600
                    val min = (durSec % 3600) / 60
                    val sec = durSec % 60
                    if (hr > 0) "%d:%02d:%02d".format(hr, min, sec) else "%02d:%02d".format(min, sec)
                } else "LIVE"
                fetchedThumbnail = info.thumbnail

                if (durSec > 0) {
                    videoOptions = calculateDynamicVideoOptions(durSec)
                    audioOptions = calculateDynamicAudioOptions(durSec)
                    selectedVideoOption = videoOptions.getOrNull(5) ?: videoOptions.last()
                    selectedAudioOption = audioOptions.getOrNull(1) ?: audioOptions.first()
                }
            } else {
                // Secondary: Lightweight scraping fallback
                val officialTitle = MediaDownloadUtils.fetchMediaMetadata(inputUrl)
                if (!officialTitle.isNullOrBlank()) {
                    mediaTitle = officialTitle
                } else {
                    mediaTitle = "Social Media Stream"
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.closeDownloaderBottomSheet()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Pro Media Downloader",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "YT-DLP",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "High speed robust extraction engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        coroutineScope.launch {
                            sheetState.hide()
                            viewModel.closeDownloaderBottomSheet()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Link Input Field with Inline Paste Action
            OutlinedTextField(
                value = inputUrl,
                onValueChange = {
                    inputUrl = it
                    isCompleted = false
                    isDownloading = false
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Paste video or audio link here...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = platformIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (inputUrl.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    inputUrl = ""
                                    isCompleted = false
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Surface(
                            onClick = {
                                SoundEffectManager.playClick()
                                val clipText = clipboardManager.getText()?.text ?: ""
                                if (clipText.isNotBlank()) {
                                    inputUrl = clipText.trim()
                                    viewModel.handleSharedUrl(clipText)
                                    viewModel.showFloatingToast("Pasted from clipboard")
                                } else {
                                    viewModel.showFloatingToast("Clipboard is empty")
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Paste",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            )

            // ==================== HEADER PREVIEW CARD ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (videoThumbnailUrl != null) {
                                AsyncImage(
                                    model = videoThumbnailUrl,
                                    contentDescription = "Video Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = platformIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // Duration badge
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.75f)
                            ) {
                                Text(
                                    text = mediaDuration,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Title & Details
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = detectedPlatform,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "• HD Stream Ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isEditingTitle) {
                                OutlinedTextField(
                                    value = mediaTitle,
                                    onValueChange = { mediaTitle = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { isEditingTitle = false },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Save Title",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (mediaTitle.isNotBlank()) mediaTitle else "Ready to download media",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { isEditingTitle = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Title",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )

                    // Storage Destination Path Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Destination Folder",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = customDownloadPath,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                showFolderDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Category Tab Selector (Audio vs Video)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selectedCategory == MediaDownloadCategory.VIDEO) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable {
                            SoundEffectManager.playClick()
                            selectedCategory = MediaDownloadCategory.VIDEO
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (selectedCategory == MediaDownloadCategory.VIDEO) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Video Resolution",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedCategory == MediaDownloadCategory.VIDEO) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selectedCategory == MediaDownloadCategory.AUDIO) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable {
                            SoundEffectManager.playClick()
                            selectedCategory = MediaDownloadCategory.AUDIO
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = if (selectedCategory == MediaDownloadCategory.AUDIO) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Audio & Music",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedCategory == MediaDownloadCategory.AUDIO) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ==================== VIDEO SECTION ====================
            if (selectedCategory == MediaDownloadCategory.VIDEO) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Video Resolutions",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    // 3-column Grid for Video Resolutions (VidMate style)
                    val chunkedVideo = videoOptions.chunked(3)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunkedVideo.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { option ->
                                    val isSelected = selectedVideoOption.id == option.id
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(58.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                SoundEffectManager.playClick()
                                                selectedVideoOption = option
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = option.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = option.estimatedSize,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                // Fill empty spaces in last row if needed
                                if (row.size < 3) {
                                    repeat(3 - row.size) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==================== AUDIO SECTION ====================
            if (selectedCategory == MediaDownloadCategory.AUDIO) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Audio Quality",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        audioOptions.forEach { option ->
                            val isSelected = selectedAudioOption.id == option.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        SoundEffectManager.playClick()
                                        selectedAudioOption = option
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = option.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = option.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = option.estimatedSize,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==================== DOWNLOAD PROGRESS & STATUS ====================
            AnimatedVisibility(visible = isDownloading || isCompleted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Downloading,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = downloadStageText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // ==================== ACTION BUTTONS ====================
            if (isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            val targetFile = downloadedFileObj
                            if (targetFile != null && targetFile.exists()) {
                                MediaDownloadUtils.shareMediaFile(context, targetFile, downloadedMimeType, mediaTitle)
                            } else {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = downloadedMimeType
                                    putExtra(Intent.EXTRA_SUBJECT, mediaTitle)
                                    putExtra(Intent.EXTRA_TEXT, "Downloaded via Arwora Hyper Toolkit: $mediaTitle")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Downloaded Media"))
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "SHARE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = {
                            SoundEffectManager.playClick()
                            val targetFile = downloadedFileObj
                            if (targetFile != null && targetFile.exists()) {
                                MediaDownloadUtils.openMediaFile(context, targetFile, downloadedMimeType)
                            } else {
                                try {
                                    val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                                    context.startActivity(downloadsIntent)
                                } catch (e: Exception) {
                                    viewModel.showFloatingToast("Saved to $customDownloadPath")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "OPEN FILE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        SoundEffectManager.playClick()

                        val trimmedUrl = inputUrl.trim()
                        if (trimmedUrl.isBlank() || (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://"))) {
                            viewModel.showFloatingToast("Please enter a valid media link")
                            return@Button
                        }

                        isDownloading = true
                        isCompleted = false
                        downloadProgress = 0f

                        val isAudio = selectedCategory == MediaDownloadCategory.AUDIO
                        val fileExt = if (isAudio) {
                            selectedAudioOption.format.lowercase()
                        } else {
                            selectedVideoOption.format.lowercase()
                        }
                        val resolvedMime = MediaDownloadUtils.getMimeType(fileExt, isAudio)
                        downloadedMimeType = resolvedMime

                        val safeTitle = mediaTitle.ifBlank { "Social_${if (isAudio) "Audio" else "Video"}" }
                            .replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                        val fileName = "${safeTitle}_${System.currentTimeMillis()}.$fileExt"
                        downloadedFileName = fileName

                        coroutineScope.launch {
                            try {
                                isDownloading = true
                                isCompleted = false
                                downloadProgress = 0f

                                val qualityId = if (isAudio) "best" else selectedVideoOption.id
                                
                                MediaDownloadUtils.ensureAppDirectories(context)
                                val appDownloadDir = MediaDownloadUtils.getMainDownloadDir(context, isAudio)
                                val targetFile = File(appDownloadDir, fileName)
                                val baseName = fileName.substringBeforeLast(".")

                                // Phase 1: High-Speed API Stream Resolution (RapidAPI & Direct Engines)
                                downloadStageText = "Resolving stream with high-speed API..."
                                var success = false
                                var streamUrl = MediaDownloadUtils.resolveStreamUrl(
                                    url = trimmedUrl, 
                                    isAudio = isAudio,
                                    quality = qualityId,
                                    audioFormat = if (isAudio) selectedAudioOption.format else "best"
                                )

                                if (streamUrl != null) {
                                    downloadStageText = "Downloading direct stream..."
                                    success = MediaDownloadUtils.downloadUrlToFile(
                                        url = streamUrl,
                                        targetFile = targetFile,
                                        onProgress = { downloaded, total ->
                                            if (total > 0) {
                                                downloadProgress = downloaded.toFloat() / total.toFloat()
                                                downloadStageText = "Downloading: %.1f MB / %.1f MB".format(downloaded / 1048576.0, total / 1048576.0)
                                            } else {
                                                downloadStageText = "Downloading: %.1f MB".format(downloaded / 1048576.0)
                                            }
                                        }
                                    )
                                    // If we resolved a stream but download failed (e.g. 403 or network), 
                                    // only fallback if the file doesn't exist or is empty
                                    if (!success && targetFile.exists() && targetFile.length() > 1024) {
                                        success = true // Treat as success if we got a substantial file despite error
                                    }
                                }

                                // Phase 2: Fallback to yt-dlp engine ONLY if no stream resolved or download failed completely
                                if (!success) {
                                    downloadStageText = "Retrying with native yt-dlp engine..."
                                    downloadProgress = 0.05f
                                    delay(500)
                                    success = MediaDownloadUtils.downloadWithYtDlp(
                                        url = trimmedUrl,
                                        targetFile = targetFile,
                                        isAudio = isAudio,
                                        quality = qualityId,
                                        onProgress = { progress, eta, line ->
                                            downloadProgress = progress
                                            downloadStageText = "Downloading: ${(progress * 100).toInt()}%"
                                            if (eta > 0) {
                                                downloadStageText += " (ETA: ${eta}s)"
                                            }
                                        }
                                    )
                                }

                                // Phase 2b: Second-chance retry with general quality format
                                if (!success && !isAudio) {
                                    downloadStageText = "Retrying with universal SD video stream..."
                                    downloadProgress = 0.02f
                                    val sdStreamUrl = MediaDownloadUtils.resolveStreamUrl(
                                        url = trimmedUrl,
                                        isAudio = false,
                                        quality = "360",
                                        audioFormat = "best"
                                    )
                                    if (sdStreamUrl != null) {
                                        success = MediaDownloadUtils.downloadUrlToFile(
                                            url = sdStreamUrl,
                                            targetFile = targetFile,
                                            onProgress = { downloaded, total ->
                                                if (total > 0) {
                                                    downloadProgress = downloaded.toFloat() / total.toFloat()
                                                    downloadStageText = "Downloading (SD): %.1f MB / %.1f MB".format(downloaded / 1048576.0, total / 1048576.0)
                                                }
                                            }
                                        )
                                    }
                                }

                                if (!success) {
                                    downloadStageText = "Extraction failed: Link expired or private content"
                                    
                                    // Save to history (Failed - No success)
                                    try {
                                        (context.applicationContext as NameGenApplication).downloadHistoryRepository.insert(
                                            DownloadHistoryEntity(
                                                fileName = fileName,
                                                url = trimmedUrl,
                                                platform = detectedPlatform,
                                                isAudio = isAudio,
                                                quality = if (isAudio) selectedAudioOption.title else selectedVideoOption.label,
                                                status = "FAILED",
                                                errorMessage = "Extraction failed"
                                            )
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }

                                    delay(3000)
                                    isDownloading = false
                                    return@launch
                                }

                                // Phase 3: Post-processing (Sidecars & Indexing)
                                downloadStageText = "Finalizing and indexing..."
                                downloadProgress = 1f

                                // 1. Save and Index Extracted Thumbnail in lm_thumbnail/
                                MediaDownloadUtils.saveExtractedThumbnail(
                                    context = context,
                                    baseName = baseName,
                                    title = mediaTitle.ifBlank { "Media Item" },
                                    platform = detectedPlatform
                                )

                                if (isAudio) {
                                    // 2. Save Synchronized Lyrics in lm_lyrics/
                                    MediaDownloadUtils.saveLyricsFile(
                                        context = context,
                                        baseName = baseName,
                                        title = mediaTitle.ifBlank { "Downloaded Audio Track" },
                                        artist = detectedPlatform
                                    )
                                } else {
                                    // 2. Save Captions / Subtitles in cc/
                                    MediaDownloadUtils.saveSubtitleFile(
                                        context = context,
                                        baseName = baseName,
                                        title = mediaTitle.ifBlank { "Downloaded Video" }
                                    )
                                }

                                downloadedFileObj = targetFile

                                // Instant MediaScanner Indexing
                                MediaDownloadUtils.scanMediaFile(context, targetFile, resolvedMime) { uri ->
                                    // Media indexed successfully into MediaStore
                                }

                                isDownloading = false
                                isCompleted = true
                                downloadStageText = "Download Complete! Saved to ArworaHyperToolkit/download/"
                                viewModel.showFloatingToast("Saved $fileName")

                                // Save to history (Success)
                                try {
                                    (context.applicationContext as NameGenApplication).downloadHistoryRepository.insert(
                                        DownloadHistoryEntity(
                                            fileName = fileName,
                                            url = trimmedUrl,
                                            platform = detectedPlatform,
                                            isAudio = isAudio,
                                            quality = if (isAudio) selectedAudioOption.title else selectedVideoOption.label,
                                            status = "SUCCESS"
                                        )
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                            } catch (e: Exception) {
                                e.printStackTrace()
                                downloadStageText = "An unexpected error occurred"
                                isDownloading = false

                                // Save to history (Failed - Exception)
                                try {
                                    val safeTitle = mediaTitle.ifBlank { "Social_${if (selectedCategory == MediaDownloadCategory.AUDIO) "Audio" else "Video"}" }
                                    (context.applicationContext as NameGenApplication).downloadHistoryRepository.insert(
                                        DownloadHistoryEntity(
                                            fileName = safeTitle,
                                            url = inputUrl,
                                            platform = "Unknown",
                                            isAudio = selectedCategory == MediaDownloadCategory.AUDIO,
                                            quality = "N/A",
                                            status = "FAILED",
                                            errorMessage = e.message ?: "Unknown error"
                                        )
                                    )
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                        }
                    },
                    enabled = !isDownloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isDownloading) "DOWNLOADING..." else "DOWNLOAD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Storage Destination Folder Dialog
    if (showFolderDialog) {
        val predefinedFolders = listOf(
            "/storage/emulated/0/Download/",
            "/storage/emulated/0/Download/cc/",
            "/storage/emulated/0/Download/lm_thumbnail/",
            "/storage/emulated/0/Download/lm_lyrics/"
        )

        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = {
                Text(
                    text = "Select Destination Folder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    predefinedFolders.forEach { folder ->
                        val isSelected = customDownloadPath == folder
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    SoundEffectManager.playClick()
                                    viewModel.setCustomDownloadPath(folder)
                                    showFolderDialog = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = folder,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private fun extractYouTubeId(url: String): String? {
    return try {
        if (url.contains("youtu.be/")) {
            url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
        } else if (url.contains("watch?v=")) {
            url.substringAfter("watch?v=").substringBefore("&")
        } else if (url.contains("shorts/")) {
            url.substringAfter("shorts/").substringBefore("?").substringBefore("&")
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
