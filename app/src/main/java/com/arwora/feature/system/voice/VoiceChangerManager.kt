package com.arwora.feature.system.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VoiceChangerManager {
    private const val TAG = "VoiceChangerManager"
    const val SAMPLE_RATE = 44100

    private val _isVoiceChangerActive = MutableStateFlow(false)
    val isVoiceChangerActive: StateFlow<Boolean> = _isVoiceChangerActive.asStateFlow()

    private val _currentPreset = MutableStateFlow(VoicePreset.FEMALE)
    val currentPreset: StateFlow<VoicePreset> = _currentPreset.asStateFlow()

    private val _customPitch = MutableStateFlow(1.0f)
    val customPitch: StateFlow<Float> = _customPitch.asStateFlow()

    private val _isFloatingOverlayEnabled = MutableStateFlow(false)
    val isFloatingOverlayEnabled: StateFlow<Boolean> = _isFloatingOverlayEnabled.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var processingJob: Job? = null

    fun setPreset(preset: VoicePreset) {
        _currentPreset.value = preset
        Log.i(TAG, "Switched voice preset: ${preset.title}")
    }

    fun setCustomPitch(pitch: Float) {
        _customPitch.value = pitch.coerceIn(-1.0f, 2.0f)
        if (_currentPreset.value != VoicePreset.CUSTOM) {
            _currentPreset.value = VoicePreset.CUSTOM
        }
    }

    fun setFloatingOverlay(enabled: Boolean) {
        _isFloatingOverlayEnabled.value = enabled
    }

    fun startService(context: Context) {
        val intent = Intent(context, VoiceChangerService::class.java).apply {
            action = VoiceChangerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, VoiceChangerService::class.java).apply {
            action = VoiceChangerService.ACTION_STOP
        }
        context.startService(intent)
    }

    @SuppressLint("MissingPermission")
    fun startAudioEngine() {
        if (_isVoiceChangerActive.value) return

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = (minBufSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )
            }

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord?.startRecording()
            audioTrack?.play()
            _isVoiceChangerActive.value = true

            processingJob = CoroutineScope(Dispatchers.IO).launch {
                val inBuffer = ShortArray(1024)
                val outBuffer = ShortArray(2048)

                while (isActive && _isVoiceChangerActive.value) {
                    val read = audioRecord?.read(inBuffer, 0, inBuffer.size) ?: 0
                    if (read > 0) {
                        val ratio = if (_customPitch.value >= 0) {
                            1.0f + (_customPitch.value * 0.75f)
                        } else {
                            1.0f + (_customPitch.value * 0.5f)
                        }

                        val processedCount = AudioPitchEngine.processBuffer(
                            input = inBuffer,
                            output = outBuffer,
                            length = read,
                            preset = _currentPreset.value,
                            customPitchRatio = ratio
                        )

                        if (processedCount > 0) {
                            audioTrack?.write(outBuffer, 0, processedCount)
                        }
                    }
                }
            }
            Log.i(TAG, "Real-time voice changer audio engine started.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice changer audio engine: ${e.message}", e)
            stopAudioEngine()
        }
    }

    fun stopAudioEngine() {
        processingJob?.cancel()
        processingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        _isVoiceChangerActive.value = false
        Log.i(TAG, "Voice changer audio engine stopped.")
    }
}
