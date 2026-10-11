package com.invision.pos.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.QuantityStepper
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.ReturnEvent
import com.invision.pos.viewmodel.ReturnUiState
import com.invision.pos.viewmodel.ReturnViewModel

@Composable
fun ReturnScreen(
    onBack: () -> Unit,
    onCompleted: (creditNoteId: Long) -> Unit,
    viewModel: ReturnViewModel = viewModel(factory = ReturnViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val currentOnCompleted by rememberUpdatedState(onCompleted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReturnEvent.Completed -> currentOnCompleted(event.creditNoteId)
                ReturnEvent.AlreadyReturned -> snackbarHostState.showSnackbar(resources.getString(R.string.return_already_returned))
                ReturnEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.return_title), onBack = onBack) },
        bottomBar = {
            if (uiState.invoice != null) {
                ReturnBar(uiState, onConfirm = viewModel::confirm)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            uiState.isLoading -> LoadingState(modifier)
            uiState.loadFailed -> ErrorState(stringResource(R.string.invoice_not_found), viewModel::retry, modifier)
            else -> ReturnContent(
                state = uiState,
                onQuantityChange = viewModel::setQuantity,
                onReturnAll = viewModel::returnAll,
                onRefundMethodChange = viewModel::onRefundMethodChange,
                onReasonChange = viewModel::onReasonChange,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReturnContent(
    state: ReturnUiState,
    onQuantityChange: (Long, Int) -> Unit,
    onReturnAll: () -> Unit,
    onRefundMethodChange: (PaymentMethod) -> Unit,
    onReasonChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val invoice = state.invoice ?: return
    val formatter = LocalMoneyFormatter.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("return_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "header") {
            SectionHeader(
                title = stringResource(R.string.return_from, invoice.number),
                action = {
                    if (!invoice.isFullyReturned) {
                        TextButton(onClick = onReturnAll) { Text(stringResource(R.string.return_all)) }
                    }
                },
            )
        }
        if (invoice.isFullyReturned) {
            item(key = "done") {
                Text(stringResource(R.string.return_nothing_left), style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(invoice.lines, key = { it.id }) { line ->
            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            listOf(line.productName, line.variantLabel).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.return_sold, line.quantity, line.returnedQuantity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(formatter.format(line.total), style = MaterialTheme.typography.bodySmall)
                    }
                    QuantityStepper(
                        value = state.quantities[line.id] ?: 0,
                        onValueChange = { onQuantityChange(line.id, it) },
                        max = line.returnableQuantity,
                    )
                }
            }
        }
        item(key = "refund") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(stringResource(R.string.return_refund_method))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // Refunds can go back on the account only for sales made to a customer account.
                    val methods = if (state.invoice.customerId != null) PaymentMethod.entries else PaymentMethod.MONEY
                    methods.forEach { method ->
                        FilterChip(
                            selected = state.refundMethod == method,
                            onClick = { onRefundMethodChange(method) },
                            label = { Text(stringResource(method.labelRes())) },
                        )
                    }
                }
                InvisionTextField(
                    value = state.reason,
                    onValueChange = onReasonChange,
                    label = stringResource(R.string.return_reason),
                    capitalization = KeyboardCapitalization.Sentences,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ReturnBar(state: ReturnUiState, onConfirm: () -> Unit) {
    val formatter = LocalMoneyFormatter.current
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.refund?.let { stringResource(R.string.return_refund, formatter.format(it)) }
                    ?: stringResource(R.string.return_none_selected),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .testTag("return_refund"),
            )
            Button(onClick = onConfirm, enabled = state.selectedUnits > 0 && !state.isSaving, modifier = Modifier.testTag("return_confirm")) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.return_confirm))
                }
            }
        }
    }
}
