package com.music.bitchord.data.taste

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class TasteTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
private data class TasteProfileData(
    val recentTracks: List<TasteTrack> = emptyList(),
    val artistCounts: Map<String, Int> = emptyMap(),
    val lastPlayedSeed: TasteTrack? = null,
)

/**
 * Local AI & Taste Engine tracking the listener's music preferences in real-time.
 *
 * Powers hyper-accurate personalized recommendations:
 * - "Jump Back In" (Recent favorites without requiring Google sign-in)
 * - "Because You Listened To [Track]" (Deep-learning radio graph based on user's seed)
 * - "Daily Mix 1, 2, 3" (Adaptive artist & mood mixes)
 */
object UserTasteProfile {
    private const val PREFS_NAME = "tunesly_user_taste"
    private const val KEY_PROFILE = "profile_json"
    private const val MAX_RECENT = 40

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var prefs: SharedPreferences

    private val inMemoryRecent = mutableListOf<TasteTrack>()
    private val inMemoryArtistCounts = ConcurrentHashMap<String, Int>()
    @Volatile
    private var inMemoryLastPlayed: TasteTrack? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return
        val data = runCatching { json.decodeFromString<TasteProfileData>(raw) }.getOrNull() ?: return
        synchronized(inMemoryRecent) {
            inMemoryRecent.clear()
            inMemoryRecent.addAll(data.recentTracks)
        }
        inMemoryArtistCounts.clear()
        inMemoryArtistCounts.putAll(data.artistCounts)
        inMemoryLastPlayed = data.lastPlayedSeed
    }

    private fun persistToDisk() {
        val data = synchronized(inMemoryRecent) {
            TasteProfileData(
                recentTracks = inMemoryRecent.toList(),
                artistCounts = inMemoryArtistCounts.toMap(),
                lastPlayedSeed = inMemoryLastPlayed,
            )
        }
        val encoded = runCatching { json.encodeToString(data) }.getOrNull() ?: return
        prefs.edit().putString(KEY_PROFILE, encoded).apply()
    }

    /**
     * Records a track played by the user. Updates recent history and artist affinity.
     */
    fun recordPlay(song: Song) {
        val vid = song.videoId.ifBlank { return }
        val title = song.title.ifBlank { return }
        val artist = song.artist.ifBlank { "Unknown Artist" }
        val art = song.thumbnailUrl.orEmpty()

        val track = TasteTrack(
            videoId = vid,
            title = title,
            artist = artist,
            thumbnailUrl = art,
            timestamp = System.currentTimeMillis(),
        )

        inMemoryLastPlayed = track

        synchronized(inMemoryRecent) {
            inMemoryRecent.removeAll { it.videoId == vid }
            inMemoryRecent.add(0, track)
            if (inMemoryRecent.size > MAX_RECENT) {
                inMemoryRecent.removeAt(inMemoryRecent.lastIndex)
            }
        }

        // Increment artist play count
        val currentCount = inMemoryArtistCounts[artist] ?: 0
        inMemoryArtistCounts[artist] = currentCount + 1

        persistToDisk()
    }

    fun getRecentTracks(): List<TasteTrack> = synchronized(inMemoryRecent) {
        inMemoryRecent.toList()
    }

    fun getLastPlayedSeed(): TasteTrack? = inMemoryLastPlayed

    fun getTopArtists(limit: Int = 5): List<String> =
        inMemoryArtistCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
}
