package com.music.bitchord.data.tuneslycloud

import com.music.bitchord.data.Http
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    private const val BASE_URL = "http://194.127.179.229:5050/api"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    val isLoggedIn: Boolean
        get() = AppSettings.tuneslyToken.value.isNotBlank()

    suspend fun register(username: String, email: String, password: String): Result<TuneslyUser> =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("username", username.trim())
                    put("email", email.trim())
                    put("password", password)
                }
                val req = Request.Builder()
                    .url("$BASE_URL/auth/register")
                    .post(json.toString().toRequestBody(JSON_MEDIA))
                    .build()

                Http.client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val resJson = JSONObject(body)
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
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun login(login: String, password: String): Result<TuneslyUser> =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("login", login.trim())
                    put("password", password)
                }
                val req = Request.Builder()
                    .url("$BASE_URL/auth/login")
                    .post(json.toString().toRequestBody(JSON_MEDIA))
                    .build()

                Http.client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val resJson = JSONObject(body)
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
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
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
            val req = Request.Builder()
                .url("$BASE_URL/history/log")
                .addHeader("Authorization", "Bearer $token")
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            Http.client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
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
        try {
            val json = JSONObject().apply {
                put("trackId", trackId)
                put("title", title)
                put("artist", artist)
                put("durationSeconds", durationSeconds)
                put("artworkUrl", artworkUrl)
            }
            val req = Request.Builder()
                .url("$BASE_URL/library/favorite")
                .addHeader("Authorization", "Bearer $token")
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            Http.client.newCall(req).execute().use { resp ->
                val resJson = JSONObject(resp.body?.string().orEmpty())
                if (resp.isSuccessful && resJson.optBoolean("success")) {
                    Result.success(resJson.optBoolean("isFavorite", false))
                } else {
                    Result.failure(Exception(resJson.optString("error", "Failed to update favorite")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getForYouRecommendations(limit: Int = 20): Result<List<TuneslyCloudTrack>> =
        withContext(Dispatchers.IO) {
            val token = AppSettings.tuneslyToken.value
            if (token.isBlank()) return@withContext Result.failure(Exception("Not logged in"))
            try {
                val req = Request.Builder()
                    .url("$BASE_URL/recommendations/for-you?limit=$limit")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                Http.client.newCall(req).execute().use { resp ->
                    val resJson = JSONObject(resp.body?.string().orEmpty())
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
                                )
                            )
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception(resJson.optString("error", "Failed to fetch recommendations")))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getTrendingRecommendations(limit: Int = 20): Result<List<TuneslyCloudTrack>> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$BASE_URL/recommendations/trending?limit=$limit")
                    .get()
                    .build()

                Http.client.newCall(req).execute().use { resp ->
                    val resJson = JSONObject(resp.body?.string().orEmpty())
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
                                )
                            )
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception(resJson.optString("error", "Failed to fetch trending")))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
