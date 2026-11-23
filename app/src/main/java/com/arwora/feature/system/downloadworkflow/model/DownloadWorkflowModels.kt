package com.arwora.feature.system.downloadworkflow.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class DownloadWorkflowConfig(
    val masterEnabled: Boolean = false,
    val categorizationEnabled: Boolean = true,
    val autoRenameEnabled: Boolean = true,
    val duplicateDetectionEnabled: Boolean = true,
    val instantNotificationEnabled: Boolean = true
)

enum class DownloadFileCategory(
    val id: String,
    val label: String,
    val folderName: String,
    val icon: ImageVector,
    val accentColor: Color,
    val extensions: Set<String>
) {
    APKS(
        id = "apks",
        label = "APKs",
        folderName = "APKs",
        icon = Icons.Default.Android,
        accentColor = Color(0xFF10B981),
        extensions = setOf("apk", "apks", "xapk", "apkm", "aab")
    ),
    ARCHIVES(
        id = "archives",
        label = "Archives",
        folderName = "Archives",
        icon = Icons.Default.FolderZip,
        accentColor = Color(0xFFF59E0B),
        extensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")
    ),
    MEDIA(
        id = "media",
        label = "Media",
        folderName = "Media",
        icon = Icons.Default.PermMedia,
        accentColor = Color(0xFF8B5CF6),
        extensions = setOf(
            "mp4", "mkv", "mov", "avi", "webm", "flv", "3gp",
            "mp3", "aac", "flac", "wav", "ogg", "m4a", "opus",
            "jpg", "jpeg", "png", "webp", "gif", "heic", "svg"
        )
    ),
    DOCUMENTS(
        id = "documents",
        label = "Documents",
        folderName = "Documents",
        icon = Icons.Default.Description,
        accentColor = Color(0xFF3B82F6),
        extensions = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "md", "rtf", "epub", "json", "xml", "html"
        )
    ),
    OTHER(
        id = "other",
        label = "Other",
        folderName = "Other",
        icon = Icons.Default.InsertDriveFile,
        accentColor = Color(0xFF64748B),
        extensions = emptySet()
    );

    companion object {
        fun fromId(id: String?): DownloadFileCategory =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OTHER

        val filterTabs: List<DownloadFileCategory> = listOf(APKS, ARCHIVES, MEDIA, DOCUMENTS, OTHER)
    }
}

data class KnownDownloadSignature(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val md5Hash: String,
    val recordedAtMillis: Long
)

data class ProcessedDownloadEvent(
    val id: String,
    val originalName: String,
    val finalName: String,
    val originalPath: String,
    val finalPath: String,
    val category: DownloadFileCategory,
    val sizeBytes: Long,
    val md5Hash: String,
    val wasRenamed: Boolean,
    val wasMoved: Boolean,
    val isDuplicate: Boolean,
    val duplicateOfName: String?,
    val processedAtMillis: Long
)
