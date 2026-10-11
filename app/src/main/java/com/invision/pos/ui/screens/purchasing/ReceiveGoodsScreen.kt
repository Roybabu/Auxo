package com.invision.pos.ui.screens.purchasing

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.inventory.PickableItem
import com.invision.pos.domain.purchasing.PurchaseFormError
import com.invision.pos.domain.purchasing.PurchaseLineError
import com.invision.pos.ui.components.DateField
import com.invision.pos.ui.components.DropdownField
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.ItemPickerField
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.screens.inventory.stockText
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.ReceiveGoodsEvent
import com.invision.pos.viewmodel.ReceiveGoodsUiState
import com.invision.pos.viewmodel.ReceiveGoodsViewModel
import java.time.LocalDate

/** Actions of the receive screen, so the content can be shown in tests without a ViewModel. */
interface ReceiveActions {
    fun onSupplierChange(supplierId: Long) = Unit
    fun onSupplierInvoiceChange(text: String) = Unit
    fun onNoteChange(text: String) = Unit
    fun onItemQueryChange(query: String) = Unit
    fun onAddItem(item: PickableItem) = Unit
    fun onLineQuantityChange(key: String, text: String) = Unit
    fun onLineCostChange(key: String, text: String) = Unit
    fun onLineBatchChange(key: String, text: String) = Unit
    fun onLineExpiryChange(key: String, date: LocalDate?) = Unit
    fun onRemoveLine(key: String) = Unit
    fun onSave() = Unit
    fun onAddSupplier() = Unit
}

