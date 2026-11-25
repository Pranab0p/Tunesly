package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

data class RemoteConfig(
    val dynamicIslandGlobalEnabled: Boolean = true,
    val announcementEnabled: Boolean = false,
    val announcementTitle: String = "",
    val announcementMessage: String = "",
    val announcementActionUrl: String = "",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "",
    val updatedAt: String = "",
)

/**
 * Fetches server-driven feature flags and live announcement banners from
 * `https://tunesly.online/api/app/config` without requiring an APK update.
 */
object RemoteConfigManager {

    private const val PREFS_NAME = "tunesly_remote_config"
    private const val KEY_DISMISSED_AT = "dismissed_announcement_at"
    private const val KEY_CACHED_JSON = "cached_config_json"

    private val ENDPOINTS = listOf(
        "https://tunesly.online/api/app/config",
        "http://194.127.179.229:5050/api/app/config",
    )

    private val json = Json { ignoreUnknownKeys = true }

    private var prefs: SharedPreferences? = null

    private val _config = MutableStateFlow(RemoteConfig())
    val config = _config.asStateFlow()

    private val _dismissedAt = MutableStateFlow("")
    val dismissedAt = _dismissedAt.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _dismissedAt.value = p.getString(KEY_DISMISSED_AT, "").orEmpty()
        p.getString(KEY_CACHED_JSON, null)?.let { cached ->
            parseConfig(cached)?.let { _config.value = it }
        }
    }

    fun dismissAnnouncement(updatedAt: String) {
        val stamp = updatedAt.ifBlank { _config.value.announcementMessage }
        _dismissedAt.value = stamp
        prefs?.edit()?.putString(KEY_DISMISSED_AT, stamp)?.apply()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        for (url in ENDPOINTS) {
            try {
                val req = Request.Builder().url(url).build()
                val body = Http.client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                if (!body.isNullOrBlank()) {
                    val parsed = parseConfig(body)
                    if (parsed != null) {
                        _config.value = parsed
                        prefs?.edit()?.putString(KEY_CACHED_JSON, body)?.apply()
                        return@withContext
                    }
                }
            } catch (_: Throwable) {
                // Try fallback endpoint
            }
        }
    }

    private fun parseConfig(raw: String): RemoteConfig? {
        return try {
            val obj = json.parseToJsonElement(raw) as? JsonObject ?: return null
            RemoteConfig(
                dynamicIslandGlobalEnabled = obj["dynamicIslandGlobalEnabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                announcementEnabled = obj["announcementEnabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                announcementTitle = obj["announcementTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                announcementMessage = obj["announcementMessage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                announcementActionUrl = obj["announcementActionUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                maintenanceMode = obj["maintenanceMode"]?.jsonPrimitive?.booleanOrNull ?: false,
                maintenanceMessage = obj["maintenanceMessage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        } catch (_: Throwable) {
            null
        }
    }
}
