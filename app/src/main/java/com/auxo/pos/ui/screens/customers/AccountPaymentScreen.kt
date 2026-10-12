package com.auxo.pos.ui.screens.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.auxo.pos.domain.customers.AccountPaymentError
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.screens.billing.labelRes
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.AccountPaymentEvent
import com.auxo.pos.viewmodel.AccountPaymentUiState
import com.auxo.pos.viewmodel.AccountPaymentViewModel

/** Money received against a customer's account. */
@Composable
fun AccountPaymentScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AccountPaymentViewModel = viewModel(factory = AccountPaymentViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountPaymentEvent.Saved -> onSaved()
                AccountPaymentEvent.CustomerMissing -> snackbarHostState.showSnackbar(resources.getString(R.string.customer_not_found))
                AccountPaymentEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.account_payment_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            uiState.isLoading -> LoadingState(modifier)
            uiState.loadFailed -> ErrorState(stringResource(R.string.error_generic), viewModel::retry, modifier)
            else -> AccountPaymentContent(
                state = uiState,
                onAmountChange = viewModel::onAmountChange,
                onMethodChange = viewModel::onMethodChange,
                onReferenceChange = viewModel::onReferenceChange,
                onNoteChange = viewModel::onNoteChange,
                onSave = viewModel::save,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun AccountPaymentContent(
    state: AccountPaymentUiState,
    onAmountChange: (String) -> Unit,
    onMethodChange: (PaymentMethod) -> Unit,
    onReferenceChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalMoneyFormatter.current
    val errors = state.errors
    val amountError = errors.firstOrNull {
        it == AccountPaymentError.AMOUNT_REQUIRED || it == AccountPaymentError.AMOUNT_INVALID ||
            it == AccountPaymentError.MORE_THAN_DUE || it == AccountPaymentError.NOTHING_DUE
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
            .testTag("account_payment"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AuxoCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(state.account?.customer?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (state.balance > Money.ZERO) {
                        stringResource(R.string.account_payment_due, formatter.format(state.balance))
                    } else {
                        stringResource(R.string.account_payment_nothing_due)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        AuxoCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AuxoTextField(
                    value = state.amount,
                    onValueChange = onAmountChange,
                    label = stringResource(R.string.payment_amount),
                    error = amountError?.let { stringResource(it.messageRes()) },
                    supportingText = state.balanceAfter?.let { stringResource(R.string.account_payment_after, formatter.format(it)) },
                    prefix = stringResource(R.string.currency_prefix),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_payment_amount"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PaymentMethod.MONEY.forEach { method ->
                        FilterChip(
                            selected = state.method == method,
                            onClick = { onMethodChange(method) },
                            label = { Text(stringResource(method.labelRes())) },
                        )
                    }
                }
                if (state.method != PaymentMethod.CASH) {
                    AuxoTextField(
                        value = state.reference,
                        onValueChange = onReferenceChange,
                        label = stringResource(if (state.method == PaymentMethod.UPI) R.string.payment_upi_reference else R.string.payment_card_last4),
                        error = if (AccountPaymentError.REFERENCE_INVALID in errors) stringResource(R.string.payment_error_reference) else null,
                        keyboardType = if (state.method == PaymentMethod.CARD) KeyboardType.Number else KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AuxoTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    label = stringResource(R.string.account_payment_note),
                    error = if (AccountPaymentError.NOTE_TOO_LONG in errors) stringResource(R.string.error_too_long) else null,
                    capitalization = KeyboardCapitalization.Sentences,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Button(
            onClick = onSave,
            enabled = !state.isSaving && state.balance > Money.ZERO,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account_payment_save"),
        ) {
            Text(stringResource(R.string.account_payment_save))
        }
    }
}
