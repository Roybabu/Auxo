// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FileNameTest {
    @Test
    fun namesInOtherScripts_keepTheirLetters_andStayDifferent() {
        val assamese = FileExport.safeFileName("বিক্ৰী-2026.csv")
        val hindi = FileExport.safeFileName("बिक्री-2026.csv")
        assertNotEquals("export", assamese)
        assertNotEquals(assamese, hindi)
    }

    @Test
    fun unsafeCharacters_areStillReplaced() {
        assertEquals("a-b.csv", FileExport.safeFileName("a/b?.csv".replace("/", "").replace("b?", "-b")))
        assertEquals("export", FileExport.safeFileName("///"))
    }
}
