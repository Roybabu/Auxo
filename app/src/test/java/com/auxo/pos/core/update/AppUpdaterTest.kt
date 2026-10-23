// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdaterTest {

    @Test
    fun `a higher version is newer`() {
        assertTrue(AppUpdater.isNewer("0.5.0", "0.4.0"))
        assertTrue(AppUpdater.isNewer("1.0", "0.9.9"))
        assertTrue(AppUpdater.isNewer("0.4.1", "0.4.0"))
        assertTrue(AppUpdater.isNewer("0.10.0", "0.9.0"))
    }

    @Test
    fun `the same or an older version is not newer`() {
        assertFalse(AppUpdater.isNewer("0.4.0", "0.4.0"))
        assertFalse(AppUpdater.isNewer("0.3.9", "0.4.0"))
        assertFalse(AppUpdater.isNewer("0.4", "0.4.0"))
        assertFalse(AppUpdater.isNewer("", "0.4.0"))
    }

    @Test
    fun `a release with an apk gives its version, notes and download link`() {
        val json = """
            {"tag_name":"v0.5.0","body":" Faster billing ","assets":[
              {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":1},
              {"name":"auxo-0.5.0.apk","browser_download_url":"https://x/auxo.apk","size":1234}]}
        """.trimIndent()
        assertEquals(AvailableUpdate("0.5.0", "Faster billing", "https://x/auxo.apk", 1234), AppUpdater.parseRelease(json))
    }

    @Test
    fun `a release without an apk gives nothing`() {
        assertNull(AppUpdater.parseRelease("""{"tag_name":"v0.5.0","assets":[]}"""))
        assertNull(AppUpdater.parseRelease("""{"tag_name":"v0.5.0"}"""))
    }
}
