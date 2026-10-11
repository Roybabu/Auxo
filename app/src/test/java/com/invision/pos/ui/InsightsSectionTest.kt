package com.invision.pos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.domain.insights.ComparisonBucket
import com.invision.pos.domain.insights.Department
import com.invision.pos.domain.insights.DepartmentSales
import com.invision.pos.domain.insights.Insights
import com.invision.pos.domain.insights.InsightsPeriod
import com.invision.pos.domain.insights.MethodSlice
import com.invision.pos.domain.insights.ProductRank
import com.invision.pos.domain.insights.SeriesPoint
import com.invision.pos.domain.inventory.InventorySummary
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.ui.screens.home.insights.InsightsSection
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.InsightsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The Home charts in each state: loading, failed, empty, and with data. The figures below are test data. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class InsightsSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 10)

    private val sample = Insights(
        period = InsightsPeriod.LAST_7_DAYS,
        from = today.minusDays(6),
        to = today,
        sales = Money(1_250_000),
        billCount = 3,
        purchases = Money(400_000),
        trend = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").mapIndexed { i, d -> SeriesPoint(d, Money(i * 50_000L)) },
        comparison = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").mapIndexed { i, d ->
            ComparisonBucket(d, Money(i * 50_000L), Money(if (i % 2 == 0) 60_000L else 0L))
        },
        paymentMethods = listOf(MethodSlice(PaymentMethod.CASH, Money(500_000)), MethodSlice(PaymentMethod.UPI, Money(750_000))),
        departments = listOf(DepartmentSales(Department.GARMENTS, Money(900_000)), DepartmentSales(Department.PACKAGED_FOOD, Money(350_000))),
        topProducts = listOf(ProductRank("Cotton T-Shirt", 6, Money(900_000)), ProductRank("Butter Biscuits", 12, Money(350_000))),
    )

    private val inventory = InventorySummary.EMPTY.copy(
        productCount = 3,
        variantCount = 10,
        lowStockCount = 2,
        outOfStockCount = 1,
        expiringSoonCount = 1,
    )

    private fun show(
        state: InsightsUiState,
        summary: InventorySummary = inventory,
        onPeriod: (InsightsPeriod) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            InvisionTheme {
                Scrollable { InsightsSection(state = state, inventory = summary, onPeriodChange = onPeriod, onRetry = onRetry) }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Scrollable(content: @Composable () -> Unit) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
    }

    @Test
    fun loading_showsOnlyAProgressIndicator() {
        show(InsightsUiState(isLoading = true), summary = InventorySummary.EMPTY)
        composeRule.onNodeWithTag("insights_loading").assertExists()
        composeRule.onNodeWithTag("card_trend").assertDoesNotExist()
    }

    @Test
    fun failed_offersARetry() {
        var retried = false
        show(InsightsUiState(isLoading = false, failed = true), summary = InventorySummary.EMPTY, onRetry = { retried = true })
        composeRule.onNodeWithTag("insights_error").assertExists()
        composeRule.onNodeWithText("Try again").performClick()
        assertTrue(retried)
    }

    @Test
    fun noActivity_saysSoInsteadOfDrawingEmptyCharts() {
        val empty = sample.copy(
            sales = Money.ZERO, billCount = 0, purchases = Money.ZERO,
            paymentMethods = emptyList(), departments = emptyList(), topProducts = emptyList(),
        )
        show(InsightsUiState(isLoading = false, insights = empty), summary = InventorySummary.EMPTY)
        composeRule.onNodeWithTag("insights_empty").assertExists()
        composeRule.onNodeWithTag("card_trend").assertDoesNotExist()
    }

    @Test
    fun withData_showsEveryGroupAndChart() {
        show(InsightsUiState(isLoading = false, insights = sample))
        listOf("card_trend", "card_compare", "card_payments", "card_departments", "card_top_products", "card_expenses", "card_stock")
            .forEach { composeRule.onNodeWithTag(it).assertExists() }
        listOf("chart_line", "chart_bars", "chart_donut", "chart_hbars", "chart_stacked")
            .forEach { composeRule.onNodeWithTag(it).assertExists() }
        listOf("Sales", "Products", "Costs", "Stock").forEach { composeRule.onNode(hasText(it)).assertExists() }
    }

    @Test
    fun expenses_areSaidToBeUnavailable_notInvented() {
        show(InsightsUiState(isLoading = false, insights = sample))
        composeRule.onNode(hasText("doesn't record expenses yet", substring = true)).assertExists()
    }

    @Test
    fun purchasesAreNamedAsPurchases_notProfitOrExpenses() {
        show(InsightsUiState(isLoading = false, insights = sample))
        composeRule.onNode(hasText("Purchases received")).assertExists()
        composeRule.onNode(hasText("this is not profit", substring = true)).assertExists()
    }

    @Test
    fun aPeriodWithPurchasesButNoSales_stillShowsTheChartsWithAnEmptySalesTrend() {
        val purchasesOnly = sample.copy(
            sales = Money.ZERO, billCount = 0,
            paymentMethods = emptyList(), departments = emptyList(), topProducts = emptyList(),
        )
        show(InsightsUiState(isLoading = false, insights = purchasesOnly))
        composeRule.onNode(hasText("No sales in this period.")).assertExists()
        composeRule.onNodeWithTag("chart_bars").assertExists()
        composeRule.onNode(hasText("No payments in this period.")).assertExists()
    }

    @Test
    fun theSelectedPeriod_isMarked_andTappingAnotherReportsIt() {
        var chosen: InsightsPeriod? = null
        show(InsightsUiState(period = InsightsPeriod.LAST_7_DAYS, isLoading = false, insights = sample), onPeriod = { chosen = it })
        composeRule.onNodeWithTag("period_LAST_7_DAYS").assertIsSelected()
        composeRule.onNodeWithTag("period_THIS_MONTH").performClick()
        assertEquals(InsightsPeriod.THIS_MONTH, chosen)
    }

    @Test
    fun stockHealth_isHiddenUntilThereAreProducts() {
        show(InsightsUiState(isLoading = false, insights = sample), summary = InventorySummary.EMPTY)
        composeRule.onNodeWithTag("card_stock").assertDoesNotExist()
    }
}
