package com.music.bitchord

import com.music.bitchord.playback.VocalAudioProcessor
import com.music.bitchord.playback.VocalMode
import com.music.bitchord.playback.audio.AudioBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class VocalAudioProcessorTest {

    @Test
    fun `bypass leaves samples completely untouched when mode is OFF`() {
        val processor = VocalAudioProcessor()
        processor.configure(44100, 2)
        processor.mode = VocalMode.OFF

        val block = AudioBlock(channelCount = 2, capacityFrames = 256)
        block.reset(256)
        for (i in 0 until 512) {
            block.samples[i] = 0.42f
        }

        processor.process(block)

        for (i in 0 until 512) {
            assertEquals(0.42f, block.samples[i], 1e-6f)
        }
    }

    @Test
    fun `karaoke mode removes center lead vocal while preserving low bass`() {
        val processor = VocalAudioProcessor()
        val sampleRate = 44100
        processor.configure(sampleRate, 2)
        processor.mode = VocalMode.KARAOKE
        processor.vocalLevel = 0.0f

        val frames = 1024
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        block.reset(frames)

        // Generate a 1000 Hz center vocal signal: L == R
        for (f in 0 until frames) {
            val t = f.toDouble() / sampleRate
            val vocal = (0.5 * sin(2.0 * PI * 1000.0 * t)).toFloat()
            block.samples[f * 2] = vocal
            block.samples[f * 2 + 1] = vocal
        }

        processor.process(block)

        // Verify that 1000 Hz center vocal is attenuated by > 15 dB (> 80% amplitude reduction)
        var maxVocalAmp = 0f
        for (f in 500 until frames) {
            maxVocalAmp = maxOf(maxVocalAmp, abs(block.samples[f * 2]))
        }
        assertTrue("Max vocal amp was $maxVocalAmp, expected < 0.15f", maxVocalAmp < 0.15f)

        // Now test 60 Hz bass preservation: L == R
        processor.flush()
        val bassBlock = AudioBlock(channelCount = 2, capacityFrames = frames)
        bassBlock.reset(frames)
        for (f in 0 until frames) {
            val t = f.toDouble() / sampleRate
            val bass = (0.7 * sin(2.0 * PI * 60.0 * t)).toFloat()
            bassBlock.samples[f * 2] = bass
            bassBlock.samples[f * 2 + 1] = bass
        }

        processor.process(bassBlock)

        var maxBassAmp = 0f
        for (f in 500 until frames) {
            maxBassAmp = maxOf(maxBassAmp, abs(bassBlock.samples[f * 2]))
        }
        assertTrue("Bass amp was $maxBassAmp, expected >= 0.65f", maxBassAmp >= 0.65f)
    }

    @Test
    fun `acapella mode attenuates stereo side instrument information`() {
        val processor = VocalAudioProcessor()
        val sampleRate = 44100
        processor.configure(sampleRate, 2)
        processor.mode = VocalMode.ACAPELLA
        processor.vocalLevel = 0.0f

        val frames = 1024
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        block.reset(frames)

        // Generate pure side instrument (guitars panned left and right out of phase): L = -R
        for (f in 0 until frames) {
            val t = f.toDouble() / sampleRate
            val guitar = (0.6 * sin(2.0 * PI * 1200.0 * t)).toFloat()
            block.samples[f * 2] = guitar
            block.samples[f * 2 + 1] = -guitar
        }

        processor.process(block)

        var maxSideAmp = 0f
        for (f in 500 until frames) {
            maxSideAmp = maxOf(maxSideAmp, abs(block.samples[f * 2]))
        }
        assertTrue("Max side amp was $maxSideAmp, expected < 0.05f", maxSideAmp < 0.05f)
    }
}
