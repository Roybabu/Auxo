package com.auxo.pos.ui.screens.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.SetupError
import com.auxo.pos.core.auth.SetupField
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.Illustration
import com.auxo.pos.ui.components.StrengthMeter
import com.auxo.pos.ui.components.SuggestionRow
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.AdminSetupUiState
import com.auxo.pos.viewmodel.AdminSetupViewModel

@StringRes
internal fun SetupError.messageRes(): Int = when (this) {
    SetupError.BUSINESS_REQUIRED -> R.string.auth_error_business_required
    SetupError.BUSINESS_TOO_LONG -> R.string.auth_error_business_too_long
    SetupError.NAME_REQUIRED -> R.string.auth_error_name_required
    SetupError.NAME_TOO_LONG -> R.string.auth_error_name_too_long
    SetupError.IDENTIFIER_REQUIRED -> R.string.auth_error_identifier_required
    SetupError.IDENTIFIER_INVALID -> R.string.auth_error_identifier_invalid
    SetupError.IDENTIFIER_TAKEN -> R.string.auth_error_identifier_taken
    SetupError.PASSWORD_REQUIRED -> R.string.auth_error_password_required
    SetupError.PASSWORD_TOO_SHORT -> R.string.auth_error_password_short
    SetupError.PASSWORD_TOO_LONG -> R.string.auth_error_password_long
    SetupError.PASSWORD_NEEDS_LETTER_AND_DIGIT -> R.string.auth_error_password_mix
    SetupError.PASSWORD_TOO_COMMON -> R.string.auth_error_password_common
    SetupError.PASSWORD_CONTAINS_IDENTIFIER -> R.string.auth_error_password_identifier
    SetupError.CONFIRM_REQUIRED -> R.string.auth_error_confirm_required
    SetupError.CONFIRM_MISMATCH -> R.string.auth_error_confirm_mismatch
}

/** Shown once, on the first launch, until the administrator exists. */
@Composable
fun AdminSetupScreen(onBack: (() -> Unit)? = null, viewModel: AdminSetupViewModel = viewModel(factory = AdminSetupViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdminSetupContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onIdentifierChange = viewModel::onIdentifierChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmChange = viewModel::onConfirmChange,
        onSubmit = viewModel::submit,
        onBack = onBack,
        onBusinessChange = viewModel::onBusinessChange,
        onSuggestionPick = viewModel::useSuggestion,
    )
}

@Composable
fun AdminSetupContent(
    state: AdminSetupUiState,
    onNameChange: (String) -> Unit,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: (() -> Unit)? = null,
    onBusinessChange: (String) -> Unit = {},
    onSuggestionPick: (String) -> Unit = {},
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AuthLayout {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("admin_setup")) {
            Illustration(R.drawable.auth_storefront, modifier = Modifier.fillMaxWidth().height(110.dp))
            Text(stringResource(R.string.auth_setup_title), style = MaterialTheme.typography.headlineMedium, color = AuthInk)
            Text(
                stringResource(R.string.auth_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AuxoTextField(
                value = state.businessName,
                onValueChange = onBusinessChange,
                label = stringResource(R.string.auth_field_business),
                error = state.errors[SetupField.BUSINESS]?.let { stringResource(it.messageRes()) },
                capitalization = KeyboardCapitalization.Words,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_business"),
            )
            AuxoTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.auth_field_name),
                error = state.errors[SetupField.NAME]?.let { stringResource(it.messageRes()) },
                capitalization = KeyboardCapitalization.Words,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_name"),
            )
            AuxoTextField(
                value = state.identifier,
                onValueChange = onIdentifierChange,
                label = stringResource(R.string.auth_field_identifier),
                supportingText = stringResource(R.string.auth_field_identifier_hint),
                error = state.errors[SetupField.IDENTIFIER]?.let { stringResource(it.messageRes()) },
                keyboardType = KeyboardType.Email,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_identifier"),
            )
            SuggestionRow(stringResource(R.string.auth_suggestions), state.suggestions, onSuggestionPick)
            AuxoTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_field_password),
                supportingText = stringResource(R.string.auth_field_password_hint),
                error = state.errors[SetupField.PASSWORD]?.let { stringResource(it.messageRes()) },
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_password"),
            )
            StrengthMeter(
                filled = state.strength,
                label = stringResource(
                    when (state.strength) {
                        1 -> R.string.auth_strength_weak
                        2 -> R.string.auth_strength_fair
                        3 -> R.string.auth_strength_good
                        else -> R.string.auth_strength_strong
                    },
                ),
            )
            AuxoTextField(
                value = state.confirm,
                onValueChange = onConfirmChange,
                label = stringResource(R.string.auth_field_confirm),
                error = state.errors[SetupField.CONFIRM]?.let { stringResource(it.messageRes()) },
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_confirm"),
            )
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.auth_setup_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("setup_save_failed"),
                )
            }
            Button(
                onClick = onSubmit,
                enabled = !state.isSubmitting,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuthEmerald, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("setup_submit"),
            ) {
                Text(stringResource(if (state.isSubmitting) R.string.auth_setup_working else R.string.auth_setup_action))
            }
            Text(
                stringResource(R.string.auth_setup_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            if (onBack != null) {
                TextButton(onClick = onBack, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth().testTag("setup_back")) {
                    Text(stringResource(R.string.auth_back_to_login))
                }
            }
        }
    }
}

@Composable
internal fun PasswordToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.testTag("password_toggle")) {
        AuxoIcon(
            if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
            stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password),
            size = AuxoIconSize.Mini,
        )
    }
}
