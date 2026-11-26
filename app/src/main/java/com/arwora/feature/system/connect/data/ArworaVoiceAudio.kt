package com.arwora.feature.system.connect.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

data class RecordedVoiceClip(
    val audioBase64: String,
    val durationMs: Long,
    val waveform: List<Float>
)

/**
 * Low-overhead AAC voice snippet recorder and visual waveform sampler for Arwora Connect P2P chat.
 */
class ArworaVoiceRecorder(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var samplerJob: Job? = null
    private var startedAtMillis: Long = 0L
    private val rawSamples = mutableListOf<Float>()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _liveWaveform = MutableStateFlow<List<Float>>(emptyList())
    val liveWaveform: StateFlow<List<Float>> = _liveWaveform.asStateFlow()

    fun startRecording(onMaxDurationReached: (() -> Unit)? = null): Result<Unit> {
        if (!hasRecordAudioPermission(appContext)) {
            return Result.failure(SecurityException("Microphone permission required"))
        }
        if (_isRecording.value) {
            return Result.success(Unit)
        }

        val tempFile = File(appContext.cacheDir, "arwora_voice_rec_${UUID.randomUUID()}.m4a")
        val mr = createMediaRecorder(appContext)
        return try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(SAMPLE_RATE_HZ)
            mr.setAudioEncodingBitRate(BIT_RATE_BPS)
            mr.setAudioChannels(1)
            mr.setMaxDuration(MAX_DURATION_MS)
            mr.setOutputFile(tempFile.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            outputFile = tempFile
            startedAtMillis = System.currentTimeMillis()
            synchronized(rawSamples) { rawSamples.clear() }
            _elapsedMs.value = 0L
            _liveWaveform.value = emptyList()
            _isRecording.value = true

            samplerJob?.cancel()
            samplerJob = scope.launch {
                while (isActive && _isRecording.value) {
                    val elapsed = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
                    _elapsedMs.value = elapsed
                    val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalized = normalizeAmplitude(amp)
                    val snapshot = synchronized(rawSamples) {
                        rawSamples.add(normalized)
                        rawSamples.takeLast(LIVE_BAR_WINDOW)
                    }
                    _liveWaveform.value = snapshot
                    if (elapsed >= MAX_DURATION_MS) {
                        onMaxDurationReached?.invoke()
                        break
                    }
                    delay(SAMPLE_INTERVAL_MS)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice recording", e)
            runCatching { mr.reset() }
            runCatching { mr.release() }
            runCatching { tempFile.delete() }
            recorder = null
            outputFile = null
            _isRecording.value = false
            Result.failure(e)
        }
    }

    suspend fun stopRecordingAndEncode(): Result<RecordedVoiceClip> = withContext(Dispatchers.IO) {
        if (!_isRecording.value && outputFile == null) {
            return@withContext Result.failure(IllegalStateException("Not recording"))
        }
        samplerJob?.cancel()
        samplerJob = null
        _isRecording.value = false

        val duration = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
        val mr = recorder
        val file = outputFile
        recorder = null
        outputFile = null

        try {
            mr?.stop()
        } catch (e: Exception) {
            Log.d(TAG, "MediaRecorder stop warning: ${e.message}")
        } finally {
            runCatching { mr?.reset() }
            runCatching { mr?.release() }
        }

        val capturedSamples = synchronized(rawSamples) { rawSamples.toList() }
        synchronized(rawSamples) { rawSamples.clear() }
        _elapsedMs.value = 0L
        _liveWaveform.value = emptyList()

        try {
            if (file == null || !file.exists() || file.length() <= 0L) {
                return@withContext Result.failure(IllegalStateException("Recording produced no audio data"))
            }
            if (duration < MIN_DURATION_MS) {
                return@withContext Result.failure(IllegalArgumentException("Voice note too short"))
            }
            val bytes = file.readBytes()
            if (bytes.size > MAX_VOICE_BYTES) {
                return@withContext Result.failure(IllegalStateException("Voice note exceeds 1.2 MB limit"))
            }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val bars = resampleWaveform(capturedSamples, WAVEFORM_BAR_COUNT)
            Result.success(
                RecordedVoiceClip(
                    audioBase64 = base64,
                    durationMs = duration.coerceAtMost(MAX_DURATION_MS.toLong()),
                    waveform = bars
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { file?.delete() }
        }
    }

    fun cancelRecording() {
        samplerJob?.cancel()
        samplerJob = null
        _isRecording.value = false
        val mr = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        runCatching { mr?.stop() }
        runCatching { mr?.reset() }
        runCatching { mr?.release() }
        runCatching { file?.delete() }
        synchronized(rawSamples) { rawSamples.clear() }
        _elapsedMs.value = 0L
        _liveWaveform.value = emptyList()
    }

    fun release() {
        cancelRecording()
        scope.cancel()
    }

    companion object {
        private const val TAG = "ArworaVoiceRecorder"
        private const val SAMPLE_RATE_HZ = 16_000
        private const val BIT_RATE_BPS = 32_000
        private const val SAMPLE_INTERVAL_MS = 75L
        private const val MIN_DURATION_MS = 350L
        const val MAX_DURATION_MS = 60_000
        const val MAX_VOICE_BYTES = 1_200_000
        const val WAVEFORM_BAR_COUNT = 32
        private const val LIVE_BAR_WINDOW = 32

        fun hasRecordAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        @Suppress("DEPRECATION")
        private fun createMediaRecorder(context: Context): MediaRecorder {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
        }

        fun normalizeAmplitude(rawAmplitude: Int): Float {
            if (rawAmplitude <= 0) return 0.14f
            // Log-like compression makes quiet & loud speech visually distinct
            val linear = (rawAmplitude / 22_000f).coerceIn(0f, 1f)
            val curved = kotlin.math.sqrt(linear.toDouble()).toFloat()
            return (0.14f + curved * 0.86f).coerceIn(0.14f, 1.0f)
        }

        fun resampleWaveform(samples: List<Float>, targetBars: Int = WAVEFORM_BAR_COUNT): List<Float> {
            val count = targetBars.coerceAtLeast(8)
            if (samples.isEmpty()) {
                return List(count) { idx ->
                    // Subtle deterministic fallback wave when amplitude callbacks were silent
                    0.22f + 0.18f * kotlin.math.abs(kotlin.math.sin(idx * 0.55)).toFloat()
                }
            }
            if (samples.size == count) {
                return samples.map { it.coerceIn(0.12f, 1.0f) }
            }
            return List(count) { bar ->
                val startIdx = (bar * samples.size) / count
                val endIdx = (((bar + 1) * samples.size) / count).coerceAtLeast(startIdx + 1).coerceAtMost(samples.size)
                var peak = 0f
                for (i in startIdx until endIdx) {
                    if (samples[i] > peak) peak = samples[i]
                }
                peak.coerceIn(0.12f, 1.0f)
            }
        }

        fun formatDurationMs(durationMs: Long): String {
            val totalSeconds = ((durationMs + 500L) / 1000L).coerceAtLeast(0L)
            val mins = totalSeconds / 60L
            val secs = totalSeconds % 60L
            return String.format(Locale.US, "%d:%02d", mins, secs)
        }
    }
}

/**
 * Lightweight single-active-clip [MediaPlayer] manager for P2P voice note playback & waveform progress.
 */
class ArworaVoicePlayer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _progressFraction = MutableStateFlow(0f)
    val progressFraction: StateFlow<Float> = _progressFraction.asStateFlow()

    fun togglePlayback(
        messageId: String,
        audioBase64: String,
        fallbackDurationMs: Long,
        onError: ((String) -> Unit)? = null
    ) {
        if (_playingMessageId.value == messageId) {
            stop()
            return
        }
        stop()

        scope.launch {
            val audioFile = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(appContext.cacheDir, VOICE_CACHE_DIR).apply { if (!exists()) mkdirs() }
                    val safeName = messageId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val target = File(dir, "$safeName.m4a")
                    if (!target.exists() || target.length() == 0L) {
                        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
                        target.writeBytes(bytes)
                    }
                    target
                }.getOrNull()
            }

            if (audioFile == null || !audioFile.exists()) {
                onError?.invoke("Unable to decode voice note")
                return@launch
            }

            val mp = MediaPlayer()
            try {
                mp.setDataSource(audioFile.absolutePath)
                mp.prepare()
                player = mp
                _playingMessageId.value = messageId
                _progressFraction.value = 0f

                mp.setOnCompletionListener {
                    stop()
                }
                mp.setOnErrorListener { _, _, _ ->
                    stop()
                    onError?.invoke("Voice playback error")
                    true
                }
                mp.start()

                val totalMs = mp.duration.takeIf { it > 0 }?.toLong() ?: fallbackDurationMs.coerceAtLeast(1L)
                progressJob?.cancel()
                progressJob = scope.launch {
                    while (isActive && _playingMessageId.value == messageId) {
                        val activeMp = player ?: break
                        val pos = runCatching { activeMp.currentPosition.toLong() }.getOrDefault(0L)
                        _progressFraction.value = (pos.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                        delay(50L)
                    }
                }
            } catch (e: Exception) {
                runCatching { mp.release() }
                player = null
                _playingMessageId.value = null
                _progressFraction.value = 0f
                onError?.invoke(e.localizedMessage ?: "Unable to play voice note")
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        val active = player
        player = null
        _playingMessageId.value = null
        _progressFraction.value = 0f
        if (active != null) {
            runCatching { if (active.isPlaying) active.stop() }
            runCatching { active.reset() }
            runCatching { active.release() }
        }
    }

    fun release() {
        stop()
        scope.cancel()
        runCatching {
            File(appContext.cacheDir, VOICE_CACHE_DIR).deleteRecursively()
        }
    }

    companion object {
        private const val VOICE_CACHE_DIR = "arwora_voice_cache"
    }
}
