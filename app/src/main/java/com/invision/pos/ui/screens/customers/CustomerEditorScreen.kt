package com.invision.pos.ui.screens.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.contacts.ContactField
import com.invision.pos.domain.contacts.CustomerForm
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.screens.billing.StatePicker
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.CustomerEditorEvent
import com.invision.pos.viewmodel.CustomerEditorUiState
import com.invision.pos.viewmodel.CustomerEditorViewModel

@Composable
fun CustomerEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: CustomerEditorViewModel = viewModel(factory = CustomerEditorViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomerEditorEvent.Saved -> onSaved(event.customerId)
                CustomerEditorEvent.Invalid -> snackbarHostState.showSnackbar(resources.getString(R.string.form_fix_errors))
                CustomerEditorEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    Scaffold(
        topBar = {
            InvisionTopAppBar(
                title = stringResource(if (uiState.isNew) R.string.customer_editor_new else R.string.customer_editor_edit),
                onBack = onBack,
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !uiState.isSaving && !uiState.isLoading && !uiState.loadFailed) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            uiState.isLoading -> LoadingState(modifier)
            uiState.loadFailed -> ErrorState(stringResource(R.string.error_generic), viewModel::retry, modifier)
            else -> CustomerEditorContent(uiState, viewModel::onFormChange, modifier)
        }
    }
}

@Composable
fun CustomerEditorContent(
    state: CustomerEditorUiState,
    onFormChange: (CustomerForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val errors = state.visibleErrors

    @Composable
    fun errorOf(field: ContactField): String? = errors[field]?.let { stringResource(it.messageRes()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
            .testTag("customer_editor"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        InvisionCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InvisionTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it.take(90))) },
                    label = stringResource(R.string.contact_field_name),
                    error = errorOf(ContactField.NAME),
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name"),
                )
                InvisionTextField(
                    value = form.phone,
                    onValueChange = { onFormChange(form.copy(phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16))) },
                    label = stringResource(R.string.billing_customer_phone),
                    error = errorOf(ContactField.PHONE),
                    supportingText = stringResource(R.string.customer_field_phone_hint),
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone"),
                )
                InvisionTextField(
                    value = form.email,
                    onValueChange = { onFormChange(form.copy(email = it.trim().take(110))) },
                    label = stringResource(R.string.contact_field_email),
                    error = errorOf(ContactField.EMAIL),
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.fillMaxWidth(),
                )
                InvisionTextField(
                    value = form.gstin,
                    onValueChange = { onFormChange(form.copy(gstin = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(15))) },
                    label = stringResource(R.string.contact_field_gstin),
                    error = errorOf(ContactField.GSTIN),
                    capitalization = KeyboardCapitalization.Characters,
                    modifier = Modifier.fillMaxWidth(),
                )
                StatePicker(
                    label = stringResource(R.string.contact_field_state),
                    selectedCode = form.stateCode,
                    onSelect = { onFormChange(form.copy(stateCode = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                InvisionTextField(
                    value = form.address,
                    onValueChange = { onFormChange(form.copy(address = it.take(220))) },
                    label = stringResource(R.string.contact_field_address),
                    error = errorOf(ContactField.ADDRESS),
                    capitalization = KeyboardCapitalization.Sentences,
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        InvisionCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.customer_section_account), style = MaterialTheme.typography.titleSmall)
                InvisionTextField(
                    value = form.creditLimit,
                    onValueChange = { onFormChange(form.copy(creditLimit = it.filter { c -> c.isDigit() || c == '.' }.take(12))) },
                    label = stringResource(R.string.customer_field_credit_limit),
                    error = errorOf(ContactField.CREDIT_LIMIT),
                    supportingText = stringResource(R.string.customer_field_credit_limit_hint),
                    prefix = "₹",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_credit_limit"),
                )
                if (state.isNew) {
                    InvisionTextField(
                        value = form.openingBalance,
                        onValueChange = { onFormChange(form.copy(openingBalance = it.filter { c -> c.isDigit() || c == '.' }.take(12))) },
                        label = stringResource(R.string.customer_field_opening_balance),
                        error = errorOf(ContactField.OPENING_BALANCE),
                        supportingText = stringResource(R.string.customer_field_opening_balance_hint),
                        prefix = "₹",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_opening_balance"),
                    )
                }
                InvisionTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it.take(320))) },
                    label = stringResource(R.string.contact_field_notes),
                    error = errorOf(ContactField.NOTES),
                    capitalization = KeyboardCapitalization.Sentences,
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
