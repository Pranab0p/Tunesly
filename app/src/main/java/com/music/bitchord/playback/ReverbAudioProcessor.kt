package com.music.bitchord.playback

import androidx.media3.common.util.UnstableApi
import com.music.bitchord.playback.audio.AudioBlock
import com.music.bitchord.playback.audio.FloatAudioProcessor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-fidelity, real-time 32-bit Float DSP Reverb Processor based on Schroeder-Freeverb topology.
 *
 * Simulates physical acoustic room characteristics (Studio, Small Room, Medium Room, Concert Hall, Cathedral)
 * using 8 parallel Lowpass Feedback Comb Filters (LBCF) and 4 cascading All-Pass Filters (APF) per channel.
 * Operates purely on Float32 audio blocks in-place with zero allocations on the audio callback path.
 */
@UnstableApi
class ReverbAudioProcessor : FloatAudioProcessor {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var preset: ReverbPreset = ReverbPreset.OFF
        set(value) {
            field = value
            updateParameters()
        }

    /** User wet level multiplier (0.0f .. 1.0f). If null, preset default is used. */
    @Volatile
    var wetMixOverride: Float? = null
        set(value) {
            field = value?.coerceIn(0f, 1f)
            updateParameters()
        }

    private var sampleRate: Int = 44100
    private var channelCount: Int = 2

    // Target parameters chasing smoothly
    private var roomFeedback: Float = 0.5f
    private var dampValue: Float = 0.5f
    private var wetGain: Float = 0f
    private var dryGain: Float = 1f
    private var stereoWidth: Float = 1f

    // 8 Comb filters per channel (Left & Right)
    private class CombFilter(size: Int) {
        val buffer = FloatArray(size)
        var bufferIndex = 0
        var filterStore = 0f

        fun process(input: Float, feedback: Float, damp: Float): Float {
            val output = buffer[bufferIndex]
            filterStore = output * (1f - damp) + filterStore * damp
            if (abs(filterStore) < DENORMAL_FLOOR) filterStore = 0f
            buffer[bufferIndex] = input + filterStore * feedback
            if (++bufferIndex >= buffer.size) bufferIndex = 0
            return output
        }

        fun flush() {
            buffer.fill(0f)
            bufferIndex = 0
            filterStore = 0f
        }
    }

    // 4 Allpass filters per channel (Left & Right)
    private class AllpassFilter(size: Int) {
        val buffer = FloatArray(size)
        var bufferIndex = 0

        fun process(input: Float): Float {
            val bufOut = buffer[bufferIndex]
            val output = -input + bufOut
            buffer[bufferIndex] = input + (bufOut * 0.5f)
            if (++bufferIndex >= buffer.size) bufferIndex = 0
            return output
        }

        fun flush() {
            buffer.fill(0f)
            bufferIndex = 0
        }
    }

    private var combFiltersL: Array<CombFilter> = emptyArray()
    private var combFiltersR: Array<CombFilter> = emptyArray()
    private var allpassFiltersL: Array<AllpassFilter> = emptyArray()
    private var allpassFiltersR: Array<AllpassFilter> = emptyArray()

    // Pre-delay ring buffer
    private var preDelayBufferL = FloatArray(0)
    private var preDelayBufferR = FloatArray(0)
    private var preDelayIndexL = 0
    private var preDelayIndexR = 0
    private var preDelaySamples = 0

    init {
        updateParameters()
    }

    private fun updateParameters() {
        val currentPreset = preset
        if (!enabled || currentPreset == ReverbPreset.OFF) {
            wetGain = 0f
            dryGain = 1f
            return
        }

        roomFeedback = (currentPreset.roomSize * 0.28f + 0.7f).coerceIn(0f, 0.98f)
        dampValue = currentPreset.damping.coerceIn(0f, 0.95f)

        val wet = wetMixOverride ?: currentPreset.wetLevel
        wetGain = (wet * 2.5f).coerceIn(0f, 1.5f) // Scaled for perceived acoustic balance
        dryGain = (1f - wet * 0.5f).coerceIn(0.5f, 1f)
        stereoWidth = currentPreset.width.coerceIn(0f, 1f)

        val targetPreDelay = ((currentPreset.preDelayMs * sampleRate) / 1000f).roundToInt()
        preDelaySamples = targetPreDelay.coerceIn(0, max(0, preDelayBufferL.size - 1))
    }

    override fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = max(11025, sampleRate)
        this.channelCount = channelCount

        val rateRatio = this.sampleRate / 44100f

        // Standard Freeverb Prime Delay Line Lengths scaled to sample rate
        val combTuningsL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val allpassTuningsL = intArrayOf(556, 441, 341, 225)
        val stereoSpread = 23

        combFiltersL = Array(combTuningsL.size) { i ->
            CombFilter(max(1, (combTuningsL[i] * rateRatio).roundToInt()))
        }
        combFiltersR = Array(combTuningsL.size) { i ->
            CombFilter(max(1, ((combTuningsL[i] + stereoSpread) * rateRatio).roundToInt()))
        }

