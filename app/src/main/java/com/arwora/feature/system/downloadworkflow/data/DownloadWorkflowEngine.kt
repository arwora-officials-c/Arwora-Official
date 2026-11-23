package com.arwora.feature.system.downloadworkflow.data

import com.arwora.feature.system.downloadworkflow.model.DownloadFileCategory
import com.arwora.feature.system.downloadworkflow.model.DownloadWorkflowConfig
import com.arwora.feature.system.downloadworkflow.model.KnownDownloadSignature
import com.arwora.feature.system.downloadworkflow.model.ProcessedDownloadEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

data class ProcessOutcome(
    val event: ProcessedDownloadEvent,
    val newSignature: KnownDownloadSignature?
)

/**
 * Stateless, memory-safe download processing engine supporting:
 * - Temporary/partial extension filtering and stability detection (>1.5s)
 * - Extension-based categorization (APKs, Archives, Media, Documents)
 * - Junk-prefix cleanup and filename normalization
 * - Streaming MD5 + size duplicate detection
 */
object DownloadWorkflowEngine {

    const val DEFAULT_STABILITY_DURATION_MS = 1_600L
    const val DEFAULT_POLL_INTERVAL_MS = 400L

    private val PARTIAL_EXTENSIONS = setOf(
        "crdownload", "part", "tmp", "download", "opdownload", "!ut", "aria2", "partial", "bak"
    )

    private val JUNK_PREFIX_REGEXES = listOf(
        Regex("^\\[[^\\]]{1,32}]\\s*[-_.]*\\s*"),
        Regex("^(y2mate\\.com\\s*-\\s*|y2mate_|ssstik\\.io_|snaptik_|savefrom\\.net\\s*-\\s*|savemp3_)", RegexOption.IGNORE_CASE),
        Regex("^(copy of\\s+|tmp_|downloaded_)", RegexOption.IGNORE_CASE)
    )

    private val JUNK_SUFFIX_REGEXES = listOf(
        Regex("\\s*\\((?:copy|downloaded|1080p|720p|480p|hd)\\)$", RegexOption.IGNORE_CASE),
        Regex("\\s*\\[(?:1080p|720p|480p|hd|official)\\]$", RegexOption.IGNORE_CASE)
    )

