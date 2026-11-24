package com.arwora.feature.system.connect.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Locale

data class OutboundFileMeta(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long
)

data class SavedReceiveDestination(
    val displayPath: String,
    val openStream: () -> OutputStream,
    val onFinalize: (success: Boolean) -> Unit
)

object ConnectNetworkUtils {

    fun resolveLocalIpv4Address(context: Context): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                val candidates = mutableListOf<String>()
                for (ni in interfaces) {
                    if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
                    val addresses = ni.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                            val host = addr.hostAddress ?: continue
                            if (ni.name.startsWith("wlan", ignoreCase = true) ||
                                ni.name.startsWith("ap", ignoreCase = true)
                            ) {
                                return host
                            }
                            candidates.add(host)
                        }
                    }
                }
                if (candidates.isNotEmpty()) return candidates.first()
            }
        } catch (_: Exception) {
            // Fall through to WifiManager
        }

        try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wm?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }

    suspend fun discoverReachableSubnetPeers(
        localIp: String,
        port: Int = ConnectSecurityStore.DEFAULT_PORT,
        timeoutMs: Int = 180
    ): List<String> = withContext(Dispatchers.IO) {
        val parts = localIp.split(".")
        if (parts.size != 4 || localIp == "127.0.0.1") return@withContext emptyList()
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        val selfLast = parts[3].toIntOrNull()

        coroutineScope {
            (1..254).filter { it != selfLast }
                .chunked(32)
                .flatMap { batch ->
                    batch.map { host ->
                        async {
                            val targetIp = "$prefix.$host"
                            try {
                                Socket().use { sock ->
                                    sock.connect(InetSocketAddress(targetIp, port), timeoutMs)
                                    targetIp
                                }
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
        }
    }

    fun readOutboundFileMeta(context: Context, uri: Uri): OutboundFileMeta? {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            if (!file.exists() || !file.isFile) return null
            return OutboundFileMeta(uri = uri, displayName = file.name, sizeBytes = file.length())
        }
        var name = "shared_file_${System.currentTimeMillis()}"
        var size = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {
        }
        if (size < 0L) {
            size = try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
        return OutboundFileMeta(uri = uri, displayName = sanitizeFileName(name), sizeBytes = size.coerceAtLeast(0L))
    }

    fun prepareReceiveDestination(
        context: Context,
        rawFileName: String,
        overrideReceiveDir: File? = null
    ): SavedReceiveDestination {
        val safeName = sanitizeFileName(rawFileName)

        if (overrideReceiveDir != null) {
            if (!overrideReceiveDir.exists()) overrideReceiveDir.mkdirs()
            val target = resolveUniqueFile(overrideReceiveDir, safeName)
            return SavedReceiveDestination(
                displayPath = target.absolutePath,
                openStream = { FileOutputStream(target) },
                onFinalize = { ok -> if (!ok && target.exists()) target.delete() }
            )
        }

        // Prefer public Download/ArworaConnect when directly writable (API < 29 or All Files Access)
        @Suppress("DEPRECATION")
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val publicSubdir = File(publicDownloads, "ArworaConnect")
        val canWriteDirect = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && runCatching { Environment.isExternalStorageManager() }.getOrDefault(false))

        if (canWriteDirect) {
            try {
                if (!publicSubdir.exists()) publicSubdir.mkdirs()
                if (publicSubdir.exists() && publicSubdir.canWrite()) {
                    val target = resolveUniqueFile(publicSubdir, safeName)
                    return SavedReceiveDestination(
                        displayPath = target.absolutePath,
                        openStream = { FileOutputStream(target) },
                        onFinalize = { ok -> if (!ok && target.exists()) target.delete() }
                    )
                }
            } catch (_: Exception) {
            }
        }

        // Scoped Storage MediaStore.Downloads (API 29+, zero storage permissions required)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ArworaConnect")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (itemUri != null) {
                    return SavedReceiveDestination(
                        displayPath = "Download/ArworaConnect/$safeName",
                        openStream = {
                            resolver.openOutputStream(itemUri)
                                ?: throw IllegalStateException("Cannot open MediaStore output stream")
                        },
                        onFinalize = { ok ->
                            try {
                                if (ok) {
                                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                                    resolver.update(itemUri, done, null, null)
                                } else {
                                    resolver.delete(itemUri, null, null)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    )
                }
            } catch (_: Exception) {
            }
        }

        // App-scoped Downloads fallback (always writable without permissions)
        val fallbackRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val appSubdir = File(fallbackRoot, "ArworaConnect").apply { if (!exists()) mkdirs() }
        val target = resolveUniqueFile(appSubdir, safeName)
        return SavedReceiveDestination(
            displayPath = target.absolutePath,
            openStream = { FileOutputStream(target) },
            onFinalize = { ok -> if (!ok && target.exists()) target.delete() }
        )
    }

    fun sanitizeFileName(rawName: String): String {
        val base = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = base.replace(Regex("[^a-zA-Z0-9._\\- ()+]"), "_")
        return cleaned.ifBlank { "arwora_transfer_${System.currentTimeMillis()}.bin" }
    }

    private fun resolveUniqueFile(dir: File, fileName: String): File {
        val initial = File(dir, fileName)
        if (!initial.exists()) return initial
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var count = 1
        while (count <= 500) {
            val candidate = File(dir, "${stem}_$count$ext")
            if (!candidate.exists()) return candidate
            count++
        }
        return File(dir, "${stem}_${System.currentTimeMillis()}$ext")
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
