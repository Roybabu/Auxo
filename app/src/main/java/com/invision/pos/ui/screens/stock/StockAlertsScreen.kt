package com.invision.pos.ui.screens.stock

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.inventory.ReorderSuggestion
import com.invision.pos.domain.model.BatchWithProduct
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.reports.ExpiryWindow
import com.invision.pos.domain.usecase.WastageError
import com.invision.pos.ui.LocalSuppliersEnabled
import com.invision.pos.ui.components.DropdownField
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.StatTile
import com.invision.pos.ui.components.StatusChip
import com.invision.pos.ui.format.DateFormats
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.screens.inventory.labelRes
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.AlertsTab
import com.invision.pos.viewmodel.StockAlertsEvent
import com.invision.pos.viewmodel.StockAlertsUiState
import com.invision.pos.viewmodel.StockAlertsViewModel
import com.invision.pos.viewmodel.WastageDialogState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@StringRes
fun ExpiryWindow.labelRes(): Int = when (this) {
    ExpiryWindow.EXPIRED -> R.string.expiry_window_expired
    ExpiryWindow.WITHIN_7_DAYS -> R.string.expiry_window_7
    ExpiryWindow.WITHIN_15_DAYS -> R.string.expiry_window_15
    ExpiryWindow.WITHIN_30_DAYS -> R.string.expiry_window_30
    ExpiryWindow.ALL -> R.string.filter_all
}

@StringRes
fun WastageError.messageRes(): Int = when (this) {
    WastageError.QUANTITY_REQUIRED -> R.string.adjust_error_required
    WastageError.QUANTITY_INVALID -> R.string.adjust_error_invalid
    WastageError.MORE_THAN_IN_BATCH -> R.string.write_off_error_more_than_batch
    WastageError.NOT_WASTAGE -> R.string.write_off_error_reason
    WastageError.NOTE_TOO_LONG -> R.string.error_too_long
    WastageError.STOCK_CHANGED -> R.string.write_off_error_changed
}

/** Callbacks of the alerts screen, so its content can be shown in tests without a ViewModel. */
interface StockAlertsActions {
    fun onTabChange(tab: AlertsTab) = Unit
    fun onToggle(variantId: Long) = Unit
    fun onSelectAll(selected: Boolean) = Unit
    fun onFallbackSupplierChange(supplierId: Long?) = Unit
    fun onCreateDrafts() = Unit
    fun onExpiryWindowChange(window: ExpiryWindow) = Unit
    fun onWriteOff(batch: BatchWithProduct) = Unit
    fun onOpenProduct(productId: Long) = Unit
    fun onRetry() = Unit
}

@Composable
fun StockAlertsScreen(
    onBack: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenOrders: () -> Unit,
    viewModel: StockAlertsViewModel = viewModel(factory = StockAlertsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.wastageDialog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StockAlertsEvent.DraftsCreated -> {
                    val created = resources.getQuantityString(R.plurals.alerts_drafts_created, event.orderIds.size, event.orderIds.size)
                    val message = if (event.skipped > 0) {
                        created + " " + resources.getQuantityString(R.plurals.alerts_skipped, event.skipped, event.skipped)
                    } else {
                        created
                    }
                    val result = snackbarHostState.showSnackbar(message, actionLabel = resources.getString(R.string.alerts_view_orders), duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) onOpenOrders()
                }
                StockAlertsEvent.NothingToOrder -> snackbarHostState.showSnackbar(resources.getString(R.string.alerts_nothing_to_order))
                is StockAlertsEvent.WrittenOff -> snackbarHostState.showSnackbar(resources.getString(R.string.written_off, event.name))
                StockAlertsEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    val openProduct = onOpenProduct
    val actions = remember(viewModel) {
        object : StockAlertsActions {
            override fun onTabChange(tab: AlertsTab) = viewModel.onTabChange(tab)
            override fun onToggle(variantId: Long) = viewModel.toggleSelected(variantId)
            override fun onSelectAll(selected: Boolean) = viewModel.selectAll(selected)
            override fun onFallbackSupplierChange(supplierId: Long?) = viewModel.onFallbackSupplierChange(supplierId)
            override fun onCreateDrafts() = viewModel.createDraftOrders()
            override fun onExpiryWindowChange(window: ExpiryWindow) = viewModel.onExpiryWindowChange(window)
            override fun onWriteOff(batch: BatchWithProduct) = viewModel.startWriteOff(batch)
            override fun onOpenProduct(productId: Long) = openProduct(productId)
            override fun onRetry() = viewModel.retry()
        }
    }

    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.alerts_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        StockAlertsContent(uiState, actions, Modifier.padding(padding))
    }

    dialog?.let { state ->
        WriteOffDialog(
            state = state,
            onReasonChange = viewModel::onWastageReasonChange,
            onQuantityChange = viewModel::onWastageQuantityChange,
            onNoteChange = viewModel::onWastageNoteChange,
            onConfirm = viewModel::confirmWriteOff,
            onDismiss = viewModel::dismissWriteOff,
        )
    }
}

