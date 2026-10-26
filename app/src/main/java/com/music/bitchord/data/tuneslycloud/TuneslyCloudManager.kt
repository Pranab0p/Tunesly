package com.music.bitchord.data.tuneslycloud

import com.music.bitchord.data.Http
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

data class TuneslyUser(
    val id: String,
    val username: String,
    val email: String,
    val totalPlayTimeSeconds: Long = 0,
)

data class TuneslyUserSummary(
    val id: String,
    val username: String,
    val avatar: String = "",
    val bio: String = "",
)

data class TuneslyPublicPlaylist(
    val id: String,
    val name: String,
    val description: String = "",
    val trackCount: Int = 0,
)

data class TuneslyPublicProfile(
    val userId: String,
    val username: String,
    val avatar: String = "",
    val bio: String = "",
    val totalPlayTimeSeconds: Long = 0,
    val memberSince: String = "",
    val topArtists: List<String> = emptyList(),
    val recentTracks: List<TuneslyCloudTrack> = emptyList(),
    val playlists: List<TuneslyPublicPlaylist> = emptyList(),
    val favoritesCount: Int = 0,
)

data class TuneslyChatMessage(
    val id: String,
    val isMine: Boolean,
    val text: String,
    val trackAttachment: TuneslyCloudTrack? = null,
    val read: Boolean = false,
    val createdAt: String = "",
)

data class TuneslyConversation(
    val username: String,
    val avatar: String = "",
    val bio: String = "",
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val hasTrack: Boolean = false,
    var unreadCount: Int = 0,
)

data class TuneslyCloudTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String = "",
    val durationSeconds: Long = 0,
    val reason: String = "",
)

object TuneslyCloudManager {
    // Both Direct IP and SSL Nginx reverse proxy
    private val BASE_URLS = listOf(
        "http://194.127.179.229:5050/api",
        "https://vibestudio.blog/tunesly/api",
    )
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    val isLoggedIn: Boolean
        get() = AppSettings.tuneslyToken.value.isNotBlank()

