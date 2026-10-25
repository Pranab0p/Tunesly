package com.music.bitchord.playback

/**
 * Acoustic room simulation presets for BitChord's custom DSP Reverb processor.
 *
 * @param roomSize Feedback factor controlling the perceived room dimensions and decay tail (0.0f .. 1.0f).
 * @param damping High frequency absorption / wall absorption (0.0f = very bright/reflective, 1.0f = heavily damped).
 * @param wetLevel Default wet mix proportion (0.0f .. 1.0f).
 * @param dryLevel Default dry source proportion (0.0f .. 1.0f).
 * @param width Stereo spread / decorrelation width (0.0f .. 1.0f).
 * @param preDelayMs Initial delay before reflections reach listener ears (milliseconds).
 */
enum class ReverbPreset(
    val roomSize: Float,
    val damping: Float,
    val wetLevel: Float,
    val dryLevel: Float,
    val width: Float,
    val preDelayMs: Float,
) {
    OFF(
        roomSize = 0f,
        damping = 0f,
        wetLevel = 0f,
        dryLevel = 1f,
        width = 0f,
        preDelayMs = 0f,
    ),
    STUDIO(
        roomSize = 0.35f,
        damping = 0.65f,
        wetLevel = 0.18f,
        dryLevel = 0.95f,
        width = 0.70f,
        preDelayMs = 6f,
    ),
    SMALL_ROOM(
        roomSize = 0.55f,
        damping = 0.48f,
        wetLevel = 0.24f,
        dryLevel = 0.90f,
        width = 0.85f,
        preDelayMs = 12f,
    ),
    MEDIUM_ROOM(
        roomSize = 0.72f,
        damping = 0.35f,
        wetLevel = 0.30f,
        dryLevel = 0.85f,
        width = 1.00f,
        preDelayMs = 22f,
    ),
    CONCERT_HALL(
        roomSize = 0.86f,
        damping = 0.22f,
        wetLevel = 0.35f,
        dryLevel = 0.80f,
        width = 1.00f,
        preDelayMs = 36f,
    ),
    CATHEDRAL(
        roomSize = 0.94f,
        damping = 0.12f,
        wetLevel = 0.42f,
        dryLevel = 0.75f,
        width = 1.00f,
        preDelayMs = 52f,
    ),
    ;

    companion object {
        fun fromName(name: String?): ReverbPreset = entries.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        } ?: OFF
    }
}
