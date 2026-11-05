package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.playback.audio.AudioBlock
import com.music.bitchord.playback.audio.FloatAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/**
 * Real-Time 32-bit Float DSP Vocal Remover & Acapella Isolation Processor.
 *
 * Implements mid/side (M/S) spectral separation with a 2nd-order trapezoidal
 * state-variable filter (SVF) crossover to:
 * - [VocalMode.KARAOKE]: Attenuate center lead vocal formant frequencies (200 Hz - 5 kHz)
 *   while preserving 100% of punchy low-end bass/kick (<200 Hz), high-end sparkle/cymbals (>5 kHz),
 *   and full stereo instrumental separation in the side channels.
 * - [VocalMode.ACAPELLA]: Isolate the lead vocal by attenuating stereo side instruments,
 *   sub-bass, and extreme high-end percussion.
 * - [VocalMode.OFF]: 100% bit-identical bypass with zero CPU cost.
 */
@UnstableApi
class VocalAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var mode: VocalMode = VocalMode.OFF

    /**
     * In KARAOKE mode: 0.0f = full vocal mute, 1.0f = full original vocal.
     * In ACAPELLA mode: 0.0f = full instrument mute (pure vocal), 1.0f = original mix.
     */
    @Volatile
    var vocalLevel: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    // SVF crossover filter coefficients
    // Low band cutoff: 200 Hz (sub-bass / kick drum isolation)
    private var lowA1 = 0f
    private var lowA2 = 0f
    private var lowA3 = 0f
    private var lowK = 1.41421356f // Q = 0.707 (Butterworth)

    // High band cutoff: 5000 Hz (air / cymbals / top sheen isolation)
    private var highA1 = 0f
    private var highA2 = 0f
    private var highA3 = 0f
    private var highK = 1.41421356f

    // Integrator states for Mid channel SVF filters
    private var lowIc1 = 0f
    private var lowIc2 = 0f
    private var highIc1 = 0f
    private var highIc2 = 0f

    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        if (channelCount != 2 || sampleRate <= 0) {
            onFlush()
            return
        }

        // Calculate SVF parameters for low crossover (200 Hz)
        val wLow = (2.0 * PI * LOW_CUTOFF_HZ / sampleRate).toFloat()
        val gLow = tan(wLow * 0.5f)
        lowK = 1.41421356f
        lowA1 = 1.0f / (1.0f + gLow * (gLow + lowK))
        lowA2 = gLow * lowA1
        lowA3 = gLow * lowA2

        // Calculate SVF parameters for high crossover (5000 Hz)
        val wHigh = (2.0 * PI * HIGH_CUTOFF_HZ / sampleRate).toFloat()
        val gHigh = tan(wHigh * 0.5f)
        highK = 1.41421356f
        highA1 = 1.0f / (1.0f + gHigh * (gHigh + highK))
        highA2 = gHigh * highA1
        highA3 = gHigh * highA2

        onFlush()
    }

    fun process(block: AudioBlock) {
        if (!mode.isEnabled || block.frameCount == 0 || block.channelCount != 2) return

        val totalSamples = block.frameCount * 2
        val samples = block.samples
        val currentMode = mode
        val currentLevel = vocalLevel

        var idx = 0
        var lic1 = lowIc1
        var lic2 = lowIc2
        var hic1 = highIc1
        var hic2 = highIc2

        val la1 = lowA1
        val la2 = lowA2
        val la3 = lowA3
        val lk = lowK

        val ha1 = highA1
        val ha2 = highA2
        val ha3 = highA3
        val hk = highK

        while (idx < totalSamples) {
            val left = samples[idx]
            val right = samples[idx + 1]

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            // Low-pass SVF filter on Mid (bass/kick preservation)
            val lv3 = mid - lic2
            val lv1 = la1 * lic1 + la2 * lv3
            val lv2 = lic2 + la2 * lic1 + la3 * lv3
            lic1 = 2f * lv1 - lic1
            lic2 = 2f * lv2 - lic2
            val midLow = lv2

            // High-pass SVF filter on Mid (air/cymbals preservation)
            val hv3 = mid - hic2
            val hv1 = ha1 * hic1 + ha2 * hv3
            val hv2 = hic2 + ha2 * hic1 + ha3 * hv3
            hic1 = 2f * hv1 - hic1
            hic2 = 2f * hv2 - hic2
            val midHigh = mid - hk * hv1 - hv2

            // Mid vocal formant band (200 Hz - 5000 Hz)
            val midVocalBand = mid - midLow - midHigh

            var outLeft: Float
            var outRight: Float

            if (currentMode == VocalMode.KARAOKE) {
                // Karaoke: Cut center lead vocal from mid band, keep bass and highs,
                // and preserve side instruments with slight makeup gain.
                val midOut = midLow + (midVocalBand * currentLevel) + midHigh
                val sideMakeup = 1.0f + (1.0f - currentLevel) * 0.12f
                val sideOut = side * sideMakeup
                outLeft = midOut + sideOut
                outRight = midOut - sideOut
            } else {
                // Acapella: Isolate vocal from mid band, attenuate sides and extreme bass/treble
                val sideOut = side * currentLevel
                val midOut = midVocalBand + ((midLow + midHigh) * currentLevel * 0.25f)
                outLeft = midOut + sideOut
                outRight = midOut - sideOut
            }

            samples[idx] = outLeft
            samples[idx + 1] = outRight
            idx += 2
        }

        // Prevent denormal underflow
        lowIc1 = if (abs(lic1) < DENORMAL_FLOOR) 0f else lic1
        lowIc2 = if (abs(lic2) < DENORMAL_FLOOR) 0f else lic2
        highIc1 = if (abs(hic1) < DENORMAL_FLOOR) 0f else hic1
        highIc2 = if (abs(hic2) < DENORMAL_FLOOR) 0f else hic2
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun onFlush() {
        lowIc1 = 0f
        lowIc2 = 0f
        highIc1 = 0f
        highIc2 = 0f
    }

    override fun onReset() {
        onFlush()
        channelCount = 0
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameCount = inputBuffer.remaining() / BYTES_PER_FRAME
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * BYTES_PER_FRAME)

        if (!mode.isEnabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val currentMode = mode
        val currentLevel = vocalLevel
        val invScale = 1.0f / 32768.0f

        var lic1 = lowIc1
        var lic2 = lowIc2
        var hic1 = highIc1
        var hic2 = highIc2

        val la1 = lowA1
        val la2 = lowA2
        val la3 = lowA3
        val lk = lowK

        val ha1 = highA1
        val ha2 = highA2
        val ha3 = highA3
        val hk = highK

        repeat(frameCount) {
            val left = inputBuffer.short.toFloat() * invScale
            val right = inputBuffer.short.toFloat() * invScale

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            val lv3 = mid - lic2
            val lv1 = la1 * lic1 + la2 * lv3
            val lv2 = lic2 + la2 * lic1 + la3 * lv3
            lic1 = 2f * lv1 - lic1
            lic2 = 2f * lv2 - lic2
            val midLow = lv2

            val hv3 = mid - hic2
            val hv1 = ha1 * hic1 + ha2 * hv3
            val hv2 = hic2 + ha2 * hic1 + ha3 * hv3
            hic1 = 2f * hv1 - hic1
            hic2 = 2f * hv2 - hic2
            val midHigh = mid - hk * hv1 - hv2

            val midVocalBand = mid - midLow - midHigh

            val outLeft: Float
            val outRight: Float

            if (currentMode == VocalMode.KARAOKE) {
                val midOut = midLow + (midVocalBand * currentLevel) + midHigh
                val sideMakeup = 1.0f + (1.0f - currentLevel) * 0.12f
                val sideOut = side * sideMakeup
                outLeft = midOut + sideOut
                outRight = midOut - sideOut
            } else {
                val sideOut = side * currentLevel
                val midOut = midVocalBand + ((midLow + midHigh) * currentLevel * 0.25f)
                outLeft = midOut + sideOut
                outRight = midOut - sideOut
            }

            outputBuffer.putShort((outLeft.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            outputBuffer.putShort((outRight.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }

        outputBuffer.flip()

        lowIc1 = if (abs(lic1) < DENORMAL_FLOOR) 0f else lic1
        lowIc2 = if (abs(lic2) < DENORMAL_FLOOR) 0f else lic2
        highIc1 = if (abs(hic1) < DENORMAL_FLOOR) 0f else hic1
        highIc2 = if (abs(hic2) < DENORMAL_FLOOR) 0f else hic2
    }



    companion object {
        private const val BYTES_PER_FRAME = 4
        private const val LOW_CUTOFF_HZ = 200f
        private const val HIGH_CUTOFF_HZ = 5000f
        private const val DENORMAL_FLOOR = 1e-15f
    }
}