    fun isPartialOrTemporaryFile(fileName: String): Boolean {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(".")) return true
        val ext = trimmed.substringAfterLast('.', "").lowercase()
        return ext in PARTIAL_EXTENSIONS
    }

    fun categorizeFileName(fileName: String): DownloadFileCategory {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return DownloadFileCategory.OTHER
        return DownloadFileCategory.entries.firstOrNull { ext in it.extensions }
            ?: DownloadFileCategory.OTHER
    }

    fun cleanFileName(rawName: String): String {
        val dotIdx = rawName.lastIndexOf('.')
        val hasExtension = dotIdx > 0 && dotIdx < rawName.length - 1
        val rawStem = if (hasExtension) rawName.substring(0, dotIdx) else rawName
        val ext = if (hasExtension) rawName.substring(dotIdx + 1).lowercase() else ""

        var cleaned = rawStem.trim()
        var previous: String
        do {
            previous = cleaned
            for (regex in JUNK_PREFIX_REGEXES) {
                cleaned = cleaned.replace(regex, "").trim()
            }
            for (regex in JUNK_SUFFIX_REGEXES) {
                cleaned = cleaned.replace(regex, "").trim()
            }
        } while (cleaned != previous && cleaned.isNotEmpty())

        cleaned = cleaned
            .replace(Regex("[_]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("^[\\s._-]+|[\\s._-]+$"), "")

        if (cleaned.isBlank()) {
            cleaned = "download_${System.currentTimeMillis() / 1000L}"
        }

        return if (ext.isNotEmpty()) "$cleaned.$ext" else cleaned
    }

    suspend fun waitForStableFileSize(
        file: File,
        stabilityDurationMs: Long = DEFAULT_STABILITY_DURATION_MS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    ): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile || isPartialOrTemporaryFile(file.name)) {
            return@withContext false
        }
        val safeDelay = stabilityDurationMs.coerceAtLeast(0L)
        if (safeDelay == 0L) {
            return@withContext file.exists() && file.isFile && file.length() > 0L
        }

        val stepMs = pollIntervalMs.coerceIn(50L, safeDelay)
        var elapsed = 0L
        var lastSize = file.length()

        while (elapsed < safeDelay) {
            delay(stepMs)
            elapsed += stepMs
            if (!file.exists() || !file.isFile || isPartialOrTemporaryFile(file.name)) {
                return@withContext false
            }
            val currentSize = file.length()
            if (currentSize != lastSize || currentSize <= 0L) {
                // File is still actively changing or empty: reset stability timer once
                if (currentSize <= 0L) return@withContext false
                lastSize = currentSize
                elapsed = 0L
            }
        }
        file.exists() && file.isFile && file.length() == lastSize && lastSize > 0L
    }

    fun computeMd5(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { input ->
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun processCompletedFile(
        file: File,
        downloadRoot: File,
        config: DownloadWorkflowConfig,
        existingSignatures: List<KnownDownloadSignature>
    ): ProcessOutcome? {
        if (!file.exists() || !file.isFile || isPartialOrTemporaryFile(file.name)) {
            return null
        }

        val originalName = file.name
        val originalPath = file.absolutePath
        val sizeBytes = file.length()
        if (sizeBytes <= 0L) return null

        val category = categorizeFileName(originalName)

        // 1. Duplicate check (streaming MD5 + file size)
        var md5Hash = ""
        var isDuplicate = false
        var duplicateOfName: String? = null
        if (config.duplicateDetectionEnabled) {
            md5Hash = computeMd5(file)
            if (md5Hash.isNotBlank()) {
                val match = existingSignatures.firstOrNull { sig ->
                    sig.sizeBytes == sizeBytes &&
                        sig.md5Hash.equals(md5Hash, ignoreCase = true) &&
                        sig.filePath != originalPath
                }
                if (match != null) {
                    isDuplicate = true
                    duplicateOfName = match.fileName
                }
            }
        }

        // 2. Automatic Rename
        val targetName = if (config.autoRenameEnabled) {
            cleanFileName(originalName)
        } else {
            originalName
        }

        // 3. Type-Based Sorting
        val targetDir = if (config.categorizationEnabled && category != DownloadFileCategory.OTHER) {
            File(downloadRoot, category.folderName).apply {
                if (!exists()) mkdirs()
            }
        } else {
            file.parentFile ?: downloadRoot
        }

        val resolvedTarget = resolveUniqueTargetFile(
            sourceFile = file,
            targetDir = targetDir,
            desiredFileName = targetName
        )

        var finalFile = file
        var moveSucceeded = false
        if (resolvedTarget.absolutePath != file.absolutePath) {
            moveSucceeded = moveFileSafely(file, resolvedTarget)
            if (moveSucceeded) {
                finalFile = resolvedTarget
            }
        }

        val now = System.currentTimeMillis()
        val wasMoved = finalFile.parentFile?.absolutePath != file.parentFile?.absolutePath
        val wasRenamed = finalFile.name != originalName

        val event = ProcessedDownloadEvent(
            id = UUID.randomUUID().toString(),
            originalName = originalName,
            finalName = finalFile.name,
            originalPath = originalPath,
            finalPath = finalFile.absolutePath,
            category = category,
            sizeBytes = sizeBytes,
            md5Hash = md5Hash,
            wasRenamed = wasRenamed,
            wasMoved = wasMoved,
            isDuplicate = isDuplicate,
            duplicateOfName = duplicateOfName,
            processedAtMillis = now
        )

        val newSig = if (config.duplicateDetectionEnabled && md5Hash.isNotBlank() && !isDuplicate) {
            KnownDownloadSignature(
                fileName = finalFile.name,
                filePath = finalFile.absolutePath,
                sizeBytes = sizeBytes,
                md5Hash = md5Hash,
                recordedAtMillis = now
            )
        } else {
            null
        }

        return ProcessOutcome(event = event, newSignature = newSig)
    }

    internal fun resolveUniqueTargetFile(
        sourceFile: File,
        targetDir: File,
        desiredFileName: String
    ): File {
        val candidate = File(targetDir, desiredFileName)
        if (!candidate.exists() || candidate.absolutePath == sourceFile.absolutePath) {
            return candidate
        }
        val dotIdx = desiredFileName.lastIndexOf('.')
        val stem = if (dotIdx > 0) desiredFileName.substring(0, dotIdx) else desiredFileName
        val ext = if (dotIdx > 0) desiredFileName.substring(dotIdx) else ""
        var counter = 1
        while (counter <= 500) {
            val numbered = File(targetDir, "${stem}_$counter$ext")
            if (!numbered.exists()) return numbered
            counter++
        }
        return File(targetDir, "${stem}_${System.currentTimeMillis()}$ext")
    }

    private fun moveFileSafely(source: File, destination: File): Boolean {
        return try {
            destination.parentFile?.let { if (!it.exists()) it.mkdirs() }
            if (source.renameTo(destination)) {
                true
            } else {
                val expectedLen = source.length()
                source.copyTo(destination, overwrite = false)
                if (destination.exists() && destination.length() == expectedLen) {
                    source.delete()
                    true
                } else {
                    destination.delete()
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
