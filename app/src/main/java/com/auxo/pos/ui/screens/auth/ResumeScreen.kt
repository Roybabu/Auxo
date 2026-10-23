// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.auth

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import com.auxo.pos.ui.components.SceneFingerprintButton
import com.auxo.pos.ui.components.BotanicalScene
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.components.Illustration
import com.auxo.pos.ui.lock.LocalFingerprint
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ResumeUiState
import com.auxo.pos.viewmodel.ResumeViewModel

/** The "Unlock with fingerprint" screen shown when the app reopens with a saved session. [onUsePassword] goes to the normal sign-in. */
@Composable
fun ResumeScreen(name: String, onUsePassword: () -> Unit) {
    // A new screen each time, so an earlier visit never leaves its failed or unavailable state behind.
    val key = rememberSaveable { java.util.UUID.randomUUID().toString() }
    val viewModel: ResumeViewModel = viewModel(key = key, factory = ResumeViewModel.factory(name))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fingerprint = LocalFingerprint.current
    val title = stringResource(R.string.lock_fingerprint_title)
    val cancel = stringResource(R.string.lock_fingerprint_cancel)
    val ask = {
        val cipher = viewModel.cipher()
        if (cipher != null) {
            fingerprint?.authenticateWithCipher(title, cancel, cipher, viewModel::fingerprintAccepted, viewModel::fingerprintRejected)
        }
    }
    // Without a working fingerprint check (or once the saved session cannot be opened) the password screen is the way in.
    LaunchedEffect(state.unavailable, fingerprint) {
        if (state.unavailable || fingerprint?.isAvailable() != true) onUsePassword() else ask()
    }
    ResumeContent(state, onUseFingerprint = { ask() }, onUsePassword = onUsePassword)
}

@Composable
fun ResumeContent(state: ResumeUiState, onUseFingerprint: () -> Unit, onUsePassword: () -> Unit) {
    // The same scene as the lock screen, so opening the app and locking it look like one thing.
    BotanicalScene(contentTag = "resume") {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Illustration(
                if (state.failed) R.drawable.fp_failed else R.drawable.fp_idle,
                modifier = Modifier.height(96.dp).testTag(if (state.failed) "resume_failed" else "resume_idle"),
            )
        }
        Text(
            stringResource(R.string.resume_title, state.name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = AuthInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(if (state.failed) R.string.resume_failed else R.string.resume_body),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs).testTag("resume_message"),
        )
        SceneFingerprintButton(
            stringResource(if (state.failed) R.string.resume_try_again else R.string.lock_use_fingerprint),
            onClick = onUseFingerprint,
            modifier = Modifier.testTag("resume_fingerprint"),
        )
        TextButton(onClick = onUsePassword, modifier = Modifier.fillMaxWidth().testTag("resume_password")) {
            Text(stringResource(R.string.resume_use_password), color = AuthEmerald, fontWeight = FontWeight.Medium)
        }
    }
}
