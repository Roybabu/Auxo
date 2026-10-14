package com.auxo.pos.ui

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.ui.screens.onboarding.OnboardingContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.OnboardingUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first-launch introduction: three pages, Skip and Next, and it only shows before the first administrator exists. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var finished = 0
    private val pages = mutableListOf<Int>()

    private fun show() {
        composeRule.setContent {
            AuxoTheme { OnboardingContent(OnboardingUiState(), onPageChanged = { pages += it }, onFinish = { finished++ }) }
        }
        composeRule.waitForIdle()
    }

    private fun screenshot(name: String) {
        val bitmap = composeRule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun next() {
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun opensOnTheSplash_withTheLogoTheTaglineAndNoSkip() {
        show()
        composeRule.onNodeWithTag("onboarding_splash").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Auxo").assertIsDisplayed()
        composeRule.onNodeWithText("Your Business\nMade Simple").assertIsDisplayed()
        composeRule.onNodeWithText("Inventory").assertIsDisplayed()
        composeRule.onNodeWithText("Billing").assertIsDisplayed()
        composeRule.onNodeWithText("Growth").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_progress").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip").assertDoesNotExist()
        screenshot("onboarding_1_splash")
    }

    @Test
    fun next_movesThroughTheIntroductionPages_thenFinishes() {
        show()
        next()
        composeRule.onNodeWithTag("onboarding_intro").assertIsDisplayed()
        composeRule.onNodeWithText("All your business in one place").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_progress").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip").assertIsDisplayed()
        screenshot("onboarding_2_intro")

        next()
        composeRule.onNodeWithTag("onboarding_categories").assertIsDisplayed()
        composeRule.onNodeWithText("Works for any business").assertIsDisplayed()
        for (index in 0..5) composeRule.onNodeWithTag("onboarding_category_$index").assertIsDisplayed()
        composeRule.onNodeWithText("Apparel & Garments").assertIsDisplayed()
        composeRule.onNodeWithText("General Store").assertIsDisplayed()
        screenshot("onboarding_3_categories")
        assertEquals(0, finished)

        next() // on the last page the arrow leads on to first-time setup
        assertEquals(1, finished)
        assertEquals(listOf(0, 1, 2), pages.distinct())
    }

    @Test
    fun progressCard_followsEveryPage_forwardAndBack() {
        show()
        fun assertStep(n: Int, heading: String, sprout: Int) {
            composeRule.onNodeWithTag("onboarding_progress").assertIsDisplayed()
            fun textOf(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            assertEquals("counter at step $n", "Step $n of 3", textOf("onboarding_step_counter"))
            assertEquals("badge at step $n", n.toString(), textOf("onboarding_step_badge"))
            assertEquals("heading at step $n", heading, textOf("onboarding_step_heading"))
            for (s in 0..2) {
                if (s == sprout) composeRule.onNodeWithTag("onboarding_sprout_$s", useUnmergedTree = true).assertExists()
                else composeRule.onNodeWithTag("onboarding_sprout_$s", useUnmergedTree = true).assertDoesNotExist()
            }
        }
        assertStep(1, "Welcome to Auxo", 0)
        next()
        assertStep(2, "See what Auxo does", 1)
        next()
        assertStep(3, "Set up your business", 2)
        screenshot("onboarding_progress_step3")
        composeRule.onNodeWithTag("onboarding").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertStep(2, "See what Auxo does", 1)
        assertEquals(listOf(0, 1, 2, 1), pages)
    }

    @Test
    fun skip_leavesTheIntroduction_fromAnyLaterPage() {
        show()
        next()
        composeRule.onNodeWithTag("onboarding_skip").performClick()
        assertEquals(1, finished)
    }

    // The gate

    private fun gate(state: AuthState, onboarding: Boolean) {
        composeRule.setContent {
            AuxoTheme {
                AuthGate(
                    state = state,
                    setup = { Text("SETUP SCREEN", modifier = Modifier.testTag("fake_setup")) },
                    login = { Text("LOGIN SCREEN", modifier = Modifier.testTag("fake_login")) },
                    onboarding = onboarding,
                    onboardingScreen = { Text("INTRODUCTION", modifier = Modifier.testTag("fake_onboarding")) },
                    app = { Text("THE APP", modifier = Modifier.testTag("fake_app")) },
                )
            }
        }
    }

    @Test
    fun firstLaunch_showsTheIntroduction_beforeLogin() {
        gate(AuthState.NeedsSetup, onboarding = true)
        composeRule.onNodeWithTag("fake_onboarding").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_login").assertDoesNotExist()
    }

    @Test
    fun onceTheIntroductionIsSeen_loginIsShown() {
        gate(AuthState.NeedsSetup, onboarding = false)
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_onboarding").assertDoesNotExist()
    }

    @Test
    fun laterLaunches_goStraightToLogin_whateverTheIntroductionFlagSays() {
        gate(AuthState.SignedOut, onboarding = true)
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_onboarding").assertDoesNotExist()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1024dp-h768dp-mdpi")
class OnboardingScreenTabletTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onATablet_everyPageIsReachable() {
        composeRule.setContent { AuxoTheme { OnboardingContent(OnboardingUiState(), onPageChanged = {}, onFinish = {}) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_splash").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_intro").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_categories").assertIsDisplayed()
    }
}
