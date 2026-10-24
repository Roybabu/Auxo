// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.auxo.pos.core.appearance.AppAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class AccentPaletteTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun distance(a: Color, b: Color): Double =
        sqrt(((a.red - b.red) * (a.red - b.red) + (a.green - b.green) * (a.green - b.green) + (a.blue - b.blue) * (a.blue - b.blue)).toDouble())

    @Test
    fun thereAreEightAccents_withUniqueIds() {
        assertEquals(8, AppAccent.entries.size)
        assertEquals(8, AppAccent.entries.map { it.id }.toSet().size)
    }

    @Test
    fun theEightSwatchesAreEasyToTellApart() {
        val swatches = AppAccent.entries.associateWith { AccentPalette.swatch(it) }
        for (a in AppAccent.entries) for (b in AppAccent.entries) {
            if (a.ordinal < b.ordinal) {
                assertTrue("$a and $b look too alike", distance(swatches.getValue(a), swatches.getValue(b)) > 0.2)
            }
        }
    }

    @Test
    fun textIsReadable_onEveryAccent_inLightAndDark() {
        for (accent in AppAccent.entries) for (dark in listOf(false, true)) {
            val c = AccentPalette.scheme(accent, dark)
            val where = "$accent dark=$dark"
            assertTrue("$where button text", contrast(c.onPrimary, c.primary) >= 4.5)
            assertTrue("$where selected text", contrast(c.onPrimaryContainer, c.primaryContainer) >= 7.0)
            assertTrue("$where secondary text", contrast(c.onSecondaryContainer, c.secondaryContainer) >= 7.0)
            assertTrue("$where body text", contrast(c.onSurface, c.surface) >= 12.0)
            assertTrue("$where page text", contrast(c.onBackground, c.background) >= 12.0)
            assertTrue("$where muted text", contrast(c.onSurfaceVariant, c.surfaceContainer) >= 4.5)
            assertTrue("$where accent on page", contrast(c.primary, c.background) >= 4.5)
            assertTrue("$where error text", contrast(c.onError, c.error) >= 4.5)
        }
    }

    @Test
    fun lightIsWhite_andDarkIsNearBlack() {
        for (accent in AppAccent.entries) {
            val light = AccentPalette.scheme(accent, dark = false)
            val dark = AccentPalette.scheme(accent, dark = true)
            assertEquals(Color.White, light.surface)
            assertTrue("$accent light page", light.background.luminance() > 0.9f)
            assertTrue("$accent dark page", dark.background.luminance() < 0.02f)
            assertTrue("$accent dark card", dark.surface.luminance() < 0.02f)
            assertTrue("$accent dark text", dark.onSurface.luminance() > 0.7f)
        }
    }

    @Test
    fun emerald_keepsTheOriginalLook() {
        val light = AccentPalette.scheme(AppAccent.EMERALD, dark = false)
        assertEquals(Emerald700, light.primary)
        assertEquals(BackgroundLight, light.background)
        val home = AccentPalette.home(AppAccent.EMERALD, dark = false)
        assertEquals(HomeGreen, home.header)
        assertEquals(HomeGreenDark, home.card)
        assertEquals(HomeAvatar, home.avatar)
    }

    @Test
    fun homeHeaderKeepsWhiteTextReadable_everywhere() {
        for (accent in AppAccent.entries) for (dark in listOf(false, true)) {
            val home = AccentPalette.home(accent, dark)
            assertTrue("$accent dark=$dark header", contrast(Color.White, home.header) >= 4.5)
            assertTrue("$accent dark=$dark card", contrast(Color.White, home.card) >= 4.5)
            assertTrue("$accent dark=$dark action icon", contrast(home.actionIcon, home.actionCircle) >= 3.0)
            assertTrue("$accent dark=$dark avatar icon", contrast(home.avatarIcon, home.avatar) >= 3.0)
        }
    }

    @Test
    fun statusColours_areReadableOnBlack() {
        val dark = AccentPalette.scheme(AppAccent.EMERALD, dark = true)
        for (tone in listOf(DarkStatusColors.success, DarkStatusColors.warning, DarkStatusColors.danger, DarkStatusColors.neutral)) {
            assertTrue(contrast(tone.content, tone.container) >= 7.0)
            assertTrue(contrast(tone.accent, dark.surface) >= 4.5)
        }
    }
}
