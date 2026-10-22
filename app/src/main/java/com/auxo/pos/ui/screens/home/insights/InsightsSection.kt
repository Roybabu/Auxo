package com.auxo.pos.ui.screens.home.insights

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.core.format.MoneyFormatter
import com.auxo.pos.domain.insights.CompactMoney
import com.auxo.pos.domain.insights.Department
import com.auxo.pos.domain.insights.Insights
import com.auxo.pos.domain.insights.InsightsPeriod
import com.auxo.pos.domain.inventory.InventorySummary
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.reports.Reports
import com.auxo.pos.ui.LocalSuppliersEnabled
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.Red700
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.InsightsUiState

/**
 * The financial charts at the bottom of the Home panel: a date filter, then charts grouped
 * under Sales, Products, Costs and Stock. Everything shown comes from saved bills, goods
 * receipts and stock; where Auxo keeps no data (expenses), the chart says so instead.
 */
@Composable
fun InsightsSection(
    state: InsightsUiState,
    inventory: InventorySummary,
    onPeriodChange: (InsightsPeriod) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalMoneyFormatter.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("insights"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(text = stringResource(R.string.insights_title), style = MaterialTheme.typography.headlineSmall)
            PeriodFilter(selected = state.period, onSelect = onPeriodChange)
            state.insights?.let { RangeCaption(it) }
        }
        val insights = state.insights
        when {
            state.isLoading -> InsightsLoading()
            state.failed || insights == null -> InsightsError(onRetry)
            !insights.hasSales && (!insights.hasPurchases || !LocalSuppliersEnabled.current) -> MessageCard(stringResource(R.string.insights_empty), tag = "insights_empty")
            else -> InsightsContent(insights, formatter)
        }
        if (inventory.hasProducts) {
            Group(stringResource(R.string.insights_group_stock)) { StockHealthCard(inventory) }
        }
    }
}

@Composable
private fun PeriodFilter(selected: InsightsPeriod, onSelect: (InsightsPeriod) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        InsightsPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(periodLabel(period)) },
                modifier = Modifier.testTag("period_${period.name}"),
            )
        }
    }
}

@Composable
private fun periodLabel(period: InsightsPeriod): String = stringResource(
    when (period) {
        InsightsPeriod.TODAY -> R.string.insights_period_today
        InsightsPeriod.LAST_7_DAYS -> R.string.insights_period_7_days
        InsightsPeriod.THIS_MONTH -> R.string.insights_period_month
        InsightsPeriod.THIS_YEAR -> R.string.insights_period_year
    },
)

