// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.debuglog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DebugLogKeysTest {

    // Not a real key: made up for this test.
    private val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun aDatabaseKeyInALogLine_isHidden() {
        val line = "E SQLiteLog: (14) statement aborts at 4: [ATTACH DATABASE 'x' AS encrypted KEY '$key'] unable to open"
        val clean = DebugLog.hideKeys(line)
        assertFalse(clean.contains(key))
        assertEquals("E SQLiteLog: (14) statement aborts at 4: [ATTACH DATABASE 'x' AS encrypted KEY '<key hidden>'] unable to open", clean)
    }

    @Test
    fun aLineWithoutAKey_isUnchanged() {
        val line = "I/Screen: billing/pos total 20000 paise"
        assertEquals(line, DebugLog.hideKeys(line))
    }
}
