package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object LocalHistoryStore {
    private const val PREF_NAME = "tunesly_local_history"
    private const val KEY_HISTORY = "history_json"
    private const val MAX_HISTORY_ITEMS = 100

    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history = _history.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        if (!this::prefs.isInitialized) return
        val raw = prefs.getString(KEY_HISTORY, null) ?: return
        runCatching {
            val list = mutableListOf<Song>()
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val sObj = jsonArray.getJSONObject(i)
                val videoId = sObj.optString("videoId", "")
                if (videoId.isNotBlank()) {
                    list.add(
                        Song(
                            videoId = videoId,
                            title = sObj.optString("title", ""),
                            artist = sObj.optString("artist", ""),
                            thumbnailUrl = sObj.optString("thumbnailUrl", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            durationText = sObj.optString("durationText", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            artistId = sObj.optString("artistId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            albumId = sObj.optString("albumId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            albumName = sObj.optString("albumName", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            isVideo = sObj.optBoolean("isVideo", false),
                        )
                    )
                }
            }
            _history.value = list
        }
    }

    fun recordSong(song: Song) {
        if (song.videoId.isBlank()) return
        val current = _history.value.toMutableList()
        current.removeAll { it.videoId == song.videoId }
        current.add(0, song)
        val trimmed = if (current.size > MAX_HISTORY_ITEMS) current.take(MAX_HISTORY_ITEMS) else current
        _history.value = trimmed
        persist(trimmed)

        // Sync with Tunesly Cloud database if logged in
        if (com.music.bitchord.data.tuneslycloud.TuneslyCloudManager.isLoggedIn) {
            scope.launch {
                try {
                    com.music.bitchord.data.tuneslycloud.TuneslyCloudManager.logPlayback(
                        trackId = song.videoId,
                        title = song.title,
                        artist = song.artist,
                        durationSeconds = 0L,
                        playedSeconds = 1L,
                        artworkUrl = song.thumbnailUrl.orEmpty(),
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun getHistory(): List<Song> = _history.value

    fun clearHistory() {
        _history.value = emptyList()
        persist(emptyList())
    }

    fun removeSong(videoId: String) {
        val updated = _history.value.filterNot { it.videoId == videoId }
        _history.value = updated
        persist(updated)
    }

    fun mergeRemoteHistory(remoteSongs: List<Song>) {
        if (remoteSongs.isEmpty()) return
        val existing = _history.value
        val existingIds = existing.map { it.videoId }.toSet()
        val combined = existing.toMutableList()
        for (song in remoteSongs) {
            if (!existingIds.contains(song.videoId)) {
                combined.add(song)
            }
        }
        val trimmed = if (combined.size > MAX_HISTORY_ITEMS) combined.take(MAX_HISTORY_ITEMS) else combined
        _history.value = trimmed
        persist(trimmed)
    }

    private fun persist(list: List<Song>) {
        if (!this::prefs.isInitialized) return
        scope.launch {
            try {
                val array = JSONArray()
                for (s in list) {
                    val obj = JSONObject().apply {
                        put("videoId", s.videoId)
                        put("title", s.title)
                        put("artist", s.artist)
                        put("thumbnailUrl", s.thumbnailUrl ?: "")
                        put("durationText", s.durationText ?: "")
                        put("artistId", s.artistId ?: "")
                        put("albumId", s.albumId ?: "")
                        put("albumName", s.albumName ?: "")
                        put("isVideo", s.isVideo)
                    }
                    array.put(obj)
                }
                prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
            } catch (_: Exception) {}
        }
    }
}
