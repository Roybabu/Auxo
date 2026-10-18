package com.auxo.pos.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.inventory.ReorderPlanner
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.testutil.TestData
import com.auxo.pos.ui.screens.stock.StockAlertsActions
import com.auxo.pos.ui.screens.stock.StockAlertsContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.StockAlertsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class ReorderListUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val flourVariant = TestData.flour(grams = 500).variants[0].id
    private val juiceVariant = TestData.juice(stock = 0).variants[0].id

    private fun state(invalid: Set<Long> = emptySet(), texts: Map<Long, String> = emptyMap()): StockAlertsUiState {
        val planned = ReorderPlanner.suggestions(
            listOf(
                TestData.tShirt().copy(preferredSupplierId = 1),
                TestData.juice(stock = 0).copy(preferredSupplierId = 2),
                TestData.flour(grams = 500),
            ),
        )
        return StockAlertsUiState(
            isLoading = false,
            today = TestData.today,
            suggestions = ReorderPlanner.withQuantities(planned, texts),
            suppliers = listOf(Supplier(id = 1, name = "Sample Textiles"), Supplier(id = 2, name = "Drinks Co")),
            quantityTexts = texts,
            invalidQuantities = invalid,
        )
    }

    private fun show(state: StockAlertsUiState, actions: StockAlertsActions = object : StockAlertsActions {}) {
        composeRule.setContent { AuxoTheme { StockAlertsContent(state, actions) } }
        composeRule.waitForIdle()
    }

    @Test
    fun theList_hasAHeaderPerSupplier_andOneForItemsWithoutOne() {
        show(state())
        composeRule.onNodeWithTag("reorder_group_2").assertIsDisplayed()
        composeRule.onNodeWithTag("reorder_group_1").assertIsDisplayed()
        composeRule.onNodeWithTag("reorder_group_0").assertIsDisplayed()
        composeRule.onNodeWithText("Drinks Co").assertIsDisplayed()
        composeRule.onNodeWithText("Sample Textiles").assertIsDisplayed()
        composeRule.onNodeWithText("No preferred supplier").assertIsDisplayed()
        composeRule.onAllNodesWithText("1 item").assertCountEquals(3)
    }

    @Test
    fun theSuggestedQuantityIsShown_andTypingReportsTheChange() {
        val typed = mutableListOf<Pair<Long, String>>()
        show(
            state(),
            object : StockAlertsActions {
                override fun onQuantityChange(variantId: Long, text: String) {
                    typed += variantId to text
                }
            },
        )
        // 500 g in stock against a 1 kg level: the suggestion is 1.5 kg.
        composeRule.onNodeWithTag("reorder_qty_$flourVariant").assertIsDisplayed()
        composeRule.onNodeWithText("1.5").assertIsDisplayed()
        composeRule.onNodeWithTag("reorder_qty_$flourVariant").performTextReplacement("2")
        assertEquals(listOf(flourVariant to "2"), typed)
    }

    @Test
    fun anUnusableQuantity_showsAnError_andDisablesTheDraftButton() {
        show(state(invalid = setOf(juiceVariant), texts = mapOf(juiceVariant to "abc")))
        composeRule.onNodeWithText("Enter a quantity").assertIsDisplayed()
        composeRule.onNodeWithTag("alerts_create_drafts").assertIsNotEnabled()
    }

    @Test
    fun withUsableQuantities_theDraftButtonIsEnabled() {
        show(state())
        composeRule.onNodeWithTag("alerts_create_drafts").assertIsEnabled()
    }
}
