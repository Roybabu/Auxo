package com.auxo.pos.ui.screens.purchasing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.model.PurchaseReceiptLine
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ReceiptDetailUiState
import com.auxo.pos.viewmodel.ReceiptDetailViewModel

@Composable
fun ReceiptDetailScreen(
    onBack: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    viewModel: ReceiptDetailViewModel = viewModel(factory = ReceiptDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = uiState as? ReceiptDetailUiState.Ready
    Scaffold(
        topBar = { AuxoTopAppBar(title = ready?.receipt?.number.orEmpty(), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val state = uiState) {
            ReceiptDetailUiState.Loading -> LoadingState(modifier)
            ReceiptDetailUiState.Failed -> ErrorState(stringResource(R.string.error_generic), viewModel::retry, modifier)
            ReceiptDetailUiState.NotFound -> EmptyState(
                iconRes = R.drawable.ic_receive,
                title = stringResource(R.string.receipt_not_found),
                message = "",
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = modifier,
            )
            is ReceiptDetailUiState.Ready -> ReceiptDetailContent(state.receipt, onOpenOrder, modifier)
        }
    }
}

@Composable
fun ReceiptDetailContent(receipt: PurchaseReceipt, onOpenOrder: (Long) -> Unit, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
            .testTag("receipt_detail"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AuxoCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                receipt.supplierName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                Text(
                    text = listOfNotNull(
                        DateFormats.dateTime(receipt.receivedAtMillis),
                        receipt.supplierInvoiceNumber?.let { stringResource(R.string.receipt_bill, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                receipt.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val orderId = receipt.orderId
                if (orderId != null && receipt.orderNumber != null) {
                    TextButton(onClick = { onOpenOrder(orderId) }) {
                        Text(stringResource(R.string.receipt_against, receipt.orderNumber))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.receive_total), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(formatter.format(receipt.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        SectionHeader(stringResource(R.string.order_items))
        AuxoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                receipt.lines.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ReceiptLineRow(line)
                }
            }
        }
    }
}

@Composable
private fun ReceiptLineRow(line: PurchaseReceiptLine) {
    val formatter = LocalMoneyFormatter.current
    val unit = line.stockUnit
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
                text = listOfNotNull(
                    stringResource(R.string.order_line_detail, unit.withSymbol(line.quantity), formatter.format(line.unitCost), unit.entrySymbol),
                    line.batchNumber?.let { stringResource(R.string.detail_batch, it) },
                    line.expiryDate?.let { stringResource(R.string.detail_expiry, DateFormats.date(it)) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatter.format(line.lineTotal), style = MaterialTheme.typography.titleSmall)
    }
}
