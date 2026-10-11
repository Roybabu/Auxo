package com.invision.pos.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.format.MoneyFormatter
import com.invision.pos.domain.inventory.InventorySummaryCalculator
import com.invision.pos.domain.model.Money
import com.invision.pos.testutil.TestData
import com.invision.pos.ui.screens.home.HomeContent
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drags and scrolls the Home panel. The window is short on purpose, so the content
 * is taller than the open panel and has to scroll.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h560dp-xhdpi")
class HomePanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val formatter = MoneyFormatter()

    private fun showHome() {
        val summary = InventorySummaryCalculator.calculate(
            listOf(TestData.tShirt(), TestData.biscuits(), TestData.juice(stock = 0)),
            TestData.today,
        )
        composeRule.setContent {
            InvisionTheme {
                HomeContent(
                    uiState = HomeUiState.Ready(
                        storeName = "Roy Mart",
                        salesToday = formatter.format(Money.ZERO),
                        billsToday = 0,
                        inventory = summary,
                        stockValueAtCost = formatter.format(summary.stockValueAtCost),
                        stockValueAtPrice = formatter.format(summary.stockValueAtPrice),
                    ),
                    onNavigate = {},
                    onAddProduct = {},
                    onOpenProduct = {},
                    onOpenInventory = {},
                    onRetry = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun panelTop(): Float = composeRule.onNodeWithTag("home_panel").getUnclippedBoundsInRoot().top.value

    private fun contentTop(): Float = composeRule.onNodeWithText("Today").getUnclippedBoundsInRoot().top.value

    /** Moves a finger up (negative [pixels]) or down from near the bottom of the panel. */
    private fun drag(pixels: Float, millis: Long) {
        composeRule.onNodeWithTag("home_panel").performTouchInput {
            val x = centerX
            val start = Offset(x, height - 40f)
            swipe(start = start, end = Offset(x, start.y + pixels), durationMillis = millis)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun panel_startsLowOnTheScreen_belowTheHeader() {
        showHome()
        val top = panelTop()
        // 97.5% of the width, but no more than 66% of this short window.
        assertTrue("panel top was $top", top > 300f && top < 400f)
    }

    @Test
    fun swipeUp_opensThePanel_andSwipeDownClosesItAgain() {
        showHome()
        val collapsed = panelTop()

        drag(pixels = -900f, millis = 300)
        val expanded = panelTop()
        assertTrue("panel moved from $collapsed to $expanded", collapsed - expanded > 250f)

        drag(pixels = 900f, millis = 300)
        assertEquals(collapsed, panelTop(), 2f)
    }

    @Test
    fun slowDragLessThanHalfway_fallsBackToCollapsed() {
        showHome()
        val collapsed = panelTop()
        drag(pixels = -150f, millis = 1000)
        assertEquals(collapsed, panelTop(), 2f)
    }

    @Test
    fun slowDragMoreThanHalfway_settlesOpen() {
        showHome()
        val collapsed = panelTop()
        drag(pixels = -400f, millis = 1000)
        assertTrue("panel top was ${panelTop()}", collapsed - panelTop() > 250f)
    }

    @Test
    fun openPanel_scrollsItsContent_withoutMoving() {
        showHome()
        drag(pixels = -900f, millis = 300)
        val open = panelTop()
        val before = contentTop()

        drag(pixels = -400f, millis = 800)

        assertEquals(open, panelTop(), 1f)
        assertTrue("content moved from $before to ${contentTop()}", before - contentTop() > 100f)
    }

    @Test
    fun scrolledContent_comesBackToTheTopBeforeThePanelCloses() {
        showHome()
        drag(pixels = -900f, millis = 300)
        val open = panelTop()
        drag(pixels = -400f, millis = 800)
        val scrolled = contentTop()

        // A short, slow drag down only has to bring the content back; it is not enough to close the panel.
        drag(pixels = 100f, millis = 800)

        assertEquals(open, panelTop(), 1f)
        assertTrue("content stayed at $scrolled", contentTop() > scrolled + 30f)
    }
}
