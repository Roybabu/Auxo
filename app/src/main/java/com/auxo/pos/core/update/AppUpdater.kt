// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A newer version published as a GitHub release with an APK attached. */
data class AvailableUpdate(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val percent: Int) : UpdateState
    data class Ready(val update: AvailableUpdate) : UpdateState
    data object Failed : UpdateState
}

/**
 * Checks the latest GitHub release of [repo] for a newer APK, downloads it and hands it to the system installer.
 * The phone checks that the new APK is signed with the same key as the installed app before it will install it.
 * Nothing is sent but the request for the release's public details.
 */
class AppUpdater(
    context: Context,
    private val scope: CoroutineScope,
    private val repo: String = REPO,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The prompt on launch is shown once per run of the app. */
    @Volatile private var launchCheckDone = false

    val currentVersion: String
        get() = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty()

    /** Looks for a newer release. [quiet] leaves the state alone when nothing newer is found or the check fails. */
    fun checkForUpdate(quiet: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        if (!quiet) _state.value = UpdateState.Checking
        scope.launch {
            val found = runCatching { latestRelease() }
            found.onSuccess { update ->
                when {
                    update != null && isNewer(update.version, currentVersion) -> _state.value = UpdateState.Available(update)
                    !quiet -> _state.value = UpdateState.UpToDate
                    else -> _state.value = UpdateState.Idle
                }
            }.onFailure { if (!quiet) _state.value = UpdateState.Failed else _state.value = UpdateState.Idle }
        }
    }

    /** The check made when the app opens; it runs once and says nothing unless a newer version exists. */
    fun checkOnLaunch() {
        if (launchCheckDone) return
        launchCheckDone = true
        checkForUpdate(quiet = true)
    }

    fun download() {
        val update = (_state.value as? UpdateState.Available)?.update ?: return
        _state.value = UpdateState.Downloading(update, 0)
        scope.launch {
            val done = runCatching { downloadApk(update) { _state.value = UpdateState.Downloading(update, it) } }
            _state.value = if (done.isSuccess) UpdateState.Ready(update) else UpdateState.Failed
        }
    }

    /** Hides the prompt for this run of the app. */
    fun dismiss() {
        if (_state.value is UpdateState.Available) _state.value = UpdateState.Idle
    }

    /** True when the phone still has to be told that this app may install apps. */
    fun needsInstallPermission(): Boolean = !appContext.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The system installer's screen for the downloaded APK, or null if nothing has been downloaded. */
    fun installIntent(): Intent? {
        val file = apkFile().takeIf { it.exists() } ?: return null
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun apkFile() = File(File(appContext.cacheDir, "updates"), "auxo-update.apk")

    private fun latestRelease(): AvailableUpdate? {
        val connection = open("https://api.github.com/repos/$repo/releases/latest")
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null // no release published yet
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            return parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(update: AvailableUpdate, onProgress: (Int) -> Unit) {
        val target = apkFile().also { it.parentFile?.mkdirs() }
        val connection = open(update.apkUrl)
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            var read = 0L
            var shown = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        val percent = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 99) else 0
                        if (percent != shown) {
                            shown = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            com.auxo.pos.core.debuglog.DebugLog.error("AppUpdater", "Failed and showed an error", e)
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun open(address: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
            instanceFollowRedirects = true
        }

    companion object {
        const val REPO = "Roybabu/Auxo"
        private const val APK_TYPE = "application/vnd.android.package-archive"
        private const val TIMEOUT_MILLIS = 15_000
        private const val BUFFER_BYTES = 16 * 1024

        /** The release's version, notes and first APK file, or null when the release carries no APK. */
        internal fun parseRelease(json: String): AvailableUpdate? {
            val release = JSONObject(json)
            val assets = release.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    return AvailableUpdate(
                        version = release.optString("tag_name").removePrefix("v").removePrefix("V"),
                        notes = release.optString("body").trim(),
                        apkUrl = asset.getString("browser_download_url"),
                        sizeBytes = asset.optLong("size"),
                    )
                }
            }
            return null
        }

        /** True when [candidate] (like "0.5.0") is a higher version than [current] (like "0.4.0"). */
        internal fun isNewer(candidate: String, current: String): Boolean {
            val a = numbers(candidate)
            val b = numbers(current)
            if (a.isEmpty()) return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun numbers(version: String): List<Int> =
            version.substringBefore('-').split('.').mapNotNull { it.trim().toIntOrNull() }
    }
}
