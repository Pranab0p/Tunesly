package com.music.bitchord.playback

/**
 * Vocal remover / stem isolation mode for real-time DSP karaoke and vocal extraction.
 */
enum class VocalMode {
    /** Normal playback, original stereo mix untouched. */
    OFF,

    /** Vocal cut / Karaoke mode: Center lead vocals attenuated, instruments & bass preserved. */
    KARAOKE,

    /** Vocal isolation / Acapella mode: Side stereo instruments attenuated, center vocals isolated. */
    ACAPELLA;

    val isEnabled: Boolean get() = this != OFF
}
