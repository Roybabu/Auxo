// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.auth

import com.auxo.pos.ui.components.GlassButton
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.PersistableBundle
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.RecoveryCodes
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.lg)) {
                // A fixed-width letter is about 0.62 of its size wide, so this is the biggest size that fits the whole code.
                val fitted = with(LocalDensity.current) { (maxWidth / (code.length.coerceAtLeast(1) * 0.62f)).toSp() }
                Text(
                    text = code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = fitted.value.coerceIn(MIN_CODE_SIZE, MAX_CODE_SIZE).sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().testTag("recovery_code"),
                )
            }
            CopyCodeButton(code)
        }
    }
}

private const val CLIP_LABEL = "Auxo recovery code"
private const val CLIP_LIFETIME_MS = 60_000L

/**
 * Copies the code for one minute only. The copy is marked sensitive (Android 13 and newer then hide it in the "copied" preview)
 * and is removed from the clipboard when the minute is up or the person leaves the screen, so it does not stay around for other apps.
 */
@Composable
private fun CopyCodeButton(code: String) {
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }
    if (copied) {
        LaunchedEffect(Unit) {
            delay(CLIP_LIFETIME_MS)
            context.clearOwnClip()
            copied = false
        }
        // Leaving the screen clears the copy too, but turning the phone sideways does not.
        DisposableEffect(Unit) {
            onDispose { if (context.findActivity()?.isChangingConfigurations != true) context.clearOwnClip() }
        }
    }
    TextButton(
        onClick = { if (context.copySensitive(code)) copied = true },
        modifier = Modifier.testTag("recovery_copy"),
    ) {
        Text(stringResource(if (copied) R.string.recovery_copied else R.string.recovery_copy))
    }
    if (copied) {
        Text(
            stringResource(R.string.recovery_copy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.md).testTag("recovery_copy_note"),
        )
    }
}

private fun Context.copySensitive(code: String): Boolean {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
    val clip = ClipData.newPlainText(CLIP_LABEL, code)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    return try {
        clipboard.setPrimaryClip(clip)
        true
    } catch (_: RuntimeException) {
        false
    }
}

/** Empties the clipboard, but only when it still holds the code copied here, so something the person copied since is left alone. */
private fun Context.clearOwnClip() {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    try {
        if (clipboard.primaryClipDescription?.label != CLIP_LABEL) return
        if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    } catch (_: RuntimeException) {
        // Best effort: the system may refuse while the app is in the background.
    }
}

private const val MAX_CODE_SIZE = 24f
private const val MIN_CODE_SIZE = 10f

/**
 * Shows what is typed in the groups the code is written in (`ABCDE-FGHJK-...`) while the field itself holds only the letters,
 * so the dashes appear as the person types or pastes and never have to be typed.
 */
private object RecoveryCodeDashes : VisualTransformation {
    private const val STEP = RecoveryCodes.GROUP + 1

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = raw.chunked(RecoveryCodes.GROUP).joinToString("-")
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + (offset - 1).coerceAtLeast(0) / RecoveryCodes.GROUP
            override fun transformedToOriginal(offset: Int): Int = (offset - offset / STEP).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/** Keeps only letters and digits, in capitals, up to the length of a code: dashes and spaces from typing or pasting are dropped. */
private fun cleanTypedCode(text: String): String = RecoveryCodes.normalize(text).take(RecoveryCodes.LENGTH)

@Composable
private fun TypeCodeField(value: String, onChange: (String) -> Unit, onDone: () -> Unit, error: String?, enabled: Boolean) {
    AuxoTextField(
        value = value,
        onValueChange = { onChange(cleanTypedCode(it)) },
        label = stringResource(R.string.recovery_field_code),
        error = error,
        // The password kind of keyboard keeps the code out of the keyboard's suggestions and its memory.
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.Characters,
        visualTransformation = RecoveryCodeDashes,
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
                GlassButton(onClick = onStart, enabled = !state.busy, modifier = Modifier.testTag("recovery_start")) {
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
                GlassButton(onClick = onWrittenDown, modifier = Modifier.testTag("recovery_written")) {
                    Text(stringResource(R.string.recovery_written))
                }
            }
            RecoveryStep.CONFIRM -> {
                KeepOffScreenshots()
                Text(stringResource(R.string.recovery_confirm_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.recovery_confirm_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TypeCodeField(state.typed, onTypedChange, onConfirm, message, !state.busy)
                GlassButton(onClick = onConfirm, enabled = !state.busy, modifier = Modifier.testTag("recovery_confirm")) {
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
                    GlassButton(onClick = onDone, modifier = Modifier.testTag("recovery_done")) {
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
    AuthLayout(backdropImage = R.drawable.auth_signup_bg, logoWidth = 220.dp, glass = true, centerGlass = true) {
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
    AuthLayout(backdropImage = R.drawable.auth_signup_bg, logoWidth = 220.dp, glass = true) {
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
                    GlassButton(onClick = onSubmit, enabled = !state.busy, modifier = Modifier.testTag("reset_submit")) {
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
                    GlassButton(onClick = onWrittenDown, modifier = Modifier.testTag("recovery_written")) {
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
                    GlassButton(onClick = onConfirmNewCode, modifier = Modifier.testTag("recovery_confirm")) {
                        Text(stringResource(R.string.recovery_confirm_action))
                    }
                    TextButton(onClick = onShowAgain, modifier = Modifier.fillMaxWidth().testTag("recovery_show_again")) {
                        Text(stringResource(R.string.recovery_show_again))
                    }
                }
                ResetStep.DONE -> {
                    Text(stringResource(R.string.reset_done_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("reset_done_title"))
                    Text(stringResource(R.string.reset_done_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GlassButton(onClick = onBack, modifier = Modifier.testTag("reset_done")) {
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
        onValueChange = { onChange(cleanTypedCode(it)) },
        label = stringResource(R.string.recovery_field_code),
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.Characters,
        visualTransformation = RecoveryCodeDashes,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("reset_code"),
    )
}
