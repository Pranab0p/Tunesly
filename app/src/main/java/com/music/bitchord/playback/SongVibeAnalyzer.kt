package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.taste.UserTasteProfile
import java.util.Locale

enum class VibeTempo {
    SLOW_MELLOW,
    MEDIUM_GROOVE,
    FAST_UPBEAT
}

enum class MusicVibeGenre(val label: String, val searchKeyword: String) {
    ROMANTIC_SOULFUL("Soulful Romance", "romantic love acoustic soulful"),
    SAD_HEARTBREAK("Heartbreak & Deep Melodies", "sad heartbreak emotional dard"),
    PUNJABI_HIPHOP("Punjabi & Desi Hip-Hop", "punjabi hip hop urban bangers"),
    PARTY_DANCE("High Energy & Party", "party dance club remix bangers"),
    CHILL_LOFI_INDIE("Chill Indie & Lo-Fi", "indie acoustic chill lofi"),
    DEVOTIONAL("Devotional & Peace", "devotional bhakti bhajan morning peace"),
    WORKOUT_PHONK("Workout & Motivation", "gym workout phonk bass trap"),
    GLOBAL_POP("Global Pop & Hits", "pop billboard global hits"),
    GENERAL("Similar Vibe", "similar songs hits")
}

data class SongVibeProfile(
    val genre: MusicVibeGenre,
    val tempo: VibeTempo,
    val detectedLanguage: String,
    val vibeLabel: String,
    val primaryArtist: String,
)

/**
 * Intelligent Music Vibe & Cohesion Classifier.
 * Analyzes song genre, tempo, language and mood to prevent jarring transitions,
 * boring radio drift, or mismatched regional songs.
 */
object SongVibeAnalyzer {

    private val PUNJABI_ARTISTS = setOf(
        "karan aujla", "diljit dosanjh", "sidhu moose wala", "shubh", "ap dhillon",
        "jordan sandhu", "amrit maan", "b praak", "guru randhawa", "parmish verma",
        "garry sandhu", "sunanda sharma", "nimrat khaira", "prabh deep", "bohemia",
        "hardy sandhu", "amrinder gill", "sukhe", "mankirt aulakh", "kaka",
        "korala maan", "wazir patar", "arjan dhillon", "prophec", "pav dharia"
    )

    private val SOUTH_INDIAN_ARTISTS = setOf(
        "anirudh ravichander", "anirudh", "sid sriram", "devi sri prasad", "dsp",
        "a.r. rahman", "ar rahman", "thaman s", "thaman", "ilaiyaraaja", "harris jayaraj",
        "santhosh narayanan", "spb", "s.p. balasubrahmanyam", "chinmayi", "yuvan shankar raja",
        "gv prakash", "g.v. prakash kumar", "dhee", "vijay prakash"
    )

    private val BHOJPURI_ARTISTS = setOf(
        "pawan singh", "khesari lal yadav", "shilpi raj", "ritesh pandey",
        "arvind akela kallu", "neelkamal singh", "pramod premi"
    )

    private val ENGLISH_ARTISTS = setOf(
        "the weeknd", "taylor swift", "drake", "ed sheeran", "billie eilish",
        "bruno mars", "dua lipa", "justin bieber", "adele", "post malone",
        "eminem", "travis scott", "rihanna", "katy perry", "shawn mendes",
        "charlie puth", "imagine dragons", "coldplay", "maroon 5", "ariana grande",
        "olivia rodrigo", "sam smith", "harry styles", "kendrick lamar", "lady gaga",
        "chainsmokers", "marshmello", "alan walker", "david guetta", "calvin harris"
    )

    private val HINDI_SOULFUL_ARTISTS = setOf(
        "arijit singh", "atif aslam", "shreya ghoshal", "pritam", "mohit chauhan",
        "jubin nautiyal", "armaan malik", "vishal mishra", "darshan raval",
        "sonu nigam", "kk", "kishore kumar", "lata mangeshkar", "udit narayan",
        "alka yagnik", "sachin-jigar", "shankar-ehsaan-loy", "amit trivedi",
        "neha kakkar", "shaan", "monali thakur", "papon", "anuv jain", "prateek kuhad"
    )

