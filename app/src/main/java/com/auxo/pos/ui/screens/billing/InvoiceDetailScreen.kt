package com.auxo.pos.ui.screens.billing

import com.auxo.pos.domain.billing.BusinessTime
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.ui.can
import com.auxo.pos.core.receipt.ReceiptOutput
import com.auxo.pos.core.receipt.WhatsAppShare
import com.auxo.pos.core.receipt.ReceiptFonts
import com.auxo.pos.core.receipt.ReceiptLabels
import com.auxo.pos.core.receipt.ReceiptRenderer
import com.auxo.pos.core.receipt.isThermal
import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.ReceiptPaper
import com.auxo.pos.ui.LocalStoreLogo
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.InvoiceDetailUiState
import com.auxo.pos.viewmodel.InvoiceDetailViewModel
import com.auxo.pos.viewmodel.InvoiceNotice
import kotlinx.coroutines.launch
import java.time.ZoneId

@Composable
fun InvoiceDetailScreen(
    onBack: () -> Unit,
    onReturn: (invoiceId: Long) -> Unit,
    onOpenInvoice: (invoiceId: Long) -> Unit,
    onNewBill: () -> Unit,
    viewModel: InvoiceDetailViewModel = viewModel(factory = InvoiceDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val logo = LocalStoreLogo.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ready = uiState as? InvoiceDetailUiState.Ready
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val formatter = LocalMoneyFormatter.current
    val bluetoothPrint = rememberBluetoothPrint(snackbarHostState, logo)

    LaunchedEffect(notice, ready?.invoice?.number) {
        val message = notice ?: return@LaunchedEffect
        val number = ready?.invoice?.number ?: return@LaunchedEffect
        viewModel.noticeShown()
        val text = when {
            message.notice == InvoiceNotice.RETURN_SAVED -> resources.getString(R.string.return_completed, number)
            message.change.isZero -> resources.getString(R.string.sale_completed, number)
            else -> resources.getString(R.string.sale_completed_change, number, formatter.format(message.change))
        }
        snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Long)
    }

    Scaffold(
        topBar = {
            val title = ready?.let {
                stringResource(
                    if (it.invoice.type == InvoiceType.RETURN) R.string.credit_note_title else R.string.invoice_title,
                    it.invoice.number,
                )
            }.orEmpty()
            AuxoTopAppBar(title = title, onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val state = uiState) {
            InvoiceDetailUiState.Loading -> LoadingState(modifier)
            InvoiceDetailUiState.Failed -> ErrorState(stringResource(R.string.error_generic), viewModel::retry, modifier)
            InvoiceDetailUiState.NotFound -> EmptyState(
                iconRes = R.drawable.ic_nav_billing,
                title = stringResource(R.string.invoice_not_found),
                message = "",
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = modifier,
            )
            is InvoiceDetailUiState.Ready -> InvoiceDetailContent(
                state = state,
                onPaperChange = viewModel::onPaperChange,
                onPrint = {
                    if (!ReceiptOutput.print(context, state.invoice, state.receiptOptions, logo)) {
                        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.invoice_print_failed)) }
                    }
                },
                onShare = {
                    scope.launch {
                        val title = resources.getString(R.string.invoice_share_title)
                        if (!ReceiptOutput.share(context, state.invoice, state.receiptOptions, title, logo)) {
                            snackbarHostState.showSnackbar(resources.getString(R.string.invoice_share_failed))
                        }
                    }
                },
                onBluetoothPrint = bluetoothPrint?.let { print -> { print(state.invoice, state.receiptOptions) } },
                onWhatsApp = {
                    scope.launch {
                        val caption = resources.getString(
                            R.string.whatsapp_caption,
                            state.invoice.number,
                            state.invoice.seller.name,
                            formatter.format(state.invoice.grandTotal),
                        )
                        if (!WhatsAppShare.shareInvoice(context, state.invoice, state.receiptOptions, caption, resources.getString(R.string.whatsapp_share_title), logo)) {
                            snackbarHostState.showSnackbar(resources.getString(R.string.invoice_whatsapp_failed))
                        }
                    }
                },
                onReturn = { onReturn(state.invoice.id) },
                onOpenInvoice = onOpenInvoice,
                onNewBill = onNewBill,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvoiceDetailContent(
    state: InvoiceDetailUiState.Ready,
    onPaperChange: (ReceiptPaper) -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onReturn: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onNewBill: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = BusinessTime.ZONE,
    onWhatsApp: (() -> Unit)? = null,
    /** Prints on the shop's Bluetooth printer; the button is left out when null (no printer chosen). */
    onBluetoothPrint: (() -> Unit)? = null,
) {
    val invoice = state.invoice
    val logo = LocalStoreLogo.current
    val context = LocalContext.current
    val labels = remember(state.receiptOptions.language) { ReceiptLabels.from(context, state.receiptOptions.language) }
    val pages = remember(invoice, state.receiptOptions, logo, labels) { ReceiptRenderer.pages(invoice, state.receiptOptions, zone, logo, labels, ReceiptFonts.from(context)) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onPrint, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.ic_print), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.invoice_print), modifier = Modifier.padding(start = Spacing.sm))
                    }
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.invoice_share), modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
                if (onBluetoothPrint != null) {
                    Button(onClick = onBluetoothPrint, modifier = Modifier.fillMaxWidth().testTag("invoice_bluetooth_print")) {
                        Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.printer_print_button), modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
                if (onWhatsApp != null) {
                    OutlinedButton(onClick = onWhatsApp, modifier = Modifier.fillMaxWidth().testTag("invoice_whatsapp")) {
                        Icon(painterResource(R.drawable.ic_chat), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.whatsapp_share), modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // A return issues a credit note and refunds money, so it is a Super Admin action.
                    if (state.canReturn && can(Permission.PROCESS_RETURN)) {
                        OutlinedButton(onClick = onReturn, modifier = Modifier.weight(1f)) {
                            Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.invoice_return), modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                    OutlinedButton(onClick = onNewBill, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.invoice_new_bill))
                    }
                }
                if (invoice.type == InvoiceType.SALE && invoice.isFullyReturned) {
                    Text(
                        stringResource(R.string.invoice_fully_returned),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                invoice.originalInvoiceId?.let { originalId ->
                    OutlinedButton(onClick = { onOpenInvoice(originalId) }) {
                        Text(stringResource(R.string.invoice_against, invoice.originalInvoiceNumber.orEmpty()))
                    }
                }
            }
        }

        item(key = "paper") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionHeader(stringResource(R.string.invoice_paper))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ReceiptPaper.entries.forEach { paper ->
                        FilterChip(
                            selected = state.paper == paper,
                            onClick = { onPaperChange(paper) },
                            label = { Text(stringResource(paper.shortLabelRes())) },
                            modifier = Modifier.testTag("paper_${paper.name}"),
                        )
                    }
                }
            }
        }

        if (state.creditNotes.isNotEmpty()) {
            item(key = "credit_notes_title") { SectionHeader(stringResource(R.string.invoice_credit_notes)) }
            items(state.creditNotes, key = { "cn_${it.id}" }) { note -> InvoiceSummaryRow(note, onClick = { onOpenInvoice(note.id) }) }
        }

        item(key = "preview_title") { SectionHeader(stringResource(R.string.invoice_preview)) }
        items(pages.indices.toList(), key = { "page_$it" }) { index ->
            val page = pages[index]
            val widthPx = if (state.paper.isThermal) 576 else 1240
            val bitmap = remember(page, widthPx) { ReceiptOutput.previewBitmap(page, widthPx).asImageBitmap() }
            val maxWidth = when (state.paper) {
                ReceiptPaper.THERMAL_58 -> 280.dp
                ReceiptPaper.THERMAL_76 -> 340.dp
                ReceiptPaper.THERMAL_80 -> 360.dp
                ReceiptPaper.A5, ReceiptPaper.A4 -> 600.dp
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .testTag("receipt_preview"),
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.invoice_preview),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** One invoice or credit note in a list. */
@Composable
fun InvoiceSummaryRow(summary: InvoiceSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    val isReturn = summary.type == InvoiceType.RETURN
    AuxoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.number, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOf(
                        DateFormats.dateTime(summary.createdAtMillis),
                        summary.buyerName ?: stringResource(R.string.billing_walk_in),
                        pluralStringResource(R.plurals.history_items, summary.itemCount, summary.itemCount),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isReturn) "−" else "") + formatter.format(summary.grandTotal),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isReturn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (isReturn) {
                    Text(
                        stringResource(R.string.history_return_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
