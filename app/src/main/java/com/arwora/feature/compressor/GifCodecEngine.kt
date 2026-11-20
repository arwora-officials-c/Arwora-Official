package com.arwora.feature.compressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GifFrameData(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val delayMs: Int
)

/**
 * Pure-Kotlin animated GIF89a quantizer + LZW encoder with Android frame decoding
 * (ImageDecoder on API 28+, Movie fallback on API 24-27, plus raw GIF stream fallback).
 */
object GifCodecEngine {

    suspend fun decodeFrames(context: Context, uri: Uri): List<GifFrameData> {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to open GIF stream")
        currentCoroutineContext().ensureActive()

        // Try Android Movie decoder first (works reliably across API 24+ for animated GIFs)
        @Suppress("DEPRECATION")
        val movie = try {
            Movie.decodeByteArray(rawBytes, 0, rawBytes.size)
        } catch (_: Throwable) {
            null
        }
        if (movie != null && movie.width() > 0 && movie.height() > 0) {
            val duration = movie.duration()
            val frames = mutableListOf<GifFrameData>()
            val stepMs = if (duration > 0) (duration / 24).coerceIn(50, 140) else 100
            val sampleTimes = if (duration > 0) {
                (0 until duration step stepMs).toList()
            } else {
                listOf(0)
            }
            val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            try {
                for (timeMs in sampleTimes) {
                    currentCoroutineContext().ensureActive()
                    bitmap.eraseColor(Color.TRANSPARENT)
                    movie.setTime(timeMs)
                    movie.draw(canvas, 0f, 0f)
                    val pixels = IntArray(movie.width() * movie.height())
                    bitmap.getPixels(pixels, 0, movie.width(), 0, 0, movie.width(), movie.height())
                    frames.add(
                        GifFrameData(
                            pixels = pixels,
                            width = movie.width(),
                            height = movie.height(),
                            delayMs = stepMs
                        )
                    )
                }
            } finally {
                bitmap.recycle()
            }
            if (frames.isNotEmpty()) return frames
        }

        // Try API 28+ ImageDecoder for static/animated GIF fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decodedBitmap = try {
                val src = ImageDecoder.createSource(rawBytes)
                var bmp: Bitmap? = null
                val drawable = ImageDecoder.decodeDrawable(src)
                if (drawable is AnimatedImageDrawable) {
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(c)
                } else {
                    bmp = ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                }
                bmp
            } catch (_: Throwable) {
                null
            }
            if (decodedBitmap != null) {
                try {
                    val w = decodedBitmap.width
                    val h = decodedBitmap.height
                    val pixels = IntArray(w * h)
                    decodedBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                    return listOf(GifFrameData(pixels, w, h, 100))
                } finally {
                    decodedBitmap.recycle()
                }
            }
        }

        // Fallback: static BitmapFactory decode
        val bmp = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: throw IllegalStateException("Unsupported or corrupted GIF file")
        try {
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            return listOf(GifFrameData(pixels, w, h, 100))
        } finally {
            bmp.recycle()
        }
    }

    suspend fun compressFramesToGif(
        frames: List<GifFrameData>,
        options: GifCompressionOptions,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit = {}
    ) {
        require(frames.isNotEmpty()) { "At least one frame is required to encode GIF" }
        val scaleFactor = (options.scalePercent.coerceIn(25, 100) / 100f)
        val step = options.frameSkip.coerceIn(1, 4)
        val targetPaletteSize = options.maxColors.coerceIn(16, 256)

        // Sub-sample frames while accumulating skipped frame durations so playback tempo holds
        val keptFrames = mutableListOf<GifFrameData>()
        var idx = 0
        while (idx < frames.size) {
            currentCoroutineContext().ensureActive()
            var accumulatedDelay = 0
            for (k in 0 until step) {
                if (idx + k < frames.size) {
                    accumulatedDelay += frames[idx + k].delayMs.coerceAtLeast(20)
                }
            }
            val src = frames[idx]
            val scaled = scaleFrame(src, scaleFactor, accumulatedDelay)
            keptFrames.add(scaled)
            idx += step
        }

        val outWidth = keptFrames.first().width
        val outHeight = keptFrames.first().height
        val colorBits = bitsForColorCount(targetPaletteSize)
        val paletteCapacity = 1 shl colorBits

        // Header
        outputStream.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShortLE(outputStream, outWidth)
        writeShortLE(outputStream, outHeight)
        // Logical Screen Descriptor: no global color table (each frame carries optimal local palette)
        outputStream.write(0x70) // 8-bit color resolution, no global table
        outputStream.write(0x00) // background index
        outputStream.write(0x00) // pixel aspect ratio

        // Netscape 2.0 Application Extension for infinite looping when animated
        if (keptFrames.size > 1) {
            outputStream.write(0x21)
            outputStream.write(0xFF)
            outputStream.write(11)
            outputStream.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            outputStream.write(3)
            outputStream.write(1)
            writeShortLE(outputStream, 0) // loop forever
            outputStream.write(0)
        }

        keptFrames.forEachIndexed { frameIndex, frame ->
            currentCoroutineContext().ensureActive()
            val quantized = quantizeFrame(frame.pixels, paletteCapacity)

            // Graphic Control Extension
            outputStream.write(0x21)
            outputStream.write(0xF9)
            outputStream.write(4)
            // Disposal method 2 (restore background) + optional transparent index 0 if frame has transparency
            val packedFlags = if (quantized.transparentIndex >= 0) 0x09 else 0x08
            outputStream.write(packedFlags)
            val delayCs = (frame.delayMs / 10).coerceIn(2, 65535)
            writeShortLE(outputStream, delayCs)
            outputStream.write(if (quantized.transparentIndex >= 0) quantized.transparentIndex else 0)
            outputStream.write(0)

            // Image Descriptor
            outputStream.write(0x2C)
            writeShortLE(outputStream, 0)
            writeShortLE(outputStream, 0)
            writeShortLE(outputStream, frame.width)
            writeShortLE(outputStream, frame.height)
            // Local color table present, size = colorBits - 1
            outputStream.write(0x80 or ((colorBits - 1) and 0x07))

            // Local Color Table
            for (c in 0 until paletteCapacity) {
                val argb = if (c < quantized.palette.size) quantized.palette[c] else 0xFF000000.toInt()
                outputStream.write((argb ushr 16) and 0xFF)
                outputStream.write((argb ushr 8) and 0xFF)
                outputStream.write(argb and 0xFF)
            }

            // LZW Compressed Image Data
            val minCodeSize = max(2, colorBits)
            outputStream.write(minCodeSize)
            encodeLzw(quantized.indexedPixels, minCodeSize, outputStream)
            outputStream.write(0x00) // block terminator

            onProgress(((frameIndex + 1).toFloat() / keptFrames.size.toFloat()).coerceIn(0f, 1f))
        }

        // Trailer
        outputStream.write(0x3B)
        outputStream.flush()
    }

    internal fun scaleFrame(frame: GifFrameData, scaleFactor: Float, newDelayMs: Int): GifFrameData {
        if (scaleFactor >= 0.99f) {
            return frame.copy(delayMs = newDelayMs)
        }
        val dstW = (frame.width * scaleFactor).roundToInt().coerceAtLeast(1)
        val dstH = (frame.height * scaleFactor).roundToInt().coerceAtLeast(1)
        val out = IntArray(dstW * dstH)
        for (y in 0 until dstH) {
            val srcY = min(frame.height - 1, (y * frame.height) / dstH)
            val srcRowOffset = srcY * frame.width
            val dstRowOffset = y * dstW
            for (x in 0 until dstW) {
                val srcX = min(frame.width - 1, (x * frame.width) / dstW)
                out[dstRowOffset + x] = frame.pixels[srcRowOffset + srcX]
            }
        }
        return GifFrameData(out, dstW, dstH, newDelayMs)
    }

    private data class QuantizedFrame(
        val palette: IntArray,
        val indexedPixels: ByteArray,
        val transparentIndex: Int
    )

    private fun quantizeFrame(pixels: IntArray, maxColors: Int): QuantizedFrame {
        var hasTransparent = false
        val colorFreq = HashMap<Int, Int>(512)
        for (p in pixels) {
            val alpha = (p ushr 24) and 0xFF
            if (alpha < 128) {
                hasTransparent = true
            } else {
                // Quantize into 5-bit RGB buckets (32x32x32) for fast adaptive histogram palette
                val r = (p ushr 16) and 0xF8
                val g = (p ushr 8) and 0xF8
                val b = p and 0xF8
                val bucket = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                colorFreq[bucket] = (colorFreq[bucket] ?: 0) + 1
            }
        }

        val reservedSlots = if (hasTransparent) 1 else 0
        val usableLimit = (maxColors - reservedSlots).coerceAtLeast(1)
        val sortedColors = colorFreq.entries
            .sortedByDescending { it.value }
            .take(usableLimit)
            .map { it.key }

        val palette = IntArray(maxColors)
        var startIdx = 0
        val transparentIndex = if (hasTransparent) {
            palette[0] = 0x00000000
            startIdx = 1
            0
        } else {
            -1
        }

        val activeCount = sortedColors.size
        for (i in 0 until activeCount) {
            palette[startIdx + i] = sortedColors[i]
        }
        val totalActivePalette = (startIdx + activeCount).coerceAtLeast(1)

        val exactCache = HashMap<Int, Byte>(min(colorFreq.size + 16, 2048))
        val indexed = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val alpha = (p ushr 24) and 0xFF
            if (alpha < 128 && transparentIndex >= 0) {
                indexed[i] = transparentIndex.toByte()
                continue
            }
            val key = (p and 0x00F8F8F8)
            val cached = exactCache[key]
            if (cached != null) {
                indexed[i] = cached
                continue
            }
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            var bestIdx = startIdx.coerceAtMost(totalActivePalette - 1)
            var bestDist = Int.MAX_VALUE
            for (c in startIdx until totalActivePalette) {
                val pc = palette[c]
                val dr = r - ((pc ushr 16) and 0xFF)
                val dg = g - ((pc ushr 8) and 0xFF)
                val db = b - (pc and 0xFF)
                val dist = dr * dr + dg * dg + db * db
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = c
                    if (dist == 0) break
                }
            }
            val bIdx = bestIdx.toByte()
            exactCache[key] = bIdx
            indexed[i] = bIdx
        }

        return QuantizedFrame(palette, indexed, transparentIndex)
    }

    private fun bitsForColorCount(count: Int): Int {
        var bits = 2
        while ((1 shl bits) < count && bits < 8) {
            bits++
        }
        return bits
    }

    private fun encodeLzw(indices: ByteArray, minCodeSize: Int, out: OutputStream) {
        val clearCode = 1 shl minCodeSize
        val eofCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var nextCode = eofCode + 1
        val maxTableSize = 4096

        val dict = HashMap<Int, Int>(4096)
        val subBlock = ByteArrayOutputStream(256)
        var bitBuffer = 0
        var bitCount = 0

        fun emitCode(code: Int, bits: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += bits
            while (bitCount >= 8) {
                subBlock.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
                if (subBlock.size() == 255) {
                    out.write(255)
                    out.write(subBlock.toByteArray())
                    subBlock.reset()
                }
            }
        }

        emitCode(clearCode, codeSize)
        if (indices.isEmpty()) {
            emitCode(eofCode, codeSize)
            if (bitCount > 0) subBlock.write(bitBuffer and 0xFF)
            if (subBlock.size() > 0) {
                out.write(subBlock.size())
                out.write(subBlock.toByteArray())
            }
            return
        }

        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 12) or k
            val existing = dict[key]
            if (existing != null) {
                prefix = existing
            } else {
                emitCode(prefix, codeSize)
                if (nextCode < maxTableSize) {
                    dict[key] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    emitCode(clearCode, codeSize)
                    dict.clear()
                    codeSize = minCodeSize + 1
                    nextCode = eofCode + 1
                }
                prefix = k
            }
        }

        emitCode(prefix, codeSize)
        emitCode(eofCode, codeSize)
        if (bitCount > 0) {
            subBlock.write(bitBuffer and 0xFF)
        }
        if (subBlock.size() > 0) {
            out.write(subBlock.size())
            out.write(subBlock.toByteArray())
        }
    }

    private fun writeShortLE(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }
}
