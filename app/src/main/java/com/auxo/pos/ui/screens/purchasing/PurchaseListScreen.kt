package com.auxo.pos.ui.screens.purchasing

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.ui.components.ActionIcon
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.StatTile
import com.auxo.pos.ui.components.StatusChip
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.screens.billing.labelRes
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.HistoryRange
import com.auxo.pos.viewmodel.OrderFilter
import com.auxo.pos.viewmodel.PurchaseListUiState
import com.auxo.pos.viewmodel.PurchaseListViewModel
import com.auxo.pos.viewmodel.PurchaseTab

@Composable
fun PurchaseListScreen(
    onBack: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    onOpenReceipt: (Long) -> Unit,
    onNewOrder: () -> Unit,
    onReceive: () -> Unit,
    onOpenSuppliers: () -> Unit,
    viewModel: PurchaseListViewModel = viewModel(factory = PurchaseListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            AuxoTopAppBar(
                title = stringResource(R.string.purchases_title),
                onBack = onBack,
                actions = { ActionIcon(R.drawable.ic_supplier, stringResource(R.string.suppliers_title), onOpenSuppliers) },
            )
        },
        floatingActionButton = {
            if (uiState.tab == PurchaseTab.ORDERS) {
                ExtendedFloatingActionButton(
                    onClick = onNewOrder,
                    icon = { Icon(painterResource(R.drawable.ic_purchase_order), contentDescription = null) },
                    text = { Text(stringResource(R.string.purchases_new_order)) },
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = onReceive,
                    icon = { Icon(painterResource(R.drawable.ic_receive), contentDescription = null) },
                    text = { Text(stringResource(R.string.purchases_receive)) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PurchaseListContent(
            state = uiState,
            onTabChange = viewModel::onTabChange,
            onOrderFilterChange = viewModel::onOrderFilterChange,
            onReceiptRangeChange = viewModel::onReceiptRangeChange,
            onQueryChange = viewModel::onQueryChange,
            onRetry = viewModel::retry,
            onOpenOrder = onOpenOrder,
            onOpenReceipt = onOpenReceipt,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun PurchaseListContent(
    state: PurchaseListUiState,
    onTabChange: (PurchaseTab) -> Unit,
    onOrderFilterChange: (OrderFilter) -> Unit,
    onReceiptRangeChange: (HistoryRange) -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    onOpenReceipt: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalMoneyFormatter.current
    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            Tab(
                selected = state.tab == PurchaseTab.ORDERS,
                onClick = { onTabChange(PurchaseTab.ORDERS) },
                text = { Text(stringResource(R.string.purchases_tab_orders)) },
            )
            Tab(
                selected = state.tab == PurchaseTab.RECEIPTS,
                onClick = { onTabChange(PurchaseTab.RECEIPTS) },
                text = { Text(stringResource(R.string.purchases_tab_receipts)) },
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            AuxoTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.purchases_search),
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (state.tab == PurchaseTab.ORDERS) {
                    OrderFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.orderFilter == filter,
                            onClick = { onOrderFilterChange(filter) },
                            label = { Text(stringResource(filter.labelRes())) },
                        )
                    }
                } else {
                    HistoryRange.entries.forEach { range ->
                        FilterChip(
                            selected = state.receiptRange == range,
                            onClick = { onReceiptRangeChange(range) },
                            label = { Text(stringResource(range.labelRes())) },
                        )
                    }
                }
            }
        }
        when {
            state.isLoading -> LoadingState()
            state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry)
            state.tab == PurchaseTab.ORDERS && state.orders.isEmpty() -> EmptyState(
                iconRes = R.drawable.ic_purchase_order,
                title = stringResource(R.string.orders_empty_title),
                message = stringResource(if (state.query.isBlank()) R.string.orders_empty_body else R.string.purchases_no_results),
            )
            state.tab == PurchaseTab.RECEIPTS && state.receipts.isEmpty() -> EmptyState(
                iconRes = R.drawable.ic_receive,
                title = stringResource(R.string.receipts_empty_title),
                message = stringResource(if (state.query.isBlank()) R.string.receipts_empty_body else R.string.purchases_no_results),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.testTag(if (state.tab == PurchaseTab.ORDERS) "order_list" else "receipt_list"),
            ) {
                if (state.tab == PurchaseTab.ORDERS) {
                    item(key = "open_value") {
                        StatTile(
                            label = stringResource(R.string.purchases_open_value),
                            value = formatter.format(state.openOrderValue),
                            iconRes = R.drawable.ic_purchase_order,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(state.orders, key = { "order-${it.id}" }) { order -> OrderRow(order, onClick = { onOpenOrder(order.id) }) }
                } else {
                    item(key = "received_total") {
                        StatTile(
                            label = stringResource(R.string.purchases_received_total),
                            value = formatter.format(state.receiptsTotal),
                            iconRes = R.drawable.ic_receive,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(state.receipts, key = { "receipt-${it.id}" }) { receipt -> ReceiptRow(receipt, onClick = { onOpenReceipt(receipt.id) }) }
                }
            }
        }
    }
}

@Composable
fun OrderRow(order: PurchaseOrder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    AuxoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(order.number, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOfNotNull(
                        order.supplierName.ifBlank { null },
                        pluralStringResource(R.plurals.order_item_count, order.lines.size, order.lines.size),
                        order.expectedDate?.let { stringResource(R.string.order_expected, DateFormats.date(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(formatter.format(order.total), style = MaterialTheme.typography.titleSmall)
                StatusChip(stringResource(order.status.labelRes()), order.status.tone())
            }
        }
    }
}

@Composable
fun ReceiptRow(receipt: PurchaseReceipt, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    AuxoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(receipt.number, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOfNotNull(
                        DateFormats.dateTime(receipt.receivedAtMillis),
                        receipt.supplierName,
                        receipt.supplierInvoiceNumber?.let { stringResource(R.string.receipt_bill, it) },
                        receipt.orderNumber,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatter.format(receipt.total), style = MaterialTheme.typography.titleSmall)
        }
    }
}
