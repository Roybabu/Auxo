// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.theme

import androidx.compose.ui.graphics.Color
import com.auxo.pos.core.appearance.AppAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconColorsTest {

    @Test
    fun emerald_keepsTheOriginalIconColours() {
        val icons = AccentPalette.icons(AppAccent.EMERALD)
        assertEquals(Color(0xFF10A56F), icons.bright)
        assertEquals(Color(0xFF064E3B), icons.deep)
        assertEquals(Color(0xFFD9F3E7), icons.mint)
    }

    @Test
    fun everyAccent_hasItsOwnIconColours() {
        val brights = AppAccent.entries.map { AccentPalette.icons(it).bright }
        assertEquals(AppAccent.entries.size, brights.toSet().size)
    }

    @Test
    fun anotherAccent_changesTheTileAndTheGlyph() {
        val emerald = AccentPalette.icons(AppAccent.EMERALD)
        val rose = AccentPalette.icons(AppAccent.ROSE)
        assertNotEquals(emerald.bright, rose.bright)
        assertNotEquals(emerald.mint, rose.mint)
        assertNotEquals(emerald.darkTileTop, rose.darkTileTop)
        // Rose is a red hue: more red than green in its glyph.
        assertTrue(rose.bright.red > rose.bright.green)
    }
}
