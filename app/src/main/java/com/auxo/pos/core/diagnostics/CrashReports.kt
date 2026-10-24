// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.diagnostics

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reports for every build, kept on the phone and sent only when the person chooses to.
 *
 * When the app crashes, a short text file is written to the app's own folder: the app and phone versions, the error with
 * where it happened, and the last screens visited. The next time the app opens it offers to share the file (see
 * CrashReportPrompt). More also has a button for a report without a crash. Nothing is ever sent on its own, and the
 * text is cleaned first (see [redact]) so it holds no customer, sales or shop details and no database key.
 */
object CrashReports {
    internal const val FOLDER = "crash-reports"
    private const val CRASH_PREFIX = "crash-"
    private const val DIAGNOSTIC_PREFIX = "diagnostic-"
    private const val KEEP = 5
    private const val TRAIL_SIZE = 20
    private const val FRAMES_PER_ERROR = 40
    private const val CAUSES = 5
    private const val MESSAGE_LIMIT = 300
    internal const val ISSUES_URL = "https://github.com/Roybabu/Auxo/issues/new"
    private const val PREFS = "crash_reports"
    private const val ASKED = "asked"

    private val trail = ArrayDeque<String>()
    @Volatile private var installed = false

    /** Starts catching crashes. Safe to call more than once. */
    fun install(app: Application) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Written on the spot, because the process is about to die. A failure here must never hide the real crash.
            runCatching {
                val folder = folder(app).apply { mkdirs() }
                val now = System.currentTimeMillis()
                File(folder, "$CRASH_PREFIX${fileStamp(now)}.txt").writeText(render(header(app), now, thread.name, error, recentScreens()))
                tidy(folder)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Remembers the screen just opened, so a report can say what the person was doing. Screen names only. */
    fun note(screen: String) {
        synchronized(trail) {
            trail.addLast("${clock(System.currentTimeMillis())} $screen")
            while (trail.size > TRAIL_SIZE) trail.removeFirst()
        }
    }

    /** The newest crash report the person has not been asked about yet, or null. */
    fun pending(context: Context): File? {
        val asked = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ASKED, null)
        return crashFiles(folder(context)).firstOrNull()?.takeIf { it.name != asked }
    }

    /** Records that the person was asked about [file], whether they sent it or not, so it is not offered again. */
    fun markAsked(context: Context, file: File) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ASKED, file.name).apply()
    }

    /** A share (chooser-ready) of one crash report. */
    fun shareIntent(context: Context, file: File): Intent? = share(context, file, "Auxo crash report")

    /** A report written now, for when something is wrong but nothing crashed: versions, recent screens, the last crash. */
    fun diagnosticShare(context: Context): Intent? = share(context, diagnosticFile(context), "Auxo diagnostic report")

    /**
     * Opens a new issue on the project's GitHub page, with the versions filled in, after copying the whole report to the
     * clipboard for the person to paste (it is too long for the web address). Issues there are public.
     */
    fun issueIntent(context: Context, report: File): Intent {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Auxo report", report.readText()))
        }
        val info = header(context).joinToString("\n")
        val body = "$info\n\nWhat happened:\n\n\nReport (paste it below):\n"
        val title = "Problem report: ${header(context).first().substringAfter("version ").substringBefore(" (")}"
        val url = "$ISSUES_URL?title=${Uri.encode(title)}&body=${Uri.encode(body)}"
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    /** A diagnostic report written to a file now (see [diagnosticShare]). */
    fun diagnosticFile(context: Context): File {
        val folder = folder(context).apply { mkdirs() }
        folder.listFiles { f -> f.name.startsWith(DIAGNOSTIC_PREFIX) }?.forEach { it.delete() }
        val now = System.currentTimeMillis()
        val file = File(folder, "$DIAGNOSTIC_PREFIX${fileStamp(now)}.txt")
        val lastCrash = crashFiles(folder).firstOrNull()?.let { runCatching { it.readText() }.getOrNull() }
        file.writeText(renderDiagnostic(header(context), now, recentScreens(), lastCrash))
        return file
    }

    private fun share(context: Context, file: File, subject: String): Intent? {
        val uri: Uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "$subject is attached.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal fun folder(context: Context): File = File(context.filesDir, FOLDER)

    private fun crashFiles(folder: File): List<File> =
        folder.listFiles { f -> f.name.startsWith(CRASH_PREFIX) }.orEmpty().sortedByDescending { it.name }

    /** Keeps only the newest few crash reports. */
    internal fun tidy(folder: File) {
        crashFiles(folder).drop(KEEP).forEach { it.delete() }
    }

    private fun recentScreens(): List<String> = synchronized(trail) { trail.toList() }

    private fun header(context: Context): List<String> {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() }
        return listOf(
            "App ${context.packageName} version ${info?.versionName} (code $code)",
            "Device ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
    }

    internal fun render(header: List<String>, time: Long, thread: String, error: Throwable, screens: List<String>): String = buildString {
        appendLine("Auxo crash report")
        header.forEach { appendLine(it) }
        appendLine("Time ${stamp(time)}")
        appendLine("Thread $thread")
        appendLine()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < CAUSES) {
            appendLine(if (depth == 0) "Error" else "Caused by")
            appendLine("  ${current.javaClass.name}${current.message?.let { ": " + redact(it) }.orEmpty()}")
            current.stackTrace.take(FRAMES_PER_ERROR).forEach { appendLine("    at $it") }
            current = current.cause
            depth++
        }
        appendLine()
        appendLine("Last screens (newest last)")
        if (screens.isEmpty()) appendLine("  none") else screens.forEach { appendLine("  $it") }
    }

    internal fun renderDiagnostic(header: List<String>, time: Long, screens: List<String>, lastCrash: String?): String = buildString {
        appendLine("Auxo diagnostic report")
        header.forEach { appendLine(it) }
        appendLine("Time ${stamp(time)}")
        appendLine("Language ${Locale.getDefault().toLanguageTag()}")
        appendLine()
        appendLine("Last screens (newest last)")
        if (screens.isEmpty()) appendLine("  none") else screens.forEach { appendLine("  $it") }
        appendLine()
        if (lastCrash == null) appendLine("No crash recorded on this phone.") else {
            appendLine("Most recent crash")
            appendLine(lastCrash)
        }
    }

    private val keyLike = Regex("[0-9a-fA-F]{64}")
    private val email = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val longNumber = Regex("\\d{6,}")

    /**
     * Cleans an error message before it is written: a database key, an email address or a long number (a phone number, an
     * account or bill number) is replaced, and the text is cut short. Messages from the phone's own code rarely hold any of
     * these, but a failed database statement can.
     */
    internal fun redact(text: String): String =
        text.replace(keyLike, "<key hidden>")
            .replace(email, "<email hidden>")
            .replace(longNumber, "<number hidden>")
            .replace('\n', ' ')
            .take(MESSAGE_LIMIT)

    private fun stamp(time: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(time))
    private fun fileStamp(time: Long) = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(time))
    private fun clock(time: Long) = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(time))
}
