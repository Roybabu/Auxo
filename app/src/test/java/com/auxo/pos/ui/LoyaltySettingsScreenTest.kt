package com.auxo.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.repository.LoyaltyRepository
import com.auxo.pos.domain.loyalty.LoyaltyRules
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.FakeLoyaltyRepository
import com.auxo.pos.ui.screens.more.LoyaltySettingsContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.LoyaltySettingsViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class LoyaltySettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val repository = FakeLoyaltyRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun show(repo: LoyaltyRepository = repository): LoyaltySettingsViewModel {
        val vm = LoyaltySettingsViewModel(repo)
        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            AuxoTheme {
                LoyaltySettingsContent(
                    state = state,
                    onBack = {},
                    onEnabledChange = vm::onEnabledChange,
                    onSpendChange = vm::onSpendChange,
                    onValueChange = vm::onValueChange,
                    onSave = vm::save,
                    onRetry = vm::retry,
                )
            }
        }
        return vm
    }

    @Test
    fun opensWithLoyaltyOff_andNothingToSave() {
        show()
        composeRule.onNodeWithTag("loyalty_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("loyalty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("loyalty_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("loyalty_example").assertIsDisplayed()
    }

    @Test
    fun switchingOn_andSaving_savesAndConfirms() {
        show()
        composeRule.onNodeWithTag("loyalty_switch").performClick()
        composeRule.onNodeWithTag("loyalty_save").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("loyalty_saved").assertIsDisplayed()
        assertTrue(repository.rules.value.enabled)
    }

    @Test
    fun typingNewNumbers_isSaved() {
        show()
        composeRule.onNodeWithTag("loyalty_spend").performTextReplacement("200")
        composeRule.onNodeWithTag("loyalty_value").performTextReplacement("2")
        composeRule.onNodeWithTag("loyalty_save").performClick()
        assertEquals(Money(20_000), repository.rules.value.spendPerPoint)
        assertEquals(Money(200), repository.rules.value.pointValue)
    }

    @Test
    fun aBadValue_savesNothing_andTheExampleGoesAway() {
        show()
        composeRule.onNodeWithTag("loyalty_value").performTextReplacement("1.005") // three decimals
        composeRule.onNodeWithTag("loyalty_example").assertDoesNotExist()
        composeRule.onNodeWithTag("loyalty_save").performClick()
        assertEquals(0, repository.saves)
        composeRule.onNodeWithTag("loyalty_saved").assertDoesNotExist()
    }

    @Test
    fun aFailedLoad_offersARetry() {
        val failing = object : LoyaltyRepository by repository {
            override suspend fun getRules(): LoyaltyRules = throw java.io.IOException("unreadable")
        }
        show(failing)
        composeRule.onNodeWithTag("loyalty_load_failed").assertIsDisplayed()
        composeRule.onNodeWithTag("loyalty_retry").assertIsDisplayed()
    }
}
