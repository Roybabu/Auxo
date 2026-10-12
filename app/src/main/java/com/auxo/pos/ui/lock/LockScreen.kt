package com.auxo.pos.ui.lock

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.auxo.pos.core.auth.LockInfo
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.screens.auth.AuthLayout
import com.auxo.pos.ui.screens.auth.PasswordToggle
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.LockMessage
import com.auxo.pos.viewmodel.LockUiState
import com.auxo.pos.viewmodel.LockViewModel

/**
 * Covers the whole app while it is locked. The app underneath stays exactly as it was, so an unfinished bill
 * is still there after unlocking, but it cannot be seen, touched or typed into from here.
 */
@Composable
fun LockOverlay(content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            // Nothing that reaches this layer is passed on to the app below it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .testTag("lock_overlay"),
    ) { content() }
}

@Composable
fun LockScreen(viewModel: LockViewModel = viewModel(factory = LockViewModel.Factory)) {
    val info by viewModel.lock.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fingerprint = LocalFingerprint.current
    val context = LocalContext.current
    val title = stringResource(R.string.lock_fingerprint_title)
    val cancel = stringResource(R.string.lock_fingerprint_cancel)
    val askFingerprint = { fingerprint?.authenticate(title, cancel) { viewModel.fingerprintAccepted() } }
    // Offer the fingerprint as soon as the lock appears; the person can still cancel and type instead.
    LaunchedEffect(info.fingerprintAvailable, context) {
        if (info.fingerprintAvailable && fingerprint?.isAvailable() == true) askFingerprint()
    }
    LockContent(
        info = info,
        state = state,
        fingerprintOffered = info.fingerprintAvailable && fingerprint?.isAvailable() == true,
        onPinChange = viewModel::onPinChange,
        onUnlockWithPin = viewModel::unlockWithPin,
        onPasswordChange = viewModel::onPasswordChange,
        onUnlockWithPassword = viewModel::unlockWithPassword,
        onUseFingerprint = { askFingerprint() },
        onSignOut = viewModel::signOut,
    )
}

@Composable
fun LockContent(
    info: LockInfo,
    state: LockUiState,
    fingerprintOffered: Boolean,
    onPinChange: (String) -> Unit,
    onUnlockWithPin: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onUnlockWithPassword: () -> Unit,
    onUseFingerprint: () -> Unit,
    onSignOut: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AuthLayout {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("lock_screen")) {
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.lock_body, info.userName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("lock_user"),
            )

            if (info.quickUnlockOff) {
                Text(
                    stringResource(R.string.lock_quick_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("lock_quick_off"),
                )
            }

            if (info.pinAvailable) {
                AuxoTextField(
                    value = state.pin,
                    onValueChange = onPinChange,
                    label = stringResource(R.string.lock_field_pin),
                    keyboardType = KeyboardType.NumberPassword,
                    visualTransformation = PasswordVisualTransformation(),
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { onUnlockWithPin() }),
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("lock_pin"),
                )
                Button(
                    onClick = onUnlockWithPin,
                    enabled = !state.busy && state.pin.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("lock_pin_unlock"),
                ) { Text(stringResource(R.string.lock_unlock)) }
            }
            if (fingerprintOffered) {
                OutlinedButton(onClick = onUseFingerprint, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("lock_fingerprint")) {
                    Text(stringResource(R.string.lock_use_fingerprint))
                }
            }

            when (val message = state.message) {
                null -> Unit
                is LockMessage.WrongPin -> LockError(pluralStringResource(R.plurals.lock_wrong_pin, message.triesLeft, message.triesLeft))
                LockMessage.PinOff -> LockError(stringResource(R.string.lock_pin_off))
                LockMessage.WrongPassword -> LockError(stringResource(R.string.lock_wrong_password))
                is LockMessage.LockedOut -> LockError(pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds))
                LockMessage.Unavailable -> LockError(stringResource(R.string.lock_unavailable))
            }

            if (info.pinAvailable || fingerprintOffered) {
                HorizontalDivider()
                Text(stringResource(R.string.lock_or_password), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AuxoTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_field_password),
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onUnlockWithPassword() }),
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("lock_password"),
            )
            Button(
                onClick = onUnlockWithPassword,
                enabled = !state.busy && state.password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("lock_password_unlock"),
            ) { Text(stringResource(R.string.lock_unlock)) }

            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs).testTag("lock_sign_out")) {
                Text(stringResource(R.string.lock_sign_out))
            }
        }
    }
}

@Composable
private fun LockError(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lock_error"))
}
