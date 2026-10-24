// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.model.Language
import com.auxo.pos.ui.components.LanguageChipContent
import com.auxo.pos.ui.theme.AuxoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class LanguageChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var picked: Language? = Language.ENGLISH
    private var calls = 0

    private fun show(current: Language = Language.ENGLISH, followsPhone: Boolean = false) {
        composeRule.setContent {
            AuxoTheme { LanguageChipContent(current, followsPhone, onChange = { picked = it; calls++ }) }
        }
    }

    @Test
    fun theChip_showsTheLanguageNowInUse_inItsOwnScript() {
        show(Language.HINDI)
        composeRule.onNodeWithTag("language_chip").assertIsDisplayed()
        composeRule.onNodeWithText("हिन्दी").assertIsDisplayed()
    }

    @Test
    fun tappingTheChip_listsEveryLanguage_andThePhoneChoice() {
        show()
        composeRule.onNodeWithTag("language_chip").performClick()
        Language.entries.forEach { composeRule.onNodeWithTag("language_option_${it.tag}").assertIsDisplayed() }
        composeRule.onNodeWithTag("language_option_phone").assertIsDisplayed()
    }

    @Test
    fun choosingALanguage_reportsIt() {
        show()
        composeRule.onNodeWithTag("language_chip").performClick()
        composeRule.onNodeWithTag("language_option_bn").performClick()
        assertEquals(Language.BENGALI, picked)
        assertEquals(1, calls)
    }

    @Test
    fun choosingSameAsThePhone_reportsNull() {
        show()
        composeRule.onNodeWithTag("language_chip").performClick()
        composeRule.onNodeWithTag("language_option_phone").performClick()
        assertNull(picked)
        assertEquals(1, calls)
    }

    @Test
    fun choosingTheLanguageAlreadyInUse_changesNothing() {
        show(Language.ENGLISH, followsPhone = false)
        composeRule.onNodeWithTag("language_chip").performClick()
        composeRule.onNodeWithTag("language_option_en").performClick()
        assertEquals(0, calls)
    }
}
