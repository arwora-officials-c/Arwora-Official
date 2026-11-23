package com.arwora.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MediaType(val label: String) {
    IMAGE("Photo / Image"),
    VIDEO("Video / Clip")
}

enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    ORIGINAL("original", "image/*", "Original Format"),
    JPEG("jpg", "image/jpeg", "JPEG (.jpg)"),
    PNG("png", "image/png", "PNG (.png)"),
    WEBP("webp", "image/webp", "WebP (.webp)")
}

data class ExifMetadata(
    val make: String?,
    val model: String?,
    val dateTimeOriginal: String?,
    val width: String?,
    val height: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hasGps: Boolean = false,
    val software: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val exposureTime: String? = null,
    val fNumber: String? = null,
    // Unified Video & Media extensions
    val mediaType: MediaType = MediaType.IMAGE,
    val durationFormatted: String? = null,
    val durationMs: Long? = null,
    val bitrate: String? = null,
    val frameRate: String? = null,
    val rotation: String? = null,
    val mimeType: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
) {
    fun cleared(): ExifMetadata = copy(
        make = null,
        model = null,
        dateTimeOriginal = null,
        latitude = null,
        longitude = null,
        hasGps = false,
        software = null,
        iso = null,
        focalLength = null,
        exposureTime = null,
        fNumber = null,
        rotation = "0°",
        title = null,
        artist = null,
        album = null
    )
}

data class DetailedTag(
    val tagKey: String,
    val displayName: String,
    val value: String,
    val category: String,
    val isSensitive: Boolean = false
)

data class FullMetadataReport(
    val fileName: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val mediaType: MediaType,
    val width: Int?,
    val height: Int?,
    val megapixels: String?,
    val aspectRatio: String?,
    // Camera & Hardware
    val make: String?,
    val model: String?,
    val lensModel: String?,
    val lensMake: String?,
    val software: String?,
    val serialNumber: String?,
    // Exposure & Optics
    val iso: String?,
    val shutterSpeed: String?,
    val aperture: String?,
    val focalLength: String?,
    val focalLength35mm: String?,
    val flash: String?,
    val whiteBalance: String?,
    val meteringMode: String?,
    val exposureProgram: String?,
    val exposureBias: String?,
    // Location & GPS
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val gpsDms: String?,
    val gpsDateStamp: String?,
    val hasGps: Boolean,
    // Dates
    val dateTaken: String?,
    val dateDigitized: String?,
    val dateModified: String?,
    // IPTC / Description
    val artist: String?,
    val copyright: String?,
    val title: String?,
    val userComment: String?,
    // Technical & Image
    val colorSpace: String?,
    val orientation: String?,
    val resolutionDpi: String?,
    val compression: String?,
    // Video
    val durationFormatted: String?,
    val durationMs: Long?,
    val bitrateFormatted: String?,
    val frameRate: String?,
    val videoRotation: String?,
    // All detected raw tags
    val allTags: List<DetailedTag>
)

data class SaveResult(
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val fileSize: Long = 0L,
    val mediaType: MediaType = MediaType.IMAGE
)

object ExifEditorUtils {

    fun getOriginalFileName(context: Context, uri: Uri): String {
        var name = "media_${System.currentTimeMillis()}"
        try {
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        val str = it.getString(index)
                        if (!str.isNullOrBlank()) {
                            name = str
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    fun detectMediaType(context: Context, uri: Uri): MediaType {
        val type = context.contentResolver.getType(uri)?.lowercase() ?: ""
        if (type.startsWith("video/")) return MediaType.VIDEO
        val name = getOriginalFileName(context, uri).lowercase()
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || 
            name.endsWith(".mov") || name.endsWith(".3gp") || name.endsWith(".avi")) {
            return MediaType.VIDEO
        }
        return MediaType.IMAGE
    }

    fun readUnifiedMetadata(context: Context, uri: Uri): ExifMetadata {
        val mediaType = detectMediaType(context, uri)
        return if (mediaType == MediaType.VIDEO) {
            readVideoMetadata(context, uri)
        } else {
            readMetadata(context, uri)
        }
    }

    fun readMetadata(context: Context, uri: Uri): ExifMetadata {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val dt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                val width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
                val height = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                val exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)

                val latLong = FloatArray(2)
                val hasGps = exif.getLatLong(latLong)
                val latitude = if (hasGps) latLong[0].toDouble() else null
                val longitude = if (hasGps) latLong[1].toDouble() else null

                inputStream.close()
                ExifMetadata(
                    make = make,
                    model = model,
                    dateTimeOriginal = dt,
                    width = width,
                    height = height,
                    latitude = latitude,
                    longitude = longitude,
                    hasGps = hasGps,
                    software = software,
                    iso = iso,
                    focalLength = focalLength,
                    exposureTime = exposureTime,
                    fNumber = fNumber,
                    mediaType = MediaType.IMAGE,
                    mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                )
            } else {
                ExifMetadata(null, null, null, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ExifMetadata(null, null, null, null, null)
        }
    }

    fun readVideoMetadata(context: Context, uri: Uri): ExifMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val frameRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            } else null
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

