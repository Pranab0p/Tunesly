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

@Serializable
data class SessionVibe(
    val moodTag: String,
    val moodKeywords: String,
    val topArtists: List<String>,
    val antiFatigueIds: Set<String>,
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

    // Real-Time Behavioral Tracking (Skips, Searches, Browsing)
    private val inMemorySkippedTracks = ConcurrentHashMap<String, Long>()
    private val inMemoryRecentSearches = mutableListOf<Pair<String, Long>>()
    private val inMemoryRecentBrowsing = mutableListOf<Pair<String, Long>>()

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

    fun setFollowedArtists(artists: Collection<String>) {
        artists.forEach { artist ->
            val clean = artist.trim()
            if (clean.isNotBlank()) {
                val current = inMemoryArtistCounts[clean] ?: 0
                if (current < 5) {
                    inMemoryArtistCounts[clean] = 5
                }
            }
        }
        persistToDisk()
    }

    fun getTopArtists(limit: Int = 5): List<String> =
        inMemoryArtistCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }

    fun recordSkip(videoId: String, artist: String) {
        if (videoId.isNotBlank()) {
            inMemorySkippedTracks[videoId] = System.currentTimeMillis()
        }
        val cleanArtist = artist.trim()
        if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown Artist", ignoreCase = true)) {
            val current = inMemoryArtistCounts[cleanArtist] ?: 0
            if (current > 1) {
                inMemoryArtistCounts[cleanArtist] = current - 1
            }
        }
    }

    fun recordCompleted(song: Song) {
        val artist = song.artist.trim()
        if (artist.isNotBlank() && !artist.equals("Unknown Artist", ignoreCase = true)) {
            val current = inMemoryArtistCounts[artist] ?: 0
            inMemoryArtistCounts[artist] = current + 2
        }
        inMemorySkippedTracks.remove(song.videoId)
        persistToDisk()
    }

    fun recordSearchIntent(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        synchronized(inMemoryRecentSearches) {
            inMemoryRecentSearches.removeAll { it.first.equals(clean, ignoreCase = true) }
            inMemoryRecentSearches.add(0, Pair(clean, System.currentTimeMillis()))
            if (inMemoryRecentSearches.size > 15) {
                inMemoryRecentSearches.removeAt(inMemoryRecentSearches.lastIndex)
            }
        }
    }

    fun recordBrowsingIntent(title: String, subtitle: String = "") {
        val combined = "$title $subtitle".trim()
        if (combined.length < 3) return
        synchronized(inMemoryRecentBrowsing) {
            inMemoryRecentBrowsing.removeAll { it.first.equals(combined, ignoreCase = true) }
            inMemoryRecentBrowsing.add(0, Pair(combined, System.currentTimeMillis()))
            if (inMemoryRecentBrowsing.size > 10) {
                inMemoryRecentBrowsing.removeAt(inMemoryRecentBrowsing.lastIndex)
            }
        }
    }

    fun getActiveSessionVibe(): SessionVibe {
        val now = System.currentTimeMillis()
        val twoHours = 2 * 60 * 60 * 1000L

        // Prune skips older than 2 hours
        inMemorySkippedTracks.entries.removeIf { now - it.value > twoHours }

        // Gather anti-fatigue tracks (recent history + skips)
        val antiFatigue = mutableSetOf<String>()
        antiFatigue.addAll(inMemorySkippedTracks.keys)
        try {
            val localHistory = com.music.bitchord.data.LocalHistoryStore.getHistory()
            antiFatigue.addAll(localHistory.take(40).map { it.videoId })
        } catch (_: Exception) {}

        // 1. Check recent search within last 45 minutes (Highest active intent)
        val recentSearch = synchronized(inMemoryRecentSearches) {
            inMemoryRecentSearches.firstOrNull { now - it.second < 45 * 60 * 1000L }?.first
        }

        // 2. Check recent browsing within last 45 minutes
        val recentBrowse = synchronized(inMemoryRecentBrowsing) {
            inMemoryRecentBrowsing.firstOrNull { now - it.second < 45 * 60 * 1000L }?.first
        }

        // 3. Check recent played track titles/artists
        val recentPlayed = synchronized(inMemoryRecent) {
            inMemoryRecent.take(4).joinToString(" ") { "${it.title} ${it.artist}" }
        }

        val contextClues = listOfNotNull(recentSearch, recentBrowse, recentPlayed).joinToString(" ").lowercase()

        // Match context clues to inferred mood
        val (tag, keywords) = when {
            contextClues.contains("sad") || contextClues.contains("dard") || contextClues.contains("broken") ||
                contextClues.contains("breakup") || contextClues.contains("judai") || contextClues.contains("b praak") ||
                contextClues.contains("channa mereya") || contextClues.contains("emotional") ->
                Pair("Heartbreak & Deep Melodies", "sad heartbreak emotional acoustic hits")

            contextClues.contains("chill") || contextClues.contains("lofi") || contextClues.contains("lo-fi") ||
                contextClues.contains("acoustic") || contextClues.contains("anuv") || contextClues.contains("prateek") ||
                contextClues.contains("indie") || contextClues.contains("slowed") || contextClues.contains("reverb") ->
                Pair("Chill Indie & Lo-Fi", "indie acoustic chill lofi songs")

            contextClues.contains("party") || contextClues.contains("dance") || contextClues.contains("punjabi") ||
                contextClues.contains("karan aujla") || contextClues.contains("shubh") || contextClues.contains("sidhu") ||
                contextClues.contains("diljit") || contextClues.contains("dj") || contextClues.contains("club") ->
                Pair("High Energy & Party", "punjabi party dance club hits")

            contextClues.contains("gym") || contextClues.contains("workout") || contextClues.contains("phonk") ||
                contextClues.contains("bass") || contextClues.contains("trap") || contextClues.contains("motivation") ->
                Pair("Workout & Motivation", "gym workout motivational bass phonk songs")

            contextClues.contains("bhakti") || contextClues.contains("hanuman") || contextClues.contains("shiv") ||
                contextClues.contains("chalisa") || contextClues.contains("bhajan") || contextClues.contains("aarti") ->
                Pair("Devotional & Peace", "morning devotional bhakti songs aarti chalisa")

            contextClues.contains("romantic") || contextClues.contains("love") || contextClues.contains("ishq") ||
                contextClues.contains("pyar") || contextClues.contains("arijit") || contextClues.contains("atif") ||
                contextClues.contains("shreya") || contextClues.contains("jubin") ->
                Pair("Pure Romance", "romantic love songs soulful bollywood")

            else -> {
                // Time of day fallback
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                when (hour) {
                    in 5..10 -> Pair("Morning Acoustic & Fresh", "morning acoustic fresh melodies")
                    in 11..16 -> Pair("Daytime Focus & Chill", "daytime acoustic focus hits")
                    in 17..21 -> Pair("Evening Hits & Trending", "trending hindi punjabi hits")
                    in 22..24, in 0..4 -> Pair("Late Night Melancholy", "late night lofi acoustic calm songs")
                    else -> Pair("Personalized Mix", "top hits popular songs")
                }
            }
        }

        return SessionVibe(
            moodTag = tag,
            moodKeywords = keywords,
            topArtists = getTopArtists(5),
            antiFatigueIds = antiFatigue,
        )
    }
}
