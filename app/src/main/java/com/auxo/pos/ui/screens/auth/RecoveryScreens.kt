package com.auxo.pos.ui.screens.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.SetupField
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.PasswordResetUiState
import com.auxo.pos.viewmodel.PasswordResetViewModel
import com.auxo.pos.viewmodel.RecoveryCodeUiState
import com.auxo.pos.viewmodel.RecoveryCodeViewModel
import com.auxo.pos.viewmodel.RecoveryMessage
import com.auxo.pos.viewmodel.RecoveryStep
import com.auxo.pos.viewmodel.ResetMessage
import com.auxo.pos.viewmodel.ResetStep

/** Keeps the window out of screenshots and the recent-apps picture while a recovery code is on screen. */
@Composable
internal fun KeepOffScreenshots() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The code, large and in a fixed-width face, so it is easy to copy by hand. It always stays on one line: the letters shrink
 * to fit the card rather than wrapping, because a code split across two lines is easy to copy wrongly.
 */
@Composable
internal fun RecoveryCodeCard(code: String, modifier: Modifier = Modifier) {
    AuxoCard(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.lg)) {
            // A fixed-width letter is about 0.62 of its size wide, so this is the biggest size that fits the whole code.
            val fitted = with(LocalDensity.current) { (maxWidth / (code.length.coerceAtLeast(1) * 0.62f)).toSp() }
            Text(
                text = code,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = fitted.coerceIn(MIN_CODE_SIZE, MAX_CODE_SIZE),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().testTag("recovery_code"),
            )
        }
    }
}

private val MAX_CODE_SIZE = 24.sp
private val MIN_CODE_SIZE = 10.sp

@Composable
private fun TypeCodeField(value: String, onChange: (String) -> Unit, onDone: () -> Unit, error: String?, enabled: Boolean) {
    AuxoTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(R.string.recovery_field_code),
        error = error,
        capitalization = KeyboardCapitalization.Characters,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("recovery_typed"),
    )
}

@Composable
private fun rememberRecoveryMessage(message: RecoveryMessage?): String? = when (message) {
    null -> null
    RecoveryMessage.WrongPassword -> stringResource(R.string.recovery_wrong_password)
    is RecoveryMessage.LockedOut -> pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds)
    RecoveryMessage.Mismatch -> stringResource(R.string.recovery_mismatch)
    RecoveryMessage.Failed -> stringResource(R.string.recovery_failed)
}

/**
 * Creating the code. [onSkip] is given only when the person may leave it for later (an install made before
 * recovery codes); [onDone] is given where there is somewhere to go back to after it is saved.
 */
