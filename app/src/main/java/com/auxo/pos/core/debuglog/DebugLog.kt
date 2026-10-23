// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.debuglog

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

/**
 * Debug-only recording of what the app does, so a bug seen on a phone can be fixed later.
 *
 * It does nothing in a release build. In a debug build it writes a file for each time the app is opened (from coming
 * to the front until it goes away), holding the app's own events, crashes with their stack traces, and the phone's log
 * lines for this app. The files are in the app's folder on the phone (Android/data/com.auxo.pos/files/debug-logs) and
 * can be shared from More. Customer names, phone numbers and the like are not written.
 */
object DebugLog {
    private const val FOLDER = "debug-logs"
    private const val SHARE_LIMIT = 10

    @Volatile private var logger: SessionLogger? = null
    private var installedFor: Application? = null
    private var started = 0
    private var readerStarted = false
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "debug-log").apply { isDaemon = true } }

    /** True only in a debug build once [install] has run. */
    val isEnabled: Boolean get() = logger != null

    fun folder(context: Context): File = context.getExternalFilesDir(FOLDER) ?: File(context.filesDir, FOLDER)

    fun install(app: Application) {
        if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (installedFor === app) return
        runCatching {
            installedFor = app
            started = 0
            val log = SessionLogger(folder(app), header = header(app))
            log.tidyUp()
            logger = log
            app.registerActivityLifecycleCallbacks(Lifecycle)
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                // Written on the spot, because the process is about to die.
                runCatching {
                    log.write("E", "FATAL", "Crash on thread ${thread.name}", error)
                    log.endSession("crash")
                }
                previous?.uncaughtException(thread, error)
            }
        }
    }

    fun event(tag: String, message: String) = write("I", tag, message, null)

    fun warn(tag: String, message: String) = write("W", tag, message, null)

    fun error(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val log = logger ?: return
        worker.execute { log.write(level, tag, message, error) }
    }

    /** A chooser-ready share of the newest log files, or null when there is nothing to send. */
    fun shareIntent(context: Context): Intent? {
        val log = logger ?: return null
        val files = log.files().take(SHARE_LIMIT)
        if (files.isEmpty()) return null
        val uris = runCatching {
            files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
            putExtra(Intent.EXTRA_SUBJECT, "Auxo debug logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun header(app: Application): List<String> {
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
        return listOf(
            "App ${app.packageName} version $version (debug build)",
            "Device ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Process ${Process.myPid()}",
        )
    }

    /** The app is open from the first screen coming up to the last one going away; turning the phone does not count. */
    private object Lifecycle : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            started++
            val log = logger ?: return
            if (!log.isOpen) {
                worker.execute {
                    log.startSession("app opened")
                    startPhoneLogReader(log)
                }
            }
        }

        override fun onActivityStopped(activity: Activity) {
            started = (started - 1).coerceAtLeast(0)
            val log = logger ?: return
            if (started == 0 && !activity.isChangingConfigurations) {
                worker.execute { log.endSession("app closed or sent to the background") }
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            event("Lifecycle", "${activity.javaClass.simpleName} created${if (savedInstanceState != null) " (restored)" else ""}")
        }

        override fun onActivityDestroyed(activity: Activity) {
            event("Lifecycle", "${activity.javaClass.simpleName} destroyed${if (activity.isChangingConfigurations) " (turning)" else ""}")
        }

        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    private val keyLike = Regex("[0-9a-fA-F]{64}")

    /** SQLite logs the text of a failed statement, which can hold the database key. Never write a key into a log file. */
    private fun hideKeys(line: String): String = keyLike.replace(line, "<key hidden>")

    /** Copies the phone's log lines for this app into the open file, from the start of the process onward. */
    private fun startPhoneLogReader(log: SessionLogger) {
        if (readerStarted) return
        readerStarted = true
        Thread({
            runCatching {
                val process = ProcessBuilder("logcat", "-v", "threadtime", "--pid=${Process.myPid()}")
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach { log.raw(hideKeys(it)) } }
            }.onFailure { log.write("W", "DebugLog", "The phone's log could not be read: ${it.message}") }
        }, "debug-log-reader").apply { isDaemon = true }.start()
    }
}