            var lat: Double? = null
            var lon: Double? = null
            if (!location.isNullOrBlank()) {
                try {
                    val match = Regex("([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)").find(location)
                    if (match != null) {
                        lat = match.groupValues[1].toDoubleOrNull()
                        lon = match.groupValues[2].toDoubleOrNull()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val durationFormatted = durationMs?.let { ms ->
                val seconds = (ms / 1000) % 60
                val minutes = (ms / (1000 * 60)) % 60
                val hours = ms / (1000 * 60 * 60)
                if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                else String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }

            val bitrateFormatted = bitrate?.toLongOrNull()?.let { bps ->
                String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
            }

            ExifMetadata(
                make = null,
                model = null,
                dateTimeOriginal = date,
                width = width,
                height = height,
                latitude = lat,
                longitude = lon,
                hasGps = lat != null && lon != null,
                software = null,
                mediaType = MediaType.VIDEO,
                mimeType = mime,
                durationFormatted = durationFormatted,
                durationMs = durationMs,
                bitrate = bitrateFormatted,
                frameRate = frameRate?.let { "$it fps" },
                rotation = rotation?.let { "$it°" },
                title = title,
                artist = artist,
                album = album
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ExifMetadata(
                make = null,
                model = null,
                dateTimeOriginal = null,
                width = null,
                height = null,
                mediaType = MediaType.VIDEO,
                mimeType = "video/mp4"
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    fun getVideoThumbnail(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    fun saveEditedVideoToGallery(
        context: Context,
        sourceUri: Uri,
        customFileName: String,
        subFolder: String = "Media Metadata Editor",
        removeAllMetadata: Boolean = false,
        removeLocation: Boolean = false
    ): SaveResult? {
        var tempFile: File? = null
        try {
            val originalMime = context.contentResolver.getType(sourceUri) ?: "video/mp4"
            val targetMime = originalMime
            val targetExtension = when {
                originalMime.contains("mkv", true) -> ".mkv"
                originalMime.contains("webm", true) -> ".webm"
                originalMime.contains("mov", true) -> ".mov"
                else -> ".mp4"
            }

            var cleanFileName = customFileName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            if (cleanFileName.isBlank()) {
                cleanFileName = "video_clean_${System.currentTimeMillis()}"
            }
            if (!cleanFileName.endsWith(targetExtension, ignoreCase = true)) {
                cleanFileName = cleanFileName.substringBeforeLast(".") + targetExtension
            }

            val cleanSubFolder = subFolder.trim().replace(Regex("[\\\\:*?\"<>|]"), "_").ifBlank { "Media Metadata Editor" }
            tempFile = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}$targetExtension")

            // Copy sanitized video stream
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val relativePath = "${Environment.DIRECTORY_MOVIES}/Arwora Hyper Toolkit/$cleanSubFolder"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, cleanFileName)
                    put(MediaStore.Video.Media.MIME_TYPE, targetMime)
                    put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val contentUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null

                try {
                    context.contentResolver.openOutputStream(contentUri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: run {
                        context.contentResolver.delete(contentUri, null, null)
                        return null
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(contentUri, contentValues, null, null)

                    MediaScannerConnection.scanFile(context, arrayOf(contentUri.toString()), arrayOf(targetMime), null)

                    return SaveResult(
                        uri = contentUri,
                        displayName = cleanFileName,
                        relativePath = "$relativePath/$cleanFileName",
                        mimeType = targetMime,
                        fileSize = tempFile.length(),
                        mediaType = MediaType.VIDEO
                    )
                } catch (e: Exception) {
                    context.contentResolver.delete(contentUri, null, null)
                    throw e
                }
            } else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val appFolder = File(moviesDir, "Arwora Hyper Toolkit/$cleanSubFolder")
                if (!appFolder.exists()) {
                    appFolder.mkdirs()
                }
                val destFile = File(appFolder, cleanFileName)
                tempFile.copyTo(destFile, overwrite = true)

                var resultUri: Uri = Uri.fromFile(destFile)
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(targetMime)) { _, uri ->
                    if (uri != null) {
                        resultUri = uri
                    }
                }

                return SaveResult(
                    uri = resultUri,
                    displayName = cleanFileName,
                    relativePath = "$relativePath/$cleanFileName",
                    mimeType = targetMime,
                    fileSize = destFile.length(),
                    mediaType = MediaType.VIDEO
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            tempFile?.delete()
        }
    }

    fun saveEditedImageToGallery(
        context: Context,
        sourceUri: Uri,
        customFileName: String,
        subFolder: String = "Media Metadata Editor",
        format: ExportFormat = ExportFormat.ORIGINAL,
        quality: Int = 95,
        keepLossless: Boolean = true,
        removeAllMetadata: Boolean = false,
        removeLocation: Boolean = false,
        removeDevice: Boolean = false,
        newDateTime: String? = null,
        newMake: String? = null,
        newModel: String? = null
    ): SaveResult? {
        var tempFile: File? = null
        try {
            val originalMime = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
            val targetMime = when (format) {
                ExportFormat.ORIGINAL -> if (originalMime.contains("png", true)) "image/png" else if (originalMime.contains("webp", true)) "image/webp" else "image/jpeg"
                ExportFormat.JPEG -> "image/jpeg"
                ExportFormat.PNG -> "image/png"
                ExportFormat.WEBP -> "image/webp"
            }

            val targetExtension = when (targetMime) {
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                else -> ".jpg"
            }

            var cleanFileName = customFileName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            if (cleanFileName.isBlank()) {
                cleanFileName = "img_edited_${System.currentTimeMillis()}"
            }
            if (!cleanFileName.endsWith(targetExtension, ignoreCase = true)) {
                cleanFileName = cleanFileName.substringBeforeLast(".") + targetExtension
            }

            val cleanSubFolder = subFolder.trim().replace(Regex("[\\\\:*?\"<>|]"), "_").ifBlank { "Media Metadata Editor" }

            // 1. Create a working temporary file in cache directory
            tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}$targetExtension")

            val isSameAsOriginalFormat = targetMime.equals(originalMime, ignoreCase = true)
            val isJpeg = targetMime == "image/jpeg"

            if (isSameAsOriginalFormat && isJpeg && keepLossless) {
                // Direct stream copy without re-encoding to preserve 100% original image quality
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return null
            } else {
                // Decode bitmap and compress with specified format and quality
                val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return null

                try {
                    val compressFormat = when (targetMime) {
                        "image/png" -> Bitmap.CompressFormat.PNG
                        "image/webp" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
                            } else {
                                @Suppress("DEPRECATION")
                                Bitmap.CompressFormat.WEBP
                            }
                        }
                        else -> Bitmap.CompressFormat.JPEG
                    }

                    tempFile.outputStream().use { output ->
                        bitmap.compress(compressFormat, quality.coerceIn(10, 100), output)
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }

            // 2. Modify EXIF metadata if target format supports EXIF (primarily JPEG)
            if (isJpeg) {
                try {
                    val exif = ExifInterface(tempFile.absolutePath)
                    val gpsTags = listOf(
                        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE_REF,
                        ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP,
                        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_SATELLITES,
                        ExifInterface.TAG_GPS_STATUS, ExifInterface.TAG_GPS_MEASURE_MODE,
                        ExifInterface.TAG_GPS_DOP, ExifInterface.TAG_GPS_SPEED_REF,
                        ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_TRACK_REF,
                        ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                        ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_MAP_DATUM,
                        ExifInterface.TAG_GPS_DEST_LATITUDE_REF, ExifInterface.TAG_GPS_DEST_LATITUDE,
                        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, ExifInterface.TAG_GPS_DEST_LONGITUDE,
                        ExifInterface.TAG_GPS_DEST_BEARING_REF, ExifInterface.TAG_GPS_DEST_BEARING,
                        ExifInterface.TAG_GPS_DEST_DISTANCE_REF, ExifInterface.TAG_GPS_DEST_DISTANCE,
                        ExifInterface.TAG_GPS_AREA_INFORMATION
                    )

                    if (removeAllMetadata) {
                        for (tag in ALL_EXIF_TAGS_TO_CLEAR) {
                            exif.setAttribute(tag, null)
                        }
                        exif.resetOrientation()
                    } else {
                        if (removeLocation) {
                            for (tag in gpsTags) {
                                exif.setAttribute(tag, null)
                            }
                        }

                        if (removeDevice) {
                            exif.setAttribute(ExifInterface.TAG_MAKE, null)
                            exif.setAttribute(ExifInterface.TAG_MODEL, null)
                        } else {
                            if (!newMake.isNullOrBlank()) {
                                exif.setAttribute(ExifInterface.TAG_MAKE, newMake)
                            }
                            if (!newModel.isNullOrBlank()) {
                                exif.setAttribute(ExifInterface.TAG_MODEL, newModel)
                            }
                        }

                        if (!newDateTime.isNullOrBlank()) {
                            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, newDateTime)
                            exif.setAttribute(ExifInterface.TAG_DATETIME, newDateTime)
                            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, newDateTime)
                        }
                    }

                    exif.saveAttributes()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val relativePath = "${Environment.DIRECTORY_PICTURES}/Arwora Hyper Toolkit/$cleanSubFolder"

            // 3. Save into public gallery-visible storage via MediaStore (API 29+) or external storage + MediaScanner
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, cleanFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, targetMime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val contentUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null

                try {
                    context.contentResolver.openOutputStream(contentUri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: run {
                        context.contentResolver.delete(contentUri, null, null)
                        return null
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(contentUri, contentValues, null, null)

                    MediaScannerConnection.scanFile(context, arrayOf(contentUri.toString()), arrayOf(targetMime), null)

                    return SaveResult(
                        uri = contentUri,
                        displayName = cleanFileName,
                        relativePath = "$relativePath/$cleanFileName",
                        mimeType = targetMime,
                        fileSize = tempFile.length(),
                        mediaType = MediaType.IMAGE
                    )
                } catch (e: Exception) {
                    context.contentResolver.delete(contentUri, null, null)
                    throw e
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appFolder = File(picturesDir, "Arwora Hyper Toolkit/$cleanSubFolder")
                if (!appFolder.exists()) {
                    appFolder.mkdirs()
                }
                val destFile = File(appFolder, cleanFileName)
                tempFile.copyTo(destFile, overwrite = true)

                var resultUri: Uri = Uri.fromFile(destFile)
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(targetMime)) { _, uri ->
                    if (uri != null) {
                        resultUri = uri
                    }
                }

                return SaveResult(
                    uri = resultUri,
                    displayName = cleanFileName,
                    relativePath = "$relativePath/$cleanFileName",
                    mimeType = targetMime,
                    fileSize = destFile.length(),
                    mediaType = MediaType.IMAGE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            tempFile?.delete()
        }
    }

    val ALL_EXIF_TAGS_TO_CLEAR = listOf(
        // GPS location data
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_STATUS, ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_DOP, ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF, ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_BEARING_REF, ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF, ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_AREA_INFORMATION, ExifInterface.TAG_GPS_DIFFERENTIAL,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        // Device & Camera & Software
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_SPECIFICATION, ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
        // Timestamps & Subseconds
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        // Technical & Camera Specs
        ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_MAX_APERTURE_VALUE,
        ExifInterface.TAG_SUBJECT_DISTANCE, ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
        ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_FLASH, ExifInterface.TAG_FLASH_ENERGY,
        ExifInterface.TAG_COLOR_SPACE, ExifInterface.TAG_SENSING_METHOD,
        ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_EXPOSURE_INDEX, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO, ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_SCENE_TYPE, ExifInterface.TAG_CUSTOM_RENDERED,
        ExifInterface.TAG_CONTRAST, ExifInterface.TAG_SATURATION, ExifInterface.TAG_SHARPNESS,
        ExifInterface.TAG_GAIN_CONTROL, ExifInterface.TAG_SPECTRAL_SENSITIVITY,
        ExifInterface.TAG_SUBJECT_AREA, ExifInterface.TAG_SUBJECT_LOCATION,
        ExifInterface.TAG_IMAGE_UNIQUE_ID, ExifInterface.TAG_MAKER_NOTE
    )

    fun stripJpegMarkersLossless(inputBytes: ByteArray): ByteArray? {
        if (inputBytes.size < 4 || inputBytes[0] != 0xFF.toByte() || inputBytes[1] != 0xD8.toByte()) {
            return null // Not a valid JPEG
        }
        val out = ByteArrayOutputStream(inputBytes.size)
        // Write SOI
        out.write(0xFF)
        out.write(0xD8)

        var i = 2
        while (i < inputBytes.size - 1) {
            if (inputBytes[i] != 0xFF.toByte()) {
                return null
            }
            val marker = inputBytes[i + 1].toInt() and 0xFF
            i += 2

            if (marker == 0xD9) { // EOI
                out.write(0xFF)
                out.write(0xD9)
                break
            }
            if (marker == 0xDA) { // SOS (Start of Scan) - rest of file is compressed entropy scan data
                if (i + 1 >= inputBytes.size) return null
                val len = ((inputBytes[i].toInt() and 0xFF) shl 8) or (inputBytes[i + 1].toInt() and 0xFF)
                out.write(0xFF)
                out.write(0xDA)
                out.write(inputBytes, i, len)
                i += len
                if (i < inputBytes.size) {
                    out.write(inputBytes, i, inputBytes.size - i)
                }
                break
            }
            if (marker in 0xD0..0xD7 || marker == 0x01) { // RST / TEM markers without length
                out.write(0xFF)
                out.write(marker)
                continue
            }
            if (i + 1 >= inputBytes.size) return null
            val len = ((inputBytes[i].toInt() and 0xFF) shl 8) or (inputBytes[i + 1].toInt() and 0xFF)
            if (len < 2 || i + len > inputBytes.size) return null

            // Drop all APP1 (EXIF / XMP / Geotags), APP2-APP15 (except essential standard), COM (Comments)
            // Keep APP0 (0xE0 = JFIF standard header) and DQT, DHT, SOF
            val shouldStrip = (marker == 0xE1) || (marker in 0xE2..0xEF) || (marker == 0xFE)

            if (!shouldStrip) {
                out.write(0xFF)
                out.write(marker)
                out.write(inputBytes, i, len)
            }
            i += len
        }
        return out.toByteArray()
    }

    /**
     * Strips and purges ALL metadata, hidden EXIF tags, GPS location data,
     * camera hardware info, and timestamps from the selected photo leaving zero
     * metadata behind, while preserving 100% of the image visual quality.
     */
    fun stripAllMetadataFromImage(
        context: Context,
        sourceUri: Uri,
        customFileName: String? = null,
        subFolder: String = "Cleaned Photos"
    ): SaveResult? {
        var tempFile: File? = null
        try {
            val originalName = getOriginalFileName(context, sourceUri)
            val originalMime = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
            val isPng = originalMime.contains("png", true) || originalName.endsWith(".png", true)
            val isWebp = originalMime.contains("webp", true) || originalName.endsWith(".webp", true)
            val isJpeg = !isPng && !isWebp

            val targetExtension = when {
                isPng -> ".png"
                isWebp -> ".webp"
                else -> ".jpg"
            }
            val targetMime = when {
                isPng -> "image/png"
                isWebp -> "image/webp"
                else -> "image/jpeg"
            }

            val baseCleanName = if (!customFileName.isNullOrBlank()) {
                customFileName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            } else {
                val base = originalName.substringBeforeLast(".")
                "clean_no_exif_${base}"
            }
            val cleanFileName = if (baseCleanName.endsWith(targetExtension, ignoreCase = true)) {
                baseCleanName
            } else {
                baseCleanName.substringBeforeLast(".") + targetExtension
            }

            val cleanSubFolder = subFolder.trim().replace(Regex("[\\\\:*?\"<>|]"), "_").ifBlank { "Cleaned Photos" }
            tempFile = File(context.cacheDir, "temp_clean_${System.currentTimeMillis()}$targetExtension")

            // 1. Detect image orientation
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var handledLossless = false
            // 2. If JPEG and natural orientation, perform bit-exact lossless marker strip to preserve 100% visual fidelity
            if (isJpeg && (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED)) {
                try {
                    val inputBytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                    if (inputBytes != null) {
                        val strippedBytes = stripJpegMarkersLossless(inputBytes)
                        if (strippedBytes != null && strippedBytes.isNotEmpty()) {
                            tempFile.writeBytes(strippedBytes)
                            handledLossless = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    handledLossless = false
                }
            }

            // 3. If orientation needs rotation or format is PNG/WebP, decode and save at 100% quality (zero EXIF tags)
            if (!handledLossless) {
                val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return null

                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postRotate(90f)
                        matrix.postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postRotate(270f)
                        matrix.postScale(-1f, 1f)
                    }
                }

                val finalBitmap = if (!matrix.isIdentity) {
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }

                val compressFormat = when {
                    isPng -> Bitmap.CompressFormat.PNG
                    isWebp -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSLESS
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                    }
                    else -> Bitmap.CompressFormat.JPEG
                }

                tempFile.outputStream().use { output ->
                    finalBitmap.compress(compressFormat, 100, output)
                }

                if (finalBitmap != bitmap) {
                    finalBitmap.recycle()
                }
                bitmap.recycle()
            }

            // 4. Double-check and purge all EXIF tags from the output file to leave zero metadata behind
            try {
                val cleanExif = ExifInterface(tempFile.absolutePath)
                for (tag in ALL_EXIF_TAGS_TO_CLEAR) {
                    cleanExif.setAttribute(tag, null)
                }
                cleanExif.resetOrientation()
                cleanExif.saveAttributes()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 5. Save into public gallery-visible Pictures directory
            val relativePath = "${Environment.DIRECTORY_PICTURES}/Arwora Hyper Toolkit/$cleanSubFolder"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, cleanFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, targetMime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val contentUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null

                try {
                    context.contentResolver.openOutputStream(contentUri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: run {
                        context.contentResolver.delete(contentUri, null, null)
                        return null
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(contentUri, contentValues, null, null)

                    MediaScannerConnection.scanFile(context, arrayOf(contentUri.toString()), arrayOf(targetMime), null)

                    return SaveResult(
                        uri = contentUri,
                        displayName = cleanFileName,
                        relativePath = "$relativePath/$cleanFileName",
                        mimeType = targetMime,
                        fileSize = tempFile.length(),
                        mediaType = MediaType.IMAGE
                    )
                } catch (e: Exception) {
                    context.contentResolver.delete(contentUri, null, null)
                    throw e
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appFolder = File(picturesDir, "Arwora Hyper Toolkit/$cleanSubFolder")
                if (!appFolder.exists()) {
                    appFolder.mkdirs()
                }
                val destFile = File(appFolder, cleanFileName)
                tempFile.copyTo(destFile, overwrite = true)

                var resultUri: Uri = Uri.fromFile(destFile)
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(targetMime)) { _, uri ->
                    if (uri != null) {
                        resultUri = uri
                    }
                }

                return SaveResult(
                    uri = resultUri,
                    displayName = cleanFileName,
                    relativePath = "$relativePath/$cleanFileName",
                    mimeType = targetMime,
                    fileSize = destFile.length(),
                    mediaType = MediaType.IMAGE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            tempFile?.delete()
        }
    }

    /**
     * Unified entry-point for completely stripping all metadata from photos or videos.
     */
    fun stripAllMetadata(
        context: Context,
        sourceUri: Uri,
        customFileName: String? = null,
        subFolder: String = "Cleaned Photos"
    ): SaveResult? {
        val type = detectMediaType(context, sourceUri)
        return if (type == MediaType.VIDEO) {
            saveEditedVideoToGallery(
                context = context,
                sourceUri = sourceUri,
                customFileName = customFileName ?: "clean_video_${System.currentTimeMillis()}.mp4",
                subFolder = subFolder,
                removeAllMetadata = true,
                removeLocation = true
            )
        } else {
            stripAllMetadataFromImage(
                context = context,
                sourceUri = sourceUri,
                customFileName = customFileName,
                subFolder = subFolder
            )
        }
    }

    fun openInGallery(context: Context, uri: Uri, mimeType: String = "image/*") {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open in Gallery"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open media", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMedia(context: Context, uri: Uri, mimeType: String = "image/*") {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Media"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share media", Toast.LENGTH_SHORT).show()
        }
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1) {
                        val size = cursor.getLong(idx)
                        if (size > 0) return size
                    }
                }
            }
        } catch (e: Exception) {}
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                val len = it.length
                if (len > 0) return len
            }
        } catch (e: Exception) {}
        return 0L
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatCoordinatesDms(latitude: Double, longitude: Double): String {
        fun toDms(coordinate: Double, isLat: Boolean): String {
            val absVal = Math.abs(coordinate)
            val deg = absVal.toInt()
            val minDouble = (absVal - deg) * 60.0
            val min = minDouble.toInt()
            val sec = (minDouble - min) * 60.0
            val dir = if (isLat) {
                if (coordinate >= 0) "N" else "S"
            } else {
                if (coordinate >= 0) "E" else "W"
            }
            return String.format(Locale.US, "%d° %02d' %04.1f\" %s", deg, min, sec, dir)
        }
        return "${toDms(latitude, true)}, ${toDms(longitude, false)}"
    }

    fun formatExposureTime(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.contains("/")) return "$raw s"
        val d = raw.toDoubleOrNull() ?: return raw
        if (d <= 0.0) return raw
        if (d < 1.0) {
            val denom = Math.round(1.0 / d)
            return "1/$denom s"
        }
        return String.format(Locale.US, "%.1f s", d)
    }

    fun readFullMetadataReport(context: Context, uri: Uri): FullMetadataReport {
        val mediaType = detectMediaType(context, uri)
        val fileName = getOriginalFileName(context, uri)
        val fileSizeBytes = getFileSize(context, uri)
        val fileSizeFormatted = formatFileSize(fileSizeBytes)
        val mimeType = context.contentResolver.getType(uri) ?: (if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg")

        if (mediaType == MediaType.VIDEO) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val frameRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                } else null
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

                var lat: Double? = null
                var lon: Double? = null
                if (!location.isNullOrBlank()) {
                    try {
                        val match = Regex("([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)").find(location)
                        if (match != null) {
                            lat = match.groupValues[1].toDoubleOrNull()
                            lon = match.groupValues[2].toDoubleOrNull()
                        }
                    } catch (e: Exception) {}
                }

                val durationFormatted = durationMs?.let { ms ->
                    val sec = (ms / 1000) % 60
                    val min = (ms / (1000 * 60)) % 60
                    val hr = ms / (1000 * 60 * 60)
                    if (hr > 0) String.format(Locale.US, "%02d:%02d:%02d", hr, min, sec)
                    else String.format(Locale.US, "%02d:%02d", min, sec)
                }

                val bitrateFormatted = bitrate?.let { bps ->
                    String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
                }

                val mp = if (width != null && height != null) {
                    String.format(Locale.US, "%.1f MP", (width * height) / 1_000_000.0)
                } else null

                val ar = if (width != null && height != null && height > 0) {
                    String.format(Locale.US, "%.2f:1", width.toDouble() / height)
                } else null

                val tags = mutableListOf<DetailedTag>()
                tags.add(DetailedTag("MIME_TYPE", "Container MIME", mimeType, "File & Specs"))
                if (width != null && height != null) {
                    tags.add(DetailedTag("RESOLUTION", "Resolution", "$width × $height", "File & Specs"))
                }
                durationFormatted?.let { tags.add(DetailedTag("DURATION", "Duration", it, "Video Codec")) }
                bitrateFormatted?.let { tags.add(DetailedTag("BITRATE", "Bitrate", it, "Video Codec")) }
                frameRate?.let { tags.add(DetailedTag("FRAMERATE", "Frame Rate", "$it fps", "Video Codec")) }
                rotation?.let { tags.add(DetailedTag("ROTATION", "Video Rotation", "$it°", "Video Codec")) }
                date?.let { tags.add(DetailedTag("DATE", "Encoding Date", it, "Dates & Time")) }
                title?.let { tags.add(DetailedTag("TITLE", "Title Tag", it, "IPTC & Tags")) }
                artist?.let { tags.add(DetailedTag("ARTIST", "Artist / Author", it, "IPTC & Tags")) }
                album?.let { tags.add(DetailedTag("ALBUM", "Album Tag", it, "IPTC & Tags")) }
                if (lat != null && lon != null) {
                    tags.add(DetailedTag("GPS_COORDINATES", "GPS Coordinates", "$lat, $lon", "Location (GPS)", true))
                }

                FullMetadataReport(
                    fileName = fileName,
                    fileSizeFormatted = fileSizeFormatted,
                    fileSizeBytes = fileSizeBytes,
                    mimeType = mimeType,
                    mediaType = MediaType.VIDEO,
                    width = width,
                    height = height,
                    megapixels = mp,
                    aspectRatio = ar,
                    make = null,
                    model = null,
                    lensModel = null,
                    lensMake = null,
                    software = null,
                    serialNumber = null,
                    iso = null,
                    shutterSpeed = null,
                    aperture = null,
                    focalLength = null,
                    focalLength35mm = null,
                    flash = null,
                    whiteBalance = null,
                    meteringMode = null,
                    exposureProgram = null,
                    exposureBias = null,
                    latitude = lat,
                    longitude = lon,
                    altitude = null,
                    gpsDms = if (lat != null && lon != null) formatCoordinatesDms(lat, lon) else null,
                    gpsDateStamp = null,
                    hasGps = lat != null && lon != null,
                    dateTaken = date,
                    dateDigitized = null,
                    dateModified = date,
                    artist = artist,
                    copyright = null,
                    title = title,
                    userComment = null,
                    colorSpace = null,
                    orientation = rotation?.let { "$it°" },
                    resolutionDpi = null,
                    compression = null,
                    durationFormatted = durationFormatted,
                    durationMs = durationMs,
                    bitrateFormatted = bitrateFormatted,
                    frameRate = frameRate?.let { "$it fps" },
                    videoRotation = rotation?.let { "$it°" },
                    allTags = tags
                )
            } catch (e: Exception) {
                e.printStackTrace()
                FullMetadataReport(
                    fileName = fileName,
                    fileSizeFormatted = fileSizeFormatted,
                    fileSizeBytes = fileSizeBytes,
                    mimeType = mimeType,
                    mediaType = MediaType.VIDEO,
                    width = null, height = null, megapixels = null, aspectRatio = null,
                    make = null, model = null, lensModel = null, lensMake = null, software = null, serialNumber = null,
                    iso = null, shutterSpeed = null, aperture = null, focalLength = null, focalLength35mm = null,
                    flash = null, whiteBalance = null, meteringMode = null, exposureProgram = null, exposureBias = null,
                    latitude = null, longitude = null, altitude = null, gpsDms = null, gpsDateStamp = null, hasGps = false,
                    dateTaken = null, dateDigitized = null, dateModified = null,
                    artist = null, copyright = null, title = null, userComment = null,
                    colorSpace = null, orientation = null, resolutionDpi = null, compression = null,
                    durationFormatted = null, durationMs = null, bitrateFormatted = null, frameRate = null, videoRotation = null,
                    allTags = emptyList()
                )
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        }

        // Image reading via ExifInterface
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) throw IllegalStateException("Cannot open stream")
            val exif = ExifInterface(inputStream)

            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)
            val lensMake = exif.getAttribute(ExifInterface.TAG_LENS_MAKE)
            val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
            val serialNumber = exif.getAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER)
                ?: exif.getAttribute(ExifInterface.TAG_LENS_SERIAL_NUMBER)

            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                ?: exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            val exposureTimeRaw = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                ?: exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE)
            val shutterSpeed = formatExposureTime(exposureTimeRaw)
            val fNumberRaw = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                ?: exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)
            val aperture = fNumberRaw?.let { if (it.startsWith("f/", true)) it else "f/$it" }
            val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { "$it mm" }
            val focalLength35mm = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { "${it} mm" }

            val flashRaw = exif.getAttribute(ExifInterface.TAG_FLASH)
            val flash = flashRaw?.let { codeStr ->
                val code = codeStr.toIntOrNull()
                when {
                    code == null -> codeStr
                    code == 0 -> "Off (Did not fire)"
                    code and 1 != 0 -> "Fired"
                    else -> "Did not fire"
                }
            }

            val whiteBalanceRaw = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)
            val whiteBalance = when (whiteBalanceRaw) {
                "0" -> "Auto"
                "1" -> "Manual"
                else -> whiteBalanceRaw
            }

            val meteringModeRaw = exif.getAttribute(ExifInterface.TAG_METERING_MODE)
            val meteringMode = when (meteringModeRaw) {
                "1" -> "Average"
                "2" -> "Center-weighted average"
                "3" -> "Spot"
                "4" -> "Multi-spot"
                "5" -> "Pattern / Multi-segment"
                "6" -> "Partial"
                else -> meteringModeRaw
            }

            val exposureProgramRaw = exif.getAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM)
            val exposureProgram = when (exposureProgramRaw) {
                "1" -> "Manual"
                "2" -> "Normal program (Auto)"
                "3" -> "Aperture priority"
                "4" -> "Shutter priority"
                "5" -> "Creative program"
                "6" -> "Action program"
                "7" -> "Portrait mode"
                "8" -> "Landscape mode"
                else -> exposureProgramRaw
            }

            val exposureBias = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.let { "$it EV" }

            val latLong = FloatArray(2)
            val hasGps = exif.getLatLong(latLong)
            val latitude = if (hasGps) latLong[0].toDouble() else null
            val longitude = if (hasGps) latLong[1].toDouble() else null
            val altitude = if (hasGps) exif.getAltitude(0.0).takeIf { it != 0.0 } else null
            val gpsDms = if (hasGps) formatCoordinatesDms(latLong[0].toDouble(), latLong[1].toDouble()) else null
            val gpsDateStamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP)

            val dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            val dateDigitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            val dateModified = exif.getAttribute(ExifInterface.TAG_DATETIME)

            val artist = exif.getAttribute(ExifInterface.TAG_ARTIST)
            val copyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT)
            val title = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
            val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)

            val colorSpaceRaw = exif.getAttribute(ExifInterface.TAG_COLOR_SPACE)
            val colorSpace = when (colorSpaceRaw) {
                "1" -> "sRGB"
                "65535" -> "Uncalibrated / Adobe RGB"
                else -> colorSpaceRaw
            }

            val orientationRaw = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            val orientation = when (orientationRaw) {
                1 -> "Normal (0°)"
                3 -> "Upside Down (180°)"
                6 -> "Rotated 90° CW"
                8 -> "Rotated 270° CW"
                else -> "Standard ($orientationRaw)"
            }

            var w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
            var h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }

            if (w == null || h == null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, opts)
                        w = opts.outWidth.takeIf { it > 0 }
                        h = opts.outHeight.takeIf { it > 0 }
                    }
                } catch (e: Exception) {}
            }

            val megapixels = if (w != null && h != null) {
                String.format(Locale.US, "%.1f MP", (w!! * h!!) / 1_000_000.0)
            } else null

            val aspectRatio = if (w != null && h != null && h!! > 0) {
                val ratio = w!!.toDouble() / h!!
                when {
                    Math.abs(ratio - 1.333) < 0.05 -> "4:3"
                    Math.abs(ratio - 1.777) < 0.05 -> "16:9"
                    Math.abs(ratio - 1.5) < 0.05 -> "3:2"
                    Math.abs(ratio - 1.0) < 0.05 -> "1:1"
                    else -> String.format(Locale.US, "%.2f:1", ratio)
                }
            } else null

            val resX = exif.getAttribute(ExifInterface.TAG_X_RESOLUTION)
            val resY = exif.getAttribute(ExifInterface.TAG_Y_RESOLUTION)
            val resolutionDpi = if (resX != null && resY != null) "$resX × $resY dpi" else resX?.let { "$it dpi" }

            val compression = exif.getAttribute(ExifInterface.TAG_COMPRESSION)

            // Collect all non-null raw tags
            val rawTags = mutableListOf<DetailedTag>()

            val checkTag = { tag: String, label: String, cat: String, sensitive: Boolean ->
                val v = exif.getAttribute(tag)
                if (!v.isNullOrBlank()) {
                    rawTags.add(DetailedTag(tag, label, v, cat, sensitive))
                }
            }

            checkTag(ExifInterface.TAG_MAKE, "Camera Manufacturer", "Camera & Lens", false)
            checkTag(ExifInterface.TAG_MODEL, "Camera Model", "Camera & Lens", false)
            checkTag(ExifInterface.TAG_LENS_MAKE, "Lens Manufacturer", "Camera & Lens", false)
            checkTag(ExifInterface.TAG_LENS_MODEL, "Lens Model", "Camera & Lens", false)
            checkTag(ExifInterface.TAG_SOFTWARE, "Software / Firmware", "Camera & Lens", false)
            checkTag(ExifInterface.TAG_BODY_SERIAL_NUMBER, "Body Serial Number", "Camera & Lens", true)
            checkTag(ExifInterface.TAG_CAMERA_OWNER_NAME, "Camera Owner", "Camera & Lens", true)
            checkTag(ExifInterface.TAG_ISO_SPEED_RATINGS, "ISO Speed", "Exposure", false)
            checkTag(ExifInterface.TAG_EXPOSURE_TIME, "Exposure Time", "Exposure", false)
            checkTag(ExifInterface.TAG_F_NUMBER, "F-Number", "Exposure", false)
            checkTag(ExifInterface.TAG_FOCAL_LENGTH, "Focal Length", "Exposure", false)
            checkTag(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, "35mm Equivalent Focal Length", "Exposure", false)
            checkTag(ExifInterface.TAG_FLASH, "Flash", "Exposure", false)
            checkTag(ExifInterface.TAG_WHITE_BALANCE, "White Balance", "Exposure", false)
            checkTag(ExifInterface.TAG_METERING_MODE, "Metering Mode", "Exposure", false)
            checkTag(ExifInterface.TAG_EXPOSURE_PROGRAM, "Exposure Program", "Exposure", false)
            checkTag(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, "Exposure Bias", "Exposure", false)
            checkTag(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, "Digital Zoom", "Exposure", false)
            checkTag(ExifInterface.TAG_GPS_LATITUDE, "GPS Latitude", "Location (GPS)", true)
            checkTag(ExifInterface.TAG_GPS_LONGITUDE, "GPS Longitude", "Location (GPS)", true)
            checkTag(ExifInterface.TAG_GPS_ALTITUDE, "GPS Altitude", "Location (GPS)", true)
            checkTag(ExifInterface.TAG_GPS_DATESTAMP, "GPS DateStamp", "Location (GPS)", true)
            checkTag(ExifInterface.TAG_GPS_TIMESTAMP, "GPS TimeStamp", "Location (GPS)", true)
            checkTag(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS Processing Method", "Location (GPS)", true)
            checkTag(ExifInterface.TAG_DATETIME_ORIGINAL, "Date Taken", "Dates & Time", false)
            checkTag(ExifInterface.TAG_DATETIME_DIGITIZED, "Date Digitized", "Dates & Time", false)
            checkTag(ExifInterface.TAG_DATETIME, "Date Modified", "Dates & Time", false)
            checkTag(ExifInterface.TAG_ARTIST, "Artist / Author", "IPTC & Tags", false)
            checkTag(ExifInterface.TAG_COPYRIGHT, "Copyright", "IPTC & Tags", false)
            checkTag(ExifInterface.TAG_IMAGE_DESCRIPTION, "Image Description", "IPTC & Tags", false)
            checkTag(ExifInterface.TAG_USER_COMMENT, "User Comment", "IPTC & Tags", false)
            checkTag(ExifInterface.TAG_COLOR_SPACE, "Color Space", "File & Specs", false)
            checkTag(ExifInterface.TAG_ORIENTATION, "Orientation", "File & Specs", false)
            checkTag(ExifInterface.TAG_X_RESOLUTION, "X-Resolution", "File & Specs", false)
            checkTag(ExifInterface.TAG_Y_RESOLUTION, "Y-Resolution", "File & Specs", false)
            checkTag(ExifInterface.TAG_COMPRESSION, "Compression", "File & Specs", false)
            checkTag(ExifInterface.TAG_BITS_PER_SAMPLE, "Bits Per Sample", "File & Specs", false)

            FullMetadataReport(
                fileName = fileName,
                fileSizeFormatted = fileSizeFormatted,
                fileSizeBytes = fileSizeBytes,
                mimeType = mimeType,
                mediaType = MediaType.IMAGE,
                width = w,
                height = h,
                megapixels = megapixels,
                aspectRatio = aspectRatio,
                make = make,
                model = model,
                lensModel = lensModel,
                lensMake = lensMake,
                software = software,
                serialNumber = serialNumber,
                iso = iso,
                shutterSpeed = shutterSpeed,
                aperture = aperture,
                focalLength = focalLength,
                focalLength35mm = focalLength35mm,
                flash = flash,
                whiteBalance = whiteBalance,
                meteringMode = meteringMode,
                exposureProgram = exposureProgram,
                exposureBias = exposureBias,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                gpsDms = gpsDms,
                gpsDateStamp = gpsDateStamp,
                hasGps = hasGps,
                dateTaken = dateTaken,
                dateDigitized = dateDigitized,
                dateModified = dateModified,
                artist = artist,
                copyright = copyright,
                title = title,
                userComment = userComment,
                colorSpace = colorSpace,
                orientation = orientation,
                resolutionDpi = resolutionDpi,
                compression = compression,
                durationFormatted = null,
                durationMs = null,
                bitrateFormatted = null,
                frameRate = null,
                videoRotation = null,
                allTags = rawTags
            )
        } catch (e: Exception) {
            e.printStackTrace()
            FullMetadataReport(
                fileName = fileName,
                fileSizeFormatted = fileSizeFormatted,
                fileSizeBytes = fileSizeBytes,
                mimeType = mimeType,
                mediaType = MediaType.IMAGE,
                width = null, height = null, megapixels = null, aspectRatio = null,
                make = null, model = null, lensModel = null, lensMake = null, software = null, serialNumber = null,
                iso = null, shutterSpeed = null, aperture = null, focalLength = null, focalLength35mm = null,
                flash = null, whiteBalance = null, meteringMode = null, exposureProgram = null, exposureBias = null,
                latitude = null, longitude = null, altitude = null, gpsDms = null, gpsDateStamp = null, hasGps = false,
                dateTaken = null, dateDigitized = null, dateModified = null,
                artist = null, copyright = null, title = null, userComment = null,
                colorSpace = null, orientation = null, resolutionDpi = null, compression = null,
                durationFormatted = null, durationMs = null, bitrateFormatted = null, frameRate = null, videoRotation = null,
                allTags = emptyList()
            )
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    fun generateMetadataTextReport(report: FullMetadataReport): String {
        val sb = StringBuilder()
        sb.append("=== MEDIA METADATA REPORT ===\n")
        sb.append("File: ${report.fileName}\n")
        sb.append("Size: ${report.fileSizeFormatted} (${report.fileSizeBytes} bytes)\n")
        sb.append("Type: ${report.mimeType}\n")
        if (report.width != null && report.height != null) {
            sb.append("Resolution: ${report.width} × ${report.height} px")
            if (!report.megapixels.isNullOrBlank()) sb.append(" (${report.megapixels})")
            if (!report.aspectRatio.isNullOrBlank()) sb.append(" [${report.aspectRatio}]")
            sb.append("\n")
        }

        if (report.hasGps) {
            sb.append("\n--- GPS LOCATION ---\n")
            sb.append("Latitude: ${report.latitude}\n")
            sb.append("Longitude: ${report.longitude}\n")
            report.gpsDms?.let { sb.append("DMS: $it\n") }
            report.altitude?.let { sb.append("Altitude: ${it}m\n") }
            report.gpsDateStamp?.let { sb.append("GPS Date: $it\n") }
        } else {
            sb.append("\nGPS: None (Safe from location tracking)\n")
        }

        if (report.mediaType == MediaType.IMAGE) {
            sb.append("\n--- CAMERA & LENS ---\n")
            report.make?.let { sb.append("Make: $it\n") }
            report.model?.let { sb.append("Model: $it\n") }
            report.lensModel?.let { sb.append("Lens: $it\n") }
            report.software?.let { sb.append("Software: $it\n") }
            report.serialNumber?.let { sb.append("Serial #: $it\n") }

            sb.append("\n--- EXPOSURE & OPTICS ---\n")
            report.iso?.let { sb.append("ISO: $it\n") }
            report.shutterSpeed?.let { sb.append("Shutter Speed: $it\n") }
            report.aperture?.let { sb.append("Aperture: $it\n") }
            report.focalLength?.let { sb.append("Focal Length: $it\n") }
            report.focalLength35mm?.let { sb.append("35mm Equivalent: $it\n") }
            report.flash?.let { sb.append("Flash: $it\n") }
            report.whiteBalance?.let { sb.append("White Balance: $it\n") }
            report.meteringMode?.let { sb.append("Metering: $it\n") }
            report.exposureBias?.let { sb.append("Bias: $it\n") }
        } else {
            sb.append("\n--- VIDEO DETAILS ---\n")
            report.durationFormatted?.let { sb.append("Duration: $it\n") }
            report.bitrateFormatted?.let { sb.append("Bitrate: $it\n") }
            report.frameRate?.let { sb.append("Framerate: $it\n") }
            report.videoRotation?.let { sb.append("Rotation: $it\n") }
        }

        sb.append("\n--- TIMESTAMPS ---\n")
        report.dateTaken?.let { sb.append("Date Taken: $it\n") }
        report.dateModified?.let { sb.append("Date Modified: $it\n") }

        if (!report.artist.isNullOrBlank() || !report.copyright.isNullOrBlank() || !report.title.isNullOrBlank()) {
            sb.append("\n--- IPTC & TAGS ---\n")
            report.title?.let { sb.append("Title: $it\n") }
            report.artist?.let { sb.append("Artist: $it\n") }
            report.copyright?.let { sb.append("Copyright: $it\n") }
            report.userComment?.let { sb.append("Comment: $it\n") }
        }

        sb.append("\nGenerated by Arwora Hyper Toolkit\n")
        return sb.toString()
    }

    fun shareImage(context: Context, uri: Uri, mimeType: String = "image/*") {
        shareMedia(context, uri, mimeType)
    }
}
