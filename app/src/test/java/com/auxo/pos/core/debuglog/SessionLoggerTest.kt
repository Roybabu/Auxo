// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.debuglog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SessionLoggerTest {

    @get:Rule val folder = TemporaryFolder()

    private var at = Instant.parse("2026-10-22T01:19:05Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = at
    }

    private fun logger(dir: File = folder.root, maxFiles: Int = 30, maxFileBytes: Long = 20L * 1024 * 1024) =
        SessionLogger(dir, clock, header = listOf("Device test"), maxFiles = maxFiles, maxFileBytes = maxFileBytes)

    @Test
    fun aSession_isOneFileWithStartHeaderEventsAndEnd() {
        val log = logger()
        log.startSession("app opened")
        log.write("I", "Billing", "Sale saved")
        log.endSession("app closed")
        val file = log.files().single()
        assertTrue(file.name.startsWith("session-20261022-011905"))
        val lines = file.readLines()
        assertTrue(lines[0].endsWith("I/Session: SESSION START (app opened)"))
        assertEquals("2026-10-22 01:19:05.000 I/Session: SESSION START (app opened)", lines[0])
        assertTrue(lines[1].endsWith("Device test"))
        assertTrue(lines.any { it.endsWith("I/Billing: Sale saved") })
        assertTrue(lines.last().endsWith("SESSION END (app closed)"))
        assertFalse(log.isOpen)
    }

    @Test
    fun eachOpening_getsItsOwnFile_evenInTheSameSecond() {
        val log = logger()
        repeat(3) {
            log.startSession("app opened")
            log.endSession("app closed")
        }
        assertEquals(3, log.files().size)
    }

    @Test
    fun anError_savesItsStackTrace() {
        val log = logger()
        log.startSession("app opened")
        log.write("E", "FATAL", "Crash", IllegalStateException("boom"))
        log.endSession("crash")
        val text = log.files().single().readText()
        assertTrue(text.contains("E/FATAL: Crash"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
    }

    @Test
    fun linesWhileTheAppIsAway_goToTheBackgroundFile_notToASession() {
        val log = logger()
        log.write("W", "Backup", "Worker ran")
        assertTrue(File(folder.root, SessionLogger.BACKGROUND_FILE).readText().contains("W/Backup: Worker ran"))
        log.raw("phone line")
        log.startSession("app opened")
        log.endSession("app closed")
        assertFalse(log.files().first { it.name.startsWith("session-") }.readText().contains("phone line"))
    }

    @Test
    fun aFileLeftUnfinished_isMarkedAsKilledOrCrashedNextTime() {
        val first = logger()
        first.startSession("app opened") // never ended: the process died
        val second = logger()
        second.tidyUp()
        second.startSession("app opened")
        second.endSession("app closed")
        val files = second.files().filter { it.name.startsWith("session-") }
        assertEquals(2, files.size)
        assertEquals(1, files.count { it.readText().contains("SESSION END (unknown") })
    }

    @Test
    fun aFinishedFile_isLeftAlone() {
        val log = logger()
        log.startSession("app opened")
        log.endSession("app closed")
        val before = log.files().single().readText()
        logger().tidyUp()
        assertEquals(before, log.files().single().readText())
    }

    @Test
    fun onlyTheNewestFilesAreKept() {
        val log = logger(maxFiles = 3)
        repeat(6) { i ->
            at = at.plusSeconds(60)
            val before = log.files().toSet()
            log.startSession("app opened")
            log.endSession("app closed")
            (log.files().toSet() - before).single().setLastModified(at.toEpochMilli())
        }
        log.tidyUp()
        assertEquals(3, log.files().size)
    }

    @Test
    fun aHugeSession_stopsGrowingAndSaysSo() {
        val log = logger(maxFileBytes = 500)
        log.startSession("app opened")
        repeat(100) { log.write("I", "Spam", "a line that is long enough to fill the file $it") }
        log.endSession("app closed")
        val text = log.files().single().readText()
        assertTrue(text.contains("file is full"))
        assertTrue(text.trimEnd().endsWith(")") && text.contains("SESSION END"))
        assertTrue(text.length < 5_000)
    }
}
