// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.auxo.pos.ui.screens.auth.SetUpButton
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.ui.screens.auth.LoginContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.LoginUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Half of a portrait phone in split screen: wide enough, but only about 400 dp tall. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h400dp-xhdpi")
class LoginSplitScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everythingOnTheLoginPageCanBeReached() {
        composeRule.setContent {
            AuxoTheme { LoginContent(LoginUiState(), {}, {}, {}, onForgotPassword = {}, onSetUpBusiness = {}) }
        }
        composeRule.onNodeWithTag("login_compact").assertIsDisplayed()
        composeRule.onNodeWithTag("login_identifier").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_password").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_setup_business").performScrollTo().assertIsDisplayed()
    }
}

/** A phone on its side: very short, and wide. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w780dp-h360dp-xhdpi")
class LoginLandscapeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everythingOnTheLoginPageCanBeReached() {
        composeRule.setContent {
            AuxoTheme { LoginContent(LoginUiState(), {}, {}, {}, onForgotPassword = {}, onSetUpBusiness = {}) }
        }
        composeRule.onNodeWithTag("login_submit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_setup_business").performScrollTo().assertIsDisplayed()
    }
}

/** A small, tall phone: the usual picture page, which now scrolls when the form does not fit. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LoginSmallPhoneTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theSlideToSignInCanBeReached() {
        composeRule.setContent {
            AuxoTheme { LoginContent(LoginUiState(), {}, {}, {}, onForgotPassword = {}, onSetUpBusiness = {}) }
        }
        composeRule.onNodeWithTag("login_submit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_setup_business").performScrollTo().assertIsDisplayed()
    }
}

/** The button under the slider on a phone with no business: it can be given another label, a note and a second choice. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class LoginBackupButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theButtonShowsItsLabel_note_andSecondChoice() {
        var taps = 0
        var another = 0
        composeRule.setContent {
            AuxoTheme {
                LoginContent(
                    LoginUiState(), {}, {}, {},
                    onSetUpBusiness = { taps++ },
                    setUpButton = SetUpButton(
                        label = "Set up a new shop",
                        icon = com.auxo.pos.R.drawable.ic_store,
                        divider = "Set up your business",
                        note = "No backups found in that folder.",
                        secondaryLabel = "Choose another folder",
                        onSecondary = { another++ },
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Set up a new shop").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("login_setup_note").assertIsDisplayed()
        composeRule.onNodeWithTag("login_setup_secondary").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, taps)
        org.junit.Assert.assertEquals(1, another)
    }
}
