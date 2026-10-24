// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.imports

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportFileTest {

    @Test
    fun utf8Bytes_areReadAsUtf8() {
        assertEquals("Café ₹20", ImportFile.decode("Café ₹20".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun excelsWindows1252Bytes_areReadAsWindows1252() {
        // "Café" saved by Excel as "CSV (Comma delimited)": the é is the single byte 0xE9, which is not valid UTF-8.
        val bytes = byteArrayOf('C'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())
        assertEquals("Café", ImportFile.decode(bytes))
    }

    @Test
    fun plainAscii_isUnchanged() {
        assertEquals("name,price", ImportFile.decode("name,price".toByteArray()))
    }
}
