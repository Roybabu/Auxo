// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.export.FileExport
import com.auxo.pos.domain.model.PurchaseOrderLine
import com.auxo.pos.domain.model.PurchaseOrderStatus
import com.auxo.pos.ui.components.ActionIcon
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.components.StatusChip
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.PurchaseOrderDetailEvent
import com.auxo.pos.viewmodel.PurchaseOrderDetailUiState
import com.auxo.pos.viewmodel.PurchaseOrderDetailViewModel

@Composable
fun PurchaseOrderDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onReceive: (Long) -> Unit,
    onOpenReceipt: (Long) -> Unit,
    viewModel: PurchaseOrderDetailViewModel = viewModel(factory = PurchaseOrderDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val ready = uiState as? PurchaseOrderDetailUiState.Ready

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseOrderDetailEvent.Share ->
                    if (!FileExport.shareText(context, event.subject, event.text, resources.getString(R.string.order_share_title))) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.export_failed))
                    }
                is PurchaseOrderDetailEvent.StatusChanged -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.order_status_changed, resources.getString(event.status.labelRes())),
                )
                PurchaseOrderDetailEvent.ChangeRejected -> snackbarHostState.showSnackbar(resources.getString(R.string.order_change_rejected))
                PurchaseOrderDetailEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    Scaffold(
        topBar = {
            AuxoTopAppBar(
                title = ready?.order?.number.orEmpty(),
                onBack = onBack,
                actions = {
                    if (ready != null) {
                        ActionIcon(R.drawable.ic_share, stringResource(R.string.order_share), viewModel::share)
                        if (ready.order.status.canEdit) {
                            ActionIcon(R.drawable.ic_edit, stringResource(R.string.action_edit), { onEdit(ready.order.id) })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val state = uiState) {
            PurchaseOrderDetailUiState.Loading -> LoadingState(modifier)
            PurchaseOrderDetailUiState.Failed -> ErrorState(stringResource(R.string.error_generic), viewModel::retry, modifier)
            PurchaseOrderDetailUiState.NotFound -> EmptyState(
                iconRes = R.drawable.ic_purchase_order,
                title = stringResource(R.string.order_not_found),
                message = "",
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = modifier,
            )
            is PurchaseOrderDetailUiState.Ready -> PurchaseOrderDetailContent(
                state = state,
                isWorking = isWorking,
                onMarkOrdered = viewModel::markOrdered,
                onReceive = { onReceive(state.order.id) },
                onCancel = { confirmCancel = true },
                onOpenReceipt = onOpenReceipt,
                modifier = modifier,
            )
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.order_cancel_title)) },
            text = { Text(stringResource(R.string.order_cancel_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancelOrder()
                }) { Text(stringResource(R.string.order_cancel), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.order_keep)) } },
        )
    }
}

@Composable
fun PurchaseOrderDetailContent(
    state: PurchaseOrderDetailUiState.Ready,
    isWorking: Boolean,
    onMarkOrdered: () -> Unit,
    onReceive: () -> Unit,
    onCancel: () -> Unit,
    onOpenReceipt: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalMoneyFormatter.current
    val order = state.order
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("order_detail"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "header") {
            AuxoCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(order.supplierName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusChip(stringResource(order.status.labelRes()), order.status.tone())
                    }
                    Text(
                        text = listOfNotNull(
                            stringResource(R.string.order_created, DateFormats.dateTime(order.createdAtMillis)),
                            order.expectedDate?.let { stringResource(R.string.order_expected, DateFormats.date(it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    order.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                        Text(stringResource(R.string.order_total_label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(formatter.format(order.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (order.status.isOpen && order.status != PurchaseOrderStatus.DRAFT) {
                        Text(
                            stringResource(R.string.order_remaining_value, formatter.format(state.remainingValue)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuxoThemeExtras.statusColors.warning.content,
                        )
                    }
                }
            }
        }
        if (order.status.isOpen) {
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (order.status == PurchaseOrderStatus.DRAFT) {
                        OutlinedButton(onClick = onMarkOrdered, enabled = !isWorking, modifier = Modifier.fillMaxWidth().testTag("order_mark_ordered")) {
                            Text(stringResource(R.string.order_mark_ordered))
                        }
                    }
                    Button(onClick = onReceive, enabled = !isWorking, modifier = Modifier.fillMaxWidth().testTag("order_receive")) {
                        Icon(painterResource(R.drawable.ic_receive), contentDescription = null)
                        Text(stringResource(R.string.order_receive), modifier = Modifier.padding(start = Spacing.sm))
                    }
                    TextButton(onClick = onCancel, enabled = !isWorking, modifier = Modifier.testTag("order_cancel")) {
                        Text(stringResource(R.string.order_cancel), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item(key = "lines_header") { SectionHeader(stringResource(R.string.order_items)) }
        item(key = "lines") {
            AuxoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    order.lines.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        OrderLineRow(line)
                    }
                }
            }
        }
        item(key = "receipts_header") { SectionHeader(stringResource(R.string.order_receipts)) }
        if (state.receipts.isEmpty()) {
            item(key = "no_receipts") {
                Text(stringResource(R.string.order_no_receipts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.receipts, key = { "receipt-${it.id}" }) { receipt ->
            ReceiptRow(receipt, onClick = { onOpenReceipt(receipt.id) })
        }
    }
}

@Composable
private fun OrderLineRow(line: PurchaseOrderLine) {
    val formatter = LocalMoneyFormatter.current
    val unit = line.stockUnit
    val done = line.quantityReceived >= line.quantityOrdered
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(listOf(line.productName, line.variantLabel).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.order_line_detail, unit.withSymbol(line.quantityOrdered), formatter.format(line.unitCost), unit.entrySymbol),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (line.quantityReceived > 0 || line.variantId == null) {
                Text(
                    text = if (line.variantId == null) {
                        stringResource(R.string.purchase_line_deleted)
                    } else {
                        stringResource(R.string.order_received_of, unit.format(line.quantityReceived), unit.format(line.quantityOrdered))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done) AuxoThemeExtras.statusColors.success.accent else AuxoThemeExtras.statusColors.warning.content,
                )
            }
        }
        Text(formatter.format(line.lineTotal), style = MaterialTheme.typography.titleSmall)
    }
}
