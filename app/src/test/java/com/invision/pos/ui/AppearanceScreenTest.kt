package com.invision.pos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.appearance.AppAccent
import com.invision.pos.core.appearance.AppearanceSettings
import com.invision.pos.ui.screens.more.MoreActions
import com.invision.pos.ui.screens.more.MoreScreen
import com.invision.pos.ui.theme.InvisionTheme
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

    private fun scrollToAppearance() {
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasTestTag("appearance"))
    }

    @Test
    fun morePage_offersEightColours_andMarksTheChosenOne() {
        composeRule.setContent {
            InvisionTheme {
                MoreScreen(actions = object : MoreActions {}, appearance = AppearanceSettings(accent = AppAccent.OCEAN))
            }
        }
        scrollToAppearance()
        composeRule.onNodeWithText("App colour").assertIsDisplayed()
        AppAccent.entries.forEach { composeRule.onNodeWithTag("accent_${it.id}").assertExists() }
        composeRule.onNodeWithTag("accent_ocean").assertIsSelected()
    }

    @Test
    fun tappingAColour_andTheDarkSwitch_reportTheChoice() {
        var accent: AppAccent? = null
        var dark: Boolean? = null
        composeRule.setContent {
            InvisionTheme {
                MoreScreen(
                    actions = object : MoreActions {},
                    onAccentChange = { accent = it },
                    onDarkModeChange = { dark = it },
                )
            }
        }
        scrollToAppearance()
        composeRule.onNodeWithTag("accent_violet").performClick()
        composeRule.onNodeWithTag("dark_mode_switch").performClick()
        assertEquals(AppAccent.VIOLET, accent)
        assertEquals(true, dark)
    }

    @Test
    fun darkTheme_turnsTheWhiteSurfacesBlack() {
        var background = 1f
        var surface = 1f
        composeRule.setContent {
            InvisionTheme(accent = AppAccent.ROSE, darkTheme = true) {
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
            InvisionTheme(accent = AppAccent.SUNSET) { primary = MaterialTheme.colorScheme.primary }
        }
        composeRule.waitForIdle()
        assertTrue("Sunset should be red-orange", primary.red > primary.blue && primary.red > primary.green)
    }
}
