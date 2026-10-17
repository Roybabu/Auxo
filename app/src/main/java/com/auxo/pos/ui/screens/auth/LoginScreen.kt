package com.auxo.pos.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.auxo.pos.ui.components.AuthPillField
import com.auxo.pos.ui.components.Illustration
import com.auxo.pos.ui.components.LanguageChip
import com.auxo.pos.ui.components.OrDivider
import com.auxo.pos.ui.theme.AuthDotIdle
import com.auxo.pos.ui.theme.AuthInkMuted
import com.auxo.pos.ui.theme.AuthMint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.LoginMessage
import com.auxo.pos.viewmodel.LoginUiState
import com.auxo.pos.viewmodel.LoginViewModel

/** The sign-in screen: the first screen on every launch. [hasBusiness] is false until a business has been set up on this device. */
@Composable
fun LoginScreen(
    hasBusiness: Boolean = true,
    onSetUpBusiness: () -> Unit = {},
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var resetting by rememberSaveable { mutableStateOf(false) }
    if (resetting && hasBusiness) {
        PasswordResetScreen(onBack = { resetting = false })
    } else {
        LoginContent(
            state, viewModel::onIdentifierChange, viewModel::onPasswordChange, viewModel::signIn,
            // Nothing to recover when this device has no business yet.
            onForgotPassword = if (hasBusiness) ({ resetting = true }) else null,
            onSetUpBusiness = onSetUpBusiness,
            topEnd = { LanguageChip() },
        )
    }
}

@Composable
fun LoginContent(
    state: LoginUiState,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: (() -> Unit)? = null,
    onSetUpBusiness: (() -> Unit)? = null,
    topEnd: (@Composable () -> Unit)? = null,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AuthLayout(
        backdropImage = R.drawable.auth_login_bg,
        logoWidth = 300.dp,
        footer = { NewToAuxo() },
        topEnd = topEnd,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("login")) {
            Text(
                stringResource(R.string.auth_login_welcome),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = AuthInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.auth_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            AuthPillField(
                value = state.identifier,
                onValueChange = onIdentifierChange,
                placeholder = stringResource(R.string.auth_field_identifier),
                icon = R.drawable.ic_mail,
                error = if (state.identifierMissing) stringResource(R.string.auth_error_login_identifier_required) else null,
                keyboardType = KeyboardType.Email,
                enabled = !state.isSigningIn,
                modifier = Modifier.testTag("login_identifier"),
            )
            AuthPillField(
                value = state.password,
                onValueChange = onPasswordChange,
                placeholder = stringResource(R.string.auth_field_password),
                icon = R.drawable.ic_lock,
                error = if (state.passwordMissing) stringResource(R.string.auth_error_login_password_required) else null,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onSignIn() }),
                enabled = !state.isSigningIn,
                modifier = Modifier.testTag("login_password"),
            )
            if (onForgotPassword != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onForgotPassword, enabled = !state.isSigningIn, modifier = Modifier.testTag("login_forgot")) {
                        Text(stringResource(R.string.auth_login_forgot), color = AuthEmerald, fontWeight = FontWeight.Medium)
                    }
                }
            }
            val message = state.message
            if (message != null) {
                Text(
                    text = when (message) {
                        LoginMessage.Rejected -> stringResource(R.string.auth_login_rejected)
                        LoginMessage.Deactivated -> stringResource(R.string.auth_login_deactivated)
                        is LoginMessage.LockedOut -> pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds)
                        LoginMessage.NoAccount -> stringResource(R.string.auth_login_no_account)
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
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuthEmerald, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("login_submit"),
            ) {
                Text(stringResource(if (state.isSigningIn) R.string.auth_login_working else R.string.auth_login_action), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            // Always present, whether or not a business exists yet: it is how a new business is started.
            if (onSetUpBusiness != null) {
                OrDivider(stringResource(R.string.auth_login_or))
                OutlinedButton(
                    onClick = onSetUpBusiness,
                    enabled = !state.isSigningIn,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("login_setup_business"),
                ) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(AuthMint), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_store), contentDescription = null, tint = AuthEmerald, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.auth_login_setup_business), color = AuthEmerald, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, tint = AuthEmerald, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** The strip under the card: the shop, a thin line, and a line inviting a new business to start. */
@Composable
private fun NewToAuxo() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Illustration(R.drawable.auth_storefront, Modifier.weight(1f).height(120.dp))
        VerticalDivider(modifier = Modifier.padding(horizontal = Spacing.md).height(56.dp), color = AuthDotIdle)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.auth_login_new_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AuthInk)
            Text(stringResource(R.string.auth_login_new_body), style = MaterialTheme.typography.bodyMedium, color = AuthInkMuted)
        }
    }
}
