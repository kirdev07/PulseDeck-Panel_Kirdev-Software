package com.kirdev.pulsedeckpanel.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** A GitHub release with an APK attached. */
data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val fraction: Float) : UpdateState
    /** The APK is on the phone; Android asks the user to confirm the install. */
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Updates from GitHub Releases of the panel repository: the newest release whose tag
 * (v1.0.1) is above the installed versionName and which has an .apk asset.
 * Android installs it only if it is signed with the same key, so a foreign APK cannot
 * replace the app. Nothing is installed without the user's confirmation.
 */
class Updater(private val context: Context, private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    val current: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    /**
     * Every time the app comes on screen (at most once per [AUTO_HOURS]): a new version is
     * downloaded right away and Android's install prompt follows. A manual check always runs.
     */
    fun checkAutomatically() {
        val busy = _state.value.let { it is UpdateState.Downloading || it is UpdateState.Ready || it is UpdateState.Checking }
        if (busy) return
        val last = prefs.getLong(LAST_CHECK, 0)
        if (System.currentTimeMillis() - last >= AUTO_HOURS * 3_600_000L) check(download = true)
    }

    fun check(download: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            val result = try {
                val release = latest()
                prefs.edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
                if (release != null && isNewer(release.version, current)) UpdateState.Available(release)
                else UpdateState.UpToDate(current)
            } catch (e: Exception) {
                UpdateState.Failed("Не удалось проверить обновления: нет интернета или GitHub недоступен.")
            }
            _state.value = result
            if (download && result is UpdateState.Available) scope.launch { download(result.release) }
        }
    }

    fun download(release: Release) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            _state.value = try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val file = File(dir, "PulseDeck-Panel-${release.version}.apk")
                withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val body = response.body ?: error("empty")
                        val total = body.contentLength().takeIf { it > 0 } ?: release.size
                        body.byteStream().use { input ->
                            file.outputStream().use { out ->
                                val buffer = ByteArray(64 * 1024)
                                var done = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    out.write(buffer, 0, read)
                                    done += read
                                    if (total > 0) _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                UpdateState.Ready(release, file).also { install(it.file) }
            } catch (e: Exception) {
                UpdateState.Failed("Не удалось скачать обновление. Попробуйте ещё раз.")
            }
        }
    }

    /** Android's own installer screen; first time the user allows installs from this app. */
    fun install(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null           // no releases yet
            if (!response.isSuccessful) error("HTTP ${response.code}")
            parse(response.body!!.string())
        }
    }

    companion object {
        const val REPO = "kirdev07/PulseDeck-Panel_Kirdev-Software"
        private const val LAST_CHECK = "last_check"
        private const val AUTO_HOURS = 1

        fun parse(json: String): Release? {
            val data = JSONObject(json)
            if (data.optBoolean("draft") || data.optBoolean("prerelease")) return null
            val assets = data.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: return null
            return Release(data.optString("tag_name").removePrefix("v").removePrefix("V"), plain(data.optString("body")),
                apk.optString("browser_download_url"), apk.optLong("size"))
        }

        /** Release notes as plain text: GitHub markdown marks (`**`, `#`, `- `, links) removed. */
        fun plain(markdown: String): String = markdown.lines().joinToString("\n") { line ->
            line.trimEnd()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^\\s*[-*+]\\s+"), "• ")
                .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("(\\*\\*|__|`)"), "")
        }.replace(Regex("\n{3,}"), "\n\n").trim()

        /** «1.0.10» > «1.0.9»; missing parts count as 0 («1.0» == «1.0.0»). */
        fun isNewer(candidate: String, installed: String): Boolean {
            val a = candidate.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val b = installed.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
