package com.auxo.pos.ui.screens.purchasing

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.domain.inventory.PickableItem
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.purchasing.PurchaseFormError
import com.auxo.pos.domain.purchasing.PurchaseLineError
import com.auxo.pos.domain.purchasing.PurchaseLineForm
import com.auxo.pos.ui.components.ActionIcon
import com.auxo.pos.ui.components.DateField
import com.auxo.pos.ui.components.DropdownField
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.ItemPickerField
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.screens.inventory.stockText
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.PurchaseOrderEditorEvent
import com.auxo.pos.viewmodel.PurchaseOrderEditorUiState
import com.auxo.pos.viewmodel.PurchaseOrderEditorViewModel
import java.time.LocalDate

/** Actions of the purchase order editor, so the content can be shown in tests without a ViewModel. */
interface OrderEditorActions {
    fun onSupplierChange(supplierId: Long) = Unit
    fun onExpectedDateChange(date: LocalDate?) = Unit
    fun onNoteChange(note: String) = Unit
    fun onItemQueryChange(query: String) = Unit
    fun onAddItem(item: PickableItem) = Unit
    fun onLineQuantityChange(key: String, text: String) = Unit
    fun onLineCostChange(key: String, text: String) = Unit
    fun onRemoveLine(key: String) = Unit
    fun onSave(markOrdered: Boolean) = Unit
    fun onAddSupplier() = Unit
}

