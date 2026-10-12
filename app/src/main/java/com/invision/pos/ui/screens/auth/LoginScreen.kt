package com.invision.pos.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.LoginMessage
import com.invision.pos.viewmodel.LoginUiState
import com.invision.pos.viewmodel.LoginViewModel

/** The sign-in screen shown each time the app opens once the administrator exists. */
@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var resetting by rememberSaveable { mutableStateOf(false) }
    if (resetting) {
        PasswordResetScreen(onBack = { resetting = false })
    } else {
        LoginContent(state, viewModel::onIdentifierChange, viewModel::onPasswordChange, viewModel::signIn, onForgotPassword = { resetting = true })
    }
}

@Composable
fun LoginContent(
    state: LoginUiState,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: (() -> Unit)? = null,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AuthLayout {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("login")) {
            Text(stringResource(R.string.auth_login_welcome), style = MaterialTheme.typography.headlineSmall)
            InvisionTextField(
                value = state.identifier,
                onValueChange = onIdentifierChange,
                label = stringResource(R.string.auth_field_identifier),
                error = if (state.identifierMissing) stringResource(R.string.auth_error_login_identifier_required) else null,
                keyboardType = KeyboardType.Email,
                enabled = !state.isSigningIn,
                modifier = Modifier.fillMaxWidth().testTag("login_identifier"),
            )
            InvisionTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_field_password),
                error = if (state.passwordMissing) stringResource(R.string.auth_error_login_password_required) else null,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onSignIn() }),
                enabled = !state.isSigningIn,
                modifier = Modifier.fillMaxWidth().testTag("login_password"),
            )
            val message = state.message
            if (message != null) {
                Text(
                    text = when (message) {
                        LoginMessage.Rejected -> stringResource(R.string.auth_login_rejected)
                        LoginMessage.Deactivated -> stringResource(R.string.auth_login_deactivated)
                        is LoginMessage.LockedOut -> pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds)
                        LoginMessage.Unavailable -> stringResource(R.string.auth_login_unavailable)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("login_error"),
                )
            }
            Button(
                onClick = onSignIn,
                enabled = !state.isSigningIn,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("login_submit"),
            ) {
                Text(stringResource(if (state.isSigningIn) R.string.auth_login_working else R.string.auth_login_action))
            }
            if (onForgotPassword != null) {
                TextButton(onClick = onForgotPassword, enabled = !state.isSigningIn, modifier = Modifier.fillMaxWidth().testTag("login_forgot")) {
                    Text(stringResource(R.string.auth_login_forgot))
                }
            }
        }
    }
}
