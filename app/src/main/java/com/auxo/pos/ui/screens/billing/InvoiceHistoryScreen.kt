// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.billing

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.ui.can
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.ui.components.rememberIsShortScreen
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.StatTile
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.HistoryRange
import com.auxo.pos.viewmodel.HistoryTab
import com.auxo.pos.viewmodel.InvoiceHistoryUiState
import com.auxo.pos.viewmodel.InvoiceHistoryViewModel

@Composable
fun InvoiceHistoryScreen(
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    viewModel: InvoiceHistoryViewModel = viewModel(factory = InvoiceHistoryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.history_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        InvoiceHistoryContent(
            state = uiState,
            onTabChange = viewModel::onTabChange,
            onRangeChange = viewModel::onRangeChange,
            onQueryChange = viewModel::onQueryChange,
            onRetry = viewModel::retry,
            onOpenInvoice = onOpenInvoice,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun InvoiceHistoryContent(
    state: InvoiceHistoryUiState,
    onTabChange: (HistoryTab) -> Unit,
    onRangeChange: (HistoryRange) -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalMoneyFormatter.current
    val status = AuxoThemeExtras.statusColors
    // Payment records and sales totals are business-wide figures; an Employee sees the invoice list only.
    val showFinancials = can(Permission.VIEW_FINANCIALS)
    Column(modifier = modifier.fillMaxSize()) {
        if (showFinancials) PrimaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            Tab(
                selected = state.tab == HistoryTab.INVOICES,
                onClick = { onTabChange(HistoryTab.INVOICES) },
                text = { Text(stringResource(R.string.history_tab_invoices)) },
            )
            Tab(
                selected = state.tab == HistoryTab.PAYMENTS,
                onClick = { onTabChange(HistoryTab.PAYMENTS) },
                text = { Text(stringResource(R.string.history_tab_payments)) },
            )
        }
        val header: @Composable (Modifier) -> Unit = { headerModifier ->
            Column(modifier = headerModifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AuxoTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.history_search_hint),
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search"),
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HistoryRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.range == range,
                        onClick = { onRangeChange(range) },
                        label = { Text(stringResource(range.labelRes())) },
                    )
                }
            }
        }
        }
        val short = rememberIsShortScreen()
        val listShown = !state.isLoading && !state.loadFailed && (state.tab == HistoryTab.PAYMENTS || state.invoices.isNotEmpty())
        if (!(short && listShown)) header(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm))

        when {
            state.isLoading -> LoadingState()
            state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry)
            state.tab == HistoryTab.INVOICES -> {
                if (state.invoices.isEmpty()) {
                    EmptyState(
                        iconRes = R.drawable.ic_invoice,
                        title = stringResource(R.string.history_empty_title),
                        message = stringResource(if (state.query.isBlank()) R.string.history_empty_body else R.string.history_no_results),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.testTag("history_list"),
                    ) {
                        if (short) item(key = "filters") { header(Modifier) }
                        // Sales and returns totals are business-wide figures.
                        if (showFinancials) item(key = "totals") {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                StatTile(stringResource(R.string.history_sales), formatter.format(state.salesTotal), Modifier.weight(1f))
                                StatTile(
                                    stringResource(R.string.history_returns),
                                    formatter.format(state.returnsTotal),
                                    Modifier.weight(1f),
                                    tone = if (state.returnsTotal > Money.ZERO) status.danger else null,
                                )
                            }
                        }
                        items(state.invoices, key = { it.id }) { invoice ->
                            InvoiceSummaryRow(invoice, onClick = { onOpenInvoice(invoice.id) })
                        }
                    }
                }
            }
            else -> PaymentsList(state, onOpenInvoice, if (short) ({ header(Modifier) }) else null)
        }
    }
}

@Composable
private fun PaymentsList(state: InvoiceHistoryUiState, onOpenInvoice: (Long) -> Unit, header: (@Composable () -> Unit)? = null) {
    val formatter = LocalMoneyFormatter.current
    val totals = state.paymentTotals
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.testTag("payments_list"),
    ) {
        header?.let { item(key = "filters") { it() } }
        item(key = "method_totals") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(stringResource(R.string.history_cash), formatter.format(totals.cash), Modifier.weight(1f))
                    StatTile(stringResource(R.string.history_upi), formatter.format(totals.upi), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(stringResource(R.string.history_card), formatter.format(totals.card), Modifier.weight(1f))
                    StatTile(
                        stringResource(R.string.history_refunds),
                        formatter.format(totals.refunds),
                        Modifier.weight(1f),
                        tone = if (totals.refunds > Money.ZERO) AuxoThemeExtras.statusColors.danger else null,
                    )
                }
                StatTile(
                    stringResource(R.string.history_net),
                    formatter.format(totals.net),
                    Modifier.fillMaxWidth(),
                    tone = AuxoThemeExtras.statusColors.success,
                )
                if (!totals.onAccount.isZero || !totals.accountPayments.isZero) {
                    Text(
                        stringResource(R.string.history_account_note, formatter.format(totals.onAccount), formatter.format(totals.accountPayments)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.payments.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(R.string.history_no_payments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
        } else {
            item(key = "list") {
                AuxoCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.payments.forEachIndexed { index, payment ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PaymentRow(payment, onClick = { if (payment.invoiceId != 0L) onOpenInvoice(payment.invoiceId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: PaymentRecord, onClick: () -> Unit) {
    val formatter = LocalMoneyFormatter.current
    val refund = payment.kind == PaymentKind.REFUND
    val method = stringResource(payment.method.labelRes())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        refund -> stringResource(R.string.history_refund_label, method)
                        payment.customerName != null -> stringResource(R.string.history_account_payment, method)
                        else -> method
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = listOfNotNull(payment.invoiceNumber ?: payment.customerName, DateFormats.dateTime(payment.createdAtMillis), payment.reference)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = (if (refund) "−" else "") + formatter.format(payment.amount),
                style = MaterialTheme.typography.titleSmall,
                color = if (refund) MaterialTheme.colorScheme.error else AuxoThemeExtras.statusColors.success.accent,
                modifier = Modifier.padding(start = 4.dp),
            )
    }
}