    fun analyze(song: Song): SongVibeProfile {
        val titleNorm = song.title.lowercase(Locale.ROOT)
        val artistNorm = song.artist.lowercase(Locale.ROOT)
        val combined = "$titleNorm $artistNorm"

        // 1. Language Detection
        val detectedLang = detectLanguage(artistNorm, titleNorm)

        // 2. Genre & Tempo Detection
        val (genre, tempo, label) = when {
            // Devotional
            containsAny(combined, "bhakti", "bhajan", "aarti", "chalisa", "hanuman", "shiv", "krishna", "ram", "stotram", "mantra", "devotional", "mata", "ganga", "shankar", "bhole") ->
                Triple(MusicVibeGenre.DEVOTIONAL, VibeTempo.SLOW_MELLOW, "Devotional & Peace")

            // Workout / Phonk
            containsAny(combined, "phonk", "gym", "workout", "sigma", "brazilian phonk", "hardstyle", "drift phonk", "motivation") ->
                Triple(MusicVibeGenre.WORKOUT_PHONK, VibeTempo.FAST_UPBEAT, "Workout & Bass Drive")

            // Chill / Lo-Fi / Indie
            containsAny(combined, "lofi", "lo-fi", "chill", "slowed", "reverb", "indie", "unplugged", "acoustic", "anuv jain", "prateek kuhad", "zaeden", "jasleen", "baarishein", "kasoor") ->
                Triple(MusicVibeGenre.CHILL_LOFI_INDIE, VibeTempo.SLOW_MELLOW, "Chill Indie & Lo-Fi")

            // Sad & Heartbreak
            containsAny(combined, "sad", "dard", "judai", "breakup", "broken", "roye", "channa mereya", "emotional", "bewafa", "tanhai", "alvida", "b praak", "mann bharrya", "qismat", "pachtaoge") ->
                Triple(MusicVibeGenre.SAD_HEARTBREAK, VibeTempo.SLOW_MELLOW, "Heartbreak & Melodies")

            // Party & Dance
            containsAny(combined, "remix", "party", "dance", "club", "dj", "mashup", "dhol", "banger", "electronic", "edm", "club mix", "bass boosted") ->
                Triple(MusicVibeGenre.PARTY_DANCE, VibeTempo.FAST_UPBEAT, "High Energy & Party")

            // Punjabi & Desi Hip-Hop
            detectedLang == "Punjabi" || containsAny(artistNorm, "karan aujla", "shubh", "sidhu", "diljit", "ap dhillon", "divine", "raftaar", "badshah", "seedhe maut", "mc stan", "bella", "drake", "travis scott") ->
                Triple(MusicVibeGenre.PUNJABI_HIPHOP, VibeTempo.MEDIUM_GROOVE, "Urban Hip-Hop & Beats")

            // Romantic & Soulful
            containsAny(combined, "romantic", "love", "ishq", "pyar", "tum", "dil", "soulful", "tum hi ho", "kesariya", "apna bana le", "humsafar", "shreya", "arijit", "atif", "jubin", "ed sheeran", "perfect", "shayad") ->
                Triple(MusicVibeGenre.ROMANTIC_SOULFUL, VibeTempo.SLOW_MELLOW, "Soulful Romance")

            // English Pop
            detectedLang == "English" ->
                Triple(MusicVibeGenre.GLOBAL_POP, VibeTempo.MEDIUM_GROOVE, "Global Pop & Hits")

            else ->
                Triple(MusicVibeGenre.GENERAL, VibeTempo.MEDIUM_GROOVE, "${song.artist} Flow")
        }

        val primaryArtist = song.artist.split(",", "&", "feat.", "ft.", "•").firstOrNull()?.trim().orEmpty().ifBlank { song.artist }

        return SongVibeProfile(
            genre = genre,
            tempo = tempo,
            detectedLanguage = detectedLang,
            vibeLabel = label,
            primaryArtist = primaryArtist,
        )
    }

