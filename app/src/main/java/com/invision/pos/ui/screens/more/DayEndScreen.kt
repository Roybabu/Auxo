package com.invision.pos.ui.screens.more

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.core.dayend.DayClosingDocument
import com.invision.pos.core.dayend.DaySummary
import com.invision.pos.core.dayend.SummaryLine
import com.invision.pos.core.format.MoneyFormatter
import com.invision.pos.domain.dayend.CashComparison
import com.invision.pos.domain.dayend.CashStatus
import com.invision.pos.domain.dayend.CountError
import com.invision.pos.domain.dayend.DayClosing
import com.invision.pos.domain.dayend.DayFigures
import com.invision.pos.ui.components.ConfirmDialog
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.format.DateFormats
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.DayClosingUiState
import com.invision.pos.viewmodel.DayClosingViewModel
import com.invision.pos.viewmodel.DayEndEvent
import com.invision.pos.viewmodel.DayEndUiState
import com.invision.pos.viewmodel.DayEndViewModel
import kotlinx.coroutines.launch

/** "Short by ₹50", "Over by ₹1.50" or "Cash matches". */
@Composable
internal fun differenceText(comparison: CashComparison, formatter: MoneyFormatter): String = when (comparison.status) {
    CashStatus.MATCHES -> stringResource(R.string.dayend_matches)
    CashStatus.SHORT -> stringResource(R.string.dayend_short, formatter.format(com.invision.pos.domain.model.Money(-comparison.difference.minorUnits)))
    CashStatus.OVER -> stringResource(R.string.dayend_over, formatter.format(comparison.difference))
}

/** Count the cash, see the difference, close the day; and the days closed before. */
@Composable
fun DayEndScreen(
    onBack: () -> Unit,
    onOpenClosing: (Long) -> Unit,
    viewModel: DayEndViewModel = viewModel(factory = DayEndViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DayEndEvent.Closed -> {
                    snackbarHostState.showSnackbar(resources.getString(R.string.dayend_saved))
                }
                DayEndEvent.AlreadyClosed -> snackbarHostState.showSnackbar(resources.getString(R.string.dayend_already_closed))
                DayEndEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.dayend_failed))
            }
        }
    }

    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.dayend_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        DayEndContent(
            state = state,
            onCountedChange = viewModel::onCountedChange,
            onNoteChange = viewModel::onNoteChange,
            onRefresh = viewModel::refresh,
            onClose = viewModel::requestClose,
            onConfirm = viewModel::confirmClose,
            onCancel = viewModel::cancelClose,
            onRetry = viewModel::load,
            onOpenClosing = onOpenClosing,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun DayEndContent(
    state: DayEndUiState,
    onCountedChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenClosing: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalMoneyFormatter.current
    when {
        state.isLoading -> LoadingState(modifier)
        state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry, modifier)
        else -> LazyColumn(
            modifier = modifier.fillMaxSize().testTag("day_end"),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val closed = state.closedToday
            val figures = state.figures
            if (closed != null) {
                item(key = "closed") { ClosedTodayCard(closed, formatter) { onOpenClosing(closed.id) } }
            } else if (figures != null) {
                item(key = "date") {
                    Text(
                        stringResource(R.string.dayend_today, state.date?.let { DateFormats.date(it) }.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item(key = "figures") { FiguresCard(figures, formatter) }
                item(key = "count") { CountCard(state, figures, onCountedChange, onNoteChange) }
                item(key = "actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f).testTag("day_end_refresh")) {
                            Text(stringResource(R.string.dayend_refresh))
                        }
                        Button(onClick = onClose, enabled = !state.isSaving, modifier = Modifier.weight(1f).testTag("day_end_close")) {
                            Text(stringResource(R.string.dayend_close_button))
                        }
                    }
                }
            }
            item(key = "history_title") { SectionHeader(stringResource(R.string.dayend_history)) }
            if (state.history.isEmpty()) {
                item(key = "history_empty") {
                    Text(
                        stringResource(R.string.dayend_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("day_end_history_empty"),
                    )
                }
            }
            items(state.history, key = { "closing_${it.id}" }) { closing -> HistoryRow(closing, formatter) { onOpenClosing(closing.id) } }
        }
    }

    val figures = state.figures
    val comparison = state.comparison
    if (state.confirming && figures != null && comparison != null) {
        ConfirmDialog(
            title = stringResource(R.string.dayend_confirm_title, state.date?.let { DateFormats.date(it) }.orEmpty()),
            message = stringResource(
                R.string.dayend_confirm_body,
                formatter.format(comparison.expected),
                formatter.format(comparison.counted),
                differenceText(comparison, formatter),
            ),
            confirmLabel = stringResource(R.string.dayend_confirm_action),
            onConfirm = onConfirm,
            onDismiss = onCancel,
        )
    }
}

@Composable
private fun FigureRow(label: String, value: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    // One reading for the label and its amount, for screen readers and for tests that read the row.
    Row(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else null)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else null)
    }
}

