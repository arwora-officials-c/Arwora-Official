package com.arwora.feature.compressor

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File
import java.util.Locale

enum class CompressorType(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: ImageVector,
    val accentHex: Long,
    val pickerMimeTypes: Array<String>
) {
    VIDEO(
        id = "video",
        title = "Video Compressor",
        subtitle = "MP4, MKV, MOV...",
        badge = "MP4",
        icon = Icons.Filled.VideoFile,
        accentHex = 0xFF7C8CFF,
        pickerMimeTypes = arrayOf("video/*")
    ),
    AUDIO(
        id = "audio",
        title = "Audio Compressor",
        subtitle = "MP3, WAV, FLAC...",
        badge = "M4A",
        icon = Icons.Filled.Audiotrack,
        accentHex = 0xFF38BDF8,
        pickerMimeTypes = arrayOf("audio/*")
    ),
    IMAGE(
        id = "image",
        title = "Image Compressor",
        subtitle = "JPG, PNG, WebP...",
        badge = "IMG",
        icon = Icons.Filled.Image,
        accentHex = 0xFF34D399,
        pickerMimeTypes = arrayOf("image/jpeg", "image/png", "image/webp", "image/*")
    ),
    PDF(
        id = "pdf",
        title = "PDF Compressor",
        subtitle = "PDF optimization",
        badge = "PDF",
        icon = Icons.Filled.PictureAsPdf,
        accentHex = 0xFFF87171,
        pickerMimeTypes = arrayOf("application/pdf")
    ),
    ARCHIVE(
        id = "archive",
        title = "Archive Compressor",
        subtitle = "ZIP, 7Z, TAR...",
        badge = "ZIP",
        icon = Icons.Filled.Archive,
        accentHex = 0xFFFBBF24,
        pickerMimeTypes = arrayOf("*/*")
    ),
    FILE(
        id = "file",
        title = "File Compressor",
        subtitle = "HTML, JSON, XML...",
        badge = "MIN",
        icon = Icons.Filled.Description,
        accentHex = 0xFFA78BFA,
        pickerMimeTypes = arrayOf("text/*", "application/json", "application/xml", "application/javascript", "*/*")
    ),
    GIF(
        id = "gif",
        title = "GIF Compressor",
        subtitle = "GIF optimization",
        badge = "GIF",
        icon = Icons.Filled.GifBox,
        accentHex = 0xFFF472B6,
        pickerMimeTypes = arrayOf("image/gif", "image/*")
    ),
    BATCH(
        id = "batch",
        title = "Batch Compressor",
        subtitle = "Multiple files together",
        badge = "MULTI",
        icon = Icons.Filled.Layers,
        accentHex = 0xFF2DD4BF,
        pickerMimeTypes = arrayOf("*/*")
    );

    val accentColor: Color
        get() = Color(accentHex)

    companion object {
        fun fromId(id: String?): CompressorType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: IMAGE
    }
}

enum class CompressorQualityPreset(val label: String, val description: String) {
    LIGHT("Light", "Highest clarity, modest size drop"),
    BALANCED("Balanced", "Best clarity-to-size ratio"),
    EXTREME("Extreme", "Smallest footprint for sharing")
}

enum class ImageOutputFormat(val label: String, val extension: String, val mimeType: String) {
    AUTO("Auto", "jpg", "image/jpeg"),
    JPEG("JPEG", "jpg", "image/jpeg"),
    WEBP_LOSSY("WebP", "webp", "image/webp"),
    WEBP_LOSSLESS("Lossless WebP", "webp", "image/webp"),
    PNG("PNG", "png", "image/png")
}

@Immutable
data class VideoCompressionOptions(
    val targetHeight: Int = 720,
    val bitrateKbps: Int = 1400,
    val keepAudio: Boolean = true
)

@Immutable
data class AudioCompressionOptions(
    val bitrateKbps: Int = 96,
    val sampleRateHz: Int = 44100,
    val mono: Boolean = false
)

@Immutable
data class ImageCompressionOptions(
    val format: ImageOutputFormat = ImageOutputFormat.AUTO,
    val quality: Int = 72,
    val maxDimensionPx: Int = 1600
)

@Immutable
data class PdfCompressionOptions(
    val preset: CompressorQualityPreset = CompressorQualityPreset.BALANCED
) {
    val renderScale: Float
        get() = when (preset) {
            CompressorQualityPreset.LIGHT -> 1.5f
            CompressorQualityPreset.BALANCED -> 1.15f
            CompressorQualityPreset.EXTREME -> 0.85f
        }

    val jpegQuality: Int
        get() = when (preset) {
            CompressorQualityPreset.LIGHT -> 82
            CompressorQualityPreset.BALANCED -> 62
            CompressorQualityPreset.EXTREME -> 40
        }
}

@Immutable
data class ArchiveCompressionOptions(
    val level: Int = 9,
    val useGzipForSingleFile: Boolean = false
)

@Immutable
data class FileCompressionOptions(
    val minifyCode: Boolean = true,
    val wrapWithGzip: Boolean = false
)

@Immutable
data class GifCompressionOptions(
    val scalePercent: Int = 70,
    val frameSkip: Int = 2,
    val maxColors: Int = 128
)

@Immutable
data class CompressorFileItem(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val originalSizeBytes: Long,
    val detectedType: CompressorType = detectCompressorTypeForFile(fileName, mimeType)
)

enum class BatchItemStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}

@Immutable
data class BatchCompressionEntry(
    val fileName: String,
    val detectedType: CompressorType,
    val originalBytes: Long,
    val compressedBytes: Long = 0L,
    val status: BatchItemStatus = BatchItemStatus.QUEUED,
    val errorNote: String? = null
)

@Immutable
data class CompressedOutputResult(
    val sourceName: String,
    val outputFile: File,
    val outputName: String,
    val mimeType: String,
    val originalBytes: Long,
    val compressedBytes: Long,
    val engineUsed: String,
    val summaryNote: String
) {
    val savedBytes: Long
        get() = (originalBytes - compressedBytes).coerceAtLeast(0L)

    val savingsPercent: Float
        get() = if (originalBytes > 0L) {
            ((originalBytes - compressedBytes).toFloat() / originalBytes.toFloat()) * 100f
        } else {
            0f
        }
}

fun detectCompressorTypeForFile(fileName: String, mimeType: String = ""): CompressorType {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
    val mime = mimeType.lowercase(Locale.US)
    return when {
        ext == "gif" || mime == "image/gif" -> CompressorType.GIF
        ext in setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v") || mime.startsWith("video/") -> CompressorType.VIDEO
        ext in setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus") || mime.startsWith("audio/") -> CompressorType.AUDIO
        ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif") || mime.startsWith("image/") -> CompressorType.IMAGE
        ext == "pdf" || mime == "application/pdf" -> CompressorType.PDF
        ext in setOf("html", "htm", "json", "xml", "css", "js", "ts", "svg", "txt", "csv", "log", "md", "yaml", "yml") ||
            mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") -> CompressorType.FILE
        else -> CompressorType.ARCHIVE
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