    private inline fun <T> executeRequest(
        endpoint: String,
        builder: (String) -> Request,
        parser: (Response, JSONObject) -> Result<T>,
    ): Result<T> {
        var lastException: Exception? = null
        for (baseUrl in BASE_URLS) {
            try {
                val req = builder("$baseUrl$endpoint")
                val resp = Http.client.newCall(req).execute()
                resp.use {
                    val body = it.body?.string().orEmpty()
                    val json = try {
                        JSONObject(body)
                    } catch (_: Exception) {
                        JSONObject()
                    }
                    return parser(it, json)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        return Result.failure(lastException ?: Exception("Network error connecting to Tunesly VPS"))
    }

    suspend fun register(username: String, email: String, password: String): Result<TuneslyUser> =
        withContext(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("username", username.trim())
                put("email", email.trim())
                put("password", password)
            }
            executeRequest(
                "/auth/register",
                builder = { url ->
                    Request.Builder()
                        .url(url)
                        .post(json.toString().toRequestBody(JSON_MEDIA))
                        .build()
                },
                parser = { resp, resJson ->
                    if (resp.isSuccessful && resJson.optBoolean("success")) {
                        val token = resJson.getString("token")
                        val userObj = resJson.getJSONObject("user")
                        val user = TuneslyUser(
                            id = userObj.optString("_id"),
                            username = userObj.optString("username"),
                            email = userObj.optString("email"),
                        )
                        AppSettings.setTuneslyAccount(token, user.username, user.email)
                        Result.success(user)
                    } else {
                        val err = resJson.optString("error", "Registration failed (${resp.code})")
                        Result.failure(Exception(err))
                    }
                },
            )
        }

    suspend fun login(login: String, password: String): Result<TuneslyUser> =
        withContext(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("login", login.trim())
                put("password", password)
            }
            executeRequest(
                "/auth/login",
                builder = { url ->
                    Request.Builder()
                        .url(url)
                        .post(json.toString().toRequestBody(JSON_MEDIA))
                        .build()
                },
                parser = { resp, resJson ->
                    if (resp.isSuccessful && resJson.optBoolean("success")) {
                        val token = resJson.getString("token")
                        val userObj = resJson.getJSONObject("user")
                        val user = TuneslyUser(
                            id = userObj.optString("_id"),
                            username = userObj.optString("username"),
                            email = userObj.optString("email"),
                            totalPlayTimeSeconds = userObj.optLong("totalPlayTimeSeconds", 0),
                        )
                        AppSettings.setTuneslyAccount(token, user.username, user.email)
                        Result.success(user)
                    } else {
                        val err = resJson.optString("error", "Login failed (${resp.code})")
                        Result.failure(Exception(err))
                    }
                },
            )
        }

    fun logout() {
        AppSettings.clearTuneslyAccount()
    }

    suspend fun logPlayback(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Long,
        playedSeconds: Long,
        artworkUrl: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        val token = AppSettings.tuneslyToken.value
        if (token.isBlank() || !AppSettings.tuneslySyncEnabled.value) return@withContext false
        try {
            val json = JSONObject().apply {
                put("trackId", trackId)
                put("title", title)
                put("artist", artist)
                put("durationSeconds", durationSeconds)
                put("playedSeconds", playedSeconds)
                put("artworkUrl", artworkUrl)
            }
            val result = executeRequest(
                "/history/log",
                builder = { url ->
                    Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $token")
                        .post(json.toString().toRequestBody(JSON_MEDIA))
                        .build()
                },
                parser = { resp, _ ->
                    Result.success(resp.isSuccessful)
                },
            )
            result.getOrDefault(false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun toggleFavorite(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Long,
        artworkUrl: String = "",
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = AppSettings.tuneslyToken.value
        if (token.isBlank()) return@withContext Result.failure(Exception("Not logged in"))
        val json = JSONObject().apply {
            put("trackId", trackId)
            put("title", title)
            put("artist", artist)
            put("durationSeconds", durationSeconds)
            put("artworkUrl", artworkUrl)
        }
        executeRequest(
            "/library/favorite",
            builder = { url ->
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .post(json.toString().toRequestBody(JSON_MEDIA))
                    .build()
            },
            parser = { resp, resJson ->
                if (resp.isSuccessful && resJson.optBoolean("success")) {
                    Result.success(resJson.optBoolean("isFavorite", false))
                } else {
                    Result.failure(Exception(resJson.optString("error", "Failed to update favorite")))
                }
            },
        )
    }

    suspend fun getForYouRecommendations(limit: Int = 20): Result<List<TuneslyCloudTrack>> =
        withContext(Dispatchers.IO) {
            val token = AppSettings.tuneslyToken.value
            if (token.isBlank()) return@withContext Result.failure(Exception("Not logged in"))
            executeRequest(
                "/recommendations/for-you?limit=$limit",
                builder = { url ->
                    Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $token")
                        .get()
                        .build()
                },
                parser = { resp, resJson ->
                    if (resp.isSuccessful && resJson.optBoolean("success")) {
                        val arr = resJson.optJSONArray("recommendations") ?: JSONArray()
                        val list = mutableListOf<TuneslyCloudTrack>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                TuneslyCloudTrack(
                                    trackId = obj.optString("trackId"),
                                    title = obj.optString("title"),
                                    artist = obj.optString("artist"),
                                    artworkUrl = obj.optString("artworkUrl"),
                                    durationSeconds = obj.optLong("durationSeconds"),
                                    reason = obj.optString("reason"),
                                ),
                            )
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception(resJson.optString("error", "Failed to fetch recommendations")))
                    }
                },
            )
        }

    suspend fun getTrendingRecommendations(limit: Int = 20): Result<List<TuneslyCloudTrack>> =
        withContext(Dispatchers.IO) {
            executeRequest(
                "/recommendations/trending?limit=$limit",
                builder = { url ->
                    Request.Builder()
                        .url(url)
                        .get()
                        .build()
                },
                parser = { resp, resJson ->
                    if (resp.isSuccessful && resJson.optBoolean("success")) {
                        val arr = resJson.optJSONArray("trending") ?: JSONArray()
                        val list = mutableListOf<TuneslyCloudTrack>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                TuneslyCloudTrack(
                                    trackId = obj.optString("trackId"),
                                    title = obj.optString("title"),
                                    artist = obj.optString("artist"),
                                    artworkUrl = obj.optString("artworkUrl"),
                                    durationSeconds = obj.optLong("durationSeconds"),
                                    reason = obj.optString("reason"),
                                ),
                            )
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception(resJson.optString("error", "Failed to fetch trending")))
                    }
                },
            )
        }

