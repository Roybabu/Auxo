// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.auxo.pos.R
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.LockInfo
import com.auxo.pos.core.auth.RecoveryPrompt
import com.auxo.pos.ui.lock.LockOverlay
import com.auxo.pos.ui.lock.LockScreen
import com.auxo.pos.ui.screens.auth.AdminSetupScreen
import com.auxo.pos.ui.screens.auth.AuthLayout
import com.auxo.pos.ui.screens.auth.BusinessExistsScreen
import com.auxo.pos.ui.screens.auth.CategorySetupScreen
import com.auxo.pos.ui.screens.auth.LoginScreen
import com.auxo.pos.ui.screens.auth.RecoveryCodeGate
import com.auxo.pos.ui.screens.auth.ResumeScreen
import com.auxo.pos.ui.screens.onboarding.OnboardingScreen
import com.auxo.pos.ui.theme.Spacing

/**
 * Decides what the app opens to: the introduction ([onboarding]) once on the very first launch, then Login on every launch (with Set up your business always on it), or the fingerprint unlock for a saved session,
 * and the app itself once someone has signed in. A Super Admin without a recovery code is shown the code
 * screen first: required after setup, offered (and skippable) for an install that predates recovery codes.
 * When the app is locked the same person's lock screen covers the app, which stays as it was underneath.
 */
@Composable
fun AuthGate(
    state: AuthState,
    setup: @Composable (onBack: () -> Unit) -> Unit = { AdminSetupScreen(onBack = it) },
    login: @Composable (onSetUpBusiness: () -> Unit) -> Unit = { LoginScreen(hasBusiness = state == AuthState.SignedOut, onSetUpBusiness = it) },
    businessExists: @Composable (onBack: () -> Unit) -> Unit = { BusinessExistsScreen(onBack = it) },
    recoveryPrompt: RecoveryPrompt = RecoveryPrompt.None,
    recovery: @Composable (canSkip: Boolean) -> Unit = { canSkip -> RecoveryCodeGate(canSkip) },
    lock: LockInfo = LockInfo(),
    lockScreen: @Composable () -> Unit = { LockScreen() },
    categoryPrompt: Boolean = false,
    categorySetup: @Composable () -> Unit = { CategorySetupScreen() },
    onboarding: Boolean = false,
    onboardingScreen: @Composable () -> Unit = { OnboardingScreen() },
    savedSessionName: String? = null,
    resume: @Composable (name: String, onUsePassword: () -> Unit) -> Unit = { name, usePassword -> ResumeScreen(name, usePassword) },
    app: @Composable () -> Unit,
) {
    when (state) {
        // Login is always the first screen; the introduction comes before it once, on the very first launch.
        // "Set up your business" is always on the login screen. With no business on the device it opens setup;
        // with one already there it only explains, so an existing business can never be replaced from here.
        AuthState.NeedsSetup, AuthState.SignedOut -> {
            var settingUp by rememberSaveable { mutableStateOf(false) }
            var usePassword by rememberSaveable { mutableStateOf(false) }
            when {
                state == AuthState.NeedsSetup && onboarding -> onboardingScreen()
                // The app was closed while someone who unlocks with a fingerprint was signed in: pick them back up.
                state == AuthState.SignedOut && savedSessionName != null && !usePassword && !settingUp ->
                    resume(savedSessionName) { usePassword = true }
                settingUp -> if (state == AuthState.NeedsSetup) setup { settingUp = false } else businessExists { settingUp = false }
                else -> login { settingUp = true }
            }
        }
        is AuthState.SignedIn -> CompositionLocalProvider(LocalCapabilities provides Capabilities(state.role, state.name, state.permissions)) {
            // A different person signing in gets a fresh app, never the previous person's screens.
            key(state.userId) {
                when (recoveryPrompt) {
                    // Straight after setup the code must be written down and confirmed before the app opens.
                    RecoveryPrompt.Required -> recovery(false)
                    RecoveryPrompt.Offered -> recovery(true)
                    // Then the admin says which categories the shop sells in; it is asked once.
                    RecoveryPrompt.None -> if (categoryPrompt) categorySetup() else Box {
                        // Locking never closes the app, so a half-made bill is still there after unlocking. While locked
                        // it is hidden from sight and from screen readers, and the lock screen covers all touches.
                        Box(
                            modifier = if (lock.locked) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                        ) { app() }
                        if (lock.locked) LockOverlay { lockScreen() }
                    }
                }
            }
        }
        AuthState.Unreadable -> AuthLayout {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("auth_unreadable")) {
                Text(stringResource(R.string.auth_unreadable_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.auth_unreadable_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}
