package com.arwora.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

data class ExtractedApkItem(
    val file: File,
    val fileName: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val lastModified: Long,
    val icon: android.graphics.drawable.Drawable? = null
)

sealed class ExtractionResult {
    data class Success(
        val file: File,
        val filePath: String,
        val fileName: String,
        val sizeFormatted: String,
        val appName: String,
        val packageName: String
    ) : ExtractionResult()

    data class Error(val message: String) : ExtractionResult()
}

object ApkExtractorUtils {

    const val DIRECTORY_NAME = "Arwora hyper toolkit"

    /**
     * Primary target directory for extracted APKs:
     * /storage/emulated/0/Download/Arwora hyper toolkit/
     */
    fun getTargetDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, DIRECTORY_NAME)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    /**
     * Extracts an installed application's APK to /storage/emulated/0/Download/Arwora hyper toolkit/
     */
    suspend fun extractApk(
        context: Context,
        appInfo: ApplicationInfo,
        appName: String,
        versionName: String
    ): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            val sourcePath = appInfo.sourceDir ?: appInfo.publicSourceDir
            if (sourcePath.isNullOrBlank()) {
                return@withContext ExtractionResult.Error("Could not locate source APK file for this application.")
            }

            val sourceFile = File(sourcePath)
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                return@withContext ExtractionResult.Error("Source APK file cannot be read. May require elevated privileges.")
            }

            // Build clean sanitized file name
            val cleanAppName = appName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                .replace("_+".toRegex(), "_")
                .trim('_')
                .ifEmpty { "App" }
            val cleanPkg = appInfo.packageName.replace("[^a-zA-Z0-9._]".toRegex(), "_")
            val cleanVer = versionName.replace("[^a-zA-Z0-9.-]".toRegex(), "_").ifEmpty { "1.0" }
            val fileName = "${cleanAppName}_${cleanPkg}_${cleanVer}.apk"

            val targetDir = getTargetDirectory()
            val destFile = File(targetDir, fileName)

            var writeSuccess = false

            // Strategy 1: Direct File Copy
            try {
                if (destFile.exists()) {
                    destFile.delete()
                }
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        copyStream(input, output)
                    }
                }
                writeSuccess = destFile.exists() && destFile.length() > 0
            } catch (e: Exception) {
                writeSuccess = false
            }

            // Strategy 2: Scoped Storage MediaStore Fallback (for Android 10+ if direct file write fails)
            if (!writeSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY_NAME")
                    }

                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(sourceFile).use { inp ->
                                copyStream(inp, out)
                            }
                        }
                        writeSuccess = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ApkExtractor", "MediaStore fallback failed", e)
                }
            }

            // Strategy 3: App External Files directory fallback if both fail
            if (!writeSuccess) {
                val appExtDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIRECTORY_NAME)
                if (!appExtDir.exists()) appExtDir.mkdirs()
                val fallbackFile = File(appExtDir, fileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(fallbackFile).use { output ->
                        copyStream(input, output)
                    }
                }
                if (fallbackFile.exists() && fallbackFile.length() > 0) {
                    val sizeFormatted = formatFileSize(fallbackFile.length())
                    MediaScannerConnection.scanFile(context, arrayOf(fallbackFile.absolutePath), arrayOf("application/vnd.android.package-archive"), null)
                    return@withContext ExtractionResult.Success(
                        file = fallbackFile,
                        filePath = fallbackFile.absolutePath,
                        fileName = fileName,
                        sizeFormatted = sizeFormatted,
                        appName = appName,
                        packageName = appInfo.packageName
                    )
                }
            }

            if (writeSuccess) {
                val sizeFormatted = formatFileSize(destFile.length())
                // Notify system media scanner so the APK is visible to downloads / file explorers
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath),
                        arrayOf("application/vnd.android.package-archive"),
                        null
                    )
                } catch (_: Exception) {}

                ExtractionResult.Success(
                    file = destFile,
                    filePath = destFile.absolutePath,
                    fileName = fileName,
                    sizeFormatted = sizeFormatted,
                    appName = appName,
                    packageName = appInfo.packageName
                )
            } else {
                ExtractionResult.Error("Failed to extract APK. Please check storage permissions.")
            }
        } catch (e: Exception) {
            ExtractionResult.Error(e.localizedMessage ?: "Unknown extraction error")
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(32 * 1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
    }

    /**
     * Lists all extracted APK files in the target directory
     */
    suspend fun getExtractedApks(context: Context): List<ExtractedApkItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ExtractedApkItem>()
        val dir = getTargetDirectory()
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".apk", ignoreCase = true) }
        val pm = context.packageManager

        files?.sortedByDescending { it.lastModified() }?.forEach { file ->
            var appName = file.nameWithoutExtension
            var packageName = ""
            var versionName = ""
            var icon: android.graphics.drawable.Drawable? = null

            try {
                val pkgInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
                if (pkgInfo != null) {
                    pkgInfo.applicationInfo?.sourceDir = file.absolutePath
                    pkgInfo.applicationInfo?.publicSourceDir = file.absolutePath
                    packageName = pkgInfo.packageName ?: ""
                    versionName = pkgInfo.versionName ?: ""
                    appName = pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: file.nameWithoutExtension
                    icon = pkgInfo.applicationInfo?.loadIcon(pm)
                }
            } catch (_: Exception) {}

            list.add(
                ExtractedApkItem(
                    file = file,
                    fileName = file.name,
                    appName = appName,
                    packageName = packageName,
                    versionName = versionName,
                    sizeBytes = file.length(),
                    sizeFormatted = formatFileSize(file.length()),
                    lastModified = file.lastModified(),
                    icon = icon
                )
            )
        }
        list
    }

    fun shareApk(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share APK via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            android.util.Log.e("ApkExtractor", "Share APK failed", e)
        }
    }

    fun installApk(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ApkExtractor", "Install APK failed", e)
        }
    }

    fun deleteApk(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = String.format(java.util.Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups.coerceIn(0, units.size - 1)]}"
    }
}
