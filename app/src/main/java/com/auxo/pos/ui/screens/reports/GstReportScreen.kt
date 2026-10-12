package com.auxo.pos.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.export.ExportFile
import com.auxo.pos.core.export.FileExport
import com.auxo.pos.core.export.XlsxWriter
import com.auxo.pos.domain.gst.GstExport
import com.auxo.pos.domain.gst.GstReport
import com.auxo.pos.ui.components.DateField
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.GstPeriodMode
import com.auxo.pos.viewmodel.GstReportUiState
import com.auxo.pos.viewmodel.GstReportViewModel
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

interface GstReportActions {
    fun onModeChange(mode: GstPeriodMode) = Unit
    fun onPrevious() = Unit
    fun onNext() = Unit
    fun onRangeChange(from: java.time.LocalDate, to: java.time.LocalDate) = Unit
    fun onFinancialYear() = Unit
    fun onRetry() = Unit
    fun onExportExcel() = Unit
    fun onExportCsv() = Unit
}

/** GST for a month or date range, with Excel and CSV export. Super Admin only. */
@Composable
fun GstReportScreen(
    onBack: () -> Unit,
    viewModel: GstReportViewModel = viewModel(factory = GstReportViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current

    fun share(report: GstReport, excel: Boolean) {
        scope.launch {
            val title = resources.getString(R.string.gst_export_title)
            val name = GstExport.baseName(report)
            val ok = if (excel) {
                val bytes = XlsxWriter.write(report.tables, viewModel.zone)
                FileExport.shareBytes(context, "$name.xlsx", bytes, XlsxWriter.MIME_TYPE, title)
            } else {
                FileExport.share(context, ExportFile("$name.csv", GstExport.csv(report, viewModel.zone)), title)
            }
            if (!ok) snackbarHostState.showSnackbar(resources.getString(R.string.gst_export_failed))
        }
    }

    val actions = remember(viewModel) {
        object : GstReportActions {
            override fun onModeChange(mode: GstPeriodMode) = viewModel.onModeChange(mode)
            override fun onPrevious() = viewModel.previousMonth()
            override fun onNext() = viewModel.nextMonth()
            override fun onRangeChange(from: java.time.LocalDate, to: java.time.LocalDate) = viewModel.onRangeChange(from, to)
            override fun onFinancialYear() = viewModel.thisFinancialYear()
            override fun onRetry() = viewModel.load()
            override fun onExportExcel() { viewModel.uiState.value.report?.let { share(it, excel = true) } }
            override fun onExportCsv() { viewModel.uiState.value.report?.let { share(it, excel = false) } }
        }
    }

    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.gst_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        GstReportContent(state, actions, Modifier.padding(padding))
    }
}

@Composable
fun GstReportContent(state: GstReportUiState, actions: GstReportActions, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("gst_report"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "period") { PeriodPicker(state, actions) }
        val report = state.report
        when {
            state.isLoading && report == null -> item(key = "loading") { LoadingState(Modifier.fillMaxWidth()) }
            state.loadFailed -> item(key = "failed") { ErrorState(stringResource(R.string.error_generic), actions::onRetry, Modifier.fillMaxWidth()) }
            report == null -> Unit
            report.isEmpty -> item(key = "empty") {
                EmptyState(
                    iconRes = R.drawable.ic_reports,
                    title = stringResource(R.string.gst_empty_title),
                    message = stringResource(R.string.gst_empty_body),
                    modifier = Modifier.fillMaxWidth().testTag("gst_empty"),
                )
            }
            else -> {
                item(key = "headline") {
                    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("gst_headline")) {
                        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(stringResource(R.string.gst_headline_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.gst_headline_counts, report.sales, report.creditNotes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Headline(stringResource(R.string.gst_taxable), formatter.format(report.net.taxable), "gst_net_taxable")
                            Headline("CGST", formatter.format(report.net.cgst), "gst_net_cgst")
                            Headline("SGST", formatter.format(report.net.sgst), "gst_net_sgst")
                            Headline("IGST", formatter.format(report.net.igst), "gst_net_igst")
                            Headline(stringResource(R.string.gst_total_tax), formatter.format(report.net.tax), "gst_net_tax")
                            Headline(stringResource(R.string.gst_invoice_value), formatter.format(report.net.invoiceValue), "gst_net_value")
                        }
                    }
                }
                item(key = "note") {
                    Text(stringResource(R.string.gst_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(report.tables.filter { !it.isEmpty }, key = { "table_${it.title}" }) { table ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SectionHeader(table.title)
                        ReportTableView(table)
                    }
                }
                item(key = "export") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = actions::onExportExcel, modifier = Modifier.weight(1f).testTag("gst_export_excel")) {
                            Text(stringResource(R.string.gst_export_excel))
                        }
                        OutlinedButton(onClick = actions::onExportCsv, modifier = Modifier.weight(1f).testTag("gst_export_csv")) {
                            Text(stringResource(R.string.gst_export_csv))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Headline(label: String, value: String, tag: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun PeriodPicker(state: GstReportUiState, actions: GstReportActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(
                selected = state.mode == GstPeriodMode.MONTH,
                onClick = { actions.onModeChange(GstPeriodMode.MONTH) },
                label = { Text(stringResource(R.string.gst_mode_month)) },
                modifier = Modifier.testTag("gst_mode_month"),
            )
            FilterChip(
                selected = state.mode == GstPeriodMode.RANGE,
                onClick = { actions.onModeChange(GstPeriodMode.RANGE) },
                label = { Text(stringResource(R.string.gst_mode_range)) },
                modifier = Modifier.testTag("gst_mode_range"),
            )
        }
        when (state.mode) {
            GstPeriodMode.MONTH -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = actions::onPrevious, modifier = Modifier.testTag("gst_previous")) { Text(stringResource(R.string.gst_previous)) }
                Text(
                    state.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + state.month.year,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag("gst_month"),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = actions::onNext, enabled = state.canGoNext, modifier = Modifier.testTag("gst_next")) { Text(stringResource(R.string.gst_next)) }
            }
            GstPeriodMode.RANGE -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DateField(
                    label = stringResource(R.string.report_from),
                    date = state.from,
                    onDateChange = { it?.let { from -> actions.onRangeChange(from, state.to) } },
                    clearable = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = stringResource(R.string.report_to),
                    date = state.to,
                    onDateChange = { it?.let { to -> actions.onRangeChange(state.from, to) } },
                    clearable = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = false,
                    onClick = actions::onFinancialYear,
                    label = { Text(stringResource(R.string.gst_financial_year)) },
                    modifier = Modifier.testTag("gst_financial_year"),
                )
            }
        }
        val period = state.period
        Text(
            stringResource(R.string.gst_period, period.from?.let(DateFormats::date).orEmpty(), period.to?.let(DateFormats::date).orEmpty()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("gst_period"),
        )
    }
}
