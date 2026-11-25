package com.music.bitchord.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.music.bitchord.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File

/**
 * BitChord ships as a sideloaded APK off GitHub Releases rather than through
 * a store, so there's nothing to push an update notice on its own — this
 * polls the repo's "latest release" once per launch and compares its tag
 * against the running build.
 *
 * The update itself is also handled here: the release's `.apk` asset is
 * downloaded into the app's cache and handed to the system package installer,
 * so the whole round trip stays inside the app instead of bouncing out to a
 * browser.
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?,
        /** The release's own Markdown body, shown as this update's "what's new". */
        val notes: String?,
    )

    private const val CACHE_SUBDIR = "updates"

    private val UPDATE_ENDPOINTS = listOf(
        "https://tunesly.online/api/app/version",
                "http://194.127.179.229:5050/api/app/version",
    )

    private const val GITHUB_RELEASE_URL =
        "https://api.github.com/repos/kushagrasinghx/BitChord/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    /** Where this update's APK download currently stands, for the dialog's progress row. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float) : DownloadState
        data class Ready(val file: File) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()

    /** Set from the UI thread when the user cancels; polled between network reads. */
    @Volatile
    private var downloadCancelled = false

    suspend fun check() = withContext(Dispatchers.IO) {
        // 1. Query Tunesly Cloud VPS endpoints (authoritative)
        for (endpoint in UPDATE_ENDPOINTS) {
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                    .build()
                val body = Http.client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                if (body != null) {
                    val obj = json.parseToJsonElement(body) as? JsonObject
                    if (obj != null) {
                        val ver = obj["version"]?.jsonPrimitive?.contentOrNull?.removePrefix("v")
                        val isArm64 = Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }
                        val arm64Apk = obj["apkArm64Url"]?.jsonPrimitive?.contentOrNull
                        val universalApk = obj["apkUniversalUrl"]?.jsonPrimitive?.contentOrNull
                        val defaultApk = obj["apkUrl"]?.jsonPrimitive?.contentOrNull
                        val apk = if (isArm64 && !arm64Apk.isNullOrBlank()) {
                            arm64Apk
                        } else if (!universalApk.isNullOrBlank()) {
                            universalApk
                        } else {
                            defaultApk
                        }
                        val relUrl = obj["releaseUrl"]?.jsonPrimitive?.contentOrNull ?: "https://tunesly.online"
                        val notes = obj["notes"]?.jsonPrimitive?.contentOrNull
                        if (ver != null) {
                            _available.value = if (isNewer(ver, BuildConfig.VERSION_NAME)) {
                                UpdateInfo(ver, relUrl, apk, notes)
                            } else {
                                null
                            }
                            return@withContext
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Wipes any APK left over from a previous run. Called once at cold start
     * so a downloaded update is only ever "Install Now" for the session that
     * downloaded it — the next launch starts clean rather than trying to work
     * out whether a leftover file is still good.
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach { it.delete() }
    }

    /**
     * One release carries the phone app and the TV app, each as a universal
     * APK plus per-ABI splits:
     *
     *     BitChord-v1.8.apk     BitChord-v1.8_arm64-v8a.apk     ...
     *     BitChordTV-v1.8.apk   BitChordTV-v1.8_arm64-v8a.apk   ...
     *
     * so this app's APKs are the ones named with its own
     * [BuildConfig.UPDATE_APK_PREFIX] — "BitChord-" never matches a TV file,
     * since the prefix ends at the hyphen. The universal build (no `_ABI`
     * suffix) is taken first, as it installs anywhere; failing that, the split
     * for this device's ABI. A release with neither leaves
     * [UpdateInfo.apkUrl] null and the UI falls back to opening the releases
     * page.
     */
    private fun apkAssetUrl(release: JsonObject): String? = runCatching {
        val apks = release["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val ours = name.startsWith(BuildConfig.UPDATE_APK_PREFIX, ignoreCase = true) &&
                    name.endsWith(".apk", ignoreCase = true) &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
                if (ours) name.removeSuffix(".apk").removeSuffix(".APK") to url else null
            }
            .orEmpty()
        apks.firstOrNull { (name, _) -> '_' !in name }?.second
            ?: Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
                apks.firstOrNull { (name, _) -> name.endsWith("_$abi") }?.second
            }
    }.getOrNull()

    /**
     * Streams the current update's APK into the app cache, reporting progress
     * through [download]. A finished file survives a cancelled dialog: until
     * the state is reset, "Install Now" comes straight back without a second
     * download.
     */
    suspend fun downloadApk(context: Context): Unit = withContext(Dispatchers.IO) {
        val info = _available.value ?: return@withContext
        val url = info.apkUrl ?: return@withContext
        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f)

        runCatching {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            // Clean up any stale partial or complete files
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "bitchord-${info.version}.apk")
            val tempFile = File(dir, "bitchord-${info.version}.part")
            if (tempFile.exists()) tempFile.delete()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Tunesly-Android/${BuildConfig.VERSION_NAME}")
                .header("Accept-Encoding", "identity")
                .build()
            Http.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Empty download body")
                val total = body.contentLength().takeIf { it > 0 }
                var readTotal = 0L

                body.byteStream().buffered(256 * 1024).use { input ->
                    tempFile.outputStream().buffered(256 * 1024).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var lastEmitTime = 0L
                        var lastPercent = -1
                        while (true) {
                            if (downloadCancelled) {
                                tempFile.delete()
                                _download.value = DownloadState.Idle
                                return@withContext
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            readTotal += read

                            val now = System.currentTimeMillis()
                            val percent = if (total != null && total > 0) ((readTotal * 100) / total).toInt() else -1
                            if (percent != lastPercent && (now - lastEmitTime >= 120 || readTotal == total)) {
                                lastPercent = percent
                                lastEmitTime = now
                                total?.let {
                                    _download.value =
                                        DownloadState.Downloading((readTotal.toFloat() / it).coerceIn(0f, 1f))
                                }
                            }
                        }
                        output.flush()
                    }
                }
                if (total != null && total > 0L && readTotal < total) {
                    tempFile.delete()
                    error("Download incomplete: $readTotal of $total bytes received")
                }
            }

            // Verify downloaded APK file integrity BEFORE marking ready
            if (!tempFile.exists() || tempFile.length() < 10L * 1024 * 1024) {
                tempFile.delete()
                error("Downloaded update file is too small or incomplete (${tempFile.length() / (1024 * 1024)} MB). Tap below to retry.")
            }

            val archiveInfo = context.packageManager.getPackageArchiveInfo(tempFile.absolutePath, 0)
            if (archiveInfo == null) {
                tempFile.delete()
                error("Package verification failed: Downloaded update is corrupted. Please tap below to retry.")
            }
            if (archiveInfo.packageName != context.packageName) {
                tempFile.delete()
                error("Package mismatch: Expected ${context.packageName} but found ${archiveInfo.packageName}.")
            }

            // Atomic rename from .part to .apk
            if (target.exists()) target.delete()
            val renamed = tempFile.renameTo(target)
            if (!renamed) {
                tempFile.copyTo(target, overwrite = true)
                tempFile.delete()
            }

            _download.value = DownloadState.Ready(target)
        }.onFailure { error ->
            _download.value = if (downloadCancelled) {
                DownloadState.Idle
            } else {
                DownloadState.Failed(error.message ?: "Download failed. Tap to retry.")
            }
        }
    }

    /** Stops an in-flight download; the next read loop sees this and bails. */
    fun cancelDownload() {
        downloadCancelled = true
    }

    /** Back to square one after a failure, so the dialog offers Download again. */
    fun resetDownload() {
        _download.value = DownloadState.Idle
    }

    /**
     * Hands a downloaded APK to the system installer.
     *
     * Validates package integrity first. If corrupted or invalid, removes it
     * immediately and resets state to Failed so the user can re-download.
     * Also explicitly grants URI permissions to OEM package installers (ColorOS, MIUI, OneUI).
     */
    fun installApk(context: Context, file: File) {
        // 1. Sanity check: Ensure file exists and is substantial (> 10MB)
        if (!file.exists() || file.length() < 10L * 1024 * 1024) {
            file.delete()
            _download.value = DownloadState.Failed("Update file was incomplete or corrupted. Tap below to re-download.")
            Toast.makeText(context, "Update file was corrupted. Tap to re-download.", Toast.LENGTH_LONG).show()
            return
        }

        // 2. Deep verification: parse package archive
        val archiveInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (archiveInfo == null || archiveInfo.packageName != context.packageName) {
            file.delete()
            _download.value = DownloadState.Failed("Update package verification failed. Tap below to re-download.")
            Toast.makeText(context, "Update package appears invalid. Tap to re-download.", Toast.LENGTH_LONG).show()
            return
        }

        // 3. Permission for sideloading unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.recoverCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            if (opened) return
        }

        // 4. FileProvider URI and Intent configuration
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }

        // 5. Explicitly grant read permissions across OEM process sandbox boundaries (ColorOS/MIUI/OneUI)
        val knownInstallerPackages = listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.coloros.packageinstaller",
            "com.oplus.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.vivo.packageinstaller",
            "com.oppo.market"
        )
        for (pkg in knownInstallerPackages) {
            runCatching {
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(installIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(installIntent, 0)
        }
        for (resolveInfo in resolveList) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            runCatching {
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        runCatching {
            context.startActivity(installIntent)
        }.onFailure { err ->
            Toast.makeText(context, "Could not start installer: ${err.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** A version split into its numeric dotted parts and whether it carries a "-suffix" (e.g. "-beta2"). */
    private data class ParsedVersion(val parts: List<Int>, val isPreRelease: Boolean)

    private fun parseVersion(raw: String): ParsedVersion {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        return ParsedVersion(base.split(".").map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /**
     * Numeric, dot-separated comparison — "1.10" outranks "1.9" — with one
     * extra rule: a "-betaN" build (see the debug build type's
     * `versionNameSuffix` in app/build.gradle.kts) is treated as older than a
     * plain release of the same numbers, since the beta by definition predates
     * the tag it was testing toward. Without this, a beta and the release it
     * matches compare equal and testers never get nudged onto the real build.
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.isPreRelease && !l.isPreRelease
    }
}
