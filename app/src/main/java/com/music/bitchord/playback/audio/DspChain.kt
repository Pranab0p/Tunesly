package com.music.bitchord.playback.audio

import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.playback.EqualizerProcessor
import com.music.bitchord.playback.LoudnessProcessor
import com.music.bitchord.playback.ReverbAudioProcessor
import com.music.bitchord.playback.ReverbPreset
import com.music.bitchord.playback.SpatialAudioProcessor
import com.music.bitchord.playback.TransitionFilterProcessor
import com.music.bitchord.playback.VocalAudioProcessor

/**
 * Composite DSP chain executing BitChord's custom audio processors in their canonical sequence:
 *
 * AudioBlock(Float32) -> VocalAudioProcessor -> ReverbAudioProcessor -> SpatialAudioProcessor
 *   -> EqualizerProcessor -> TransitionFilterProcessor -> LoudnessProcessor -> AudioBlock(Float32)
 *
 * Operates purely on in-place Float32 audio blocks without intermediate fixed-point quantization,
 * preserving full dynamic range and headroom.
 */
class DspChain(
    val vocal: VocalAudioProcessor = VocalAudioProcessor(),
    val reverb: ReverbAudioProcessor = ReverbAudioProcessor(),
    val spatial: SpatialAudioProcessor = SpatialAudioProcessor(),
    val equalizer: EqualizerProcessor = EqualizerProcessor(),
    val transition: TransitionFilterProcessor = TransitionFilterProcessor(),
    val loudness: LoudnessProcessor = LoudnessProcessor(),
) : FloatAudioProcessor {

    private var currentSampleRate: Int = 0
    private var processCounter: Long = 0L

    override fun configure(sampleRate: Int, channelCount: Int) {
        this.currentSampleRate = sampleRate
        vocal.configure(sampleRate, channelCount)
        reverb.configure(sampleRate, channelCount)
        spatial.configure(sampleRate, channelCount)
        equalizer.configure(sampleRate, channelCount)
        transition.configure(sampleRate, channelCount)
        loudness.configure(sampleRate, channelCount)
    }

    /** A new track has begun gaplessly on this sink; see [LoudnessProcessor.onStreamBoundary]. */
    fun onStreamBoundary() {
        loudness.onStreamBoundary()
    }

    override fun process(block: AudioBlock) {
        if (block.frameCount == 0) return

        if (BuildConfig.DEBUG) {
            processCounter++
            if (processCounter == 1L || processCounter % 500L == 0L) {
                try {
                    val count = processCounter
                    val frames = block.frameCount
                    val sr = currentSampleRate
                    val vocalMode = vocal.mode
                    val spatialOn = spatial.enabled
                    val eqOn = equalizer.isEnabled
                    val transitionOn = transition.isFiltering
                    Log.d(
                        TAG,
                        "process() #$count frames=$frames sr=$sr " +
                            "vocal=$vocalMode spatial=$spatialOn eq=$eqOn transition=$transitionOn",
                    )
                } catch (_: Throwable) {
                }
            }
        }

        vocal.process(block)
        reverb.process(block)
        spatial.process(block)
        equalizer.process(block)
        transition.process(block)
        // The equaliser leaves its overs in place for the stages after it to
        // see; the loudness stage's limiter is what catches whatever survives.
        loudness.holdLimiter = equalizer.isShaping
        loudness.process(block)
    }

    @Suppress("DEPRECATION")
    override fun flush() {
        vocal.flush()
        reverb.flush()
        spatial.flush()
        equalizer.flush()
        transition.flush()
        loudness.flush()
    }

    override fun reset() {
        processCounter = 0L
        vocal.reset()
        reverb.reset()
        spatial.reset()
        equalizer.reset()
        transition.reset()
        loudness.reset()
    }

    companion object {
        private const val TAG = "DspChain"
    }
}
