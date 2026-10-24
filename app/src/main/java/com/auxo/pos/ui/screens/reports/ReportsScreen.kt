// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.reports

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.export.FileExport
import com.auxo.pos.core.format.MoneyFormatter
import com.auxo.pos.domain.model.formatRatePercent
import com.auxo.pos.domain.reports.ColumnAlign
import com.auxo.pos.domain.reports.ExpiryWindow
import com.auxo.pos.domain.reports.ItemKind
import com.auxo.pos.domain.reports.PeriodPreset
import com.auxo.pos.domain.reports.ReportPaging
import com.auxo.pos.domain.reports.ReportTable
import com.auxo.pos.domain.reports.ReportType
import com.auxo.pos.domain.reports.ReportValue
import com.auxo.pos.ui.LocalCapabilities
import com.auxo.pos.ui.selectableCategories
import com.auxo.pos.ui.LocalSuppliersEnabled
import com.auxo.pos.ui.components.ActionIcon
import com.auxo.pos.ui.components.DateField
import com.auxo.pos.ui.components.DropdownField
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.screens.more.label
import com.auxo.pos.ui.screens.stock.labelRes
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ReportsEvent
import com.auxo.pos.viewmodel.ReportsUiState
import com.auxo.pos.viewmodel.ReportsViewModel
import com.auxo.pos.viewmodel.usesCategory
import com.auxo.pos.viewmodel.usesExpiryWindow
import com.auxo.pos.viewmodel.usesItemKind
import com.auxo.pos.viewmodel.usesOutstanding
import com.auxo.pos.viewmodel.availableReportTypes
import com.auxo.pos.viewmodel.usesSupplier
import java.time.LocalDate

@StringRes
fun ReportType.labelRes(): Int = when (this) {
    ReportType.SALES_SUMMARY -> R.string.report_sales_summary
    ReportType.ITEM_SALES -> R.string.report_item_sales
    ReportType.COLLECTIONS -> R.string.report_collections
    ReportType.GST_RATES -> R.string.report_gst_rates
    ReportType.HSN_SUMMARY -> R.string.report_hsn
    ReportType.PURCHASES -> R.string.report_purchases
    ReportType.STOCK_MOVEMENTS -> R.string.report_movements
    ReportType.WASTAGE -> R.string.report_wastage
    ReportType.STOCK_VALUE -> R.string.report_stock_value
    ReportType.LOW_STOCK -> R.string.report_low_stock
    ReportType.EXPIRY -> R.string.report_expiry
    ReportType.CUSTOMER_BALANCES -> R.string.report_customer_balances
}

@StringRes
fun PeriodPreset.labelRes(): Int = when (this) {
    PeriodPreset.TODAY -> R.string.history_range_today
    PeriodPreset.YESTERDAY -> R.string.period_yesterday
    PeriodPreset.LAST_7_DAYS -> R.string.history_range_7_days
    PeriodPreset.THIS_MONTH -> R.string.history_range_month
    PeriodPreset.LAST_MONTH -> R.string.period_last_month
    PeriodPreset.THIS_FINANCIAL_YEAR -> R.string.period_financial_year
    PeriodPreset.ALL -> R.string.history_range_all
    PeriodPreset.CUSTOM -> R.string.period_custom
}

@StringRes
fun ItemKind.labelRes(): Int = when (this) {
    ItemKind.ALL -> R.string.filter_all
    ItemKind.PRODUCTS -> R.string.item_kind_products
    ItemKind.DISHES -> R.string.item_kind_dishes
}

/** How a report cell reads on screen; exported files use [com.auxo.pos.domain.reports.ReportCsv] instead. */
fun ReportValue.displayText(formatter: MoneyFormatter): String = when (this) {
    is ReportValue.Text -> value
    is ReportValue.Count -> value.toString()
    is ReportValue.Amount -> formatter.format(value)
    is ReportValue.Quantity -> unit.format(value)
    is ReportValue.Day -> DateFormats.date(value)
    is ReportValue.Moment -> DateFormats.dateTime(epochMillis)
    is ReportValue.Rate -> formatRatePercent(basisPoints) + "%"
    ReportValue.Empty -> ""
}

