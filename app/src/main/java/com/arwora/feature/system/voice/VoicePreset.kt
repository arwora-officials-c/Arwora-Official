package com.arwora.feature.system.voice

enum class VoicePreset(
    val title: String,
    val pitchFactor: Float,
    val description: String
) {
    ORIGINAL("Original", 1.0f, "Natural voice without effects"),
    FEMALE("Girl / Female", 1.4f, "Higher pitch (+1.4) female voice"),
    DEEP_MALE("Deep Male", 0.75f, "Lower pitch (-0.8) heavy male voice"),
    ROBOT("Robot Vocoder", 0.8f, "Deep pitch with metallic ring modulation"),
    CHIPMUNK("Alien / Chipmunk", 1.8f, "High frequency chipmunk modulator"),
    CUSTOM("Custom Pitch", 1.0f, "Adjustable pitch slider (-1.0 to +2.0)")
}
