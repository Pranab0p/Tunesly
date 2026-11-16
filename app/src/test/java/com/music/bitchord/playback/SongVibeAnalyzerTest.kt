package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongVibeAnalyzerTest {

    private fun testSong(videoId: String, title: String, artist: String, durationText: String? = "3:30") = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = "",
        fromAutoplay = false,
        durationText = durationText,
    )

    @Test
    fun `test romantic soulful vibe detection`() {
        val song = testSong("tum_hi_ho", "Tum Hi Ho", "Arijit Singh")
        val vibe = SongVibeAnalyzer.analyze(song)
        assertEquals(MusicVibeGenre.ROMANTIC_SOULFUL, vibe.genre)
        assertEquals("Hindi", vibe.detectedLanguage)
        assertEquals(VibeTempo.SLOW_MELLOW, vibe.tempo)
    }

    @Test
    fun `test punjabi hip-hop vibe detection`() {
        val song = testSong("winning_speech", "Winning Speech", "Karan Aujla")
        val vibe = SongVibeAnalyzer.analyze(song)
        assertEquals(MusicVibeGenre.PUNJABI_HIPHOP, vibe.genre)
        assertEquals("Punjabi", vibe.detectedLanguage)
        assertEquals(VibeTempo.MEDIUM_GROOVE, vibe.tempo)
    }

    @Test
    fun `test devotional vibe detection`() {
        val song = testSong("hanuman_chalisa", "Shree Hanuman Chalisa", "Gulshan Kumar, Hariharan")
        val vibe = SongVibeAnalyzer.analyze(song)
        assertEquals(MusicVibeGenre.DEVOTIONAL, vibe.genre)
    }

    @Test
    fun `test cohesion filters out party remix for romantic songs`() {
        val romanticSong = testSong("kesariya", "Kesariya", "Arijit Singh")
        val vibe = SongVibeAnalyzer.analyze(romanticSong)

        val loudRemix = testSong("remix_1", "Kesariya DJ Remix Bass Boosted", "DJ Rahul")
        val soulfulMatch = testSong("match_1", "Apna Bana Le", "Arijit Singh, Sachin-Jigar")

        assertFalse(SongVibeAnalyzer.isCohesive(loudRemix, vibe))
        assertTrue(SongVibeAnalyzer.isCohesive(soulfulMatch, vibe))
    }

    @Test
    fun `test cohesion filters out podcast and long hour videos`() {
        val seed = testSong("seed_1", "Softly", "Karan Aujla")
        val vibe = SongVibeAnalyzer.analyze(seed)

        val podcast = testSong("pod_1", "Karan Aujla Podcast Interview on Life", "The Ranveer Show")
        val longVideo = testSong("long_1", "All Songs Jukebox Karan Aujla", "Speed Records", durationText = "45:30")
        val normalSong = testSong("song_1", "52 Bars", "Karan Aujla", durationText = "3:25")

        assertFalse(SongVibeAnalyzer.isCohesive(podcast, vibe))
        assertFalse(SongVibeAnalyzer.isCohesive(longVideo, vibe))
        assertTrue(SongVibeAnalyzer.isCohesive(normalSong, vibe))
    }
}
