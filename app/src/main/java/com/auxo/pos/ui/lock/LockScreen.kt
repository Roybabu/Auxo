// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.lock

import com.auxo.pos.ui.components.GlassButton
import com.auxo.pos.ui.components.SceneFingerprintButton
import com.auxo.pos.ui.components.BotanicalScene
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
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
import com.auxo.pos.ui.components.AuthPillField
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoIconStyle
import com.auxo.pos.ui.components.OrDivider
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.screens.auth.PasswordToggle
import com.auxo.pos.ui.screens.auth.SlideToSignIn
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

    BotanicalScene(contentTag = "lock_screen") {
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = AuthInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.lock_body, info.userName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs).testTag("lock_user"),
        )

        if (info.quickUnlockOff) {
            Text(
                stringResource(R.string.lock_quick_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("lock_quick_off"),
            )
        }

        if (fingerprintOffered) {
            SceneFingerprintButton(stringResource(R.string.lock_use_fingerprint), onClick = onUseFingerprint, modifier = Modifier.testTag("lock_fingerprint"))
        }

        val message = state.message
        if (info.pinAvailable) {
            AuthPillField(
                value = state.pin,
                onValueChange = onPinChange,
                placeholder = stringResource(R.string.lock_field_pin),
                icon = R.drawable.ic_lock,
                highlightError = message is LockMessage.WrongPin,
                keyboardType = KeyboardType.NumberPassword,
                visualTransformation = PasswordVisualTransformation(),
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onUnlockWithPin() }),
                enabled = !state.busy,
                fieldHeight = LOCK_FIELD_HEIGHT,
                modifier = Modifier.testTag("lock_pin"),
            )
            UnlockButton(
                enabled = !state.busy && state.pin.isNotEmpty(),
                busy = state.busy,
                onClick = onUnlockWithPin,
                modifier = Modifier.testTag("lock_pin_unlock"),
            )
        }

        when (message) {
            null -> Unit
            is LockMessage.WrongPin -> LockError(pluralStringResource(R.plurals.lock_wrong_pin, message.triesLeft, message.triesLeft))
            LockMessage.PinOff -> LockError(stringResource(R.string.lock_pin_off))
            LockMessage.WrongPassword -> LockError(stringResource(R.string.lock_wrong_password))
            is LockMessage.LockedOut -> LockError(pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds))
            LockMessage.Unavailable -> LockError(stringResource(R.string.lock_unavailable))
        }

        if (info.pinAvailable || fingerprintOffered) {
            OrDivider(stringResource(R.string.lock_or_password))
        }
        AuthPillField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = stringResource(R.string.auth_field_password),
            icon = R.drawable.ic_lock,
            highlightError = message == LockMessage.WrongPassword,
            keyboardType = KeyboardType.Password,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onUnlockWithPassword() }),
            enabled = !state.busy,
            fieldHeight = LOCK_FIELD_HEIGHT,
            modifier = Modifier.testTag("lock_password"),
        )
        // The same slider as the fingerprint button above and the sign-in page: slide to the end to unlock.
        SlideToSignIn(
            isSigningIn = state.busy,
            onSignIn = onUnlockWithPassword,
            label = stringResource(R.string.lock_slide_unlock),
            workingLabel = stringResource(R.string.lock_unlocking),
            enabled = state.password.isNotEmpty(),
            modifier = Modifier.testTag("lock_password_unlock"),
        )

        TextButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().testTag("lock_sign_out"),
        ) {
            AuxoIcon(R.drawable.ic_logout, contentDescription = null, size = AuxoIconSize.Mini)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.lock_sign_out), color = AuthEmerald, fontWeight = FontWeight.Medium)
        }
    }
}

private val LOCK_FIELD_HEIGHT = 51.dp

private val LOCK_BUTTON_HEIGHT = 52.dp

/** "Unlock": a liquid-glass pill with a circled arrow (a spinner while working). */
@Composable
private fun UnlockButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val live = enabled || busy
    GlassButton(onClick = onClick, enabled = enabled, lit = live, modifier = modifier) {
        Text(
            stringResource(R.string.lock_unlock),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(start = 40.dp),
        )
        Box(
            modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = if (live) 0.24f else 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                AuxoIcon(
                    R.drawable.ic_arrow_forward,
                    contentDescription = null,
                    size = AuxoIconSize.Mini,
                    style = AuxoIconStyle.Flat,
                    accent = if (live) Color.White else AuthEmerald,
                )
            }
        }
    }
}

@Composable
private fun LockError(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lock_error"))
}
