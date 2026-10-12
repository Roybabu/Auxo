package com.auxo.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.customers.CustomerHistory
import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.core.format.MoneyFormatter
import com.auxo.pos.ui.screens.customers.CustomerHistoryContent
import com.auxo.pos.ui.theme.AuxoTheme
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class CustomerHistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val formatter = MoneyFormatter()
    private val day = 24 * 3_600_000L

    private fun bill(id: Long, type: InvoiceType, minor: Long, at: Long) = InvoiceSummary(id, "N-$id", type, at, "Asha", null, Money(minor), 2)

    /** A figure shown inside the tile carrying [tag]. */
    private fun assertTile(tag: String, text: String) {
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag))).assertIsDisplayed()
    }

    private fun show(history: CustomerHistory, onOpen: (Long) -> Unit = {}) {
        composeRule.setContent { AuxoTheme { CustomerHistoryContent(history, onOpen) } }
    }

    @Test
    fun theFourFigures_areShown() {
        val history = CustomerHistory.from(
            listOf(
                bill(1, InvoiceType.SALE, 120_000, 10 * day),
                bill(2, InvoiceType.SALE, 30_000, 10 * day + 3_600_000),
                bill(3, InvoiceType.SALE, 50_050, 15 * day),
                bill(4, InvoiceType.RETURN, 20_000, 16 * day),
            ),
            ZoneOffset.UTC,
        )
        show(history)
        assertTile("history_total_spend", formatter.format(Money(180_050)))
        assertTile("history_visits", "2")
        assertTile("history_average", formatter.format(Money(66_683)))
        composeRule.onNodeWithTag("history_last_visit").assertIsDisplayed()
        composeRule.onNodeWithText("Purchases (3)").assertExists()
        composeRule.onNodeWithTag("customer_history_screen").performScrollToNode(hasText("Returns (1)"))
        composeRule.onNodeWithText("Returns (1)").assertIsDisplayed()
    }

    @Test
    fun withNoPurchases_itSaysSo_andShowsDashesNotZeros() {
        show(CustomerHistory.EMPTY)
        composeRule.onNodeWithTag("history_empty").assertIsDisplayed()
        assertTile("history_average", "—")
        assertTile("history_last_visit", "No purchases yet")
        composeRule.onNodeWithText("Purchases (0)").assertDoesNotExist()
    }

    @Test
    fun tappingABill_opensIt() {
        val opened = mutableListOf<Long>()
        show(CustomerHistory.from(listOf(bill(7, InvoiceType.SALE, 10_000, day)), ZoneOffset.UTC), onOpen = { opened += it })
        composeRule.onNodeWithTag("customer_history_screen").performScrollToNode(hasText("N-7"))
        composeRule.onNodeWithText("N-7").performClick()
        assertEquals(listOf(7L), opened)
    }
}
