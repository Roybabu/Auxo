package com.auxo.pos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.appearance.AppAccent
import com.auxo.pos.core.appearance.AppearanceSettings
import com.auxo.pos.ui.screens.more.AppearanceContent
import com.auxo.pos.ui.screens.more.MoreActions
import com.auxo.pos.ui.screens.more.MoreScreen
import com.auxo.pos.ui.theme.AuxoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class AppearanceScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun morePage_hasOneAppearanceRow_andNoInlineControls() {
        var opened = 0
        composeRule.setContent {
            AuxoTheme {
                MoreScreen(
                    actions = object : MoreActions {
                        override fun onOpenAppearance() {
                            opened++
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasTestTag("more_appearance"))
        composeRule.onAllNodesWithText("Appearance").assertCountEquals(1)
        composeRule.onNodeWithText("Theme and app colors").assertIsDisplayed()
        composeRule.onNodeWithTag("dark_mode_switch").assertDoesNotExist()
        composeRule.onNodeWithTag("accent_ocean").assertDoesNotExist()
        composeRule.onNodeWithText("App colour").assertDoesNotExist()

        // The whole row opens the screen, not just its icon.
        composeRule.onNodeWithText("Theme and app colors").performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithTag("more_appearance").performClick()
        assertEquals(2, opened)
    }

    @Test
    fun morePage_noLongerListsBillingSettings() {
        composeRule.setContent { AuxoTheme { MoreScreen(actions = object : MoreActions {}) } }
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasTestTag("more_appearance"))
        composeRule.onNodeWithText("Billing settings").assertDoesNotExist()
        composeRule.onNodeWithText("Store details, GST, UPI and receipts").assertDoesNotExist()
    }

    @Test
    fun appearanceScreen_offersEightColours_andMarksTheChosenOne() {
        composeRule.setContent {
            AuxoTheme {
                AppearanceContent(AppearanceSettings(accent = AppAccent.OCEAN), onBack = {}, onAccentChange = {}, onDarkModeChange = {})
            }
        }
        composeRule.onNodeWithText("App colour").assertIsDisplayed()
        composeRule.onNodeWithTag("dark_mode_switch").assertIsDisplayed()
        AppAccent.entries.forEach { composeRule.onNodeWithTag("accent_${it.id}").assertExists() }
        composeRule.onNodeWithTag("accent_ocean").assertIsSelected()
    }

    @Test
    fun appearanceScreen_tappingAColour_andTheDarkSwitch_reportTheChoice() {
        var accent: AppAccent? = null
        var dark: Boolean? = null
        composeRule.setContent {
            AuxoTheme {
                AppearanceContent(AppearanceSettings(), onBack = {}, onAccentChange = { accent = it }, onDarkModeChange = { dark = it })
            }
        }
        composeRule.onNodeWithTag("accent_violet").performClick()
        composeRule.onNodeWithTag("dark_mode_switch").performClick()
        assertEquals(AppAccent.VIOLET, accent)
        assertEquals(true, dark)
    }

    @Test
    fun appearanceScreen_changesApplyImmediately_andBackWorks() {
        var back = 0
        composeRule.setContent {
            var settings by remember { mutableStateOf(AppearanceSettings()) }
            AuxoTheme(accent = settings.accent, darkTheme = settings.darkMode) {
                AppearanceContent(
                    settings,
                    onBack = { back++ },
                    onAccentChange = { settings = settings.copy(accent = it) },
                    onDarkModeChange = { settings = settings.copy(darkMode = it) },
                )
            }
        }
        composeRule.onNodeWithTag("dark_mode_switch").assertIsOff()
        composeRule.onNodeWithTag("dark_mode_switch").performClick()
        composeRule.onNodeWithTag("dark_mode_switch").assertIsOn()
        composeRule.onNodeWithTag("accent_rose").performClick()
        composeRule.onNodeWithTag("accent_rose").assertIsSelected()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }

    @Test
    fun darkTheme_turnsTheWhiteSurfacesBlack() {
        var background = 1f
        var surface = 1f
        composeRule.setContent {
            AuxoTheme(accent = AppAccent.ROSE, darkTheme = true) {
                background = MaterialTheme.colorScheme.background.luminance()
                surface = MaterialTheme.colorScheme.surface.luminance()
            }
        }
        composeRule.waitForIdle()
        assertTrue(background < 0.02f)
        assertTrue(surface < 0.02f)
    }

    @Test
    fun theColourFollowsTheChoice() {
        var primary = androidx.compose.ui.graphics.Color.Unspecified
        composeRule.setContent {
            AuxoTheme(accent = AppAccent.SUNSET) { primary = MaterialTheme.colorScheme.primary }
        }
        composeRule.waitForIdle()
        assertTrue("Sunset should be red-orange", primary.red > primary.blue && primary.red > primary.green)
    }
}
