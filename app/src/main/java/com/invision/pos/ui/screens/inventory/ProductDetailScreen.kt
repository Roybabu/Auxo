package com.invision.pos.ui.screens.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.inventory.InventoryFilter
import com.invision.pos.domain.inventory.StockAdjustmentError
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.domain.model.StockStatus
import com.invision.pos.domain.model.StockUnit
import com.invision.pos.ui.components.ActionIcon
import com.invision.pos.ui.components.BarcodeImage
import com.invision.pos.ui.components.ConfirmDialog
import com.invision.pos.ui.components.DateField
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.components.StatusChip
import com.invision.pos.ui.format.DateFormats
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.AdjustStockDialogState
import com.invision.pos.viewmodel.ProductDetailEvent
import com.invision.pos.viewmodel.ProductDetailUiState
import com.invision.pos.viewmodel.ProductDetailViewModel
import java.time.LocalDate

@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onPrintLabels: (Long) -> Unit,
    viewModel: ProductDetailViewModel = viewModel(factory = ProductDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val adjustDialog by viewModel.adjustDialog.collectAsStateWithLifecycle()
    val confirmDelete by viewModel.confirmDelete.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ProductDetailEvent.StockUpdated -> snackbarHostState.showSnackbar(resources.getString(R.string.stock_updated))
                ProductDetailEvent.Deleted -> currentOnBack()
                ProductDetailEvent.ActionFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    val product = (uiState as? ProductDetailUiState.Ready)?.product

    Scaffold(
        topBar = {
            InvisionTopAppBar(
                title = product?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (product != null) {
                        ActionIcon(R.drawable.ic_print, stringResource(R.string.action_print_labels), { onPrintLabels(product.id) })
                        ActionIcon(R.drawable.ic_edit, stringResource(R.string.action_edit), { onEdit(product.id) })
                        ActionIcon(R.drawable.ic_delete, stringResource(R.string.action_delete), viewModel::requestDelete)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (val state = uiState) {
            ProductDetailUiState.Loading -> LoadingState(contentModifier)
            ProductDetailUiState.Failed -> ErrorState(
                message = stringResource(R.string.error_generic),
                onRetry = viewModel::retry,
                modifier = contentModifier,
            )
            ProductDetailUiState.NotFound -> EmptyState(
                iconRes = R.drawable.ic_nav_inventory,
                title = stringResource(R.string.detail_not_found),
                message = "",
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = contentModifier,
            )
            is ProductDetailUiState.Ready -> ProductDetailContent(
                state = state,
                onAdjustStock = viewModel::startAdjust,
                modifier = contentModifier,
            )
        }
    }

    adjustDialog?.let { dialog ->
        AdjustStockDialog(
            state = dialog,
            onReasonChange = viewModel::onAdjustReasonChange,
            onQuantityChange = viewModel::onAdjustQuantityChange,
            onBatchChange = viewModel::onAdjustBatchChange,
            onExpiryChange = viewModel::onAdjustExpiryChange,
            onNoteChange = viewModel::onAdjustNoteChange,
            onConfirm = viewModel::confirmAdjust,
            onDismiss = viewModel::dismissAdjust,
        )
    }

    if (confirmDelete && product != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_product_title),
            message = stringResource(R.string.delete_product_body, product.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::confirmDeleteProduct,
            onDismiss = viewModel::dismissDelete,
            destructive = true,
        )
    }
}

@Composable
fun ProductDetailContent(
    state: ProductDetailUiState.Ready,
    onAdjustStock: (ProductVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val product = state.product
    val variantLabels = product.variants.associate { it.id to it.attributesLabel }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "header") { ProductHeader(product) }
        item(key = "variants_title") { SectionHeader(stringResource(R.string.detail_variants)) }
        items(product.variants, key = { "variant_${it.id}" }) { variant ->
            VariantCard(
                variant = variant,
                category = product.category,
                unit = product.stockUnit,
                today = state.today,
                onAdjustStock = { onAdjustStock(variant) },
            )
        }
        item(key = "history_title") { SectionHeader(stringResource(R.string.detail_history)) }
        if (state.movements.isEmpty()) {
            item(key = "history_empty") {
                Text(
                    text = stringResource(R.string.detail_no_history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item(key = "history") {
                InvisionCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.movements.forEachIndexed { index, movement ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            MovementRow(movement, variantLabels[movement.variantId].orEmpty(), product.stockUnit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductHeader(product: Product) {
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusChip(
                    text = stringResource(product.category.labelRes()),
                    tone = InvisionThemeExtras.statusColors.neutral,
                )
                StatusChip(text = stringResource(product.stockStatus.labelRes()), tone = product.stockStatus.tone())
            }
            Text(text = product.name, style = MaterialTheme.typography.headlineSmall)
            product.brand?.let { Text(stringResource(R.string.detail_brand, it), style = MaterialTheme.typography.bodyMedium) }
            product.material?.let { Text(stringResource(R.string.detail_material, it), style = MaterialTheme.typography.bodyMedium) }
            if (product.category == ProductCategory.PACKAGED_FOOD) {
                Text(stringResource(product.dietType.labelRes()), style = MaterialTheme.typography.bodyMedium)
            }
            if (product.category.isSold) {
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.detail_gst, gstLabel(product.gstRate)),
                        product.hsnCode?.let { stringResource(R.string.detail_hsn, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(stringResource(R.string.detail_ingredient_note), style = MaterialTheme.typography.bodyMedium)
            }
            product.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = stockText(product.stockUnit, product.totalStock),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun VariantCard(
    variant: ProductVariant,
    category: ProductCategory,
    unit: StockUnit,
    today: LocalDate,
    onAdjustStock: () -> Unit,
) {
    val formatter = LocalMoneyFormatter.current
    val status = InvisionThemeExtras.statusColors
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = variant.attributesLabel.ifEmpty { stringResource(R.string.detail_variant_standard) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                when {
                    variant.isExpired(today) -> StatusChip(stringResource(R.string.status_expired), status.danger)
                    variant.stockStatus == StockStatus.OUT_OF_STOCK ->
                        StatusChip(stringResource(R.string.stock_status_out), status.danger)
                    variant.expiresWithin(today, InventoryFilter.EXPIRY_WARNING_DAYS) ->
                        StatusChip(stringResource(R.string.status_expiring), status.warning)
                    else -> StatusChip(stringResource(variant.stockStatus.labelRes()), variant.stockStatus.tone())
                }
            }
            if (!category.isSold) {
                Text(
                    text = variant.costPrice?.let { stringResource(R.string.detail_cost_per, formatter.format(it), unit.entrySymbol) }
                        ?: stringResource(R.string.detail_cost_unknown),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        text = formatter.format(variant.sellingPrice),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    variant.mrp?.let {
                        Text(
                            text = stringResource(R.string.detail_mrp, formatter.format(it)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    variant.costPrice?.let {
                        Text(
                            text = stringResource(R.string.detail_cost, formatter.format(it)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            BarcodeImage(
                value = variant.barcode,
                modifier = Modifier
                    .width(200.dp)
                    .height(48.dp),
            )
            Text(text = variant.barcode, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            val extra = listOfNotNull(
                variant.sku?.let { stringResource(R.string.detail_sku, it) },
                if (variant.lowStockThreshold > 0) stringResource(R.string.detail_low_stock_at, unit.format(variant.lowStockThreshold)) else null,
                if (variant.reorderQuantity > 0) stringResource(R.string.detail_reorder_quantity, unit.format(variant.reorderQuantity)) else null,
            )
            if (extra.isNotEmpty()) {
                Text(
                    text = extra.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (category.tracksExpiry && variant.batches.isNotEmpty()) {
                BatchList(variant = variant, unit = unit, today = today)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stockText(unit, variant.stockQuantity),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onAdjustStock) { Text(stringResource(R.string.action_adjust_stock)) }
            }
        }
    }
}

/** Batches of one variant, earliest expiry first, with expired ones marked. */
@Composable
private fun BatchList(variant: ProductVariant, unit: StockUnit, today: LocalDate) {
    val status = InvisionThemeExtras.statusColors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.detail_batches), style = MaterialTheme.typography.labelLarge)
        variant.batches.forEach { batch ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = listOfNotNull(
                        batch.batchNumber?.let { stringResource(R.string.detail_batch, it) } ?: stringResource(R.string.detail_batch_none),
                        batch.expiryDate?.let { stringResource(R.string.detail_expiry, DateFormats.date(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when {
                    batch.isExpired(today) -> StatusChip(stringResource(R.string.status_expired), status.danger)
                    batch.expiresWithin(today, InventoryFilter.EXPIRY_WARNING_DAYS) ->
                        StatusChip(stringResource(R.string.status_expiring), status.warning)
                    else -> Unit
                }
                Text(unit.format(batch.quantity), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val expired = variant.expiredQuantity(today)
        if (expired > 0) {
            Text(
                text = stringResource(R.string.detail_expired_stock, unit.format(expired)),
                style = MaterialTheme.typography.bodySmall,
                color = status.danger.accent,
            )
        }
    }
}

@Composable
private fun MovementRow(movement: StockMovement, variantLabel: String, unit: StockUnit) {
    val status = InvisionThemeExtras.statusColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(stringResource(movement.reason.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(variantLabel.ifEmpty { null }, DateFormats.dateTime(movement.createdAtMillis), movement.note)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val change = movement.quantityChange
        Text(
            text = if (change > 0) "+" + unit.format(change) else unit.format(change),
            style = MaterialTheme.typography.titleMedium,
            color = if (change > 0) status.success.accent else status.danger.accent,
        )
    }
}

@Composable
fun AdjustStockDialog(
    state: AdjustStockDialogState,
    onReasonChange: (StockChangeReason) -> Unit,
    onQuantityChange: (String) -> Unit,
    onBatchChange: (String) -> Unit,
    onExpiryChange: (LocalDate?) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adjust_title)) },
        text = {
            AdjustStockForm(
                state = state,
                onReasonChange = onReasonChange,
                onQuantityChange = onQuantityChange,
                onBatchChange = onBatchChange,
                onExpiryChange = onExpiryChange,
                onNoteChange = onNoteChange,
                onDone = onConfirm,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Reason, quantity, batch and note fields of the stock adjustment dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdjustStockForm(
    state: AdjustStockDialogState,
    onReasonChange: (StockChangeReason) -> Unit,
    onQuantityChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onBatchChange: (String) -> Unit = {},
    onExpiryChange: (LocalDate?) -> Unit = {},
) {
    val unit = state.unit
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = listOfNotNull(
                state.variant.attributesLabel.ifEmpty { null },
                stringResource(R.string.adjust_current, unit.format(state.variant.stockQuantity)),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.reasons.forEach { reason ->
                FilterChip(
                    selected = state.reason == reason,
                    onClick = { onReasonChange(reason) },
                    label = { Text(stringResource(reason.labelRes())) },
                )
            }
        }
        val counted = state.reason == StockChangeReason.CORRECTION
        InvisionTextField(
            value = state.quantity,
            onValueChange = onQuantityChange,
            label = when {
                unit == StockUnit.UNIT -> stringResource(if (counted) R.string.adjust_counted else R.string.adjust_quantity)
                else -> stringResource(if (counted) R.string.adjust_counted_unit else R.string.adjust_quantity_unit, unit.entrySymbol)
            },
            error = state.error?.takeIf { it != StockAdjustmentError.BATCH_INVALID }?.let { stringResource(it.messageRes()) },
            supportingText = state.newStock?.let { stringResource(R.string.adjust_new_stock, unit.format(it)) },
            keyboardType = if (unit == StockUnit.UNIT) KeyboardType.Number else KeyboardType.Decimal,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("adjust_quantity"),
        )
        if (state.asksForBatch) {
            InvisionTextField(
                value = state.batchNumber,
                onValueChange = onBatchChange,
                label = stringResource(R.string.adjust_batch),
                error = if (state.error == StockAdjustmentError.BATCH_INVALID) stringResource(R.string.adjust_error_batch) else null,
                capitalization = KeyboardCapitalization.Characters,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = stringResource(R.string.field_expiry),
                date = state.expiryDate,
                onDateChange = onExpiryChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        InvisionTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = stringResource(R.string.adjust_note),
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
