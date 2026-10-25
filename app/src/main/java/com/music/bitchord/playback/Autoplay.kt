package com.music.bitchord.playback

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.data.tuneslycloud.TuneslyCloudManager
import com.music.bitchord.playback.QueueCoordinator.asQueueEntry
import kotlinx.coroutines.CancellationException

/** Most AutoPlay-suggested tracks kept queued ahead of the current one at once. */
const val MAX_QUEUED_AUTOPLAY = 10

/**
 * Whether AutoPlay is on for the music this device is playing.
 *
 * In a party it is the party's setting and not this phone's: AutoPlay is shared
 * there, so one member's preference cannot be what decides. [localPreference] is
 * `AppSettings.autoplay` and applies everywhere else.
 *
 * Shared by the playback service and by every surface that draws the toggle,
 * because they went their own ways once and the result was a button that lied:
 * the player read the local preference while the service obeyed the party's, so
 * a listener could sit under "AutoPlay on" getting no suggestions at all, press
 * it, and watch the label stay exactly as it was.
 */
fun autoplayEnabledFor(party: ListenTogether.State, localPreference: Boolean): Boolean =
    if (party.inParty) party.playback.autoplayEnabled else localPreference

/**
 * The one device in a party that tops the queue up, or null if there is none.
 *
 * Everybody runs this over the same membership and gets the same answer, which
 * is what stops five phones appending five different sets of suggestions to one
 * queue. The host supplies while it is connected; otherwise the lowest member id
 * takes over, chosen for being stable rather than for meaning anything.
 *
 * Except in a party locked to its host, where there is no stand-in. The server
 * refuses a `queueAdd` from a listener outright — `host_only`, whatever the
 * listener believes it is doing — so electing one produced a control that was
 * always refused, an error on that listener's phone and suggestions for nobody.
 * Returning null instead lets AutoPlay wait for the host, which is what the
 * party's own policy already says should happen.
 */
fun autoplaySupplierId(party: ListenTogether.State): String? {
    if (!party.inParty) return null
    val host = party.members.firstOrNull { it.isHost && it.connected }?.memberId
    if (party.hostOnlyControl) return host
    return host ?: party.members.asSequence()
        .filter { it.connected }
        .minByOrNull { it.memberId }
        ?.memberId
}

/**
 * Whether AutoPlay should forget its last seed and try the current track again.
 *
 * A completed load normally leaves its seed behind to de-duplicate the several
 * player callbacks caused by one queue edit. If that edit leaves the current
 * track with nothing after it, however, the seed is no longer useful: keeping
 * it would make an enabled AutoPlay queue stay empty forever.
 */
fun autoplayQueueNeedsRefresh(
    enabled: Boolean,
    repeatAll: Boolean,
    currentIndex: Int,
    itemCount: Int,
    loadInProgress: Boolean,
): Boolean = enabled &&
    !repeatAll &&
    !loadInProgress &&
    currentIndex >= 0 &&
    currentIndex == itemCount - 1

/**
 * Finds the YouTube id that should seed AutoPlay for a song. Module tracks do not
 * carry YouTube ids, so they are matched on YouTube before the radio request.
 */
suspend fun youtubeSeedFor(song: Song): String? {
    if (SourceRegistry.parseTrackKey(song.videoId) == null) return song.videoId
    val target = TrackMatcher.targetOf(song)
    val query = TrackMatcher.queries(target).firstOrNull() ?: return null
    return YtMusicRepository.search(query, SearchFilter.SONGS)
        .getOrNull()
        ?.filterIsInstance<SearchResult.Track>()
        ?.map { it.song }
        ?.let { TrackMatcher.best(it, target) }
        ?.videoId
}

/**
 * Loads, de-duplicates and resolves one AutoPlay batch. The playback service is
 * the only caller for the AutoPlay toggle; the player UI's explicit radio start
 * uses this same helper for its initial station batch.
 */
suspend fun loadAutoplayTracks(
    existing: List<Song>,
    seedSong: Song,
    limit: Int = MAX_QUEUED_AUTOPLAY,
): Result<List<Song>> {
    val seed = youtubeSeedFor(seedSong)
    val ytRelated = if (seed != null) {
        YtMusicRepository.radio(seed).getOrElse { emptyList() }
    } else {
        emptyList()
    }

    var extra = QueueBuilder.extend(existing, ytRelated, limit)

    // Smart Infinite Radio: If YouTube radio returned fewer than limit tracks (or failed),
    // automatically top up with Tunesly Cloud AI Recommendations matching seed song's mood!
    if (extra.size < limit) {
        val needed = limit - extra.size
        val cloudTracks = fetchCloudAutoplayCandidates(needed, seedSong)
        if (cloudTracks.isNotEmpty()) {
            val combined = ytRelated + cloudTracks
            extra = QueueBuilder.extend(existing, combined, limit)
        }
    }

    if (extra.isEmpty()) return Result.success(emptyList())

    return Result.success(extra.map {
        it.copy(
            radioName = seedSong.radioName ?: "Tunesly Infinite Radio",
            playbackSource = seedSong.playbackSource,
            playbackSourceType = seedSong.playbackSourceType,
            playbackSourceId = seedSong.playbackSourceId,
        ).asQueueEntry(QueueTier.AUTOPLAY)
    })
}

private suspend fun fetchCloudAutoplayCandidates(limit: Int, seedSong: Song? = null): List<Song> {
    val titleLower = seedSong?.title?.lowercase().orEmpty()
    val artistLower = seedSong?.artist?.lowercase().orEmpty()
    val mood = when {
        titleLower.contains("love") || titleLower.contains("ishq") || titleLower.contains("pyaar") ||
        titleLower.contains("dil") || artistLower.contains("arijit") || artistLower.contains("atif") -> "romantic"
        titleLower.contains("party") || titleLower.contains("dance") || titleLower.contains("club") ||
        artistLower.contains("karan aujla") || artistLower.contains("shubh") || artistLower.contains("diljit") ||
        artistLower.contains("ap dhillon") -> "party"
        titleLower.contains("sad") || titleLower.contains("dard") || titleLower.contains("judaai") ||
        artistLower.contains("b praak") -> "sad"
        titleLower.contains("chill") || titleLower.contains("lofi") || artistLower.contains("anuv jain") ||
        artistLower.contains("local train") || artistLower.contains("prateek kuhad") -> "chill"
        titleLower.contains("gym") || titleLower.contains("workout") || titleLower.contains("josh") -> "workout"
        titleLower.contains("ram") || titleLower.contains("shiva") || titleLower.contains("krishna") ||
        titleLower.contains("hanuman") || titleLower.contains("aarti") || titleLower.contains("bhajan") -> "devotional"
        else -> null
    }
    return try {
        val res = if (mood != null) {
            TuneslyCloudManager.getMoodRecommendations(mood, limit)
        } else if (TuneslyCloudManager.isLoggedIn) {
            TuneslyCloudManager.getForYouRecommendations(limit)
        } else {
            TuneslyCloudManager.getTrendingRecommendations(limit)
        }
        res.getOrNull().orEmpty().map { t ->
            Song(
                videoId = t.trackId,
                title = t.title,
                artist = t.artist,
                thumbnailUrl = t.artworkUrl.ifBlank { "https://i.ytimg.com/vi/${t.trackId}/hqdefault.jpg" },
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
