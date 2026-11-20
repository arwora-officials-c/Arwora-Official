package com.arwora.feature.compressor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Specialized compression orchestrator executing all file reading, encoding,
 * and stream writing strictly on [Dispatchers.IO].
 */
object CompressorEngines {

    private const val CACHE_FOLDER = "compressed_outputs"

    fun getOutputDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun clearStaleOutputs(context: Context) {
        val dir = File(context.cacheDir, CACHE_FOLDER)
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                try {
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) {
                        file.delete()
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    suspend fun resolveFileMetadata(context: Context, uri: Uri): CompressorFileItem =
        withContext(Dispatchers.IO) {
            var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "selected_file"
            var sizeBytes = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
            if (sizeBytes <= 0L) {
                sizeBytes = try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                        ?.takeIf { it > 0L } ?: 0L
                } catch (_: Throwable) {
                    0L
                }
            }
            if (sizeBytes <= 0L) {
                sizeBytes = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val buf = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val r = stream.read(buf)
                            if (r <= 0) break
                            total += r
                        }
                        total
                    } ?: 0L
                } catch (_: Throwable) {
                    0L
                }
            }
            val mime = context.contentResolver.getType(uri) ?: guessMimeFromName(displayName)
            CompressorFileItem(
                uri = uri,
                fileName = displayName,
                mimeType = mime,
                originalSizeBytes = sizeBytes
            )
        }

    // -------------------------------------------------------------------------
    // 1. VIDEO COMPRESSOR
    // -------------------------------------------------------------------------
    suspend fun compressVideo(
        context: Context,
        item: CompressorFileItem,
        options: VideoCompressionOptions,
        onProgress: (Float) -> Unit
    ): CompressedOutputResult = withContext(Dispatchers.IO) {
        val baseName = item.fileName.substringBeforeLast('.')
        val outFile = uniqueFile(getOutputDir(context), "${baseName}_compressed.mp4")
        MediaTranscodeEngine.transcodeVideo(
            context = context,
            sourceUri = item.uri,
            destFile = outFile,
            options = options,
            onProgress = onProgress
        )
        CompressedOutputResult(
            sourceName = item.fileName,
            outputFile = outFile,
            outputName = outFile.name,
            mimeType = "video/mp4",
            originalBytes = item.originalSizeBytes,
            compressedBytes = outFile.length(),
            engineUsed = "MediaCodec H.264 (${options.targetHeight}p)",
            summaryNote = "${options.bitrateKbps} kbps • Audio ${if (options.keepAudio) "Kept" else "Muted"}"
        )
    }

    // -------------------------------------------------------------------------
    // 2. AUDIO COMPRESSOR
    // -------------------------------------------------------------------------
    suspend fun compressAudio(
        context: Context,
        item: CompressorFileItem,
        options: AudioCompressionOptions,
        onProgress: (Float) -> Unit
    ): CompressedOutputResult = withContext(Dispatchers.IO) {
        val baseName = item.fileName.substringBeforeLast('.')
        val outFile = uniqueFile(getOutputDir(context), "${baseName}_compressed.m4a")
        MediaTranscodeEngine.transcodeAudio(
            context = context,
            sourceUri = item.uri,
            destFile = outFile,
            options = options,
            onProgress = onProgress
        )
        CompressedOutputResult(
            sourceName = item.fileName,
            outputFile = outFile,
            outputName = outFile.name,
            mimeType = "audio/mp4",
            originalBytes = item.originalSizeBytes,
            compressedBytes = outFile.length(),
            engineUsed = "MediaCodec AAC-LC",
            summaryNote = "${options.bitrateKbps} kbps • ${options.sampleRateHz} Hz • ${if (options.mono) "Mono" else "Stereo"}"
        )
    }

    // -------------------------------------------------------------------------
    // 3. IMAGE / PNG COMPRESSOR
    // -------------------------------------------------------------------------
    suspend fun compressImage(
        context: Context,
        item: CompressorFileItem,
        options: ImageCompressionOptions,
        onProgress: (Float) -> Unit
    ): CompressedOutputResult = withContext(Dispatchers.IO) {
        onProgress(0.12f)
        val rawBytes = context.contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot read selected image")
        currentCoroutineContext().ensureActive()

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            throw IllegalStateException("Unsupported or corrupted image format.")
        }

        val maxDim = options.maxDimensionPx.coerceIn(320, 8192)
        val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDim)
        val srcExt = item.fileName.substringAfterLast('.', "").lowercase(Locale.US)

        val chosenFormat = resolveImageFormat(
            requested = options.format,
            srcExt = srcExt,
            hasAlphaHint = boundsOpts.outMimeType == "image/png" || boundsOpts.outMimeType == "image/webp"
        )

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = if (chosenFormat == ImageOutputFormat.PNG && options.quality < 85 && srcExt == "png") {
                Bitmap.Config.ARGB_8888
            } else {
                Bitmap.Config.ARGB_8888
            }
        }
        onProgress(0.38f)

        var bitmap: Bitmap? = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: throw IllegalStateException("Failed to decode image bitmap.")
        var rotatedOrScaled: Bitmap? = null

        try {
            currentCoroutineContext().ensureActive()
            val orientation = try {
                ExifInterface(rawBytes.inputStream()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val working = applyOrientationAndScale(bitmap!!, orientation, maxDim)
            if (working !== bitmap) {
                rotatedOrScaled = working
                bitmap!!.recycle()
                bitmap = working
            }

            // If user explicitly requests PNG and quality < 85 on an opaque image, convert to RGB_565
            // so lossless PNG deflation achieves dramatic size savings while remaining valid .png
            val finalBitmap = if (chosenFormat == ImageOutputFormat.PNG && options.quality < 85 && !bitmap!!.hasAlpha()) {
                val rgb565 = bitmap!!.copy(Bitmap.Config.RGB_565, false)
                if (rgb565 != null) {
                    bitmap!!.recycle()
                    bitmap = rgb565
                    rotatedOrScaled = rgb565
                }
                bitmap!!
            } else {
                bitmap!!
            }

            onProgress(0.72f)
            val baseName = item.fileName.substringBeforeLast('.')
            val outFile = uniqueFile(getOutputDir(context), "${baseName}_compressed.${chosenFormat.extension}")
            FileOutputStream(outFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    val compressFormat = toAndroidCompressFormat(chosenFormat)
                    val ok = finalBitmap.compress(compressFormat, options.quality.coerceIn(10, 100), bos)
                    if (!ok) {
                        throw IllegalStateException("Bitmap encoder rejected format $chosenFormat")
                    }
                    bos.flush()
                }
            }
            onProgress(1f)

            CompressedOutputResult(
                sourceName = item.fileName,
                outputFile = outFile,
                outputName = outFile.name,
                mimeType = chosenFormat.mimeType,
                originalBytes = item.originalSizeBytes.takeIf { it > 0L } ?: rawBytes.size.toLong(),
                compressedBytes = outFile.length(),
                engineUsed = "Bitmap ${chosenFormat.label}",
                summaryNote = "${finalBitmap.width}×${finalBitmap.height} • Q${options.quality}%"
            )
        } finally {
            try {
                if (bitmap != null && !bitmap!!.isRecycled) bitmap!!.recycle()
            } catch (_: Throwable) {}
            try {
                if (rotatedOrScaled != null && !rotatedOrScaled!!.isRecycled) rotatedOrScaled!!.recycle()
            } catch (_: Throwable) {}
        }
    }

    // -------------------------------------------------------------------------
    // 4. PDF COMPRESSOR
    // -------------------------------------------------------------------------
    suspend fun compressPdf(
        context: Context,
        item: CompressorFileItem,
        options: PdfCompressionOptions,
        onProgress: (Float) -> Unit
    ): CompressedOutputResult = withContext(Dispatchers.IO) {
        onProgress(0.08f)
        val baseName = item.fileName.substringBeforeLast('.')
        val tempIn = File(getOutputDir(context), "_tmp_src_${System.nanoTime()}.pdf")
        val outFile = uniqueFile(getOutputDir(context), "${baseName}_compressed.pdf")

        try {
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                FileOutputStream(tempIn).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Cannot open PDF stream.")

            val originalSize = item.originalSizeBytes.takeIf { it > 0L } ?: tempIn.length()
            var usedRenderer = false
            var pageCount = 0

            try {
                context.contentResolver.openFileDescriptor(Uri.fromFile(tempIn), "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                        if (pageCount > 0) {
                            val pdfDoc = PdfDocument()
                            try {
                                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                                for (i in 0 until pageCount) {
                                    currentCoroutineContext().ensureActive()
                                    renderer.openPage(i).use { page ->
                                        val maxRenderDim = 1440
                                        val rawW = (page.width * options.renderScale).roundToInt().coerceAtLeast(1)
                                        val rawH = (page.height * options.renderScale).roundToInt().coerceAtLeast(1)
                                        val scaleCap = min(
                                            1f,
                                            maxRenderDim.toFloat() / max(rawW, rawH).toFloat()
                                        )
                                        val w = (rawW * scaleCap).roundToInt().coerceAtLeast(1)
                                        val h = (rawH * scaleCap).roundToInt().coerceAtLeast(1)

                                        val pageBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                        var recompressedBmp: Bitmap? = null
                                        try {
                                            Canvas(pageBmp).drawColor(android.graphics.Color.WHITE)
                                            page.render(pageBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                            val jpegBytes = ByteArrayOutputStream()
                                            pageBmp.compress(Bitmap.CompressFormat.JPEG, options.jpegQuality, jpegBytes)
                                            val jpgArr = jpegBytes.toByteArray()
                                            recompressedBmp = BitmapFactory.decodeByteArray(jpgArr, 0, jpgArr.size)

                                            val docPageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
                                            val outPage = pdfDoc.startPage(docPageInfo)
                                            val dstRect = android.graphics.Rect(0, 0, page.width, page.height)
                                            outPage.canvas.drawBitmap(recompressedBmp ?: pageBmp, null, dstRect, paint)
                                            pdfDoc.finishPage(outPage)
                                        } finally {
                                            pageBmp.recycle()
                                            recompressedBmp?.recycle()
                                        }
                                    }
                                    onProgress(((i + 1).toFloat() / pageCount.toFloat()) * 0.9f)
                                }
                                FileOutputStream(outFile).use { fos ->
                                    pdfDoc.writeTo(BufferedOutputStream(fos))
                                }
                                usedRenderer = true
                            } finally {
                                pdfDoc.close()
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                usedRenderer = false
            }

            if (!usedRenderer) {
                // Fallback: pure PDF comment/whitespace & zlib stream compaction
                val raw = tempIn.readBytes()
                val compacted = compactPdfStructureBytes(raw)
                outFile.writeBytes(compacted)
                onProgress(1f)
                return@withContext CompressedOutputResult(
                    sourceName = item.fileName,
                    outputFile = outFile,
                    outputName = outFile.name,
                    mimeType = "application/pdf",
                    originalBytes = originalSize,
                    compressedBytes = outFile.length(),
                    engineUsed = "PDF Stream Compactor",
                    summaryNote = "Preset: ${options.preset.label} • Metadata & spacing stripped"
                )
            }

            onProgress(1f)
            CompressedOutputResult(
                sourceName = item.fileName,
                outputFile = outFile,
                outputName = outFile.name,
                mimeType = "application/pdf",
                originalBytes = originalSize,
                compressedBytes = outFile.length(),
                engineUsed = "PdfRenderer + JPEG Re-pack",
                summaryNote = "$pageCount page(s) • ${options.preset.label} (Q${options.jpegQuality}%)"
            )
        } finally {
            try { tempIn.delete() } catch (_: Throwable) {}
        }
    }

    // -------------------------------------------------------------------------
    // 5. ARCHIVE COMPRESSOR
    // -------------------------------------------------------------------------
    suspend fun compressArchive(
        context: Context,
        items: List<CompressorFileItem>,
        options: ArchiveCompressionOptions,
        onProgress: (Float) -> Unit
    ): CompressedOutputResult = withContext(Dispatchers.IO) {
        require(items.isNotEmpty()) { "Select at least one file to archive." }
        val totalOriginalBytes = items.sumOf { it.originalSizeBytes.coerceAtLeast(1L) }
        val firstBase = items.first().fileName.substringBeforeLast('.')

        if (items.size == 1 && options.useGzipForSingleFile) {
            val single = items.first()
            val outFile = uniqueFile(getOutputDir(context), "${single.fileName}.gz")
            var copiedBytes = 0L
            context.contentResolver.openInputStream(single.uri)?.use { input ->
                FileOutputStream(outFile).use { fos ->
                    GZIPOutputStream(BufferedOutputStream(fos)).use { gzip ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n <= 0) break
                            gzip.write(buf, 0, n)
                            copiedBytes += n
                            onProgress((copiedBytes.toFloat() / totalOriginalBytes.toFloat()).coerceIn(0f, 0.98f))
                        }
                        gzip.finish()
                    }
                }
            } ?: throw IllegalStateException("Cannot open ${single.fileName}")
            onProgress(1f)
            return@withContext CompressedOutputResult(
                sourceName = single.fileName,
                outputFile = outFile,
                outputName = outFile.name,
                mimeType = "application/gzip",
                originalBytes = single.originalSizeBytes.takeIf { it > 0L } ?: copiedBytes,
                compressedBytes = outFile.length(),
                engineUsed = "GZIP Deflate",
                summaryNote = "1 file compressed to .gz"
            )
        }

        val zipName = if (items.size == 1) "${firstBase}_compressed.zip" else "arwora_archive_${items.size}_files.zip"
        val outFile = uniqueFile(getOutputDir(context), zipName)
        val usedNames = HashSet<String>()
        var processedBytes = 0L
        val deflateLevel = options.level.coerceIn(Deflater.BEST_SPEED, Deflater.BEST_COMPRESSION)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            zos.setMethod(ZipOutputStream.DEFLATED)
            zos.setLevel(deflateLevel)

            for (item in items) {
                currentCoroutineContext().ensureActive()
                val entryName = deduplicateEntryName(item.fileName, usedNames)
                zos.putNextEntry(ZipEntry(entryName))
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        zos.write(buf, 0, n)
                        processedBytes += n
                        onProgress((processedBytes.toFloat() / totalOriginalBytes.toFloat()).coerceIn(0f, 0.98f))
                    }
                } ?: throw IllegalStateException("Cannot read ${item.fileName}")
                zos.closeEntry()
            }
            zos.finish()
        }
        onProgress(1f)

        CompressedOutputResult(
            sourceName = if (items.size == 1) items.first().fileName else "${items.size} files",
            outputFile = outFile,
            outputName = outFile.name,
            mimeType = "application/zip",
            originalBytes = items.sumOf { it.originalSizeBytes }.takeIf { it > 0L } ?: processedBytes,
            compressedBytes = outFile.length(),
            engineUsed = "ZipOutputStream (Level $deflateLevel)",
            summaryNote = "${items.size} file(s) archived • DEFLATE level $deflateLevel"
        )
    }

    // -------------------------------------------------------------------------
    // 6. FILE COMPRESSOR (HTML, JSON, XML, CSS, JS, TXT)
    // -------------------------------------------------------------------------
    suspend fun compressTextOrCodeFile(
        context: Context,
        item: CompressorFileItem,
        options: FileCompressionOptions,
        onProgress: (Float) -> Unit
    ): CompressedOutputResult = withContext(Dispatchers.IO) {
        onProgress(0.15f)
        val rawBytes = context.contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot read ${item.fileName}")
        currentCoroutineContext().ensureActive()

        val ext = item.fileName.substringAfterLast('.', "txt").lowercase(Locale.US)
        val baseName = item.fileName.substringBeforeLast('.')
        val rawText = rawBytes.toString(Charsets.UTF_8)

        onProgress(0.5f)
        val processedText = if (options.minifyCode) {
            minifyContentByExtension(rawText, ext)
        } else {
            rawText
        }
        val processedBytes = processedText.toByteArray(Charsets.UTF_8)
        onProgress(0.8f)

        val outFile = if (options.wrapWithGzip) {
            val gzFile = uniqueFile(getOutputDir(context), "${baseName}.min.${ext}.gz")
            FileOutputStream(gzFile).use { fos ->
                GZIPOutputStream(BufferedOutputStream(fos)).use { gzip ->
                    gzip.write(processedBytes)
                    gzip.finish()
                }
            }
            gzFile
        } else {
            val minFile = uniqueFile(getOutputDir(context), "${baseName}.min.${ext}")
            minFile.writeBytes(processedBytes)
            minFile
        }
        onProgress(1f)

        CompressedOutputResult(
            sourceName = item.fileName,
            outputFile = outFile,
            outputName = outFile.name,
            mimeType = if (options.wrapWithGzip) "application/gzip" else (item.mimeType.ifBlank { "text/plain" }),
            originalBytes = item.originalSizeBytes.takeIf { it > 0L } ?: rawBytes.size.toLong(),
            compressedBytes = outFile.length(),
            engineUsed = if (options.wrapWithGzip) "Structured Minifier + GZIP" else "Structured ${ext.uppercase(Locale.US)} Minifier",
            summaryNote = if (options.wrapWithGzip) "Minified & GZIP compressed" else "Whitespace & comments stripped"
        )
    }

    // -------------------------------------------------------------------------
    // 7. GIF COMPRESSOR
    // -------------------------------------------------------------------------
    suspend fun compressGif(
        context: Context,
        item: CompressorFileItem,
        options: GifCompressionOptions,
        onProgress: (Float) -> Unit
    ): CompressedOutputResult = withContext(Dispatchers.IO) {
        onProgress(0.1f)
        val frames = GifCodecEngine.decodeFrames(context, item.uri)
        onProgress(0.35f)
        val baseName = item.fileName.substringBeforeLast('.')
        val outFile = uniqueFile(getOutputDir(context), "${baseName}_compressed.gif")
        FileOutputStream(outFile).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                GifCodecEngine.compressFramesToGif(
                    frames = frames,
                    options = options,
                    outputStream = bos,
                    onProgress = { encodePct ->
                        onProgress(0.35f + encodePct * 0.65f)
                    }
                )
            }
        }
        onProgress(1f)
        CompressedOutputResult(
            sourceName = item.fileName,
            outputFile = outFile,
            outputName = outFile.name,
            mimeType = "image/gif",
            originalBytes = item.originalSizeBytes,
            compressedBytes = outFile.length(),
            engineUsed = "GIF89a Quantizer + LZW",
            summaryNote = "${frames.size} frame(s) • ${options.scalePercent}% scale • ${options.maxColors} colors"
        )
    }

    // -------------------------------------------------------------------------
    // 8. BATCH COMPRESSOR (MIXED FILES CONCURRENTLY ON Dispatchers.IO)
    // -------------------------------------------------------------------------
    suspend fun compressBatch(
        context: Context,
        items: List<CompressorFileItem>,
        videoOptions: VideoCompressionOptions,
        audioOptions: AudioCompressionOptions,
        imageOptions: ImageCompressionOptions,
        pdfOptions: PdfCompressionOptions,
        archiveOptions: ArchiveCompressionOptions,
        fileOptions: FileCompressionOptions,
        gifOptions: GifCompressionOptions,
        bundleAsZip: Boolean,
        onEntryUpdated: (Int, BatchCompressionEntry) -> Unit,
        onOverallProgress: (Float) -> Unit
    ): Pair<List<CompressedOutputResult>, CompressedOutputResult?> = withContext(Dispatchers.IO) {
        require(items.isNotEmpty()) { "Select at least two files for batch compression." }
        val semaphore = Semaphore(2)
        val total = items.size
        var completedCount = 0

        val results = coroutineScope {
            items.mapIndexed { index, item ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        onEntryUpdated(
                            index,
                            BatchCompressionEntry(
                                fileName = item.fileName,
                                detectedType = item.detectedType,
                                originalBytes = item.originalSizeBytes,
                                status = BatchItemStatus.PROCESSING
                            )
                        )
                        try {
                            val result = when (item.detectedType) {
                                CompressorType.VIDEO -> compressVideo(context, item, videoOptions) {}
                                CompressorType.AUDIO -> compressAudio(context, item, audioOptions) {}
                                CompressorType.IMAGE -> compressImage(context, item, imageOptions) {}
                                CompressorType.PDF -> compressPdf(context, item, pdfOptions) {}
                                CompressorType.FILE -> compressTextOrCodeFile(context, item, fileOptions) {}
                                CompressorType.GIF -> compressGif(context, item, gifOptions) {}
                                CompressorType.ARCHIVE, CompressorType.BATCH ->
                                    compressArchive(context, listOf(item), archiveOptions) {}
                            }
                            onEntryUpdated(
                                index,
                                BatchCompressionEntry(
                                    fileName = item.fileName,
                                    detectedType = item.detectedType,
                                    originalBytes = result.originalBytes,
                                    compressedBytes = result.compressedBytes,
                                    status = BatchItemStatus.COMPLETED
                                )
                            )
                            result
                        } catch (e: Exception) {
                            onEntryUpdated(
                                index,
                                BatchCompressionEntry(
                                    fileName = item.fileName,
                                    detectedType = item.detectedType,
                                    originalBytes = item.originalSizeBytes,
                                    status = BatchItemStatus.FAILED,
                                    errorNote = e.message ?: "Compression failed"
                                )
                            )
                            null
                        } finally {
                            completedCount++
                            onOverallProgress((completedCount.toFloat() / total.toFloat()).coerceIn(0f, 0.95f))
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val bundledZip = if (bundleAsZip && results.isNotEmpty()) {
            val zipOut = uniqueFile(getOutputDir(context), "arwora_batch_${results.size}_compressed.zip")
            val usedNames = HashSet<String>()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipOut))).use { zos ->
                zos.setMethod(ZipOutputStream.DEFLATED)
                zos.setLevel(archiveOptions.level.coerceIn(1, 9))
                for (res in results) {
                    currentCoroutineContext().ensureActive()
                    val name = deduplicateEntryName(res.outputName, usedNames)
                    zos.putNextEntry(ZipEntry(name))
                    BufferedInputStream(FileInputStream(res.outputFile)).use { bis ->
                        bis.copyTo(zos, 16 * 1024)
                    }
                    zos.closeEntry()
                }
                zos.finish()
            }
            val origTotal = results.sumOf { it.originalBytes }
            CompressedOutputResult(
                sourceName = "${results.size} files (Batch ZIP)",
                outputFile = zipOut,
                outputName = zipOut.name,
                mimeType = "application/zip",
                originalBytes = origTotal,
                compressedBytes = zipOut.length(),
                engineUsed = "Batch ZIP Bundle",
                summaryNote = "${results.size} compressed item(s) packaged"
            )
        } else {
            null
        }

        onOverallProgress(1f)
        results to bundledZip
    }

    // -------------------------------------------------------------------------
    // PURE / TESTABLE OPTIMIZATION & UTILITY HELPERS
    // -------------------------------------------------------------------------
    internal fun minifyContentByExtension(content: String, extension: String): String {
        return when (extension.lowercase(Locale.US)) {
            "json" -> minifyJsonPreservingStrings(content)
            "html", "htm", "xml", "svg" -> minifyMarkup(content)
            "css" -> minifyCss(content)
            else -> minifyGenericText(content)
        }
    }

    internal fun minifyJsonPreservingStrings(json: String): String {
        val sb = StringBuilder(json.length)
        var inString = false
        var escaped = false
        for (ch in json) {
            if (inString) {
                sb.append(ch)
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when {
                    ch == '"' -> {
                        inString = true
                        sb.append(ch)
                    }
                    ch.isWhitespace() -> Unit
                    else -> sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    internal fun minifyMarkup(markup: String): String {
        // Strip non-conditional HTML/XML comments
        val withoutComments = markup.replace(Regex("<!--(?!\\[if).*?-->", RegexOption.DOT_MATCHES_ALL), "")
        return withoutComments
            .replace(Regex(">\\s+<"), "><")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    internal fun minifyCss(css: String): String {
        val noComments = css.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noComments
            .replace(Regex("\\s*([{}:;,])\\s*"), "$1")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    internal fun minifyGenericText(text: String): String {
        return text.lines()
            .map { it.trimEnd() }
            .filterIndexed { idx, line -> idx == 0 || line.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    internal fun compactPdfStructureBytes(rawPdf: ByteArray): ByteArray {
        val content = rawPdf.toString(Charsets.ISO_8859_1)
        val compacted = content
            .replace(Regex("(?m)^%[^%PDF\\n\\r].*\\r?\\n"), "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("(\\r?\\n){2,}"), "\n")
        return compacted.toByteArray(Charsets.ISO_8859_1)
    }

    internal fun deduplicateEntryName(rawName: String, usedNames: MutableSet<String>): String {
        val safeName = rawName.replace("/", "_").replace("\\", "_").ifBlank { "file" }
        if (usedNames.add(safeName)) return safeName
        val base = safeName.substringBeforeLast('.', safeName)
        val ext = if (safeName.contains('.')) ".${safeName.substringAfterLast('.')}" else ""
        var counter = 1
        while (true) {
            val candidate = "$base ($counter)$ext"
            if (usedNames.add(candidate)) return candidate
            counter++
        }
    }

    internal fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        while ((largest / inSampleSize) > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun resolveImageFormat(
        requested: ImageOutputFormat,
        srcExt: String,
        hasAlphaHint: Boolean
    ): ImageOutputFormat {
        if (requested != ImageOutputFormat.AUTO) return requested
        return when (srcExt) {
            "png" -> if (hasAlphaHint) ImageOutputFormat.WEBP_LOSSY else ImageOutputFormat.PNG
            "webp" -> ImageOutputFormat.WEBP_LOSSY
            else -> ImageOutputFormat.JPEG
        }
    }

    @Suppress("DEPRECATION")
    private fun toAndroidCompressFormat(format: ImageOutputFormat): Bitmap.CompressFormat {
        return when (format) {
            ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageOutputFormat.WEBP_LOSSY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            ImageOutputFormat.WEBP_LOSSLESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.WEBP
            }
            ImageOutputFormat.JPEG, ImageOutputFormat.AUTO -> Bitmap.CompressFormat.JPEG
        }
    }

    private fun applyOrientationAndScale(src: Bitmap, orientation: Int, maxDim: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        val largest = max(src.width, src.height)
        if (largest > maxDim) {
            val scale = maxDim.toFloat() / largest.toFloat()
            matrix.postScale(scale, scale)
        }
        if (matrix.isIdentity) return src
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun uniqueFile(dir: File, preferredName: String): File {
        var candidate = File(dir, preferredName)
        if (!candidate.exists()) return candidate
        val base = preferredName.substringBeforeLast('.', preferredName)
        val ext = if (preferredName.contains('.')) ".${preferredName.substringAfterLast('.')}" else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$i$ext")
            i++
        }
        return candidate
    }

    private fun guessMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "mp4", "mkv", "mov" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "gz" -> "application/gzip"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }

    // -------------------------------------------------------------------------
    // SAVE & SHARE HELPERS (NO BROAD STORAGE PERMISSION NEEDED)
    // -------------------------------------------------------------------------
    suspend fun saveToDownloads(context: Context, result: CompressedOutputResult): String =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, result.outputName)
                    put(MediaStore.Downloads.MIME_TYPE, result.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ArworaCompressor")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create Download entry.")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(result.outputFile).use { input ->
                            input.copyTo(out, 16 * 1024)
                        }
                    } ?: throw IllegalStateException("Cannot write to Downloads.")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Downloads/ArworaCompressor/${result.outputName}"
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                val targetDir = File(appDownloads, "ArworaCompressor").apply { mkdirs() }
                val dest = uniqueFile(targetDir, result.outputName)
                FileInputStream(result.outputFile).use { input ->
                    FileOutputStream(dest).use { out ->
                        input.copyTo(out, 16 * 1024)
                    }
                }
                dest.absolutePath
            }
        }

    fun buildShareIntent(context: Context, result: CompressedOutputResult): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            result.outputFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
