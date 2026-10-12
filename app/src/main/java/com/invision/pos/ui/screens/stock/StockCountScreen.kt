package com.invision.pos.ui.screens.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.inventory.CountReason
import com.invision.pos.domain.inventory.CountReviewRow
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.ScanOutcome
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.components.StatusChip
import com.invision.pos.ui.components.rememberBarcodeScanner
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.CountCandidate
import com.invision.pos.viewmodel.CountEditor
import com.invision.pos.viewmodel.CountStage
import com.invision.pos.viewmodel.StockCountEvent
import com.invision.pos.viewmodel.StockCountUiState
import com.invision.pos.viewmodel.StockCountViewModel
import kotlin.math.abs

/** Callbacks of the stock count screen, so its content can be shown in tests without a ViewModel. */
interface StockCountActions {
    fun onStart() = Unit
    fun onSearchTextChange(text: String) = Unit
    fun onSearchSubmitted() = Unit
    fun onScan() = Unit
    fun onPick(candidate: CountCandidate) = Unit
    fun onEditRow(row: CountReviewRow) = Unit
    fun onCountTextChange(text: String) = Unit
    fun onSaveCount() = Unit
    fun onDismissEditor() = Unit
    fun onRemoveLine(variantId: Long) = Unit
    fun onReview() = Unit
    fun onBackToCounting() = Unit
    fun onReasonChange(reason: CountReason) = Unit
    fun onNoteChange(note: String) = Unit
    fun onPost() = Unit
    fun onConfirmPost() = Unit
    fun onDismissConfirm() = Unit
    fun onCancelCount() = Unit
    fun onConfirmCancel() = Unit
    fun onDismissCancel() = Unit
    fun onRetry() = Unit
}