@Composable
private fun FiguresCard(figures: DayFigures, formatter: MoneyFormatter) {
    InvisionCard(modifier = Modifier.fillMaxWidth().testTag("day_end_figures")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.dayend_sales_section), style = MaterialTheme.typography.titleSmall)
            FigureRow(stringResource(R.string.dayend_bills, figures.bills), "")
            FigureRow(stringResource(R.string.dayend_returns, figures.returns), "")
            FigureRow(stringResource(R.string.dayend_net_sales), formatter.format(figures.netSales))

            Text(stringResource(R.string.dayend_cash_section), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
            FigureRow(stringResource(R.string.dayend_cash_received), formatter.format(figures.cashReceived))
            FigureRow(stringResource(R.string.dayend_cash_refunded), formatter.format(figures.cashRefunded))
            FigureRow(stringResource(R.string.dayend_cash_accounts), formatter.format(figures.cashFromAccounts))
            FigureRow(stringResource(R.string.dayend_expected), formatter.format(figures.expectedCash), Modifier.testTag("day_end_expected"), bold = true)

            Text(stringResource(R.string.dayend_other_section), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
            FigureRow(stringResource(R.string.dayend_upi), formatter.format(figures.upiNet))
            FigureRow(stringResource(R.string.dayend_card), formatter.format(figures.cardNet))
            FigureRow(stringResource(R.string.dayend_on_account), formatter.format(figures.onAccountNet))
        }
    }
}

@Composable
private fun CountCard(state: DayEndUiState, figures: DayFigures, onCountedChange: (String) -> Unit, onNoteChange: (String) -> Unit) {
    val formatter = LocalMoneyFormatter.current
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            InvisionTextField(
                value = state.counted,
                onValueChange = onCountedChange,
                label = stringResource(R.string.dayend_counted_label),
                prefix = stringResource(R.string.currency_prefix),
                supportingText = stringResource(R.string.dayend_counted_hint),
                error = when (state.countError) {
                    CountError.REQUIRED -> stringResource(R.string.dayend_count_required)
                    CountError.INVALID -> stringResource(R.string.dayend_count_invalid)
                    null -> null
                },
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth().testTag("day_end_counted"),
            )
            val comparison = state.comparison
            if (comparison != null) {
                Text(
                    differenceText(comparison, formatter),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (comparison.status == CashStatus.MATCHES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("day_end_difference"),
                )
            }
            InvisionTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = stringResource(R.string.dayend_note_label),
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth().testTag("day_end_note"),
            )
        }
    }
}

@Composable
private fun ClosedTodayCard(closing: DayClosing, formatter: MoneyFormatter, onOpen: () -> Unit) {
    InvisionCard(modifier = Modifier.fillMaxWidth().testTag("day_end_closed")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.dayend_closed_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.dayend_closed_body,
                    closing.closedBy,
                    DateFormats.dateTime(closing.closedAtMillis),
                    formatter.format(closing.counted),
                    differenceText(closing.comparison, formatter),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (closing.billsAfterClosing > 0) {
                Text(
                    stringResource(R.string.dayend_after_closing, closing.billsAfterClosing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("day_end_after_closing"),
                )
            }
            OutlinedButton(onClick = onOpen, modifier = Modifier.testTag("day_end_open_summary")) {
                Text(stringResource(R.string.dayend_open_summary))
            }
        }
    }
}