        allpassFiltersL = Array(allpassTuningsL.size) { i ->
            AllpassFilter(max(1, (allpassTuningsL[i] * rateRatio).roundToInt()))
        }
        allpassFiltersR = Array(allpassTuningsL.size) { i ->
            AllpassFilter(max(1, ((allpassTuningsL[i] + stereoSpread) * rateRatio).roundToInt()))
        }

        val maxPreDelay = (this.sampleRate * 0.1f).roundToInt() // Up to 100ms pre-delay capacity
        preDelayBufferL = FloatArray(maxPreDelay)
        preDelayBufferR = FloatArray(maxPreDelay)
        preDelayIndexL = 0
        preDelayIndexR = 0

        updateParameters()
    }

    override fun process(block: AudioBlock) {
        if (!enabled || preset == ReverbPreset.OFF || block.frameCount == 0) return

        val channels = block.channelCount
        val frames = block.frameCount
        val samples = block.samples

        val feedback = roomFeedback
        val damp = dampValue
        val wet = wetGain
        val dry = dryGain
        val width = stereoWidth

        val preSize = preDelayBufferL.size
        val preDelay = preDelaySamples

        var sampleIndex = 0

        if (channels == 2) {
            // Stereo processing
            for (f in 0 until frames) {
                val inputL = samples[sampleIndex]
                val inputR = samples[sampleIndex + 1]

                // Pre-delay stage
                var delayedL = inputL
                var delayedR = inputR
                if (preSize > 0 && preDelay > 0) {
                    val readIdxL = (preDelayIndexL - preDelay + preSize) % preSize
                    val readIdxR = (preDelayIndexR - preDelay + preSize) % preSize
                    delayedL = preDelayBufferL[readIdxL]
                    delayedR = preDelayBufferR[readIdxR]
                    preDelayBufferL[preDelayIndexL] = inputL
                    preDelayBufferR[preDelayIndexR] = inputR
                    if (++preDelayIndexL >= preSize) preDelayIndexL = 0
                    if (++preDelayIndexR >= preSize) preDelayIndexR = 0
                }

                val monoIn = (delayedL + delayedR) * 0.015f // Input gain scaling for comb saturation headroom

                // Accumulate 8 parallel comb filters for Left
                var outL = 0f
                for (i in combFiltersL.indices) {
                    outL += combFiltersL[i].process(monoIn, feedback, damp)
                }

                // Accumulate 8 parallel comb filters for Right
                var outR = 0f
                for (i in combFiltersR.indices) {
                    outR += combFiltersR[i].process(monoIn, feedback, damp)
                }

                // Cascade 4 allpass filters
                for (i in allpassFiltersL.indices) {
                    outL = allpassFiltersL[i].process(outL)
                }
                for (i in allpassFiltersR.indices) {
                    outR = allpassFiltersR[i].process(outR)
                }

                // Stereo matrix crossfeed based on spatial width
                val wet1 = outL * (1f + width) + outR * (1f - width)
                val wet2 = outR * (1f + width) + outL * (1f - width)

                // Blend dry and wet in-place
                samples[sampleIndex] = inputL * dry + wet1 * 0.5f * wet
                samples[sampleIndex + 1] = inputR * dry + wet2 * 0.5f * wet

                sampleIndex += 2
            }
        } else if (channels == 1) {
            // Mono processing
            for (f in 0 until frames) {
                val input = samples[sampleIndex]
                val monoIn = input * 0.015f

                var out = 0f
                for (i in combFiltersL.indices) {
                    out += combFiltersL[i].process(monoIn, feedback, damp)
                }
                for (i in allpassFiltersL.indices) {
                    out = allpassFiltersL[i].process(out)
                }

                samples[sampleIndex] = input * dry + out * wet
                sampleIndex++
            }
        } else {
            // Multi-channel fallback: apply reverb to first 2 channels
            for (f in 0 until frames) {
                val inputL = samples[sampleIndex]
                val inputR = samples[sampleIndex + 1]
                val monoIn = (inputL + inputR) * 0.015f

                var outL = 0f
                var outR = 0f
                for (i in combFiltersL.indices) {
                    outL += combFiltersL[i].process(monoIn, feedback, damp)
                    outR += combFiltersR[i].process(monoIn, feedback, damp)
                }
                for (i in allpassFiltersL.indices) {
                    outL = allpassFiltersL[i].process(outL)
                    outR = allpassFiltersR[i].process(outR)
                }

                samples[sampleIndex] = inputL * dry + outL * wet
                samples[sampleIndex + 1] = inputR * dry + outR * wet
                sampleIndex += channels
            }
        }
    }

    override fun flush() {
        for (f in combFiltersL) f.flush()
        for (f in combFiltersR) f.flush()
        for (f in allpassFiltersL) f.flush()
        for (f in allpassFiltersR) f.flush()
        preDelayBufferL.fill(0f)
        preDelayBufferR.fill(0f)
        preDelayIndexL = 0
        preDelayIndexR = 0
    }

    override fun reset() {
        flush()
    }

    companion object {
        private const val DENORMAL_FLOOR = 1e-15f
    }
}