@Composable
fun StockCountScreen(
    onBack: () -> Unit,
    viewModel: StockCountViewModel = viewModel(factory = StockCountViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is StockCountEvent.Posted -> if (event.adjusted == 0) {
                    resources.getString(R.string.count_posted_none)
                } else {
                    resources.getQuantityString(R.plurals.count_posted, event.adjusted, event.adjusted)
                }
                is StockCountEvent.Partial -> resources.getString(R.string.count_partial, event.adjusted, event.failed)
                StockCountEvent.Cancelled -> resources.getString(R.string.count_cancelled)
                StockCountEvent.CountClosed -> resources.getString(R.string.count_closed)
                is StockCountEvent.BarcodeNotFound -> resources.getString(R.string.count_barcode_missing, event.barcode)
                StockCountEvent.ScannerUnavailable -> resources.getString(R.string.count_scanner_unavailable)
                StockCountEvent.Failed -> resources.getString(R.string.count_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val scan = rememberBarcodeScanner { outcome ->
        when (outcome) {
            is ScanOutcome.Scanned -> viewModel.onBarcodeScanned(outcome.value)
            ScanOutcome.Unavailable -> viewModel.onScannerUnavailable()
            ScanOutcome.Cancelled -> Unit
        }
    }

    val actions = remember(viewModel, scan) {
        object : StockCountActions {
            override fun onStart() = viewModel.start()
            override fun onSearchTextChange(text: String) = viewModel.onSearchTextChange(text)
            override fun onSearchSubmitted() = viewModel.onSearchSubmitted()
            override fun onScan() = scan()
            override fun onPick(candidate: CountCandidate) = viewModel.openEditor(candidate)
            override fun onEditRow(row: CountReviewRow) = viewModel.openEditor(row)
            override fun onCountTextChange(text: String) = viewModel.onCountTextChange(text)
            override fun onSaveCount() = viewModel.saveCount()
            override fun onDismissEditor() = viewModel.dismissEditor()
            override fun onRemoveLine(variantId: Long) {
                viewModel.removeLine(variantId)
                viewModel.dismissEditor()
            }
            override fun onReview() = viewModel.goToReview()
            override fun onBackToCounting() = viewModel.backToCounting()
            override fun onReasonChange(reason: CountReason) = viewModel.onReasonChange(reason)
            override fun onNoteChange(note: String) = viewModel.onNoteChange(note)
            override fun onPost() = viewModel.requestPost()
            override fun onConfirmPost() = viewModel.confirmPost()
            override fun onDismissConfirm() = viewModel.dismissConfirm()
            override fun onCancelCount() = viewModel.requestCancel()
            override fun onConfirmCancel() = viewModel.confirmCancel()
            override fun onDismissCancel() = viewModel.dismissCancel()
            override fun onRetry() = Unit
        }
    }

    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.count_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        StockCountContent(state, actions, Modifier.padding(padding))
    }
}

@Composable
fun StockCountContent(state: StockCountUiState, actions: StockCountActions, modifier: Modifier = Modifier) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.loadFailed -> ErrorState(stringResource(R.string.error_generic), actions::onRetry, modifier)
        state.count == null -> EmptyState(
            iconRes = R.drawable.ic_nav_inventory,
            title = stringResource(R.string.count_intro_title),
            message = stringResource(R.string.count_intro_body),
            actionLabel = stringResource(R.string.count_start),
            onAction = actions::onStart,
            modifier = modifier.testTag("count_intro"),
        )
        state.stage == CountStage.COUNTING -> CountingStage(state, actions, modifier)
        else -> ReviewStage(state, actions, modifier)
    }

    state.editor?.let { editor ->
        CountEditorDialog(
            editor = editor,
            alreadyCounted = state.count?.line(editor.variant.id) != null,
            onTextChange = actions::onCountTextChange,
            onSave = actions::onSaveCount,
            onRemove = { actions.onRemoveLine(editor.variant.id) },
            onDismiss = actions::onDismissEditor,
        )
    }
    if (state.cancelConfirmOpen) {
        AlertDialog(
            onDismissRequest = actions::onDismissCancel,
            title = { Text(stringResource(R.string.count_cancel_title)) },
            text = { Text(stringResource(R.string.count_cancel_message)) },
            confirmButton = {
                TextButton(onClick = actions::onConfirmCancel, modifier = Modifier.testTag("count_cancel_confirm")) {
                    Text(stringResource(R.string.count_cancel_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = actions::onDismissCancel) { Text(stringResource(R.string.count_keep_counting)) } },
        )
    }
    if (state.confirmOpen) {
        val changes = state.differences.size
        AlertDialog(
            onDismissRequest = actions::onDismissConfirm,
            title = { Text(stringResource(if (changes > 0) R.string.count_confirm_title else R.string.count_finish_title)) },
            text = {
                Text(
                    if (changes > 0) {
                        pluralStringResource(R.plurals.count_confirm_message, changes, changes)
                    } else {
                        stringResource(R.string.count_finish_message)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = actions::onConfirmPost, modifier = Modifier.testTag("count_confirm")) {
                    Text(stringResource(if (changes > 0) R.string.count_post else R.string.count_finish))
                }
            },
            dismissButton = { TextButton(onClick = actions::onDismissConfirm) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CountingStage(state: StockCountUiState, actions: StockCountActions, modifier: Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("count_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "search") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InvisionTextField(
                    value = state.searchText,
                    onValueChange = actions::onSearchTextChange,
                    label = stringResource(R.string.count_search_hint),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("count_search")
                        .onPreviewKeyEvent { event ->
                            // USB and Bluetooth barcode scanners type the code and then press Enter.
                            if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                if (event.type == KeyEventType.KeyDown) actions.onSearchSubmitted()
                                true
                            } else {
                                false
                            }
                        },
                    imeAction = ImeAction.Search,
                    keyboardActions = KeyboardActions(onSearch = { actions.onSearchSubmitted() }),
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                )
                IconButton(onClick = actions::onScan, modifier = Modifier.testTag("count_scan")) {
                    Icon(painterResource(R.drawable.ic_scan), contentDescription = stringResource(R.string.count_scan))
                }
            }
        }
        if (state.searchText.isNotBlank()) {
            if (state.searchResults.isEmpty()) {
                item(key = "no_results") {
                    Text(stringResource(R.string.count_no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.searchResults, key = { "result_${it.variant.id}" }) { candidate ->
                CandidateRow(candidate, counted = state.count?.line(candidate.variant.id) != null, onClick = { actions.onPick(candidate) })
            }
        }
        item(key = "heading") {
            SectionHeader(pluralStringResource(R.plurals.count_counted_items, state.rows.size, state.rows.size), modifier = Modifier.padding(top = Spacing.sm))
        }
        if (state.rows.isEmpty()) {
            item(key = "nothing_yet") {
                Text(stringResource(R.string.count_nothing_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.rows, key = { "row_${it.variant.id}" }) { row ->
            CountedRow(row, showDifference = false, onClick = { actions.onEditRow(row) })
        }
        item(key = "buttons") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                Button(
                    onClick = actions::onReview,
                    enabled = state.rows.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("count_review"),
                ) { Text(stringResource(R.string.count_review)) }
                OutlinedButton(onClick = actions::onCancelCount, modifier = Modifier.fillMaxWidth().testTag("count_cancel")) {
                    Text(stringResource(R.string.count_cancel))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewStage(state: StockCountUiState, actions: StockCountActions, modifier: Modifier) {
    val formatter = LocalMoneyFormatter.current
    val differences = state.differences
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("count_review_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "summary") {
            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(pluralStringResource(R.plurals.count_counted_items, state.summary.counted, state.summary.counted), style = MaterialTheme.typography.titleSmall)
                    if (differences.isEmpty()) {
                        Text(stringResource(R.string.count_review_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("count_no_differences"))
                    } else {
                        Text(
                            pluralStringResource(R.plurals.count_review_differences, differences.size, differences.size),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("count_differences_summary"),
                        )
                        if (state.summary.short > 0) {
                            Text(
                                stringResource(R.string.count_short_value, state.summary.short, formatter.format(state.summary.shortageValue)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = InvisionThemeExtras.statusColors.danger.content,
                            )
                        }
                        if (state.summary.extra > 0) {
                            Text(
                                stringResource(R.string.count_extra_value, state.summary.extra, formatter.format(state.summary.extraValue)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(stringResource(R.string.count_review_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(differences, key = { "diff_${it.variant.id}" }) { row ->
            CountedRow(row, showDifference = true, onClick = { actions.onEditRow(row) })
        }
        item(key = "reason") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.sm)) {
                SectionHeader(stringResource(R.string.count_reason_heading))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CountReason.entries.forEach { reason ->
                        FilterChip(
                            selected = state.reason == reason,
                            onClick = { actions.onReasonChange(reason) },
                            label = { Text(stringResource(reason.labelRes())) },
                            modifier = Modifier.testTag("count_reason_${reason.name}"),
                        )
                    }
                }
                InvisionTextField(
                    value = state.note,
                    onValueChange = actions::onNoteChange,
                    label = stringResource(R.string.count_note_label),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("count_note"),
                )
            }
        }
        item(key = "buttons") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                Button(
                    onClick = actions::onPost,
                    enabled = !state.isPosting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("count_post"),
                ) { Text(stringResource(if (differences.isEmpty()) R.string.count_finish else R.string.count_post)) }
                OutlinedButton(onClick = actions::onBackToCounting, modifier = Modifier.fillMaxWidth().testTag("count_back")) {
                    Text(stringResource(R.string.count_back))
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: CountCandidate, counted: Boolean, onClick: () -> Unit) {
    val variant = candidate.variant
    InvisionCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("count_result_${variant.id}")) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(itemName(candidate.product.name, variant.attributesLabel), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.count_in_stock, candidate.product.stockUnit.format(variant.stockQuantity)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (counted) StatusChip(stringResource(R.string.count_counted_chip), InvisionThemeExtras.statusColors.success)
        }
    }
}

@Composable
private fun CountedRow(row: CountReviewRow, showDifference: Boolean, onClick: () -> Unit) {
    val unit = row.product.stockUnit
    val status = InvisionThemeExtras.statusColors
    InvisionCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("count_row_${row.variant.id}")) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(itemName(row.product.name, row.variant.attributesLabel), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.count_line, unit.format(row.line.expected), unit.format(row.line.counted)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val difference = row.difference
            when {
                difference < 0 -> StatusChip(stringResource(R.string.count_diff_short, unit.format(abs(difference))), status.danger)
                difference > 0 -> StatusChip(stringResource(R.string.count_diff_extra, unit.format(difference)), status.warning)
                !showDifference -> StatusChip(stringResource(R.string.count_diff_match), status.success)
            }
        }
    }
}

@Composable
private fun CountEditorDialog(
    editor: CountEditor,
    alreadyCounted: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(itemName(editor.product.name, editor.variant.attributesLabel)) },
        text = { CountEditorForm(editor, alreadyCounted, onTextChange, onSave, onRemove) },
        confirmButton = { TextButton(onClick = onSave, modifier = Modifier.testTag("count_save")) { Text(stringResource(R.string.count_editor_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** What the count dialog asks for. Kept apart from the dialog window so it can be tested on its own. */
@Composable
fun CountEditorForm(
    editor: CountEditor,
    alreadyCounted: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
) {
    val unit = editor.product.stockUnit
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            stringResource(R.string.count_in_stock, unit.format(editor.variant.stockQuantity)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InvisionTextField(
            value = editor.text,
            onValueChange = onTextChange,
            label = stringResource(R.string.count_editor_field, unit.entrySymbol),
            error = if (editor.error) stringResource(R.string.count_editor_error) else null,
            keyboardType = if (unit.scale == 1) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("count_input"),
        )
        if (alreadyCounted) {
            TextButton(onClick = onRemove, modifier = Modifier.testTag("count_remove")) {
                Text(stringResource(R.string.count_editor_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun itemName(name: String, label: String): String = listOf(name, label).filter { it.isNotBlank() }.joinToString(" · ")

private fun CountReason.labelRes(): Int = when (this) {
    CountReason.ROUTINE -> R.string.count_reason_routine
    CountReason.YEAR_END -> R.string.count_reason_year_end
    CountReason.SPOT_CHECK -> R.string.count_reason_spot_check
    CountReason.OTHER -> R.string.count_reason_other
}