    private fun detectLanguage(artistNorm: String, titleNorm: String): String {
        if (PUNJABI_ARTISTS.any { artistNorm.contains(it) } || containsAny(titleNorm, "jatt", "pind", "yaari", "gabru", "soorma", "gallan", "kudi", "sohneya", "akhiyan", "suit")) {
            return "Punjabi"
        }
        if (SOUTH_INDIAN_ARTISTS.any { artistNorm.contains(it) } || containsAny(titleNorm, "telugu", "tamil", "malayalam", "kannada")) {
            return "South Indian"
        }
        if (BHOJPURI_ARTISTS.any { artistNorm.contains(it) } || containsAny(titleNorm, "bhojpuri", "kamariya", "choli", "lollypop")) {
            return "Bhojpuri"
        }
        if (ENGLISH_ARTISTS.any { artistNorm.contains(it) }) {
            return "English"
        }
        if (HINDI_SOULFUL_ARTISTS.any { artistNorm.contains(it) } || containsAny(titleNorm, "pyar", "ishq", "dil", "mohabbat", "tum", "hum", "zindagi", "sanam", "saathiya", "humsafar", "deewane")) {
            return "Hindi"
        }
        return UserTasteProfile.getEffectivePreferredLanguages().firstOrNull() ?: "Hindi"
    }

    /**
     * Strictly verifies whether a candidate track is harmonious with the current song's
     * vibe, tempo, and language, rejecting jarring or random YouTube radio drift.
     */
    fun isCohesive(candidate: Song, seedVibe: SongVibeProfile): Boolean {
        if (candidate.videoId.isBlank()) return false
        val cTitle = candidate.title.lowercase(Locale.ROOT)
        val cArtist = candidate.artist.lowercase(Locale.ROOT)
        val cCombined = "$cTitle $cArtist"

        // 1. Filter out non-song noise, full movies, podcasts, or long compilations
        if (containsAny(cTitle, "jukebox", "all songs audio", "full movie", "podcast", "interview", "scene", "dialogue", "trailer", "teaser", "status video", "ringtone", "reaction video")) {
            return false
        }

        // 2. Reject excessively long videos (e.g. > 10 min)
        candidate.durationText?.let { dur ->
            val colons = dur.count { it == ':' }
            if (colons > 1) return false // Hour-long video
            val parts = dur.split(':')
            val mins = parts.firstOrNull()?.toIntOrNull() ?: 0
            if (mins > 10) return false
        }

        // 3. Genre & Tempo Clash Protection
        when (seedVibe.genre) {
            MusicVibeGenre.ROMANTIC_SOULFUL,
            MusicVibeGenre.SAD_HEARTBREAK,
            MusicVibeGenre.CHILL_LOFI_INDIE -> {
                // Reject loud party remixes or harsh phonk
                if (containsAny(cTitle, "dj remix", "club mix", "hardstyle", "phonk", "bass boosted", "dhol mix", "party blaster", "screaming")) {
                    return false
                }
            }

            MusicVibeGenre.PUNJABI_HIPHOP,
            MusicVibeGenre.PARTY_DANCE,
            MusicVibeGenre.WORKOUT_PHONK -> {
                // Reject slow devotional or lullabies
                if (containsAny(cTitle, "aarti", "chalisa", "bhajan", "lullaby", "sleep music", "ghazal slow", "soothing meditation")) {
                    return false
                }
            }

            MusicVibeGenre.DEVOTIONAL -> {
                // Reject party, club, alcohol or vulgar songs
                if (containsAny(cCombined, "party", "club", "dance mix", "hip hop", "rap", "alcohol", "sharab", "daroo", "choli", "kamariya")) {
                    return false
                }
            }

            else -> {}
        }

        // 4. Regional Language Shield: Prevent random Bhojpuri/unrelated insertions into Hindi/Punjabi/English sessions
        val userLangs = UserTasteProfile.getEffectivePreferredLanguages()
        val isCandidateBhojpuri = BHOJPURI_ARTISTS.any { cArtist.contains(it) } || containsAny(cTitle, "bhojpuri", "kamariya", "choli")
        if (isCandidateBhojpuri && seedVibe.detectedLanguage != "Bhojpuri" && !userLangs.contains("Bhojpuri")) {
            return false
        }

        return true
    }

    /**
     * Builds smart targeted search queries to guarantee pure vibe matching.
     */
    fun buildVibeSearchQueries(seedSong: Song, vibe: SongVibeProfile): List<String> {
        val artist = vibe.primaryArtist
        val queries = mutableListOf<String>()

        queries.add("$artist similar songs")
        queries.add("$artist ${vibe.genre.searchKeyword}")
        queries.add("${vibe.genre.searchKeyword} ${vibe.detectedLanguage} songs hits")

        return queries
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true)) return true
        }
        return false
    }
}