@Composable
fun RecoveryCodeBody(
    state: RecoveryCodeUiState,
    onPasswordChange: (String) -> Unit,
    onStart: () -> Unit,
    onWrittenDown: () -> Unit,
    onTypedChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onShowAgain: () -> Unit,
    onSkip: (() -> Unit)?,
    onDone: (() -> Unit)?,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val message = rememberRecoveryMessage(state.message)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("recovery_flow")) {
        when (state.step) {
            RecoveryStep.INTRO -> {
                Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.recovery_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.recovery_intro_warning), style = MaterialTheme.typography.bodyMedium)
                if (state.needsPassword) {
                    AuxoTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = stringResource(R.string.recovery_field_password),
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { onStart() }),
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("recovery_password"),
                    )
                }
                if (message != null) ErrorLine(message, "recovery_error")
                Button(onClick = onStart, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("recovery_start")) {
                    Text(stringResource(R.string.recovery_create))
                }
                if (onSkip != null) {
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().testTag("recovery_skip")) {
                        Text(stringResource(R.string.recovery_not_now))
                    }
                }
            }
            RecoveryStep.SHOW -> {
                KeepOffScreenshots()
                Text(stringResource(R.string.recovery_show_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.recovery_show_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RecoveryCodeCard(state.code)
                Text(stringResource(R.string.recovery_show_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onWrittenDown, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("recovery_written")) {
                    Text(stringResource(R.string.recovery_written))
                }
            }
            RecoveryStep.CONFIRM -> {
                KeepOffScreenshots()
                Text(stringResource(R.string.recovery_confirm_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.recovery_confirm_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TypeCodeField(state.typed, onTypedChange, onConfirm, message, !state.busy)
                Button(onClick = onConfirm, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("recovery_confirm")) {
                    Text(stringResource(R.string.recovery_confirm_action))
                }
                TextButton(onClick = onShowAgain, modifier = Modifier.fillMaxWidth().testTag("recovery_show_again")) {
                    Text(stringResource(R.string.recovery_show_again))
                }
            }
            RecoveryStep.SAVED -> {
                Text(stringResource(R.string.recovery_saved_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("recovery_saved"))
                Text(stringResource(R.string.recovery_saved_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onDone != null) {
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("recovery_done")) {
                        Text(stringResource(R.string.recovery_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorLine(text: String, tag: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(tag))
}

/** Full-screen prompt the app shows over everything else until the code is made (or, for older installs, put off). */
@Composable
fun RecoveryCodeGate(canSkip: Boolean, viewModel: RecoveryCodeViewModel = viewModel(factory = RecoveryCodeViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AuthLayout {
        RecoveryCodeBody(
            state = state,
            onPasswordChange = viewModel::onPasswordChange,
            onStart = viewModel::start,
            onWrittenDown = viewModel::onWrittenDown,
            onTypedChange = viewModel::onTypedChange,
            onConfirm = viewModel::confirm,
            onShowAgain = viewModel::showCodeAgain,
            onSkip = if (canSkip) viewModel::skip else null,
            onDone = null,
        )
    }
}

/** The same flow from the More tab, for making a new code later. */
@Composable
fun RecoveryCodeScreen(onBack: () -> Unit, viewModel: RecoveryCodeViewModel = viewModel(factory = RecoveryCodeViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.recovery_screen_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            horizontalAlignment = Alignment.Start,
        ) {
            RecoveryCodeBody(
                state = state,
                onPasswordChange = viewModel::onPasswordChange,
                onStart = viewModel::start,
                onWrittenDown = viewModel::onWrittenDown,
                onTypedChange = viewModel::onTypedChange,
                onConfirm = viewModel::confirm,
                onShowAgain = viewModel::showCodeAgain,
                onSkip = null,
                onDone = onBack,
            )
        }
    }
}

/** "Forgot password?" from the sign-in screen. */
@Composable
fun PasswordResetScreen(onBack: () -> Unit, viewModel: PasswordResetViewModel = viewModel(factory = PasswordResetViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PasswordResetContent(
        state = state,
        onCodeChange = viewModel::onCodeChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmChange = viewModel::onConfirmChange,
        onSubmit = viewModel::submit,
        onWrittenDown = viewModel::onWrittenDown,
        onTypedChange = viewModel::onTypedChange,
        onConfirmNewCode = viewModel::confirmNewCode,
        onShowAgain = viewModel::showCodeAgain,
        onBack = onBack,
    )
}

@Composable
fun PasswordResetContent(
    state: PasswordResetUiState,
    onCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onWrittenDown: () -> Unit,
    onTypedChange: (String) -> Unit,
    onConfirmNewCode: () -> Unit,
    onShowAgain: () -> Unit,
    onBack: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AuthLayout {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("password_reset")) {
            when (state.step) {
                ResetStep.FORM -> {
                    Text(stringResource(R.string.reset_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.reset_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TypeCodeFieldWithTag(state.code, onCodeChange, !state.busy)
                    AuxoTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = stringResource(R.string.reset_field_new_password),
                        supportingText = stringResource(R.string.auth_field_password_hint),
                        error = state.errors[SetupField.PASSWORD]?.let { stringResource(it.messageRes()) },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("reset_password"),
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
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("reset_confirm"),
                    )
                    when (val message = state.message) {
                        null -> Unit
                        ResetMessage.WrongCode -> ErrorLine(stringResource(R.string.reset_wrong_code), "reset_error")
                        is ResetMessage.LockedOut -> ErrorLine(pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds), "reset_error")
                        ResetMessage.NoCode -> ErrorLine(stringResource(R.string.reset_no_code), "reset_error")
                        ResetMessage.Unavailable -> ErrorLine(stringResource(R.string.auth_login_unavailable), "reset_error")
                        ResetMessage.Mismatch -> Unit
                    }
                    Button(onClick = onSubmit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("reset_submit")) {
                        Text(stringResource(if (state.busy) R.string.reset_working else R.string.reset_action))
                    }
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("reset_back")) {
                        Text(stringResource(R.string.reset_back))
                    }
                }
                ResetStep.SHOW -> {
                    KeepOffScreenshots()
                    Text(stringResource(R.string.reset_new_code_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.reset_new_code_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RecoveryCodeCard(state.newCode)
                    Text(stringResource(R.string.recovery_show_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onWrittenDown, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("recovery_written")) {
                        Text(stringResource(R.string.recovery_written))
                    }
                }
                ResetStep.CONFIRM -> {
                    KeepOffScreenshots()
                    Text(stringResource(R.string.recovery_confirm_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.recovery_confirm_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TypeCodeField(
                        state.typed,
                        onTypedChange,
                        onConfirmNewCode,
                        if (state.message == ResetMessage.Mismatch) stringResource(R.string.recovery_mismatch) else null,
                        true,
                    )
                    Button(onClick = onConfirmNewCode, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("recovery_confirm")) {
                        Text(stringResource(R.string.recovery_confirm_action))
                    }
                    TextButton(onClick = onShowAgain, modifier = Modifier.fillMaxWidth().testTag("recovery_show_again")) {
                        Text(stringResource(R.string.recovery_show_again))
                    }
                }
                ResetStep.DONE -> {
                    Text(stringResource(R.string.reset_done_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("reset_done_title"))
                    Text(stringResource(R.string.reset_done_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("reset_done")) {
                        Text(stringResource(R.string.reset_go_to_sign_in))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeCodeFieldWithTag(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    AuxoTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(R.string.recovery_field_code),
        capitalization = KeyboardCapitalization.Characters,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("reset_code"),
    )
}