/** Callbacks of the reports screen, so its content can be shown in tests without a ViewModel. */
interface ReportsActions {
    fun onTypeChange(type: ReportType) = Unit
    fun onPresetChange(preset: PeriodPreset) = Unit
    fun onCustomRangeChange(from: LocalDate?, to: LocalDate?) = Unit
    fun onCategoryChange(categoryId: Long?) = Unit
    fun onSupplierChange(supplierId: Long?) = Unit
    fun onItemKindChange(kind: ItemKind) = Unit
    fun onExpiryWindowChange(window: ExpiryWindow) = Unit
    fun onOnlyOutstandingChange(only: Boolean) = Unit
    fun onRetry() = Unit
}

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReportsEvent.Export ->
                    if (!FileExport.share(context, event.file, resources.getString(R.string.export_chooser))) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.export_failed))
                    }
            }
        }
    }

    val actions = remember(viewModel) {
        object : ReportsActions {
            override fun onTypeChange(type: ReportType) = viewModel.onTypeChange(type)
            override fun onPresetChange(preset: PeriodPreset) = viewModel.onPresetChange(preset)
            override fun onCustomRangeChange(from: LocalDate?, to: LocalDate?) = viewModel.onCustomRangeChange(from, to)
            override fun onCategoryChange(categoryId: Long?) = viewModel.onCategoryChange(categoryId)
            override fun onSupplierChange(supplierId: Long?) = viewModel.onSupplierChange(supplierId)
            override fun onItemKindChange(kind: ItemKind) = viewModel.onItemKindChange(kind)
            override fun onExpiryWindowChange(window: ExpiryWindow) = viewModel.onExpiryWindowChange(window)
            override fun onOnlyOutstandingChange(only: Boolean) = viewModel.onOnlyOutstandingChange(only)
            override fun onRetry() = viewModel.retry()
        }
    }

    Scaffold(
        topBar = {
            AuxoTopAppBar(
                title = stringResource(R.string.reports_title),
                onBack = onBack,
                actions = {
                    if (uiState.table != null && !uiState.isLoading) {
                        ActionIcon(R.drawable.ic_download, stringResource(R.string.action_export_csv), viewModel::export)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        ReportsContent(uiState, actions, Modifier.padding(padding))
    }
}

@Composable
fun ReportsContent(
    state: ReportsUiState,
    actions: ReportsActions,
    modifier: Modifier = Modifier,
) {
    val filters = state.filters
    val type = filters.type
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
            .testTag("reports"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val typeLabels = ReportType.entries.associateWith { stringResource(it.labelRes()) }
        val suppliersOn = LocalSuppliersEnabled.current
        val capabilities = LocalCapabilities.current
        DropdownField(
            label = stringResource(R.string.report_type),
            value = typeLabels.getValue(type),
            options = availableReportTypes(suppliersOn, capabilities::can),
            optionText = { typeLabels.getValue(it) },
            onSelect = actions::onTypeChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_type"),
        )
        if (type.usesPeriod) {
            ChipRow {
                PeriodPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = filters.preset == preset,
                        onClick = { actions.onPresetChange(preset) },
                        label = { Text(stringResource(preset.labelRes())) },
                    )
                }
            }
            if (filters.preset == PeriodPreset.CUSTOM) {
                DateField(
                    label = stringResource(R.string.report_from),
                    date = state.period?.from,
                    onDateChange = { actions.onCustomRangeChange(it, filters.customTo ?: state.period?.to) },
                    clearable = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = stringResource(R.string.report_to),
                    date = state.period?.to,
                    onDateChange = { actions.onCustomRangeChange(filters.customFrom ?: state.period?.from, it) },
                    clearable = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val period = state.period
            Text(
                text = when {
                    period == null -> ""
                    period.from == null && period.to == null -> stringResource(R.string.report_period_all)
                    else -> stringResource(
                        R.string.report_period,
                        period.from?.let(DateFormats::date).orEmpty(),
                        period.to?.let(DateFormats::date).orEmpty(),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.report_as_of_today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (type.usesCategory) {
            ChipRow {
                FilterChip(selected = filters.categoryId == null, onClick = { actions.onCategoryChange(null) }, label = { Text(stringResource(R.string.movements_all_items)) })
                selectableCategories(alsoShow = filters.categoryId).forEach { row ->
                    FilterChip(
                        selected = filters.categoryId == row.id,
                        onClick = { actions.onCategoryChange(row.id) },
                        label = { Text(row.label()) },
                        modifier = Modifier.testTag("report_category_${row.id}"),
                    )
                }
            }
        }
        if (type.usesSupplier) {
            val all = stringResource(R.string.report_all_suppliers)
            DropdownField(
                label = stringResource(R.string.order_supplier),
                value = state.suppliers.firstOrNull { it.id == filters.supplierId }?.name ?: all,
                options = listOf<Long?>(null) + state.suppliers.map { it.id },
                optionText = { id -> state.suppliers.firstOrNull { it.id == id }?.name ?: all },
                onSelect = actions::onSupplierChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (type.usesItemKind) {
            ChipRow {
                ItemKind.entries.forEach { kind ->
                    FilterChip(selected = filters.itemKind == kind, onClick = { actions.onItemKindChange(kind) }, label = { Text(stringResource(kind.labelRes())) })
                }
            }
        }
        if (type.usesExpiryWindow) {
            ChipRow {
                ExpiryWindow.entries.forEach { window ->
                    FilterChip(
                        selected = filters.expiryWindow == window,
                        onClick = { actions.onExpiryWindowChange(window) },
                        label = { Text(stringResource(window.labelRes())) },
                    )
                }
            }
        }
        if (type.usesOutstanding) {
            FilterChip(
                selected = filters.onlyOutstanding,
                onClick = { actions.onOnlyOutstandingChange(!filters.onlyOutstanding) },
                label = { Text(stringResource(R.string.report_only_outstanding)) },
            )
        }

        val table = state.table
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.loadFailed -> ErrorState(stringResource(R.string.error_generic), actions::onRetry, Modifier.fillMaxWidth())
            table == null || table.isEmpty -> Text(
                stringResource(R.string.report_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.lg),
            )
            else -> ReportTableView(table)
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        content()
    }
}

/** The report as a table that scrolls sideways, with its totals row in bold. */
@Composable
fun ReportTableView(table: ReportTable, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    val widths = table.columns.mapIndexed { index, column ->
        when {
            index == 0 -> 160.dp
            column.align == ColumnAlign.END -> 112.dp
            else -> 140.dp
        }
    }
    // Back to the first page when a different report or fewer rows arrive; a refresh keeps the page.
    var requestedPage by rememberSaveable(table.columns) { mutableIntStateOf(0) }
    val page = ReportPaging.clamp(requestedPage, table.rows.size)
    val shown = ReportPaging.range(page, table.rows.size)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            pluralStringResource(R.plurals.report_rows, table.rows.size, table.rows.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AuxoCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.horizontalScroll(rememberScrollState()).testTag("report_table")) {
                TableRow(
                    cells = table.columns.map { it.title },
                    aligns = table.columns.map { it.align },
                    widths = widths,
                    bold = true,
                    background = true,
                )
                table.rows.slice(shown).forEach { row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TableRow(row.map { it.displayText(formatter) }, table.columns.map { it.align }, widths)
                }
                table.totals?.let { totals ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TableRow(totals.map { it.displayText(formatter) }, table.columns.map { it.align }, widths, bold = true)
                }
            }
        }
        if (table.rows.size > ReportPaging.PAGE_SIZE) {
            val pages = ReportPaging.pageCount(table.rows.size)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { requestedPage = page - 1 }, enabled = page > 0, modifier = Modifier.testTag("report_previous")) {
                    Text(stringResource(R.string.report_previous_page))
                }
                Text(
                    stringResource(R.string.report_page_position, shown.first + 1, shown.last + 1, table.rows.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("report_page_position"),
                )
                TextButton(onClick = { requestedPage = page + 1 }, enabled = page < pages - 1, modifier = Modifier.testTag("report_next")) {
                    Text(stringResource(R.string.report_next_page))
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    aligns: List<ColumnAlign>,
    widths: List<Dp>,
    bold: Boolean = false,
    background: Boolean = false,
) {
    Row(
        modifier = if (background) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier,
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = if (aligns.getOrNull(index) == ColumnAlign.END) TextAlign.End else TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(widths.getOrElse(index) { 120.dp })
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}