@Composable
private fun RangeCaption(insights: Insights) {
    val text = if (insights.from == insights.to) {
        DateFormats.date(insights.from)
    } else {
        stringResource(R.string.insights_range, DateFormats.date(insights.from), DateFormats.date(insights.to))
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InsightsLoading() {
    val label = stringResource(R.string.loading)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .testTag("insights_loading"),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun InsightsError(onRetry: () -> Unit) {
    AuxoCard(modifier = Modifier
        .fillMaxWidth()
        .testTag("insights_error")) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(text = stringResource(R.string.insights_error), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun MessageCard(message: String, tag: String) {
    AuxoCard(modifier = Modifier
        .fillMaxWidth()
        .testTag(tag)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@Composable
private fun InsightsContent(insights: Insights, formatter: MoneyFormatter) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Group(stringResource(R.string.insights_group_sales)) {
            TrendCard(insights, formatter)
            if (LocalSuppliersEnabled.current) ComparisonCard(insights, formatter)
            PaymentMethodsCard(insights, formatter)
        }
        Group(stringResource(R.string.insights_group_products)) {
            DepartmentsCard(insights, formatter)
            TopProductsCard(insights, formatter)
        }
        if (LocalSuppliersEnabled.current) {
            Group(stringResource(R.string.insights_group_costs)) {
                ExpensesCard()
            }
        }
    }
}

/** A heading with the cards that belong under it. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    AuxoCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TrendCard(insights: Insights, formatter: MoneyFormatter) {
    val bills = pluralStringResource(R.plurals.insights_bills, insights.billCount, insights.billCount)
    ChartCard(
        title = stringResource(R.string.insights_trend_title),
        subtitle = stringResource(R.string.insights_trend_total, formatter.format(insights.sales), bills),
        modifier = Modifier.testTag("card_trend"),
    ) {
        if (!insights.hasSales) {
            Note(stringResource(R.string.insights_trend_empty))
        } else {
            val peak = insights.trend.maxByOrNull { it.amount }
            LineChart(
                points = insights.trend,
                description = stringResource(
                    R.string.insights_trend_description,
                    formatter.format(insights.sales),
                    formatter.format(peak?.amount ?: insights.sales),
                    peak?.label.orEmpty(),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Note(stringResource(R.string.insights_trend_note))
        }
    }
}

@Composable
private fun ComparisonCard(insights: Insights, formatter: MoneyFormatter) {
    ChartCard(
        title = stringResource(R.string.insights_compare_title),
        modifier = Modifier.testTag("card_compare"),
    ) {
        ChartLegend(
            listOf(
                LegendItem(ChartPalette.Green, stringResource(R.string.insights_legend_sales)),
                LegendItem(ChartPalette.Amber, stringResource(R.string.insights_legend_purchases)),
            ),
        )
        PairedBarChart(
            buckets = insights.comparison,
            description = stringResource(
                R.string.insights_compare_description,
                formatter.format(insights.sales),
                formatter.format(insights.purchases),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Note(stringResource(R.string.insights_compare_note))
    }
}

@Composable
private fun methodColor(method: PaymentMethod) = when (method) {
    PaymentMethod.CASH -> ChartPalette.Green
    PaymentMethod.UPI -> ChartPalette.Amber
    PaymentMethod.CARD -> ChartPalette.Blue
    PaymentMethod.CREDIT -> ChartPalette.Slate
}

@Composable
private fun PaymentMethodsCard(insights: Insights, formatter: MoneyFormatter) {
    ChartCard(
        title = stringResource(R.string.insights_payments_title),
        modifier = Modifier.testTag("card_payments"),
    ) {
        if (insights.paymentMethods.isEmpty()) {
            Note(stringResource(R.string.insights_payments_empty))
        } else {
            val slices = insights.paymentMethods.map {
                DonutSlice(
                    label = Reports.methodName(it.method),
                    value = it.amount.minorUnits,
                    color = methodColor(it.method),
                    valueText = formatter.format(it.amount),
                )
            }
            val total = insights.paymentMethods.fold(0L) { acc, slice -> acc + slice.amount.minorUnits }
            DonutChart(
                slices = slices,
                centreLabel = stringResource(R.string.insights_payments_total),
                centreValue = CompactMoney.format(Money(total)),
                description = stringResource(
                    R.string.insights_payments_description,
                    slices.joinToString { "${it.label} ${it.valueText}" },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun departmentName(department: Department): String = stringResource(
    when (department) {
        Department.GARMENTS -> R.string.insights_dept_garments
        Department.FOOTWEAR -> R.string.insights_dept_footwear
        Department.ACCESSORIES -> R.string.insights_dept_accessories
        Department.PACKAGED_FOOD -> R.string.insights_dept_food
        Department.BEVERAGES -> R.string.insights_dept_beverages
        Department.RESTAURANT -> R.string.insights_dept_restaurant
        Department.GENERAL -> R.string.insights_dept_general
        Department.ELECTRONICS -> R.string.insights_dept_electronics
        Department.PHARMACY -> R.string.insights_dept_pharmacy
        Department.COSMETICS -> R.string.insights_dept_cosmetics
        Department.OTHER -> R.string.insights_dept_other
    },
)

@Composable
private fun DepartmentsCard(insights: Insights, formatter: MoneyFormatter) {
    ChartCard(
        title = stringResource(R.string.insights_departments_title),
        modifier = Modifier.testTag("card_departments"),
    ) {
        if (insights.departments.isEmpty()) {
            Note(stringResource(R.string.insights_departments_empty))
        } else {
            val items = insights.departments.map {
                BarItem(
                    label = departmentName(it.department),
                    value = it.amount.minorUnits,
                    valueText = formatter.format(it.amount),
                    color = ChartPalette.Green,
                )
            }
            HorizontalBarChart(
                items = items,
                description = stringResource(
                    R.string.insights_departments_description,
                    items.joinToString { "${it.label} ${it.valueText}" },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Note(stringResource(R.string.insights_departments_note))
        }
    }
}

@Composable
private fun TopProductsCard(insights: Insights, formatter: MoneyFormatter) {
    ChartCard(
        title = stringResource(R.string.insights_top_title),
        modifier = Modifier.testTag("card_top_products"),
    ) {
        if (insights.topProducts.isEmpty()) {
            Note(stringResource(R.string.insights_top_empty))
        } else {
            Column {
                insights.topProducts.forEachIndexed { index, product ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = (index + 1).toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = product.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = stringResource(R.string.insights_top_sold, product.quantity.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(text = formatter.format(product.amount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Auxo records no expenses, so this says so and points to the one cost it does record. */
@Composable
private fun ExpensesCard() {
    ChartCard(
        title = stringResource(R.string.insights_expenses_title),
        modifier = Modifier.testTag("card_expenses"),
    ) {
        Note(stringResource(R.string.insights_expenses_unavailable))
    }
}

@Composable
private fun StockHealthCard(inventory: InventorySummary) {
    val healthy = (inventory.variantCount - inventory.lowStockCount - inventory.outOfStockCount).coerceAtLeast(0)
    val expiring = inventory.expiringSoonCount + inventory.expiredCount
    ChartCard(
        title = stringResource(R.string.insights_stock_title),
        modifier = Modifier.testTag("card_stock"),
    ) {
        StackedBar(
            segments = listOf(
                StackedSegment(stringResource(R.string.insights_stock_ok), healthy, ChartPalette.Green),
                StackedSegment(stringResource(R.string.insights_stock_low), inventory.lowStockCount, ChartPalette.Amber),
                StackedSegment(stringResource(R.string.insights_stock_out), inventory.outOfStockCount, Red700),
            ),
            description = stringResource(R.string.insights_stock_description, healthy, inventory.lowStockCount, inventory.outOfStockCount),
        )
        if (expiring > 0) Note(stringResource(R.string.insights_stock_expiring, expiring))
    }
}
