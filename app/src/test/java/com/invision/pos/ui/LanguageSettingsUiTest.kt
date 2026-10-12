package com.invision.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.appearance.AppearanceSettings
import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.StoreSettings
import com.invision.pos.testutil.TestData
import com.invision.pos.ui.screens.billing.BillingSettingsContent
import com.invision.pos.ui.screens.more.AppearanceContent
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.BillingSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h2400dp-xhdpi")
class LanguageSettingsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun appearance(selected: Language?, onChange: (Language?) -> Unit) {
        composeRule.setContent {
            InvisionTheme {
                AppearanceContent(
                    AppearanceSettings(), onBack = {}, onAccentChange = {}, onDarkModeChange = {},
                    appLanguage = selected, onAppLanguageChange = onChange,
                )
            }
        }
    }

    @Test
    fun theLanguageCard_isHidden_whenNothingListensForChanges() {
        composeRule.setContent { InvisionTheme { AppearanceContent(AppearanceSettings(), onBack = {}, onAccentChange = {}, onDarkModeChange = {}) } }
        composeRule.onNodeWithTag("language_card").assertDoesNotExist()
    }

    @Test
    fun theLanguageCard_offersThePhoneAndEachLanguage_byItsOwnName() {
        appearance(null) {}
        composeRule.onNodeWithTag("language_card").assertIsDisplayed()
        composeRule.onNodeWithTag("language_phone").assertIsSelected()
        Language.entries.forEach {
            composeRule.onNodeWithTag("language_${it.tag}").assertExists()
        }
        composeRule.onNodeWithTag("language_hi").assertIsNotSelected()
    }

    @Test
    fun theChosenLanguage_isMarked() {
        appearance(Language.BENGALI) {}
        composeRule.onNodeWithTag("language_bn").assertIsSelected()
        composeRule.onNodeWithTag("language_phone").assertIsNotSelected()
    }

    @Test
    fun tappingALanguage_reportsIt_andThePhoneRowReportsNull() {
        val chosen = mutableListOf<Language?>()
        appearance(Language.ENGLISH) { chosen += it }
        composeRule.onNodeWithTag("language_hi").performClick()
        composeRule.onNodeWithTag("language_phone").performClick()
        assertEquals(listOf(Language.HINDI, null), chosen)
        assertNull(chosen.last())
    }

    // The bill language, a shop setting

    @Test
    fun theBillLanguage_isChosenOnTheShopSettings() {
        var settings = TestData.settings
        composeRule.setContent {
            InvisionTheme {
                BillingSettingsContent(
                    state = BillingSettingsUiState(isLoading = false, settings = settings),
                    onChange = { transform -> settings = transform(settings) },
                    onGstinChange = {},
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_bill_language_hi"))
        composeRule.onNodeWithTag("settings_bill_language_en").assertIsSelected()
        composeRule.onNodeWithTag("settings_bill_language_hi").performClick()
        assertEquals(Language.HINDI, settings.receipt.language)
    }

    @Test
    fun newShops_printBillsInEnglish() {
        assertEquals(Language.ENGLISH, StoreSettings().receipt.language)
        assertEquals(Language.ENGLISH, TestData.settings.receipt.language)
    }
}
