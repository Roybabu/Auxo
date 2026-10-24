// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.billing.QuotationStatus
import com.auxo.pos.domain.billing.QuotationSummary
import com.auxo.pos.domain.model.Money
import com.auxo.pos.ui.screens.billing.QuotationsDialog
import com.auxo.pos.ui.theme.AuxoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class QuotationsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 12)
    private val quotes = listOf(
        QuotationSummary(1, "QT/26-27/00001", 1, today.plusDays(5), "Asha", "", 2, Money(450_00), QuotationStatus.OPEN, null),
        QuotationSummary(2, "QT/26-27/00002", 2, today.minusDays(1), "", "", 1, Money(99_00), QuotationStatus.OPEN, null),
        QuotationSummary(3, "QT/26-27/00003", 3, today.plusDays(9), "Ravi", "", 1, Money(10_00), QuotationStatus.CONVERTED, 7),
    )

    private fun show(
        canSave: Boolean = false,
        counterIsEmpty: Boolean = true,
        list: List<QuotationSummary> = quotes,
        onSave: (Int) -> Unit = {},
        onConvert: (Long) -> Unit = {},
        onShare: (Long) -> Unit = {},
        onDelete: (Long) -> Unit = {},
    ) = composeRule.setContent {
        AuxoTheme {
            QuotationsDialog(list, canSave, counterIsEmpty, null, today, onSave, onConvert, onShare, onDelete, {})
        }
    }

    @Test
    fun list_showsValidity_expiry_andConvertedState() {
        show()
        composeRule.onNodeWithTag("quote_status_1").assertIsDisplayed()
        composeRule.onNodeWithText("Valid until 17 Oct 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Expired on 11 Oct 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Converted to a bill").assertIsDisplayed()
        composeRule.onNodeWithTag("quote_convert_3").assertDoesNotExist() // already used
    }

    @Test
    fun convertAndShare_reportTheQuotation() {
        var converted: Long? = null
        var shared: Long? = null
        show(onConvert = { converted = it }, onShare = { shared = it })
        composeRule.onNodeWithTag("quote_convert_1").performClick()
        composeRule.onNodeWithTag("quote_share_1").performClick()
        assertEquals(1L, converted)
        assertEquals(1L, shared)
    }

    @Test
    fun convert_needsAnEmptyCounter() {
        show(counterIsEmpty = false)
        composeRule.onNodeWithTag("quote_convert_1").assertIsNotEnabled()
        composeRule.onNodeWithText("Hold or clear the current bill before converting a quotation.").assertIsDisplayed()
    }

    @Test
    fun saving_offersValidityChoices_defaultingToFifteenDays() {
        var days: Int? = null
        show(canSave = true, onSave = { days = it })
        composeRule.onNodeWithTag("quote_save").performClick()
        assertEquals(15, days)
        composeRule.onNodeWithTag("quote_days_30").performClick()
        composeRule.onNodeWithTag("quote_save").performClick()
        assertEquals(30, days)
    }

    @Test
    fun noSaveSection_whenThereIsNothingOnTheCounter() {
        show(canSave = false)
        composeRule.onNodeWithTag("quote_save").assertDoesNotExist()
    }

    @Test
    fun deleting_asksFirst() {
        var deleted: Long? = null
        show(onDelete = { deleted = it })
        composeRule.onNodeWithTag("quote_delete_2").performClick()
        assertEquals(null, deleted)
        composeRule.onNodeWithText("Delete this quotation?").assertIsDisplayed()
        composeRule.onNodeWithText("QT/26-27/00002 will be deleted. This cannot be undone.").assertIsDisplayed()
    }

    @Test
    fun emptyList_saysSo() {
        show(list = emptyList())
        composeRule.onNodeWithTag("quote_empty").assertIsDisplayed()
    }
}
