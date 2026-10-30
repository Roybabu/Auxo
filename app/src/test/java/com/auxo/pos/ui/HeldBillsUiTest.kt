// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui

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
import com.auxo.pos.domain.billing.HeldBillSummary
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.HeldBill
import androidx.compose.ui.test.assertCountEquals
import com.auxo.pos.ui.screens.billing.HeldBillsDialog
import com.auxo.pos.ui.screens.billing.HoldBillForm
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
        composeRule.setContent { AuxoTheme { HoldBillForm(label = label, onLabelChange = { label = it }) } }
        composeRule.onNodeWithTag("hold_label").assertIsDisplayed()
        composeRule.onNodeWithTag("hold_label").performTextInput(" K")
        composeRule.waitForIdle()
        assertTrue(label.contains("K"))
    }

    @Test
    fun holdForm_showsTheCustomersSavedPhone_orTheOptionalLabelWhenThereIsNone() {
        var phone by androidx.compose.runtime.mutableStateOf("9830012345")
        composeRule.setContent {
            AuxoTheme { HoldBillForm(label = "Anita Das", onLabelChange = {}, phone = phone, onPhoneChange = { phone = it }) }
        }
        composeRule.onNodeWithText("9830012345").assertIsDisplayed()

        phone = ""
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Customer phone (optional)").assertIsDisplayed()
    }

    @Test
    fun heldList_showsEachBill_andResumeNeedsAnEmptyCounter() {
        var resumed: Long? = null
        composeRule.setContent {
            AuxoTheme {
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
            AuxoTheme {
                HeldBillsDialog(bills, counterIsEmpty = false, resumedId = null, onResume = {}, onDiscard = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag("held_resume_1").assertIsNotEnabled()
        composeRule.onNodeWithText("Hold or clear the current bill before resuming another.").assertIsDisplayed()
    }

    private val heldBill = HeldBill(
        bills[0],
        Cart(
            lines = listOf(
                CartLine(
                    variantId = 7, productId = 7, productName = "Basmati rice", variantLabel = "5 kg", barcode = "", hsnCode = null,
                    unitPrice = Money(450_00), mrp = null, gstRate = null, quantity = 1, availableStock = 10,
                ),
            ),
        ),
    )

    @Test
    fun theList_hasNoDiscardButton_onlyResumeAndReview() {
        composeRule.setContent {
            AuxoTheme { HeldBillsDialog(bills, counterIsEmpty = true, resumedId = null, onResume = {}, onDiscard = {}, onDismiss = {}) }
        }
        composeRule.onNodeWithTag("held_review_open_1").assertIsDisplayed()
        composeRule.onNodeWithTag("held_resume_1").assertIsDisplayed()
        composeRule.onNodeWithTag("held_discard_1").assertDoesNotExist()
        composeRule.onAllNodes(hasText("Discard")).assertCountEquals(0)
    }

    @Test
    fun review_showsTheItems_andOffersResumeAndClose() {
        var resumed: Long? = null
        composeRule.setContent {
            AuxoTheme {
                HeldBillsDialog(
                    bills, counterIsEmpty = true, resumedId = null, onResume = { resumed = it }, onDiscard = {}, onDismiss = {},
                    onLoad = { heldBill },
                )
            }
        }
        composeRule.onNodeWithTag("held_review_open_1").performClick()
        composeRule.onNodeWithText("Basmati rice").assertIsDisplayed()
        composeRule.onNodeWithText("5 kg").assertIsDisplayed()
        composeRule.onNodeWithTag("held_review_resume").performClick()
        assertEquals(1L, resumed)
    }

    @Test
    fun discard_livesInReview_asksAreYouSure_andOnlyThenRemoves() {
        val discarded = mutableListOf<Long>()
        composeRule.setContent {
            AuxoTheme {
                HeldBillsDialog(
                    bills, counterIsEmpty = true, resumedId = null, onResume = {}, onDiscard = { discarded += it }, onDismiss = {},
                    onLoad = { heldBill },
                )
            }
        }
        composeRule.onNodeWithTag("held_review_open_1").performClick()
        composeRule.onNodeWithTag("held_review_discard").performClick()
        composeRule.onNodeWithText("Discard this held bill?").assertIsDisplayed()
        composeRule.onNodeWithText("Are you sure?", substring = true).assertIsDisplayed()
        assertTrue(discarded.isEmpty())
        // Cancel keeps it.
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(discarded.isEmpty())

        composeRule.onNodeWithTag("held_review_discard").performClick()
        // The confirmation's own button is the last "Discard" on screen.
        composeRule.onAllNodes(hasText("Discard")).onLast().performClick()
        assertEquals(listOf(1L), discarded)
    }

    @Test
    fun aBillThatCannotBeRead_canStillBeReviewedAndDiscarded() {
        composeRule.setContent {
            AuxoTheme {
                HeldBillsDialog(bills, counterIsEmpty = true, resumedId = null, onResume = {}, onDiscard = {}, onDismiss = {}, onLoad = { null })
            }
        }
        composeRule.onNodeWithTag("held_review_open_2").performClick()
        composeRule.onNodeWithTag("held_review_unreadable").assertIsDisplayed()
        composeRule.onNodeWithTag("held_review_discard").assertIsDisplayed()
    }

    @Test
    fun theBillOnTheCounter_isMarked_insteadOfOfferingResume() {
        composeRule.setContent {
            AuxoTheme {
                HeldBillsDialog(bills, counterIsEmpty = false, resumedId = 1, onResume = {}, onDiscard = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("On the counter now").assertIsDisplayed()
    }

    @Test
    fun emptyList_saysSo() {
        composeRule.setContent {
            AuxoTheme { HeldBillsDialog(emptyList(), counterIsEmpty = true, resumedId = null, onResume = {}, onDiscard = {}, onDismiss = {}) }
        }
        composeRule.onNodeWithText("No bills are on hold.").assertIsDisplayed()
    }
}
