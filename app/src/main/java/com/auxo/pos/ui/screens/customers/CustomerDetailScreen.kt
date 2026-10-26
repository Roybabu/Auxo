// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.customers

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.export.FileExport
import com.auxo.pos.domain.billing.IndianStates
import com.auxo.pos.domain.customers.LedgerLine
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.Money
import com.auxo.pos.ui.components.ActionIcon
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.StatTile
import com.auxo.pos.ui.components.StatusChip
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.screens.billing.InvoiceSummaryRow
import com.auxo.pos.ui.screens.billing.labelRes
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.CustomerDetailEvent
import kotlinx.coroutines.launch
import com.auxo.pos.viewmodel.CustomerDetailUiState
import com.auxo.pos.viewmodel.CustomerDetailViewModel

@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onReceivePayment: (Long) -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit = {},
    viewModel: CustomerDetailViewModel = viewModel(factory = CustomerDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val formatter = LocalMoneyFormatter.current
    val ready = uiState as? CustomerDetailUiState.Ready

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomerDetailEvent.Export ->
                    if (!FileExport.share(context, event.file, resources.getString(R.string.export_chooser))) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.export_failed))
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            AuxoTopAppBar(
                title = ready?.account?.customer?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (ready != null) {
                        ActionIcon(R.drawable.ic_download, stringResource(R.string.customer_export_statement), viewModel::exportStatement)
                        ActionIcon(R.drawable.ic_edit, stringResource(R.string.action_edit), { onEdit(ready.account.customer.id) })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val state = uiState) {
            CustomerDetailUiState.Loading -> LoadingState(modifier)
            CustomerDetailUiState.Failed -> ErrorState(stringResource(R.string.error_generic), viewModel::retry, modifier)
            CustomerDetailUiState.NotFound -> EmptyState(
                iconRes = R.drawable.ic_nav_customers,
                title = stringResource(R.string.customer_not_found),
                message = "",
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = modifier,
            )
            is CustomerDetailUiState.Ready -> CustomerDetailContent(
                state = state,
                onReceivePayment = { onReceivePayment(state.account.customer.id) },
                onOpenInvoice = onOpenInvoice,
                onOpenHistory = { onOpenHistory(state.account.customer.id) },
                onRemind = {
                    val shop = state.reminderShop
                    if (shop != null && !openReminder(context, state.account, shop, formatter)) {
                        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.reminder_failed)) }
                    }
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun CustomerDetailContent(
    state: CustomerDetailUiState.Ready,
    onReceivePayment: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    onRemind: (() -> Unit)? = null,
) {
    val formatter = LocalMoneyFormatter.current
    val customer = state.account.customer
    val balance = state.account.balance
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("customer_detail"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "header") {
            AuxoCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = stringResource(if (balance < Money.ZERO) R.string.customer_detail_advance else R.string.customer_detail_balance),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatter.format(if (balance < Money.ZERO) Money(-balance.minorUnits) else balance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (balance > Money.ZERO) AuxoThemeExtras.statusColors.warning.content else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("customer_balance"),
                    )
                    Text(
                        text = customer.creditLimit?.let { stringResource(R.string.customer_credit_limit, formatter.format(it)) }
                            ?: stringResource(R.string.customer_no_limit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val contact = listOfNotNull(
                        customer.phone,
                        customer.email,
                        customer.gstin?.let { stringResource(R.string.customer_gstin, it) },
                        customer.stateCode?.let { IndianStates.byCode(it)?.name },
                    )
                    if (contact.isNotEmpty()) {
                        Text(contact.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    customer.address?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    customer.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onReceivePayment,
                        enabled = balance > Money.ZERO,
                        modifier = Modifier
                            .padding(top = Spacing.sm)
                            .testTag("customer_receive_payment"),
                    ) {
                        Icon(painterResource(R.drawable.ic_wallet), contentDescription = null)
                        Text(stringResource(R.string.customer_receive_payment), modifier = Modifier.padding(start = Spacing.sm))
                    }
                    if (onRemind != null && balance > Money.ZERO && state.reminderShop != null) {
                        OutlinedButton(onClick = onRemind, modifier = Modifier.testTag("customer_remind")) {
                            Icon(painterResource(R.drawable.ic_chat), contentDescription = null)
                            Text(stringResource(R.string.reminder_button), modifier = Modifier.padding(start = Spacing.sm))
                        }
                        Text(
                            stringResource(R.string.reminder_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.testTag("customer_history")) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = null)
                        Text(stringResource(R.string.customer_history_open), modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
            }
        }
        item(key = "totals") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(stringResource(R.string.customer_bought), formatter.format(state.bought), Modifier.weight(1f))
                StatTile(stringResource(R.string.customer_paid), formatter.format(state.paid), Modifier.weight(1f))
            }
        }
        if (state.hasPoints) {
            item(key = "points_balance") {
                StatTile(
                    stringResource(R.string.customer_points_balance),
                    state.account.points.toString(),
                    Modifier.fillMaxWidth().testTag("customer_points"),
                )
            }
        }
        item(key = "tabs") {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.customer_statement)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.customer_bills, state.invoices.size)) })
                if (state.hasPoints) {
                    Tab(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        text = { Text(stringResource(R.string.customer_points_tab, state.pointsHistory.size)) },
                        modifier = Modifier.testTag("customer_points_tab"),
                    )
                }
            }
        }
        if (tab == 0) {
            if (state.statement.isEmpty()) {
                item(key = "no_entries") { EmptyLine(stringResource(R.string.customer_statement_empty)) }
            } else {
                item(key = "statement") {
                    AuxoCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.testTag("customer_statement")) {
                            state.statement.forEachIndexed { index, line ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                LedgerRow(line, onOpenInvoice)
                            }
                        }
                    }
                }
            }
        } else if (tab == 2 && state.hasPoints) {
            item(key = "points") {
                AuxoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.testTag("customer_points_list")) {
                        state.pointsHistory.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PointsRow(entry, onOpenInvoice)
                        }
                    }
                }
            }
        } else {
            if (state.invoices.isEmpty()) {
                item(key = "no_bills") { EmptyLine(stringResource(R.string.customer_bills_empty)) }
            } else {
                items(state.invoices, key = { "invoice-${it.id}" }) { invoice ->
                    InvoiceSummaryRow(invoice, onClick = { onOpenInvoice(invoice.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.md),
    )
}

@Composable
private fun PointsRow(entry: LedgerEntry, onOpenInvoice: (Long) -> Unit) {
    val status = AuxoThemeExtras.statusColors
    val gained = entry.points > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(entry.invoiceId?.let { id -> Modifier.clickable { onOpenInvoice(id) } } ?: Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(stringResource(entry.type.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(DateFormats.dateTime(entry.createdAtMillis), entry.invoiceNumber, entry.note).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (gained) pluralStringResource(R.plurals.customer_points_gained, entry.points, entry.points) else pluralStringResource(R.plurals.customer_points_used, -entry.points, -entry.points),
            style = MaterialTheme.typography.titleSmall,
            color = if (gained) status.success.accent else status.warning.content,
        )
    }
}

@Composable
private fun LedgerRow(line: LedgerLine, onOpenInvoice: (Long) -> Unit) {
    val formatter = LocalMoneyFormatter.current
    val status = AuxoThemeExtras.statusColors
    val entry = line.entry
    val owesMore = entry.amount > Money.ZERO
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(entry.invoiceId?.let { id -> Modifier.clickable { onOpenInvoice(id) } } ?: Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(stringResource(entry.type.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(
                    DateFormats.dateTime(entry.createdAtMillis),
                    entry.invoiceNumber,
                    entry.method?.let { stringResource(it.labelRes()) },
                    entry.reference,
                    entry.note,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (owesMore) "+" else "−") + formatter.format(if (owesMore) entry.amount else Money(-entry.amount.minorUnits)),
                style = MaterialTheme.typography.titleSmall,
                color = if (owesMore) status.warning.content else status.success.accent,
            )
            StatusChip(
                text = stringResource(R.string.customer_entry_balance, formatter.format(line.balanceAfter)),
                tone = status.neutral,
            )
        }
    }
}
