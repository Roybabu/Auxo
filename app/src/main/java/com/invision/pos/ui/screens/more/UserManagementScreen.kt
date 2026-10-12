package com.invision.pos.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.core.auth.ManagedUser
import com.invision.pos.core.auth.Role
import com.invision.pos.core.auth.SetupField
import com.invision.pos.ui.components.ConfirmDialog
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.screens.auth.PasswordToggle
import com.invision.pos.ui.screens.auth.messageRes
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.EmployeeForm
import com.invision.pos.viewmodel.ResetForm
import com.invision.pos.viewmodel.UserManagementEvent
import com.invision.pos.viewmodel.UserManagementUiState
import com.invision.pos.viewmodel.UserManagementViewModel

/** Super Admin only (the route and the data layer both refuse anyone else). */
@Composable
fun UserManagementScreen(
    onBack: () -> Unit,
    viewModel: UserManagementViewModel = viewModel(factory = UserManagementViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is UserManagementEvent.Created -> resources.getString(R.string.users_created, event.name)
                is UserManagementEvent.Activated -> resources.getString(R.string.users_activated, event.name)
                is UserManagementEvent.Deactivated -> resources.getString(R.string.users_deactivated, event.name)
                is UserManagementEvent.PasswordReset -> resources.getString(R.string.users_password_reset_done, event.name)
                UserManagementEvent.Failed -> resources.getString(R.string.error_generic)
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.users_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        UserManagementContent(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            onAdd = viewModel::openCreate,
            onActiveChange = viewModel::onActiveChange,
            onReset = viewModel::openReset,
        )
    }

    state.createForm?.let { form ->
        CreateEmployeeDialog(
            form = form,
            onName = viewModel::onNameChange,
            onIdentifier = viewModel::onIdentifierChange,
            onPassword = viewModel::onPasswordChange,
            onConfirm = viewModel::onConfirmChange,
            onSubmit = viewModel::submitCreate,
            onDismiss = viewModel::closeCreate,
        )
    }
    state.resetForm?.let { form ->
        ResetPasswordDialog(
            form = form,
            onPassword = viewModel::onResetPasswordChange,
            onConfirm = viewModel::onResetConfirmChange,
            onSubmit = viewModel::submitReset,
            onDismiss = viewModel::closeReset,
        )
    }
    state.confirmDeactivate?.let { user ->
        ConfirmDialog(
            title = stringResource(R.string.users_deactivate_title),
            message = stringResource(R.string.users_deactivate_body, user.name),
            confirmLabel = stringResource(R.string.users_deactivate_action),
            onConfirm = viewModel::confirmDeactivate,
            onDismiss = viewModel::cancelDeactivate,
            destructive = true,
        )
    }
}

@Composable
fun UserManagementContent(
    state: UserManagementUiState,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
    onActiveChange: (ManagedUser, Boolean) -> Unit,
    onReset: (ManagedUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry, modifier)
        else -> LazyColumn(
            modifier = modifier.fillMaxSize().testTag("users_list"),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "add") {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag("users_add")) {
                    Text(stringResource(R.string.users_add))
                }
            }
            items(state.users, key = { it.id }) { user ->
                UserRow(user, onActiveChange = { onActiveChange(user, it) }, onReset = { onReset(user) })
            }
            if (state.users.none { it.role == Role.EMPLOYEE }) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.users_no_employees),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("users_empty"),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: ManagedUser, onActiveChange: (Boolean) -> Unit, onReset: () -> Unit) {
    InvisionCard(modifier = Modifier.fillMaxWidth().testTag("user_${user.identifier}")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.name, style = MaterialTheme.typography.titleSmall)
                    Text(user.identifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(user.role.labelRes()) + " · " + stringResource(if (user.active) R.string.users_status_active else R.string.users_status_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (user.role == Role.EMPLOYEE) {
                    Switch(
                        checked = user.active,
                        onCheckedChange = onActiveChange,
                        modifier = Modifier.testTag("user_active_${user.identifier}"),
                    )
                }
            }
            if (user.role == Role.EMPLOYEE) {
                OutlinedButton(onClick = onReset, modifier = Modifier.testTag("user_reset_${user.identifier}")) {
                    Text(stringResource(R.string.users_reset_password))
                }
            } else {
                Text(
                    stringResource(R.string.users_super_admin_protected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreateEmployeeDialog(
    form: EmployeeForm,
    onName: (String) -> Unit,
    onIdentifier: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    FormDialog(
        onDismissRequest = { if (!form.isSaving) onDismiss() },
        title = { Text(stringResource(R.string.users_add)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("users_create_form"),
            ) {
                Text(
                    stringResource(R.string.users_create_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InvisionTextField(
                    value = form.name,
                    onValueChange = onName,
                    label = stringResource(R.string.auth_field_name),
                    error = form.errors[SetupField.NAME]?.let { stringResource(it.messageRes()) },
                    capitalization = KeyboardCapitalization.Words,
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("employee_name"),
                )
                InvisionTextField(
                    value = form.identifier,
                    onValueChange = onIdentifier,
                    label = stringResource(R.string.auth_field_identifier),
                    supportingText = stringResource(R.string.auth_field_identifier_hint),
                    error = form.errors[SetupField.IDENTIFIER]?.let { stringResource(it.messageRes()) },
                    keyboardType = KeyboardType.Email,
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("employee_identifier"),
                )
                InvisionTextField(
                    value = form.password,
                    onValueChange = onPassword,
                    label = stringResource(R.string.auth_field_password),
                    supportingText = stringResource(R.string.auth_field_password_hint),
                    error = form.errors[SetupField.PASSWORD]?.let { stringResource(it.messageRes()) },
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("employee_password"),
                )
                InvisionTextField(
                    value = form.confirm,
                    onValueChange = onConfirm,
                    label = stringResource(R.string.auth_field_confirm),
                    error = form.errors[SetupField.CONFIRM]?.let { stringResource(it.messageRes()) },
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("employee_confirm"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !form.isSaving, modifier = Modifier.testTag("employee_create")) {
                Text(stringResource(R.string.users_create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !form.isSaving) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ResetPasswordDialog(
    form: ResetForm,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    FormDialog(
        onDismissRequest = { if (!form.isSaving) onDismiss() },
        title = { Text(stringResource(R.string.users_reset_title, form.user.name)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("users_reset_form"),
            ) {
                Text(
                    stringResource(R.string.users_reset_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InvisionTextField(
                    value = form.password,
                    onValueChange = onPassword,
                    label = stringResource(R.string.users_new_password),
                    supportingText = stringResource(R.string.auth_field_password_hint),
                    error = form.errors[SetupField.PASSWORD]?.let { stringResource(it.messageRes()) },
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("reset_password"),
                )
                InvisionTextField(
                    value = form.confirm,
                    onValueChange = onConfirm,
                    label = stringResource(R.string.auth_field_confirm),
                    error = form.errors[SetupField.CONFIRM]?.let { stringResource(it.messageRes()) },
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("reset_confirm"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !form.isSaving, modifier = Modifier.testTag("reset_submit")) {
                Text(stringResource(R.string.users_reset_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !form.isSaving) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Tests set this so a form is drawn in the page instead of a dialog window, because a text field inside a
 * dialog window never reports idle under Robolectric. Real use always shows a dialog.
 */
internal val LocalInlineForms = compositionLocalOf { false }

@Composable
private fun FormDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    if (LocalInlineForms.current) {
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Spacing.lg)) {
            title()
            text()
            Row { dismissButton(); confirmButton() }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    }
}
