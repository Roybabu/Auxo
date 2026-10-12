package com.auxo.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.inventory.CountLine
import com.auxo.pos.domain.inventory.CountReason
import com.auxo.pos.domain.inventory.StockCount
import com.auxo.pos.domain.inventory.StockCounts
import com.auxo.pos.testutil.TestData
import com.auxo.pos.ui.screens.stock.StockCountActions
import com.auxo.pos.ui.screens.stock.StockCountContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.CountCandidate
import com.auxo.pos.viewmodel.CountEditor
import com.auxo.pos.viewmodel.CountStage
import com.auxo.pos.viewmodel.StockCountUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class StockCountUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val shirt = TestData.tShirt()
    private val m = shirt.variants[0]
    private val l = shirt.variants[1]
    private val products = listOf(shirt)

    private fun state(
        lines: List<CountLine> = emptyList(),
        stage: CountStage = CountStage.COUNTING,
        started: Boolean = true,
        build: (StockCountUiState) -> StockCountUiState = { it },
    ): StockCountUiState {
        val count = if (started) StockCount(1, 0, lines) else null
        val rows = count?.let { StockCounts.rows(it, products) }.orEmpty().asReversed()
        return build(
            StockCountUiState(
                isLoading = false,
                count = count,
                stage = stage,
                rows = rows,
                summary = StockCounts.summary(rows),
            ),
        )
    }

    private fun show(state: StockCountUiState, actions: StockCountActions = object : StockCountActions {}) {
        composeRule.setContent { AuxoTheme { StockCountContent(state, actions) } }
        composeRule.waitForIdle()
    }

    @Test
    fun beforeACountStarts_theScreenExplainsItAndOffersToStart() {
        var started = 0
        show(state(started = false), object : StockCountActions { override fun onStart() { started++ } })
        composeRule.onNodeWithTag("count_intro").assertIsDisplayed()
        composeRule.onNodeWithText("Stock changes only after you review the differences and confirm.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Start stock count").performClick()
        assertEquals(1, started)
    }

    @Test
    fun counting_listsWhatWasCounted_withTheirDifferences() {
        show(state(listOf(CountLine(m.id, 8, 5), CountLine(l.id, 1, 1))))
        composeRule.onNodeWithText("2 items counted").assertIsDisplayed()
        composeRule.onNodeWithText("Expected 8 · Counted 5").assertIsDisplayed()
        composeRule.onNodeWithText("3 short").assertIsDisplayed()
        composeRule.onNodeWithText("Matches").assertIsDisplayed()
        composeRule.onNodeWithTag("count_review").assertIsEnabled()
    }

    @Test
    fun theReviewButton_waitsForSomethingToBeCounted() {
        show(state())
        composeRule.onNodeWithText("Nothing counted yet. Scan or search an item above.").assertIsDisplayed()
        composeRule.onNodeWithTag("count_review").assertIsNotEnabled()
    }

    @Test
    fun searchResults_canBePicked() {
        val picked = mutableListOf<Long>()
        show(
            state { it.copy(searchText = "shirt", searchResults = listOf(CountCandidate(shirt, m), CountCandidate(shirt, l))) },
            object : StockCountActions { override fun onPick(candidate: CountCandidate) { picked += candidate.variant.id } },
        )
        composeRule.onNodeWithTag("count_result_${m.id}").performClick()
        assertEquals(listOf(m.id), picked)
    }

    @Test
    fun review_showsOnlyDifferences_withTheirValue_andTheReasons() {
        val lines = listOf(CountLine(m.id, 8, 5), CountLine(l.id, 1, 1))
        show(state(lines, CountStage.REVIEW))
        composeRule.onNodeWithTag("count_differences_summary").assertIsDisplayed()
        composeRule.onNodeWithText("1 item differs from your stock").assertIsDisplayed()
        composeRule.onNodeWithText("1 short, worth ₹750.00 at cost", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("count_row_${m.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("count_row_${l.id}").assertDoesNotExist()
        for (reason in CountReason.entries) composeRule.onNodeWithTag("count_reason_${reason.name}").assertExists()
        composeRule.onNodeWithText("Update stock").assertIsDisplayed()
    }

    @Test
    fun review_withNoDifferences_offersToFinish() {
        show(state(listOf(CountLine(l.id, 1, 1)), CountStage.REVIEW))
        composeRule.onNodeWithTag("count_no_differences").assertIsDisplayed()
        composeRule.onNodeWithText("Finish count").assertIsDisplayed()
    }

    @Test
    fun postingAsksForConfirmation_andOnlyTheConfirmButtonPosts() {
        var asked = 0
        var posted = 0
        val lines = listOf(CountLine(m.id, 8, 5), CountLine(l.id, 1, 4))
        val actions = object : StockCountActions {
            override fun onPost() { asked++ }
            override fun onConfirmPost() { posted++ }
        }
        show(state(lines, CountStage.REVIEW), actions)
        composeRule.onNodeWithTag("count_post").performClick()
        assertEquals(1, asked)
        assertEquals(0, posted)
    }

    @Test
    fun theConfirmationDialog_saysHowManyItemsWillChange() {
        var posted = 0
        val lines = listOf(CountLine(m.id, 8, 5), CountLine(l.id, 1, 4))
        show(
            state(lines, CountStage.REVIEW) { it.copy(confirmOpen = true) },
            object : StockCountActions { override fun onConfirmPost() { posted++ } },
        )
        composeRule.onNodeWithText("Update stock?").assertIsDisplayed()
        composeRule.onNodeWithText("This will change the stock of 2 items to match your count. It is saved in the activity log.").assertIsDisplayed()
        composeRule.onNodeWithTag("count_confirm").performClick()
        assertEquals(1, posted)
    }

    @Test
    fun cancellingAsksFirst() {
        var cancelled = 0
        show(
            state(listOf(CountLine(m.id, 8, 5))) { it.copy(cancelConfirmOpen = true) },
            object : StockCountActions { override fun onConfirmCancel() { cancelled++ } },
        )
        composeRule.onNodeWithText("Cancel this count?").assertIsDisplayed()
        composeRule.onNodeWithText("The quantities you entered will be thrown away. Stock does not change.").assertIsDisplayed()
        composeRule.onNodeWithTag("count_cancel_confirm").performClick()
        assertEquals(1, cancelled)
    }
}
