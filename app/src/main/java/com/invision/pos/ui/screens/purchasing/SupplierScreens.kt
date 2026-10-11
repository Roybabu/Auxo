package com.invision.pos.ui.screens.purchasing

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.contacts.ContactField
import com.invision.pos.domain.contacts.SupplierForm
import com.invision.pos.domain.model.Supplier
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.StatusChip
import com.invision.pos.ui.screens.billing.StatePicker
import com.invision.pos.ui.screens.customers.messageRes
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.SupplierEditorEvent
import com.invision.pos.viewmodel.SupplierEditorUiState
import com.invision.pos.viewmodel.SupplierEditorViewModel
import com.invision.pos.viewmodel.SupplierListUiState
import com.invision.pos.viewmodel.SupplierListViewModel

@Composable
fun SupplierListScreen(
    onBack: () -> Unit,
    onOpenSupplier: (Long) -> Unit,
    onAddSupplier: () -> Unit,
    viewModel: SupplierListViewModel = viewModel(factory = SupplierListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.suppliers_title), onBack = onBack) },
        floatingActionButton = {
            if (uiState.activeCount + uiState.hiddenCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onAddSupplier,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.suppliers_add)) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        SupplierListContent(
            state = uiState,
            onQueryChange = viewModel::onQueryChange,
            onShowHiddenChange = viewModel::onShowHiddenChange,
            onRetry = viewModel::retry,
            onOpenSupplier = onOpenSupplier,
            onAddSupplier = onAddSupplier,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun SupplierListContent(
    state: SupplierListUiState,
    onQueryChange: (String) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenSupplier: (Long) -> Unit,
    onAddSupplier: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry, modifier)
        state.activeCount + state.hiddenCount == 0 -> EmptyState(
            iconRes = R.drawable.ic_supplier,
            title = stringResource(R.string.suppliers_empty_title),
            message = stringResource(R.string.suppliers_empty_body),
            actionLabel = stringResource(R.string.suppliers_add),
            onAction = onAddSupplier,
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("supplier_list"),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    InvisionTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        label = stringResource(R.string.suppliers_search),
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.hiddenCount > 0) {
                        FilterChip(
                            selected = state.showHidden,
                            onClick = { onShowHiddenChange(!state.showHidden) },
                            label = { Text(stringResource(R.string.suppliers_show_hidden, state.hiddenCount)) },
                        )
                    }
                }
            }
            if (state.suppliers.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.suppliers_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.lg),
                    )
                }
            }
            items(state.suppliers, key = { it.id }) { supplier ->
                SupplierRow(supplier, onClick = { onOpenSupplier(supplier.id) })
            }
        }
    }
}

@Composable
private fun SupplierRow(supplier: Supplier, onClick: () -> Unit) {
    InvisionCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(painterResource(R.drawable.ic_supplier), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(supplier.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(supplier.contactPerson, supplier.phone, supplier.gstin)
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!supplier.active) StatusChip(stringResource(R.string.supplier_hidden), InvisionThemeExtras.statusColors.neutral)
        }
    }
}

@Composable
fun SupplierEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SupplierEditorViewModel = viewModel(factory = SupplierEditorViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SupplierEditorEvent.Saved -> onSaved()
                is SupplierEditorEvent.ActiveChanged -> snackbarHostState.showSnackbar(
                    resources.getString(if (event.active) R.string.supplier_shown_done else R.string.supplier_hidden_done),
                )
                is SupplierEditorEvent.Invalid -> snackbarHostState.showSnackbar(resources.getString(R.string.form_fix_errors))
                SupplierEditorEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    Scaffold(
        topBar = {
            InvisionTopAppBar(
                title = stringResource(if (uiState.isNew) R.string.supplier_editor_new else R.string.supplier_editor_edit),
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
            else -> SupplierEditorContent(uiState, viewModel::onFormChange, viewModel::setActive, modifier)
        }
    }
}

@Composable
fun SupplierEditorContent(
    state: SupplierEditorUiState,
    onFormChange: (SupplierForm) -> Unit,
    onActiveChange: (Boolean) -> Unit,
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
            .testTag("supplier_editor"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        InvisionCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InvisionTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it.take(90))) },
                    label = stringResource(R.string.supplier_field_name),
                    error = errorOf(ContactField.NAME),
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_name"),
                )
                InvisionTextField(
                    value = form.contactPerson,
                    onValueChange = { onFormChange(form.copy(contactPerson = it.take(90))) },
                    label = stringResource(R.string.supplier_field_contact),
                    error = errorOf(ContactField.CONTACT_PERSON),
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.fillMaxWidth(),
                )
                InvisionTextField(
                    value = form.phone,
                    onValueChange = { onFormChange(form.copy(phone = it.filter { c -> c.isDigit() || c in "+- " }.take(18))) },
                    label = stringResource(R.string.supplier_field_phone),
                    error = errorOf(ContactField.PHONE),
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_phone"),
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
        if (!state.isNew) {
            Text(
                stringResource(R.string.supplier_hidden_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { onActiveChange(!state.active) },
                enabled = !state.isSaving,
                modifier = Modifier.testTag("supplier_toggle_active"),
            ) {
                Text(stringResource(if (state.active) R.string.supplier_hide else R.string.supplier_show))
            }
        }
    }
}