@Composable
fun PurchaseOrderEditorScreen(
    onBack: () -> Unit,
    onSaved: (orderId: Long) -> Unit,
    onAddSupplier: () -> Unit,
    viewModel: PurchaseOrderEditorViewModel = viewModel(factory = PurchaseOrderEditorViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseOrderEditorEvent.Saved -> onSaved(event.orderId)
                is PurchaseOrderEditorEvent.Invalid -> snackbarHostState.showSnackbar(resources.getString(R.string.form_fix_errors))
                is PurchaseOrderEditorEvent.AlreadyAdded -> snackbarHostState.showSnackbar(resources.getString(R.string.purchase_already_added, event.label))
                PurchaseOrderEditorEvent.NotEditable -> snackbarHostState.showSnackbar(resources.getString(R.string.order_not_editable))
                PurchaseOrderEditorEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    val addSupplier = onAddSupplier
    val actions = remember(viewModel) {
        object : OrderEditorActions {
            override fun onSupplierChange(supplierId: Long) = viewModel.onSupplierChange(supplierId)
            override fun onExpectedDateChange(date: LocalDate?) = viewModel.onExpectedDateChange(date)
            override fun onNoteChange(note: String) = viewModel.onNoteChange(note)
            override fun onItemQueryChange(query: String) = viewModel.onItemQueryChange(query)
            override fun onAddItem(item: PickableItem) = viewModel.addItem(item)
            override fun onLineQuantityChange(key: String, text: String) = viewModel.onLineQuantityChange(key, text)
            override fun onLineCostChange(key: String, text: String) = viewModel.onLineCostChange(key, text)
            override fun onRemoveLine(key: String) = viewModel.removeLine(key)
            override fun onSave(markOrdered: Boolean) = viewModel.save(markOrdered)
            override fun onAddSupplier() = addSupplier()
        }
    }

    Scaffold(
        topBar = {
            AuxoTopAppBar(
                title = if (uiState.isNew) stringResource(R.string.order_editor_new) else stringResource(R.string.order_editor_edit, uiState.number),
                onBack = onBack,
                actions = {
                    if (!uiState.notEditable) {
                        TextButton(onClick = { viewModel.save(markOrdered = false) }, enabled = !uiState.isSaving && !uiState.isLoading && !uiState.loadFailed) {
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
            uiState.notEditable -> EmptyState(
                iconRes = R.drawable.ic_purchase_order,
                title = stringResource(R.string.order_not_editable),
                message = "",
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = modifier,
            )
            else -> PurchaseOrderEditorContent(uiState, actions, modifier)
        }
    }
}

@Composable
fun PurchaseOrderEditorContent(
    state: PurchaseOrderEditorUiState,
    actions: OrderEditorActions,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalMoneyFormatter.current
    val formErrors = state.visibleFormErrors
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("order_editor"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "details") {
            AuxoCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (state.suppliers.isEmpty()) {
                        Text(stringResource(R.string.order_no_suppliers), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = actions::onAddSupplier) { Text(stringResource(R.string.suppliers_add)) }
                    } else {
                        DropdownField(
                            label = stringResource(R.string.order_supplier),
                            value = state.suppliers.firstOrNull { it.id == state.supplierId }?.name.orEmpty(),
                            options = state.suppliers,
                            optionText = { it.name },
                            onSelect = { actions.onSupplierChange(it.id) },
                            placeholder = stringResource(R.string.order_supplier_pick),
                            error = if (PurchaseFormError.SUPPLIER_REQUIRED in formErrors) stringResource(R.string.purchase_error_supplier) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("order_supplier"),
                        )
                    }
                    DateField(
                        label = stringResource(R.string.order_expected_date),
                        date = state.expectedDate,
                        onDateChange = actions::onExpectedDateChange,
                        emptyText = stringResource(R.string.order_expected_none),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (PurchaseFormError.EXPECTED_DATE_IN_PAST in formErrors) {
                        Text(stringResource(R.string.purchase_error_date_past), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    AuxoTextField(
                        value = state.note,
                        onValueChange = actions::onNoteChange,
                        label = stringResource(R.string.order_note),
                        error = if (PurchaseFormError.NOTE_TOO_LONG in formErrors) stringResource(R.string.error_too_long) else null,
                        capitalization = KeyboardCapitalization.Sentences,
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item(key = "items_header") { SectionHeader(stringResource(R.string.order_items)) }
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
        if (state.lines.isEmpty()) {
            item(key = "no_lines") {
                Text(
                    stringResource(R.string.purchase_items_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (PurchaseFormError.NO_LINES in formErrors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.lines, key = { it.key }) { line ->
            PurchaseLineCard(
                line = line,
                errors = state.visibleLineErrors[line.key].orEmpty(),
                onQuantityChange = { actions.onLineQuantityChange(line.key, it) },
                onCostChange = { actions.onLineCostChange(line.key, it) },
                onRemove = { actions.onRemoveLine(line.key) },
            )
        }
        item(key = "total") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.purchase_estimated_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(formatter.format(state.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        item(key = "buttons") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = { actions.onSave(true) },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_save_send"),
                ) { Text(stringResource(R.string.order_save_and_send)) }
                OutlinedButton(
                    onClick = { actions.onSave(false) },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_save_draft"),
                ) { Text(stringResource(R.string.order_save_draft)) }
            }
        }
    }
}

/** One item on an order or receipt: quantity and cost, with its errors. [extra] adds fields such as batch and expiry. */
@Composable
fun PurchaseLineCard(
    line: PurchaseLineForm,
    errors: Set<PurchaseLineError>,
    onQuantityChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    supportingQuantity: String? = null,
    extra: @Composable () -> Unit = {},
) {
    val formatter = LocalMoneyFormatter.current
    val unit = line.stockUnit
    val quantityError = errors.firstOrNull {
        it == PurchaseLineError.QUANTITY_REQUIRED || it == PurchaseLineError.QUANTITY_INVALID || it == PurchaseLineError.MORE_THAN_ORDERED
    }
    val costError = errors.firstOrNull { it == PurchaseLineError.COST_REQUIRED || it == PurchaseLineError.COST_INVALID }
    val otherErrors = errors - setOfNotNull(quantityError, costError) -
        setOf(PurchaseLineError.BATCH_TOO_LONG, PurchaseLineError.ALREADY_EXPIRED)
    AuxoCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.productName, style = MaterialTheme.typography.titleSmall)
                    if (line.variantLabel.isNotBlank()) {
                        Text(line.variantLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onRemove != null) ActionIcon(R.drawable.ic_delete, stringResource(R.string.purchase_remove_line), onRemove)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AuxoTextField(
                    value = line.quantity,
                    onValueChange = onQuantityChange,
                    label = stringResource(R.string.purchase_quantity, unit.entrySymbol),
                    error = quantityError?.let { stringResource(it.messageRes()) },
                    supportingText = supportingQuantity,
                    keyboardType = if (unit.scale == 1) KeyboardType.Number else KeyboardType.Decimal,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("line_quantity_${line.key}"),
                )
                AuxoTextField(
                    value = line.unitCost,
                    onValueChange = onCostChange,
                    label = stringResource(R.string.field_cost_per, unit.entrySymbol),
                    error = costError?.let { stringResource(it.messageRes()) },
                    prefix = stringResource(R.string.currency_prefix),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("line_cost_${line.key}"),
                )
            }
            extra()
            otherErrors.forEach {
                Text(stringResource(it.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            val quantity = unit.parse(line.quantity)
            val cost = Money.parseOrNull(line.unitCost)
            if (quantity != null && cost != null) {
                Text(
                    stringResource(R.string.purchase_line_total, formatter.format(unit.costOf(quantity, cost))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
