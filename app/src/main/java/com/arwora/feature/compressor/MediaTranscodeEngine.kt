package com.arwora.feature.compressor

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Hardware-accelerated Android MediaCodec + MediaExtractor + MediaMuxer
 * video & audio transcoding engine running strictly on background IO threads.
 */
object MediaTranscodeEngine {

    private const val TIMEOUT_US = 10_000L

    suspend fun transcodeVideo(
        context: Context,
        sourceUri: Uri,
        destFile: File,
        options: VideoCompressionOptions,
        onProgress: (Float) -> Unit
    ) {
        val retriever = MediaMetadataRetriever()
        var durationUs = 0L
        var srcWidth = 0
        var srcHeight = 0
        var rotation = 0
        try {
            retriever.setDataSource(context, sourceUri)
            durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) * 1000L
            srcWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            srcHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }

        val extractor = MediaExtractor()
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var eglPipeline: EglTranscodeSurface? = null

        try {
            extractor.setDataSource(context, sourceUri, null)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (videoTrackIndex < 0 && mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = fmt
                    if (srcWidth <= 0 && fmt.containsKey(MediaFormat.KEY_WIDTH)) {
                        srcWidth = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (srcHeight <= 0 && fmt.containsKey(MediaFormat.KEY_HEIGHT)) {
                        srcHeight = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (durationUs <= 0L && fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = fmt.getLong(MediaFormat.KEY_DURATION)
                    }
                } else if (audioTrackIndex < 0 && mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                }
            }

            if (videoTrackIndex < 0 || videoFormat == null) {
                throw IllegalStateException("No compatible video track found in selected file.")
            }

            if (srcWidth <= 0) srcWidth = 1280
            if (srcHeight <= 0) srcHeight = 720

            val (outWidth, outHeight) = computeScaledEvenDimensions(
                srcWidth = srcWidth,
                srcHeight = srcHeight,
                targetShortEdge = options.targetHeight.coerceIn(240, 2160)
            )

            val outputVideoFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                outWidth,
                outHeight
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, options.bitrateKbps.coerceIn(300, 12_000) * 1000)
                val srcFps = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(15, 60)
                } else 30
                setInteger(MediaFormat.KEY_FRAME_RATE, srcFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderInputSurface = encoder.createInputSurface()
            encoder.start()

            eglPipeline = EglTranscodeSurface(encoderInputSurface, outWidth, outHeight)
            eglPipeline.makeCurrent()

            val srcMime = videoFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Missing video MIME type")
            decoder = MediaCodec.createDecoderByType(srcMime)
            decoder.configure(videoFormat, eglPipeline.decoderSurface, null, 0)
            decoder.start()

            muxer = MediaMuxer(destFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }

            val audioMime = audioFormat?.getString(MediaFormat.KEY_MIME) ?: ""
            val canPassthroughAudio = options.keepAudio &&
                audioTrackIndex >= 0 &&
                audioFormat != null &&
                (audioMime == MediaFormat.MIMETYPE_AUDIO_AAC || audioMime == "audio/mp4a-latm")

            val muxerAudioTrack = if (canPassthroughAudio && audioFormat != null) {
                muxer.addTrack(audioFormat)
            } else -1

            var muxerVideoTrack = -1
            var muxerStarted = false

            extractor.selectTrack(videoTrackIndex)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var encoderDone = false

            while (!encoderDone) {
                currentCoroutineContext().ensureActive()

                // Feed decoder
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = decoder.getInputBuffer(inIndex)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Drain decoder -> render to Surface -> encode
                if (!decoderDone) {
                    val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outIndex >= 0) {
                        val doRender = bufferInfo.size > 0
                        val ptsUs = bufferInfo.presentationTimeUs
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        decoder.releaseOutputBuffer(outIndex, doRender)
                        if (doRender) {
                            eglPipeline.awaitAndDrawFrame(ptsUs)
                            if (durationUs > 0L) {
                                onProgress((ptsUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 0.92f))
                            }
                        }
                        if (isEos) {
                            encoder.signalEndOfInputStream()
                            decoderDone = true
                        }
                    }
                }

                // Drain encoder -> muxer
                var drainEnc = true
                while (drainEnc) {
                    val encIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        encIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> drainEnc = false
                        encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        encIndex >= 0 -> {
                            val encodedBuf = encoder.getOutputBuffer(encIndex)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && encodedBuf != null && muxerStarted && muxerVideoTrack >= 0) {
                                encodedBuf.position(bufferInfo.offset)
                                encodedBuf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerVideoTrack, encodedBuf, bufferInfo)
                            }
                            val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            encoder.releaseOutputBuffer(encIndex, false)
                            if (isEos) {
                                encoderDone = true
                                drainEnc = false
                            }
                        }
                    }
                }
            }

            // Copy AAC audio track after video frames if enabled
            if (canPassthroughAudio && muxerStarted && muxerAudioTrack >= 0) {
                extractor.unselectTrack(videoTrackIndex)
                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val audioBuf = ByteBuffer.allocateDirect(256 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = extractor.readSampleData(audioBuf, 0)
                    if (size < 0) break
                    audioInfo.offset = 0
                    audioInfo.size = size
                    audioInfo.presentationTimeUs = extractor.sampleTime
                    audioInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, audioBuf, audioInfo)
                    extractor.advance()
                }
            }
            onProgress(1f)
        } finally {
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            try { eglPipeline?.release() } catch (_: Throwable) {}
            try { muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    suspend fun transcodeAudio(
        context: Context,
        sourceUri: Uri,
        destFile: File,
        options: AudioCompressionOptions,
        onProgress: (Float) -> Unit
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(context, sourceUri, null)
            var audioTrackIndex = -1
            var srcFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    srcFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || srcFormat == null) {
                throw IllegalStateException("No audio track found in selected file.")
            }

            extractor.selectTrack(audioTrackIndex)
            val srcMime = srcFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Missing audio MIME type")
            val durationUs = if (srcFormat.containsKey(MediaFormat.KEY_DURATION)) {
                srcFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            var srcSampleRate = if (srcFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                srcFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            var srcChannels = if (srcFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                srcFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val isRawPcm = srcMime == MediaFormat.MIMETYPE_AUDIO_RAW
            if (!isRawPcm) {
                decoder = MediaCodec.createDecoderByType(srcMime)
                decoder.configure(srcFormat, null, null, 0)
                decoder.start()
            }

            val targetSampleRate = options.sampleRateHz.coerceIn(16000, 48000)
            val targetChannels = if (options.mono) 1 else srcChannels.coerceIn(1, 2)

            val encFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                targetSampleRate,
                targetChannels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, options.bitrateKbps.coerceIn(32, 320) * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(destFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrack = -1
            var muxerStarted = false

            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var extractorDone = false
            var decoderDone = false
            var encoderDone = false
            var totalSamplesWritten = 0L
            val rawReadBuf = if (isRawPcm) ByteBuffer.allocateDirect(16 * 1024) else null

            while (!encoderDone) {
                currentCoroutineContext().ensureActive()

                if (isRawPcm && !decoderDone && rawReadBuf != null) {
                    val encInIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (encInIdx >= 0) {
                        val encInBuf = encoder.getInputBuffer(encInIdx)
                        rawReadBuf.clear()
                        val readBytes = extractor.readSampleData(rawReadBuf, 0)
                        if (readBytes < 0) {
                            encoder.queueInputBuffer(
                                encInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            decoderDone = true
                        } else {
                            val pcmBytes = ByteArray(readBytes)
                            rawReadBuf.position(0)
                            rawReadBuf.get(pcmBytes)
                            val converted = resamplePcm16(
                                input = pcmBytes,
                                srcSampleRate = srcSampleRate,
                                srcChannels = srcChannels,
                                dstSampleRate = targetSampleRate,
                                dstChannels = targetChannels
                            )
                            val chunkSize = min(converted.size, encInBuf?.remaining() ?: converted.size)
                            encInBuf?.clear()
                            encInBuf?.put(converted, 0, chunkSize)
                            val ptsUs = (totalSamplesWritten * 1_000_000L) / targetSampleRate
                            totalSamplesWritten += chunkSize / (2L * targetChannels)
                            encoder.queueInputBuffer(encInIdx, 0, chunkSize, ptsUs, 0)
                            val sampleTime = extractor.sampleTime
                            if (durationUs > 0L && sampleTime >= 0L) {
                                onProgress((sampleTime.toFloat() / durationUs.toFloat()).coerceIn(0f, 0.95f))
                            }
                            extractor.advance()
                        }
                    }
                } else if (decoder != null) {
                    // Feed decoder
                    if (!extractorDone) {
                        val decInIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (decInIdx >= 0) {
                            val decInBuf = decoder.getInputBuffer(decInIdx)
                            if (decInBuf != null) {
                                val size = extractor.readSampleData(decInBuf, 0)
                                if (size < 0) {
                                    decoder.queueInputBuffer(
                                        decInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    extractorDone = true
                                } else {
                                    val pts = extractor.sampleTime
                                    decoder.queueInputBuffer(decInIdx, 0, size, pts, 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    // Drain decoder -> feed encoder
                    if (!decoderDone) {
                        val decOutIdx = decoder.dequeueOutputBuffer(decInfo, TIMEOUT_US)
                        when {
                            decOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val newFmt = decoder.outputFormat
                                if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                    srcSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                }
                                if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                    srcChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                }
                            }
                            decOutIdx >= 0 -> {
                                val decOutBuf = decoder.getOutputBuffer(decOutIdx)
                                val isEos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                                if (decInfo.size > 0 && decOutBuf != null) {
                                    decOutBuf.position(decInfo.offset)
                                    val pcm = ByteArray(decInfo.size)
                                    decOutBuf.get(pcm)
                                    val converted = resamplePcm16(
                                        input = pcm,
                                        srcSampleRate = srcSampleRate,
                                        srcChannels = srcChannels,
                                        dstSampleRate = targetSampleRate,
                                        dstChannels = targetChannels
                                    )
                                    var offset = 0
                                    while (offset < converted.size) {
                                        currentCoroutineContext().ensureActive()
                                        val encInIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                                        if (encInIdx >= 0) {
                                            val encInBuf = encoder.getInputBuffer(encInIdx)
                                            val cap = encInBuf?.remaining() ?: converted.size
                                            val writeLen = min(cap, converted.size - offset)
                                            encInBuf?.clear()
                                            encInBuf?.put(converted, offset, writeLen)
                                            val ptsUs = (totalSamplesWritten * 1_000_000L) / targetSampleRate
                                            totalSamplesWritten += writeLen / (2L * targetChannels)
                                            encoder.queueInputBuffer(encInIdx, 0, writeLen, ptsUs, 0)
                                            offset += writeLen
                                        }
                                        drainAudioEncoder(
                                            encoder = encoder,
                                            encInfo = encInfo,
                                            muxer = muxer,
                                            muxerTrackProvider = { muxerTrack },
                                            onTrackCreated = {
                                                muxerTrack = it
                                                muxerStarted = true
                                            },
                                            muxerStarted = { muxerStarted },
                                            onEos = { encoderDone = true }
                                        )
                                    }
                                    if (durationUs > 0L) {
                                        onProgress(
                                            (decInfo.presentationTimeUs.toFloat() / durationUs.toFloat())
                                                .coerceIn(0f, 0.95f)
                                        )
                                    }
                                }
                                decoder.releaseOutputBuffer(decOutIdx, false)
                                if (isEos) {
                                    val encInIdx = encoder.dequeueInputBuffer(TIMEOUT_US * 4)
                                    if (encInIdx >= 0) {
                                        encoder.queueInputBuffer(
                                            encInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                        )
                                    }
                                    decoderDone = true
                                }
                            }
                        }
                    }
                }

                drainAudioEncoder(
                    encoder = encoder,
                    encInfo = encInfo,
                    muxer = muxer,
                    muxerTrackProvider = { muxerTrack },
                    onTrackCreated = {
                        muxerTrack = it
                        muxerStarted = true
                    },
                    muxerStarted = { muxerStarted },
                    onEos = { encoderDone = true }
                )
            }
            onProgress(1f)
        } finally {
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            try { muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private fun drainAudioEncoder(
        encoder: MediaCodec,
        encInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        muxerTrackProvider: () -> Int,
        onTrackCreated: (Int) -> Unit,
        muxerStarted: () -> Boolean,
        onEos: () -> Unit
    ) {
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(encInfo, 0L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted()) {
                        val track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        onTrackCreated(track)
                    }
                }
                outIdx >= 0 -> {
                    val buf = encoder.getOutputBuffer(outIdx)
                    if ((encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        encInfo.size = 0
                    }
                    val track = muxerTrackProvider()
                    if (encInfo.size > 0 && buf != null && muxerStarted() && track >= 0) {
                        buf.position(encInfo.offset)
                        buf.limit(encInfo.offset + encInfo.size)
                        muxer.writeSampleData(track, buf, encInfo)
                    }
                    val eos = (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (eos) {
                        onEos()
                        return
                    }
                }
            }
        }
    }

    internal fun computeScaledEvenDimensions(
        srcWidth: Int,
        srcHeight: Int,
        targetShortEdge: Int
    ): Pair<Int, Int> {
        val shortEdge = min(srcWidth, srcHeight)
        if (shortEdge <= targetShortEdge) {
            return align16(srcWidth) to align16(srcHeight)
        }
        val scale = targetShortEdge.toFloat() / shortEdge.toFloat()
        val w = align16((srcWidth * scale).roundToInt())
        val h = align16((srcHeight * scale).roundToInt())
        return w to h
    }

    private fun align16(value: Int): Int {
        return (((value + 8) / 16) * 16).coerceAtLeast(16)
    }

    internal fun resamplePcm16(
        input: ByteArray,
        srcSampleRate: Int,
        srcChannels: Int,
        dstSampleRate: Int,
        dstChannels: Int
    ): ByteArray {
        if (srcSampleRate == dstSampleRate && srcChannels == dstChannels) return input
        val inChannels = srcChannels.coerceIn(1, 2)
        val outChannels = dstChannels.coerceIn(1, 2)
        val inFrames = input.size / (2 * inChannels)
        if (inFrames <= 0) return ByteArray(0)

        val inBuf = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val ratio = dstSampleRate.toDouble() / srcSampleRate.toDouble()
        val outFrames = max(1, (inFrames * ratio).roundToInt())
        val outBytes = ByteArray(outFrames * 2 * outChannels)
        val outBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until outFrames) {
            val srcPos = min((i / ratio).toInt(), inFrames - 1)
            val baseIdx = srcPos * inChannels
            val left = inBuf.get(baseIdx)
            val right = if (inChannels == 2) inBuf.get(baseIdx + 1) else left

            if (outChannels == 1) {
                val mixed = ((left.toInt() + right.toInt()) / 2).toShort()
                outBuf.put(mixed)
            } else {
                outBuf.put(left)
                outBuf.put(right)
            }
        }
        return outBytes
    }
}

private class EglTranscodeSurface(
    outputSurface: Surface,
    width: Int,
    height: Int
) : SurfaceTexture.OnFrameAvailableListener {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var textureId = 0
    private var program = 0
    private var uMvpHandle = 0
    private var uStHandle = 0
    private var aPosHandle = 0
    private var aTexHandle = 0

    private val stMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val frameSync = Object()
    @Volatile
    private var frameAvailable = false

    val decoderSurfaceTexture: SurfaceTexture
    val decoderSurface: Surface

    private val quadVertices: FloatBuffer = ByteBuffer.allocateDirect(20 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(
                floatArrayOf(
                    -1f, -1f, 0f, 0f, 0f,
                     1f, -1f, 0f, 1f, 0f,
                    -1f,  1f, 0f, 0f, 1f,
                     1f,  1f, 0f, 1f, 1f
                )
            )
            position(0)
        }

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1, // EGL_RECORDABLE_ANDROID
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
        val config = configs[0] ?: throw IllegalStateException("No EGLConfig for video transcoding")

        eglContext = EGL14.eglCreateContext(
            eglDisplay, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, config, outputSurface, intArrayOf(EGL14.EGL_NONE), 0
        )

        makeCurrent()
        GLES20.glViewport(0, 0, width, height)
        Matrix.setIdentityM(mvpMatrix, 0)
        initGlProgram()

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        decoderSurfaceTexture = SurfaceTexture(textureId)
        decoderSurfaceTexture.setOnFrameAvailableListener(this)
        decoderSurface = Surface(decoderSurfaceTexture)
    }

    fun makeCurrent() {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(frameSync) {
            frameAvailable = true
            frameSync.notifyAll()
        }
    }

    fun awaitAndDrawFrame(ptsUs: Long) {
        synchronized(frameSync) {
            val deadline = System.currentTimeMillis() + 1500L
            while (!frameAvailable) {
                val rem = deadline - System.currentTimeMillis()
                if (rem <= 0L) break
                frameSync.wait(rem)
            }
            frameAvailable = false
        }
        decoderSurfaceTexture.updateTexImage()
        decoderSurfaceTexture.getTransformMatrix(stMatrix)

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        quadVertices.position(0)
        GLES20.glVertexAttribPointer(aPosHandle, 3, GLES20.GL_FLOAT, false, 20, quadVertices)
        GLES20.glEnableVertexAttribArray(aPosHandle)

        quadVertices.position(3)
        GLES20.glVertexAttribPointer(aTexHandle, 2, GLES20.GL_FLOAT, false, 20, quadVertices)
        GLES20.glEnableVertexAttribArray(aTexHandle)

        GLES20.glUniformMatrix4fv(uMvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uStHandle, 1, false, stMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsUs * 1000L)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun initGlProgram() {
        val vertexSrc = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """.trimIndent()
        val fragmentSrc = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """.trimIndent()

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        aPosHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTexHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uMvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uStHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    fun release() {
        try { decoderSurface.release() } catch (_: Throwable) {}
        try { decoderSurfaceTexture.release() } catch (_: Throwable) {}
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}
