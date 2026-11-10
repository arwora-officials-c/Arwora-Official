package com.arwora.feature.system.voice

import kotlin.math.sin

object AudioPitchEngine {

    private var phase = 0.0

    /**
     * Pitch shifts a mono 16-bit PCM buffer by [preset] and [customPitchRatio].
     * pitchFactor > 1.0 shifts higher (female / chipmunk).
     * pitchFactor < 1.0 shifts lower (deep male / robot).
     */
    fun processBuffer(
        input: ShortArray,
        output: ShortArray,
        length: Int,
        preset: VoicePreset,
        customPitchRatio: Float
    ): Int {
        val factor = when (preset) {
            VoicePreset.CUSTOM -> customPitchRatio
            VoicePreset.ORIGINAL -> 1.0f
            else -> preset.pitchFactor
        }

        if (preset == VoicePreset.ORIGINAL && factor == 1.0f) {
            System.arraycopy(input, 0, output, 0, length)
            return length
        }

        val outLen = (length / factor).toInt().coerceAtMost(output.size)
        for (i in 0 until outLen) {
            val srcIndex = (i * factor)
            val index0 = srcIndex.toInt().coerceIn(0, length - 1)
            val index1 = (index0 + 1).coerceIn(0, length - 1)
            val frac = (srcIndex - index0).toFloat()

            var sample = (input[index0] * (1f - frac) + input[index1] * frac).toInt()

            // Apply special modulation for Robot and Alien presets
            if (preset == VoicePreset.ROBOT) {
                // 50Hz metallic ring modulation
                phase += (2.0 * Math.PI * 50.0 / 44100.0)
                val modulator = sin(phase).toFloat()
                sample = (sample * (0.6f + 0.4f * modulator)).toInt()
            } else if (preset == VoicePreset.CHIPMUNK && factor >= 1.7f) {
                phase += (2.0 * Math.PI * 120.0 / 44100.0)
                val tremolo = (1.0f + 0.15f * sin(phase).toFloat())
                sample = (sample * tremolo).toInt()
            }

            output[i] = sample.coerceIn(-32768, 32767).toShort()
        }
        return outLen
    }
}