@Composable
fun ReceiveGoodsScreen(
    onBack: () -> Unit,
    onSaved: (receiptId: Long) -> Unit,
    onAddSupplier: () -> Unit,
    viewModel: ReceiveGoodsViewModel = viewModel(factory = ReceiveGoodsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is ReceiveGoodsEvent.Saved -> {
                    onSaved(event.receiptId)
                    null
                }
                is ReceiveGoodsEvent.Invalid -> resources.getString(R.string.form_fix_errors)
                is ReceiveGoodsEvent.AlreadyAdded -> resources.getString(R.string.purchase_already_added, event.label)
                ReceiveGoodsEvent.OverReceived -> resources.getString(R.string.receive_over)
                ReceiveGoodsEvent.DuplicateSupplierInvoice -> resources.getString(R.string.receive_duplicate_bill)
                ReceiveGoodsEvent.OrderClosed -> resources.getString(R.string.receive_order_closed)
                ReceiveGoodsEvent.ItemsMissing -> resources.getString(R.string.receive_items_missing)
                ReceiveGoodsEvent.Failed -> resources.getString(R.string.error_generic)
            }
            if (message != null) snackbarHostState.showSnackbar(message)
        }
    }

    val addSupplier = onAddSupplier
    val actions = remember(viewModel) {
        object : ReceiveActions {
            override fun onSupplierChange(supplierId: Long) = viewModel.onSupplierChange(supplierId)
            override fun onSupplierInvoiceChange(text: String) = viewModel.onSupplierInvoiceChange(text)
            override fun onNoteChange(text: String) = viewModel.onNoteChange(text)
            override fun onItemQueryChange(query: String) = viewModel.onItemQueryChange(query)
            override fun onAddItem(item: PickableItem) = viewModel.addItem(item)
            override fun onLineQuantityChange(key: String, text: String) = viewModel.onLineQuantityChange(key, text)
            override fun onLineCostChange(key: String, text: String) = viewModel.onLineCostChange(key, text)
            override fun onLineBatchChange(key: String, text: String) = viewModel.onLineBatchChange(key, text)
            override fun onLineExpiryChange(key: String, date: LocalDate?) = viewModel.onLineExpiryChange(key, date)
            override fun onRemoveLine(key: String) = viewModel.removeLine(key)
            override fun onSave() = viewModel.save()
            override fun onAddSupplier() = addSupplier()
        }
    }

    Scaffold(
        topBar = {
            val order = uiState.order
            InvisionTopAppBar(
                title = if (order == null) stringResource(R.string.receive_title) else stringResource(R.string.receive_title_order, order.number),
                onBack = onBack,
                actions = {
                    if (!uiState.orderClosed) {
                        TextButton(onClick = viewModel::save, enabled = !uiState.isSaving && !uiState.isLoading && !uiState.loadFailed) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            uiState.isLoading -> LoadingState(modifier)
            uiState.loadFailed -> ErrorState(stringResource(R.string.error_generic), viewModel::retry, modifier)
            uiState.orderClosed -> EmptyState(
                iconRes = R.drawable.ic_receive,
                title = stringResource(R.string.receive_order_closed),
                message = "",
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = modifier,
            )
            else -> ReceiveGoodsContent(uiState, actions, modifier)
        }
    }
}

@Composable
fun ReceiveGoodsContent(
    state: ReceiveGoodsUiState,
    actions: ReceiveActions,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalMoneyFormatter.current
    val formErrors = if (state.showErrors) state.formErrors else emptySet()
    val lineErrors = if (state.showErrors) state.lineErrors else emptyMap()
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("receive_goods"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "details") {
            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val order = state.order
                    when {
                        order != null -> Text(
                            stringResource(R.string.receive_from, order.supplierName),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        state.suppliers.isEmpty() -> {
                            Text(stringResource(R.string.order_no_suppliers), style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = actions::onAddSupplier) { Text(stringResource(R.string.suppliers_add)) }
                        }
                        else -> DropdownField(
                            label = stringResource(R.string.order_supplier),
                            value = state.suppliers.firstOrNull { it.id == state.supplierId }?.name.orEmpty(),
                            options = state.suppliers,
                            optionText = { it.name },
                            onSelect = { actions.onSupplierChange(it.id) },
                            placeholder = stringResource(R.string.order_supplier_pick),
                            error = if (PurchaseFormError.SUPPLIER_REQUIRED in formErrors) stringResource(R.string.purchase_error_supplier) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("receive_supplier"),
                        )
                    }
                    InvisionTextField(
                        value = state.supplierInvoice,
                        onValueChange = actions::onSupplierInvoiceChange,
                        label = stringResource(R.string.receive_supplier_bill),
                        error = when {
                            state.duplicateSupplierInvoice -> stringResource(R.string.receive_duplicate_bill)
                            PurchaseFormError.SUPPLIER_INVOICE_INVALID in formErrors -> stringResource(R.string.purchase_error_bill_number)
                            else -> null
                        },
                        supportingText = stringResource(R.string.receive_supplier_bill_hint),
                        capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receive_bill_number"),
                    )
                    InvisionTextField(
                        value = state.note,
                        onValueChange = actions::onNoteChange,
                        label = stringResource(R.string.receive_note),
                        error = if (PurchaseFormError.NOTE_TOO_LONG in formErrors) stringResource(R.string.error_too_long) else null,
                        capitalization = KeyboardCapitalization.Sentences,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(if (state.isFromOrder) R.string.receive_order_hint else R.string.receive_direct_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "items_header") { SectionHeader(stringResource(R.string.order_items)) }
        if (!state.isFromOrder) {
            item(key = "picker") {
                ItemPickerField(
                    state = state.picker,
                    onQueryChange = actions::onItemQueryChange,
                    onPick = actions::onAddItem,
                    label = stringResource(R.string.purchase_add_item),
                    detail = { stockText(it.product.stockUnit, it.variant.stockQuantity) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val generalErrors = formErrors.filter { it == PurchaseFormError.NO_LINES || it == PurchaseFormError.NOTHING_TO_RECEIVE }
        if (state.lines.isEmpty() || generalErrors.isNotEmpty()) {
            item(key = "no_lines") {
                Text(
                    text = generalErrors.firstOrNull()?.let { stringResource(it.messageRes()) } ?: stringResource(R.string.receive_items_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (generalErrors.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.lines, key = { it.key }) { line ->
            val errors = lineErrors[line.key].orEmpty()
            PurchaseLineCard(
                line = line,
                errors = errors,
                onQuantityChange = { actions.onLineQuantityChange(line.key, it) },
                onCostChange = { actions.onLineCostChange(line.key, it) },
                onRemove = if (state.isFromOrder) null else ({ actions.onRemoveLine(line.key) }),
                supportingQuantity = line.remaining?.let { stringResource(R.string.receive_due, line.stockUnit.withSymbol(it)) },
                extra = {
                    if (line.tracksExpiry) {
                        InvisionTextField(
                            value = line.batchNumber,
                            onValueChange = { actions.onLineBatchChange(line.key, it) },
                            label = stringResource(R.string.receive_batch),
                            error = if (PurchaseLineError.BATCH_TOO_LONG in errors) stringResource(R.string.purchase_line_batch_too_long) else null,
                            capitalization = KeyboardCapitalization.Characters,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("line_batch_${line.key}"),
                        )
                        DateField(
                            label = stringResource(R.string.field_expiry),
                            date = line.expiryDate,
                            onDateChange = { actions.onLineExpiryChange(line.key, it) },
                            emptyText = stringResource(R.string.receive_expiry_none),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (PurchaseLineError.ALREADY_EXPIRED in errors) {
                            Text(stringResource(R.string.purchase_line_expired), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        }
        item(key = "total") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.receive_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(formatter.format(state.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        item(key = "save") {
            Button(
                onClick = actions::onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receive_save"),
            ) { Text(stringResource(R.string.receive_save)) }
        }
    }
}
