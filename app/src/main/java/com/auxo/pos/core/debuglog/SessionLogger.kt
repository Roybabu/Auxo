package com.auxo.pos.core.debuglog

import java.io.File
import java.io.RandomAccessFile
import java.io.Writer
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Writes one text file per time the app is open: the file starts when the app comes to the front and ends when it goes
 * away. Used only by debug builds (see [DebugLog]) so that a problem seen on a phone can be read afterwards.
 *
 * Nothing here ever throws: a logger that crashed the app would defeat its purpose.
 */
class SessionLogger(
    private val dir: File,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val header: List<String> = emptyList(),
    private val maxFiles: Int = 30,
    private val maxAgeDays: Long = 14,
    private val maxFileBytes: Long = 20L * 1024 * 1024,
) {
    private var out: Writer? = null
    private var written = 0L
    private var capped = false
    private var currentFile: File? = null

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    val isOpen: Boolean @Synchronized get() = out != null

    /** The log files, newest first. */
    fun files(): List<File> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: emptyArray())
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })

    /** Marks files left unfinished by an earlier run (the app was killed or crashed) and deletes the oldest. */
    @Synchronized
    fun tidyUp() {
        runCatching {
            dir.mkdirs()
            files().filter { it != currentFile && it.name.startsWith(SESSION_PREFIX) && !endsProperly(it) }.forEach {
                runCatching { it.appendText("${now()} W/Session: SESSION END (unknown: the app was killed or crashed; check for FATAL lines)\n") }
            }
            val cutoff = clock.millis() - maxAgeDays * 24 * 60 * 60 * 1000
            files().forEachIndexed { index, file ->
                if (file != currentFile && (index >= maxFiles || file.lastModified() < cutoff)) file.delete()
            }
        }
    }

    @Synchronized
    fun startSession(reason: String) {
        runCatching {
            if (out != null) return
            dir.mkdirs()
            val at = zoned()
            var file = File(dir, "$SESSION_PREFIX${fileStamp.format(at)}.log")
            var n = 1
            while (file.exists()) file = File(dir, "$SESSION_PREFIX${fileStamp.format(at)}-${n++}.log")
            currentFile = file
            out = file.bufferedWriter()
            written = 0
            capped = false
            line("I", "Session", "SESSION START ($reason)")
            header.forEach { line("I", "Session", it) }
        }
    }

    @Synchronized
    fun endSession(reason: String) {
        runCatching {
            if (out == null) return
            line("I", "Session", "SESSION END ($reason)", force = true)
            out?.close()
        }
        out = null
        currentFile = null
    }

    /** One line from the app. While no session is open (the app is in the background) it goes to background.log. */
    @Synchronized
    fun write(level: String, tag: String, message: String, error: Throwable? = null) {
        runCatching {
            if (out != null) {
                line(level, tag, message)
                if (error != null) error.stackTraceToString().lineSequence().forEach { raw("    $it") }
            } else {
                backgroundLog().appendText("${now()} $level/$tag: $message\n" + (error?.stackTraceToString()?.plus("\n") ?: ""))
            }
        }
    }

    /** A line already formatted elsewhere (the phone's own log); dropped when no session is open. */
    @Synchronized
    fun raw(text: String) {
        runCatching {
            val w = out ?: return
            if (capped) return
            if (written > maxFileBytes) {
                capped = true
                w.write("${now()} W/Session: file is full, further lines are not saved\n")
                w.flush()
                return
            }
            w.write(text)
            w.write("\n")
            w.flush()
            written += text.length + 1
        }
    }

    private fun line(level: String, tag: String, message: String, force: Boolean = false) {
        val text = "${now()} $level/$tag: $message"
        if (force) {
            out?.write(text + "\n")
            out?.flush()
        } else {
            raw(text)
        }
    }

    private fun backgroundLog(): File {
        dir.mkdirs()
        val file = File(dir, BACKGROUND_FILE)
        if (file.length() > 512 * 1024) file.writeText("") // keep it small: it only holds what happens while the app is away
        return file
    }

    private fun endsProperly(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { f ->
            val size = f.length()
            val start = maxOf(0L, size - 400)
            f.seek(start)
            val tail = ByteArray((size - start).toInt())
            f.readFully(tail)
            String(tail).trimEnd().lineSequence().lastOrNull().orEmpty().contains("SESSION END")
        }
    }.getOrDefault(true)

    private fun zoned() = Instant.ofEpochMilli(clock.millis()).atZone(clock.zone)
    private fun now(): String = stamp.format(zoned())

    companion object {
        const val SESSION_PREFIX = "session-"
        const val BACKGROUND_FILE = "background.log"
    }
}
