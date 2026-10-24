// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportsTest {

    private val header = listOf("App com.auxo.pos version 0.4.0 (code 4)", "Device Test Phone, Android 15 (API 35)")

    @Test
    fun redact_hidesKeysEmailsAndLongNumbers_andKeepsOrdinaryText() {
        val key = "0123456789abcdef".repeat(4)
        val text = redactSample(key)
        assertFalse(text.contains(key))
        assertFalse(text.contains("owner@example.com"))
        assertFalse(text.contains("9876543210"))
        assertTrue(text.contains("<key hidden>"))
        assertTrue(text.contains("no such column: price"))
        assertTrue(text.contains("row 42"))
    }

    private fun redactSample(key: String) =
        CrashReports.redact("no such column: price, row 42, key $key, mail owner@example.com, phone 9876543210")

    @Test
    fun redact_cutsLongMessages_andJoinsLines() {
        val out = CrashReports.redact("z".repeat(1000) + "\nb")
        assertEquals(300, out.length)
        assertFalse(out.contains('\n'))
    }

    @Test
    fun render_hasVersions_error_causes_andScreens() {
        val error = IllegalStateException("outer", IllegalArgumentException("inner for 9876543210"))
        val text = CrashReports.render(header, 1_700_000_000_000, "main", error, listOf("10:00:01 home", "10:00:05 billing/pos"))
        assertTrue(text.startsWith("Auxo crash report"))
        assertTrue(text.contains("version 0.4.0 (code 4)"))
        assertTrue(text.contains("Thread main"))
        assertTrue(text.contains("java.lang.IllegalStateException: outer"))
        assertTrue(text.contains("Caused by"))
        assertTrue(text.contains("java.lang.IllegalArgumentException: inner for <number hidden>"))
        assertTrue(text.contains("10:00:05 billing/pos"))
        assertFalse(text.contains("9876543210"))
    }

    @Test
    fun render_withNoScreens_saysNone() {
        val text = CrashReports.render(header, 0, "main", RuntimeException("x"), emptyList())
        assertTrue(text.contains("  none"))
    }

    @Test
    fun renderDiagnostic_includesTheLastCrash_orSaysThereWasNone() {
        val with = CrashReports.renderDiagnostic(header, 0, listOf("10:00:01 home"), "Auxo crash report\nError")
        assertTrue(with.contains("Most recent crash"))
        assertTrue(with.contains("Auxo crash report"))
        val without = CrashReports.renderDiagnostic(header, 0, emptyList(), null)
        assertTrue(without.contains("No crash recorded on this phone."))
    }

    @Test
    fun tidy_keepsOnlyTheNewestFiveCrashReports() {
        val dir: File = Files.createTempDirectory("crash").toFile()
        try {
            (1..8).forEach { File(dir, "crash-2026102$it-000000.txt").writeText("x") }
            File(dir, "diagnostic-1.txt").writeText("x")
            CrashReports.tidy(dir)
            val names = dir.list().orEmpty().sorted()
            assertEquals(5, names.count { it.startsWith("crash-") })
            assertTrue("crash-20261028-000000.txt" in names)
            assertFalse("crash-20261023-000000.txt" in names)
            assertTrue("diagnostic-1.txt" in names)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun issueBody_putsTheWholeShortReportInTheIssue() {
        val body = CrashReports.issueBody("App x version 1", "Auxo crash report\nError boom")
        assertTrue(body.contains("App x version 1"))
        assertTrue(body.contains("Error boom"))
        assertFalse(body.contains("shortened"))
    }

    @Test
    fun issueBody_cutsALongReportToFitAWebAddress_andSaysSo() {
        val long = (1..2000).joinToString("\n") { "    at com.auxo.pos.Thing$it.run(Thing.kt:$it)" }
        val body = CrashReports.issueBody("App x version 1", long)
        assertTrue(java.net.URLEncoder.encode(body, "UTF-8").length <= CrashReports.ISSUE_BODY_LIMIT)
        assertTrue(body.contains("shortened"))
        assertTrue(body.contains("at com.auxo.pos.Thing1.run"))
    }
}
