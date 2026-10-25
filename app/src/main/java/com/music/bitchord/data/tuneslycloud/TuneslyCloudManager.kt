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
}