@Composable
fun StockAlertsContent(
    state: StockAlertsUiState,
    actions: StockAlertsActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            Tab(
                selected = state.tab == AlertsTab.LOW_STOCK,
                onClick = { actions.onTabChange(AlertsTab.LOW_STOCK) },
                text = { Text(stringResource(R.string.alerts_tab_low, state.suggestions.size)) },
            )
            Tab(
                selected = state.tab == AlertsTab.EXPIRY,
                onClick = { actions.onTabChange(AlertsTab.EXPIRY) },
                text = { Text(stringResource(R.string.alerts_tab_expiry)) },
            )
        }
        when {
            state.isLoading -> LoadingState()
            state.loadFailed -> ErrorState(stringResource(R.string.error_generic), actions::onRetry)
            state.tab == AlertsTab.LOW_STOCK -> LowStockTab(state, actions)
            else -> ExpiryTab(state, actions)
        }
    }
}

@Composable
private fun LowStockTab(state: StockAlertsUiState, actions: StockAlertsActions) {
    if (state.suggestions.isEmpty()) {
        EmptyState(
            iconRes = R.drawable.ic_alert,
            title = stringResource(R.string.alerts_low_empty_title),
            message = stringResource(R.string.alerts_low_empty_body),
        )
        return
    }
    val selected = state.selected
    val suppliersOn = LocalSuppliersEnabled.current
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.testTag("low_stock_list"),
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(if (suppliersOn) R.string.alerts_low_intro else R.string.alerts_low_intro_plain), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (suppliersOn) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.alerts_selected, selected.size, selected.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { actions.onSelectAll(selected.size < state.suggestions.size) }) {
                        Text(stringResource(if (selected.size < state.suggestions.size) R.string.alerts_select_all else R.string.alerts_select_none))
                    }
                }
                if (suppliersOn && selected.any { it.supplierId == null }) {
                    if (state.suppliers.isEmpty()) {
                        Text(stringResource(R.string.order_no_suppliers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else {
                        val none = stringResource(R.string.editor_supplier_none)
                        DropdownField(
                            label = stringResource(R.string.alerts_fallback_supplier),
                            value = state.supplierName(state.fallbackSupplierId) ?: none,
                            options = listOf<Long?>(null) + state.suppliers.map { it.id },
                            optionText = { id -> state.supplierName(id) ?: none },
                            onSelect = actions::onFallbackSupplierChange,
                            supportingText = if (state.selectedWithoutSupplier > 0) {
                                pluralStringResource(R.plurals.alerts_skipped, state.selectedWithoutSupplier, state.selectedWithoutSupplier)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alerts_fallback_supplier"),
                        )
                    }
                }
                if (suppliersOn) Button(
                    onClick = actions::onCreateDrafts,
                    enabled = selected.isNotEmpty() && !state.isCreating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alerts_create_drafts"),
                ) {
                    Text(stringResource(R.string.alerts_create_drafts))
                }
            }
        }
        items(state.suggestions, key = { it.variant.id }) { suggestion ->
            SuggestionRow(
                suggestion = suggestion,
                checked = suggestion.variant.id !in state.deselected,
                supplierName = state.supplierName(suggestion.supplierId),
                showSupplier = suppliersOn,
                onToggle = { actions.onToggle(suggestion.variant.id) },
                onOpen = { actions.onOpenProduct(suggestion.product.id) },
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: ReorderSuggestion,
    checked: Boolean,
    supplierName: String?,
    showSupplier: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val status = InvisionThemeExtras.statusColors
    val unit = suggestion.product.stockUnit
    val variant = suggestion.variant
    InvisionCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSupplier) {
                Checkbox(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.testTag("suggestion_${variant.id}"))
            } else {
                Spacer(Modifier.width(Spacing.lg))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    listOf(suggestion.product.name, variant.attributesLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.alerts_stock_line, unit.format(variant.stockQuantity), unit.format(variant.effectiveLowStockLevel)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showSupplier) {
                    Text(
                        text = supplierName ?: stringResource(R.string.alerts_no_supplier),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (supplierName == null) status.warning.content else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (suggestion.isOutOfStock) {
                    StatusChip(stringResource(R.string.stock_status_out), status.danger)
                } else {
                    StatusChip(stringResource(R.string.stock_status_low), status.warning)
                }
                Text(stringResource(R.string.alerts_suggested, unit.format(suggestion.suggestedQuantity)), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ExpiryTab(state: StockAlertsUiState, actions: StockAlertsActions) {
    val formatter = LocalMoneyFormatter.current
    Column {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ExpiryWindow.entries.forEach { window ->
                FilterChip(
                    selected = state.expiryWindow == window,
                    onClick = { actions.onExpiryWindowChange(window) },
                    label = { Text(stringResource(window.labelRes())) },
                )
            }
        }
        if (state.batches.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_expiry,
                title = stringResource(R.string.alerts_expiry_empty_title),
                message = stringResource(R.string.alerts_expiry_empty_body),
            )
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.testTag("expiry_list"),
        ) {
            if (state.expiredBatchCount > 0) {
                item(key = "expired_tile") {
                    StatTile(
                        label = pluralStringResource(R.plurals.expiry_expired_value, state.expiredBatchCount, state.expiredBatchCount),
                        value = formatter.format(state.expiredValue),
                        iconRes = R.drawable.ic_expiry,
                        tone = InvisionThemeExtras.statusColors.danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(state.batches, key = { it.batch.id }) { batch ->
                BatchRow(batch, state.today, onWriteOff = { actions.onWriteOff(batch) }, onOpen = { actions.onOpenProduct(batch.productId) })
            }
        }
    }
}

@Composable
private fun BatchRow(item: BatchWithProduct, today: LocalDate, onWriteOff: () -> Unit, onOpen: () -> Unit) {
    val status = InvisionThemeExtras.statusColors
    val formatter = LocalMoneyFormatter.current
    val batch = item.batch
    val expiry = batch.expiryDate
    InvisionCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    listOf(item.productName, item.variantLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    batch.isExpired(today) -> StatusChip(stringResource(R.string.status_expired), status.danger)
                    expiry != null && !expiry.isAfter(today.plusDays(7)) -> StatusChip(stringResource(R.string.status_expiring), status.warning)
                    else -> Unit
                }
            }
            Text(
                text = listOfNotNull(
                    batch.batchNumber?.let { stringResource(R.string.detail_batch, it) } ?: stringResource(R.string.detail_batch_none),
                    expiry?.let { expiryText(it, today) } ?: stringResource(R.string.receive_expiry_none),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(
                        item.stockUnit.format(batch.quantity).let { stringResource(R.string.quantity_in_stock, it) },
                        item.valueAtCost?.let { stringResource(R.string.expiry_value_at_cost, formatter.format(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onWriteOff, modifier = Modifier.testTag("write_off_${batch.id}")) {
                    Text(stringResource(R.string.write_off))
                }
            }
        }
    }
}

@Composable
private fun expiryText(expiry: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, expiry).toInt()
    return when {
        days < 0 -> stringResource(R.string.expiry_expired_on, DateFormats.date(expiry))
        days == 0 -> stringResource(R.string.expiry_today)
        else -> pluralStringResource(R.plurals.expiry_days_left, days, days, DateFormats.date(expiry))
    }
}

@Composable
fun WriteOffDialog(
    state: WastageDialogState,
    onReasonChange: (StockChangeReason) -> Unit,
    onQuantityChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.write_off_title)) },
        text = { WriteOffForm(state, onReasonChange, onQuantityChange, onNoteChange) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isSaving) {
                Text(stringResource(R.string.write_off), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Reason, quantity and note for writing off a batch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WriteOffForm(
    state: WastageDialogState,
    onReasonChange: (StockChangeReason) -> Unit,
    onQuantityChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.batch
    val unit = item.stockUnit
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = listOfNotNull(
                listOf(item.productName, item.variantLabel).filter { it.isNotBlank() }.joinToString(" · "),
                item.batch.batchNumber?.let { stringResource(R.string.detail_batch, it) },
                stringResource(R.string.quantity_in_stock, unit.format(item.batch.quantity)),
            ).joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StockChangeReason.WASTAGE.forEach { reason ->
                FilterChip(
                    selected = state.reason == reason,
                    onClick = { onReasonChange(reason) },
                    label = { Text(stringResource(reason.labelRes())) },
                )
            }
        }
        InvisionTextField(
            value = state.quantity,
            onValueChange = onQuantityChange,
            label = stringResource(R.string.write_off_quantity, unit.entrySymbol),
            error = state.error?.takeIf { it != WastageError.NOTE_TOO_LONG }?.let { stringResource(it.messageRes()) },
            keyboardType = if (unit.scale == 1) KeyboardType.Number else KeyboardType.Decimal,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("write_off_quantity"),
        )
        InvisionTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = stringResource(R.string.write_off_note),
            error = if (state.error == WastageError.NOTE_TOO_LONG) stringResource(R.string.error_too_long) else null,
            capitalization = KeyboardCapitalization.Sentences,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.write_off_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