    suspend fun getMoodRecommendations(mood: String, limit: Int = 25): Result<List<TuneslyCloudTrack>> =
        withContext(Dispatchers.IO) {
            val token = AppSettings.tuneslyToken.value
            executeRequest(
                "/recommendations/mood?mood=$mood&limit=$limit",
                builder = { url ->
                    val b = Request.Builder().url(url).get()
                    if (token.isNotBlank()) b.addHeader("Authorization", "Bearer $token")
                    b.build()
                },
                parser = { resp, resJson ->
                    if (resp.isSuccessful && resJson.optBoolean("success")) {
                        val arr = resJson.optJSONArray("recommendations") ?: JSONArray()
                        val list = mutableListOf<TuneslyCloudTrack>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                TuneslyCloudTrack(
                                    trackId = obj.optString("trackId"),
                                    title = obj.optString("title"),
                                    artist = obj.optString("artist"),
                                    artworkUrl = obj.optString("artworkUrl"),
                                    durationSeconds = obj.optLong("durationSeconds"),
                                    reason = obj.optString("reason"),
                                ),
                            )
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception(resJson.optString("error", "Failed to fetch mood recommendations")))
                    }
                },
            )
        }

    suspend fun fetchProfile(): Result<TuneslyUser> = withContext(Dispatchers.IO) {
        val token = AppSettings.tuneslyToken.value
        if (token.isBlank()) return@withContext Result.failure(Exception("Not logged in"))
        executeRequest(
            "/auth/me",
            builder = { url ->
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
            },
            parser = { resp, resJson ->
                if (resp.isSuccessful && resJson.optBoolean("success")) {
                    val userObj = resJson.getJSONObject("user")
                    val user = TuneslyUser(
                        id = userObj.optString("_id"),
                        username = userObj.optString("username"),
                        email = userObj.optString("email"),
                        totalPlayTimeSeconds = userObj.optLong("totalPlayTimeSeconds", 0),
                    )
                    AppSettings.setTuneslyAccount(token, user.username, user.email)
                    Result.success(user)
                } else {
                    Result.failure(Exception("Failed to load profile"))
                }
            },
        )
    }

    suspend fun checkUsernameAvailable(username: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val clean = username.trim().lowercase()
        if (clean.length < 3) return@withContext Result.failure(Exception("Username must be at least 3 characters"))
        executeRequest(
            "/users/check-username/$clean",
            builder = { url -> Request.Builder().url(url).get().build() },
            parser = { resp, resJson ->
                if (resp.isSuccessful && resJson.optBoolean("success")) {
                    Result.success(resJson.optBoolean("available", false))
                } else {
                    Result.failure(Exception(resJson.optString("error", "Failed to check username")))
                }
            },
        )
    }

    suspend fun searchUsers(query: String): Result<List<TuneslyUserSummary>> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext Result.success(emptyList())
        executeRequest(
            "/users/search?q=$clean",
            builder = { url -> Request.Builder().url(url).get().build() },
            parser = { resp, resJson ->
                if (resp.isSuccessful && resJson.optBoolean("success")) {
                    val arr = resJson.optJSONArray("users") ?: JSONArray()
                    val list = mutableListOf<TuneslyUserSummary>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            TuneslyUserSummary(
                                id = obj.optString("_id"),
                                username = obj.optString("username"),
                                avatar = obj.optString("avatar"),
                                bio = obj.optString("bio"),
                            ),
                        )
                    }
                    Result.success(list)
                } else {
                    Result.failure(Exception(resJson.optString("error", "Search failed")))
                }
            },
        )
    }

    suspend fun getUserProfile(username: String): Result<TuneslyPublicProfile> = withContext(Dispatchers.IO) {
        executeRequest(
            "/users/profile/${username.trim()}",
            builder = { url -> Request.Builder().url(url).get().build() },
            parser = { resp, resJson ->
                if (resp.isSuccessful && resJson.optBoolean("success")) {
                    val p = resJson.getJSONObject("profile")
                    val topArtistsArr = p.optJSONArray("topArtists") ?: JSONArray()
                    val artists = (0 until topArtistsArr.length()).map { topArtistsArr.getString(it) }

                    val recentArr = p.optJSONArray("recentTracks") ?: JSONArray()
                    val recent = (0 until recentArr.length()).map { i ->
                        val obj = recentArr.getJSONObject(i)
                        TuneslyCloudTrack(
                            trackId = obj.optString("trackId"),
                            title = obj.optString("title"),
                            artist = obj.optString("artist"),
                            artworkUrl = obj.optString("artworkUrl"),
                        )
                    }

                    val playArr = p.optJSONArray("playlists") ?: JSONArray()
                    val playlists = (0 until playArr.length()).map { i ->
                        val obj = playArr.getJSONObject(i)
                        TuneslyPublicPlaylist(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            description = obj.optString("description"),
                            trackCount = obj.optInt("trackCount", 0),
                        )
                    }

                    Result.success(
                        TuneslyPublicProfile(
                            userId = p.optString("userId"),
                            username = p.optString("username"),
                            avatar = p.optString("avatar"),
                            bio = p.optString("bio"),
                            totalPlayTimeSeconds = p.optLong("totalPlayTimeSeconds", 0),
                            memberSince = p.optString("memberSince"),
                            topArtists = artists,
                            recentTracks = recent,
                            playlists = playlists,
                            favoritesCount = p.optInt("favoritesCount", 0),
                        )
                    )
                } else {
                    Result.failure(Exception(resJson.optString("error", "User profile not found")))
                }
            },
        )
    }

    suspend fun getConversations(): Result<List<TuneslyConversation>> = withContext(Dispatchers.IO) {
        val token = AppSettings.tuneslyToken.value
        if (token.isBlank()) return@withContext Result.failure(Exception("Please log in to use chat"))
        executeRequest(
            "/messages/conversations",
            builder = { url ->
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
            },
            parser = { resp, resJson ->
                if (resp.isSuccessful && resJson.optBoolean("success")) {
                    val arr = resJson.optJSONArray("conversations") ?: JSONArray()
                    val list = mutableListOf<TuneslyConversation>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val userObj = obj.optJSONObject("user") ?: JSONObject()
                        list.add(
                            TuneslyConversation(
                                username = userObj.optString("username"),
                                avatar = userObj.optString("avatar"),
                                bio = userObj.optString("bio"),
                                lastMessage = obj.optString("lastMessage"),
                                lastMessageTime = obj.optString("lastMessageTime"),
                                hasTrack = obj.optBoolean("hasTrack", false),
                                unreadCount = obj.optInt("unreadCount", 0),
                            ),
                        )
                    }
                    Result.success(list)
                } else {
                    Result.failure(Exception(resJson.optString("error", "Failed to load chats")))
                }
            },
        )
    }

    suspend fun getChatThread(username: String): Result<List<TuneslyChatMessage>> = withContext(Dispatchers.IO) {
        val token = AppSettings.tuneslyToken.value
        if (token.isBlank()) return@withContext Result.failure(Exception("Please log in to view chat"))
        executeRequest(
            "/messages/thread/${username.trim()}",
            builder = { url ->
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
            },
            parser = { resp, resJson ->
                if (resp.isSuccessful && resJson.optBoolean("success")) {
                    val arr = resJson.optJSONArray("messages") ?: JSONArray()
                    val list = mutableListOf<TuneslyChatMessage>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val trackObj = obj.optJSONObject("trackAttachment")
                        val track = if (trackObj != null && trackObj.optString("trackId").isNotBlank()) {
                            TuneslyCloudTrack(
                                trackId = trackObj.optString("trackId"),
                                title = trackObj.optString("title"),
                                artist = trackObj.optString("artist"),
                                artworkUrl = trackObj.optString("artworkUrl"),
                            )
                        } else null

                        list.add(
                            TuneslyChatMessage(
                                id = obj.optString("id"),
                                isMine = obj.optBoolean("isMine", false),
                                text = obj.optString("text"),
                                trackAttachment = track,
                                read = obj.optBoolean("read", false),
                                createdAt = obj.optString("createdAt"),
                            ),
                        )
                    }
                    Result.success(list)
                } else {
                    Result.failure(Exception(resJson.optString("error", "Failed to load thread")))
                }
            },
        )
    }

    suspend fun sendMessage(
        recipientUsername: String,
        text: String,
        attachedTrack: TuneslyCloudTrack? = null,
    ): Result<TuneslyChatMessage> = withContext(Dispatchers.IO) {
        val token = AppSettings.tuneslyToken.value
        if (token.isBlank()) return@withContext Result.failure(Exception("Please log in to send message"))
        val json = JSONObject().apply {
            put("recipientUsername", recipientUsername.trim())
            put("text", text.trim())
            if (attachedTrack != null) {
                put("trackAttachment", JSONObject().apply {
                    put("trackId", attachedTrack.trackId)
                    put("title", attachedTrack.title)
                    put("artist", attachedTrack.artist)
                    put("artworkUrl", attachedTrack.artworkUrl)
                })
            }
        }

        executeRequest(
            "/messages/send",
            builder = { url ->
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .post(json.toString().toRequestBody(JSON_MEDIA))
                    .build()
            },
            parser = { resp, resJson ->
                if (resp.isSuccessful && resJson.optBoolean("success")) {
                    val msgObj = resJson.getJSONObject("message")
                    Result.success(
                        TuneslyChatMessage(
                            id = msgObj.optString("id"),
                            isMine = true,
                            text = msgObj.optString("text"),
                            trackAttachment = attachedTrack,
                            read = false,
                            createdAt = msgObj.optString("createdAt"),
                        ),
                    )
                } else {
                    Result.failure(Exception(resJson.optString("error", "Failed to send message")))
                }
            },
        )
    }
}
