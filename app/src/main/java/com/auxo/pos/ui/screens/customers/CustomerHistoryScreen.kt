package com.auxo.pos.ui.screens.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import com.auxo.pos.R
import com.auxo.pos.domain.customers.CustomerHistory
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.components.StatTile
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.screens.billing.InvoiceSummaryRow
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.CustomerHistoryUiState
import com.auxo.pos.viewmodel.CustomerHistoryViewModel

/** What one customer has bought: total spend, visits, last visit, average bill, and every bill and return. */
@Composable
fun CustomerHistoryScreen(
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    viewModel: CustomerHistoryViewModel = viewModel(factory = CustomerHistoryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = uiState as? CustomerHistoryUiState.Ready
    Scaffold(
        topBar = {
            AuxoTopAppBar(
                title = ready?.customer?.name ?: stringResource(R.string.customer_history_title),
                onBack = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val state = uiState) {
            CustomerHistoryUiState.Loading -> LoadingState(modifier)
            CustomerHistoryUiState.Failed -> ErrorState(stringResource(R.string.error_generic), viewModel::retry, modifier)
            CustomerHistoryUiState.NotFound -> EmptyState(
                iconRes = R.drawable.ic_nav_customers,
                title = stringResource(R.string.customer_not_found),
                message = "",
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = modifier,
            )
            is CustomerHistoryUiState.Ready -> CustomerHistoryContent(state.history, onOpenInvoice, modifier)
        }
    }
}

@Composable
fun CustomerHistoryContent(history: CustomerHistory, onOpenInvoice: (Long) -> Unit, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("customer_history_screen"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "stats") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(stringResource(R.string.customer_history_total_spend), formatter.format(history.totalSpend), Modifier.weight(1f).testTag("history_total_spend"))
                    StatTile(stringResource(R.string.customer_history_visits), history.visitCount.toString(), Modifier.weight(1f).testTag("history_visits"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        stringResource(R.string.customer_history_last_visit),
                        history.lastVisitMillis?.let { DateFormats.dateTime(it) } ?: stringResource(R.string.customer_history_never),
                        Modifier.weight(1f).testTag("history_last_visit"),
                    )
                    StatTile(
                        stringResource(R.string.customer_history_average),
                        history.averageBill?.let { formatter.format(it) } ?: "—",
                        Modifier.weight(1f).testTag("history_average"),
                    )
                }
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.customer_history_total_spend_note),
                        history.firstVisitMillis?.let { stringResource(R.string.customer_history_since, DateFormats.dateTime(it)) },
                        history.returnsTotal.takeIf { !it.isZero }?.let { stringResource(R.string.customer_history_returned, formatter.format(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (history.purchases.isEmpty() && history.returns.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.customer_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().testTag("history_empty"),
                )
            }
        }
        if (history.purchases.isNotEmpty()) {
            item(key = "purchases_header") { SectionHeader(stringResource(R.string.customer_history_purchases, history.purchases.size)) }
            items(history.purchases, key = { "sale-${it.id}" }) { InvoiceSummaryRow(it, onClick = { onOpenInvoice(it.id) }) }
        }
        if (history.returns.isNotEmpty()) {
            item(key = "returns_header") { SectionHeader(stringResource(R.string.customer_history_returns, history.returns.size)) }
            items(history.returns, key = { "return-${it.id}" }) { InvoiceSummaryRow(it, onClick = { onOpenInvoice(it.id) }) }
        }
    }
}
