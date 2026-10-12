package com.invision.pos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.domain.billing.HeldBillSummary
import com.invision.pos.domain.model.Money
import com.invision.pos.ui.screens.billing.HeldBillsDialog
import com.invision.pos.ui.screens.billing.HoldBillForm
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
class HeldBillsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bills = listOf(
        HeldBillSummary(1, "Table 4", 1_000, 2_000, 3, Money(450_00)),
        HeldBillSummary(2, "", 1_000, 1_500, 1, Money(99_00)),
    )

    @Test
    fun holdForm_offersTheCustomerNameAndReturnsWhatWasTyped() {
        // The form is tested without the dialog window: text fields inside a dialog never become idle in these tests.
        var label by androidx.compose.runtime.mutableStateOf("Asha")
        composeRule.setContent { InvisionTheme { HoldBillForm(label = label, onLabelChange = { label = it }) } }
        composeRule.onNodeWithTag("hold_label").assertIsDisplayed()
        composeRule.onNodeWithTag("hold_label").performTextInput(" K")
        composeRule.waitForIdle()
        assertTrue(label.contains("K"))
    }

    @Test
    fun heldList_showsEachBill_andResumeNeedsAnEmptyCounter() {
        var resumed: Long? = null
        composeRule.setContent {
            InvisionTheme {
                HeldBillsDialog(bills, counterIsEmpty = true, resumedId = null, onResume = { resumed = it }, onDiscard = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Table 4").assertIsDisplayed()
        composeRule.onNodeWithText("Held bill").assertIsDisplayed() // the unnamed one
        composeRule.onNodeWithTag("held_resume_1").assertIsEnabled()
        composeRule.onNodeWithTag("held_resume_1").performClick()
        assertEquals(1L, resumed)
    }

    @Test
    fun heldList_disablesResume_whenAnotherBillIsOnTheCounter() {
        composeRule.setContent {
            InvisionTheme {
                HeldBillsDialog(bills, counterIsEmpty = false, resumedId = null, onResume = {}, onDiscard = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag("held_resume_1").assertIsNotEnabled()
        composeRule.onNodeWithText("Hold or clear the current bill before resuming another.").assertIsDisplayed()
    }

    @Test
    fun discard_asksFirst_andOnlyThenRemoves() {
        val discarded = mutableListOf<Long>()
        composeRule.setContent {
            InvisionTheme {
                HeldBillsDialog(bills, counterIsEmpty = true, resumedId = null, onResume = {}, onDiscard = { discarded += it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag("held_discard_1").performClick()
        composeRule.onNodeWithText("Discard this held bill?").assertIsDisplayed()
        assertTrue(discarded.isEmpty())
        // Cancel keeps it.
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(discarded.isEmpty())

        composeRule.onNodeWithTag("held_discard_1").performClick()
        // The dialog's own button, not the row's.
        composeRule.onAllNodes(hasText("Discard")).onLast().performClick()
        assertEquals(listOf(1L), discarded)
    }

    @Test
    fun theBillOnTheCounter_isMarked_insteadOfOfferingResume() {
        composeRule.setContent {
            InvisionTheme {
                HeldBillsDialog(bills, counterIsEmpty = false, resumedId = 1, onResume = {}, onDiscard = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("On the counter now").assertIsDisplayed()
    }

    @Test
    fun emptyList_saysSo() {
        composeRule.setContent {
            InvisionTheme { HeldBillsDialog(emptyList(), counterIsEmpty = true, resumedId = null, onResume = {}, onDiscard = {}, onDismiss = {}) }
        }
        composeRule.onNodeWithText("No bills are on hold.").assertIsDisplayed()
    }
}