@Composable
private fun HistoryRow(closing: DayClosing, formatter: MoneyFormatter, onOpen: () -> Unit) {
    InvisionCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("closing_${closing.id}")) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(DateFormats.date(closing.date), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.dayend_history_item, formatter.format(closing.figures.expectedCash), formatter.format(closing.counted)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                differenceText(closing.comparison, formatter),
                style = MaterialTheme.typography.bodySmall,
                color = if (closing.comparison.status == CashStatus.MATCHES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** A closed day, read only, with print and share. */
@Composable
fun DayClosingScreen(
    onBack: () -> Unit,
    viewModel: DayClosingViewModel = viewModel(factory = DayClosingViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val summary = state.closing?.let { daySummary(it, LocalMoneyFormatter.current) }

    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.dayend_summary_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        DayClosingContent(
            state = state,
            summary = summary,
            onRetry = viewModel::load,
            onPrint = {
                if (summary != null && !DayClosingDocument.print(context, summary)) {
                    scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.dayend_output_failed)) }
                }
            },
            onShare = {
                if (summary != null) {
                    scope.launch {
                        if (!DayClosingDocument.share(context, summary, resources.getString(R.string.dayend_share_title))) {
                            snackbarHostState.showSnackbar(resources.getString(R.string.dayend_output_failed))
                        }
                    }
                }
            },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun DayClosingContent(
    state: DayClosingUiState,
    summary: DaySummary?,
    onRetry: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry, modifier)
        state.notFound || summary == null -> EmptyState(
            iconRes = R.drawable.ic_history,
            title = stringResource(R.string.dayend_not_found),
            message = "",
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize().testTag("day_closing"),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "head") {
                Column {
                    Text(summary.title, style = MaterialTheme.typography.titleLarge)
                    Text(summary.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "lines") {
                InvisionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        summary.lines.forEach { line ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = if (line.gapBefore) Spacing.sm else Spacing.xs)) {
                                Text(line.label, modifier = Modifier.weight(1f), fontWeight = if (line.bold) FontWeight.SemiBold else null)
                                if (line.value.isNotEmpty()) Text(line.value, fontWeight = if (line.bold) FontWeight.SemiBold else null)
                            }
                        }
                    }
                }
            }
            state.closing?.takeIf { it.billsAfterClosing > 0 }?.let { closing ->
                item(key = "after") {
                    Text(
                        stringResource(R.string.dayend_after_closing, closing.billsAfterClosing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).testTag("day_closing_share")) { Text(stringResource(R.string.dayend_share)) }
                    Button(onClick = onPrint, modifier = Modifier.weight(1f).testTag("day_closing_print")) { Text(stringResource(R.string.dayend_print)) }
                }
            }
        }
    }
}

/** The closing worded for the screen and for the printed page: same lines in both. */
@Composable
internal fun daySummary(closing: DayClosing, formatter: MoneyFormatter): DaySummary {
    val f = closing.figures
    val lines = listOf(
        SummaryLine(stringResource(R.string.dayend_bills, f.bills)),
        SummaryLine(stringResource(R.string.dayend_returns, f.returns)),
        SummaryLine(stringResource(R.string.dayend_net_sales), formatter.format(f.netSales), bold = true),
        SummaryLine(stringResource(R.string.dayend_cash_section), gapBefore = true, bold = true),
        SummaryLine(stringResource(R.string.dayend_cash_received), formatter.format(f.cashReceived)),
        SummaryLine(stringResource(R.string.dayend_cash_refunded), formatter.format(f.cashRefunded)),
        SummaryLine(stringResource(R.string.dayend_cash_accounts), formatter.format(f.cashFromAccounts)),
        SummaryLine(stringResource(R.string.dayend_expected), formatter.format(f.expectedCash), bold = true),
        SummaryLine(stringResource(R.string.dayend_summary_counted), formatter.format(closing.counted), bold = true),
        SummaryLine(stringResource(R.string.dayend_summary_difference), differenceText(closing.comparison, formatter), bold = true),
        SummaryLine(stringResource(R.string.dayend_other_section), gapBefore = true, bold = true),
        SummaryLine(stringResource(R.string.dayend_upi), formatter.format(f.upiNet)),
        SummaryLine(stringResource(R.string.dayend_card), formatter.format(f.cardNet)),
        SummaryLine(stringResource(R.string.dayend_on_account), formatter.format(f.onAccountNet)),
    ) + listOfNotNull(closing.note?.let { SummaryLine(it, gapBefore = true) })
    val date = DateFormats.date(closing.date)
    return DaySummary(
        title = stringResource(R.string.dayend_summary_title) + " · " + date,
        subtitle = stringResource(R.string.dayend_summary_subtitle, date, DateFormats.dateTime(closing.closedAtMillis), closing.closedBy),
        lines = lines,
        footer = stringResource(R.string.dayend_summary_footer),
        name = "Day-end-${closing.date}",
    )
}
