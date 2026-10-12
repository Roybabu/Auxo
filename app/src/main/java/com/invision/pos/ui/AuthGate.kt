package com.invision.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.invision.pos.R
import com.invision.pos.core.auth.AuthState
import com.invision.pos.core.auth.RecoveryPrompt
import com.invision.pos.ui.screens.auth.AdminSetupScreen
import com.invision.pos.ui.screens.auth.AuthLayout
import com.invision.pos.ui.screens.auth.LoginScreen
import com.invision.pos.ui.screens.auth.RecoveryCodeGate
import com.invision.pos.ui.theme.Spacing

/**
 * Decides what the app opens to: Admin Setup on the very first launch, Login on every launch after that,
 * and the app itself once someone has signed in. A Super Admin without a recovery code is shown the code
 * screen first: required after setup, offered (and skippable) for an install that predates recovery codes.
 */
@Composable
fun AuthGate(
    state: AuthState,
    setup: @Composable () -> Unit = { AdminSetupScreen() },
    login: @Composable () -> Unit = { LoginScreen() },
    recoveryPrompt: RecoveryPrompt = RecoveryPrompt.None,
    recovery: @Composable (canSkip: Boolean) -> Unit = { canSkip -> RecoveryCodeGate(canSkip) },
    app: @Composable () -> Unit,
) {
    when (state) {
        AuthState.NeedsSetup -> setup()
        AuthState.SignedOut -> login()
        is AuthState.SignedIn -> CompositionLocalProvider(LocalCapabilities provides Capabilities(state.role, state.name, state.permissions)) {
            // A different person signing in gets a fresh app, never the previous person's screens.
            key(state.userId) {
                when (recoveryPrompt) {
                    // Straight after setup the code must be written down and confirmed before the app opens.
                    RecoveryPrompt.Required -> recovery(false)
                    RecoveryPrompt.Offered -> recovery(true)
                    RecoveryPrompt.None -> app()
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
