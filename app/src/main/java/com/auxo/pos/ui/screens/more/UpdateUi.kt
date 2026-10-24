// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auxo.pos.AuxoApplication
import com.auxo.pos.R
import com.auxo.pos.core.update.AppUpdater
import com.auxo.pos.core.update.UpdateState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.theme.Spacing

private fun Context.appUpdater(): AppUpdater = (applicationContext as AuxoApplication).container.appUpdater

/** Opens the system installer for the downloaded update, first asking the phone to allow installs from Auxo if needed. */
@Composable
private fun rememberInstall(updater: AppUpdater, onNeedsPermission: () -> Unit): () -> Unit {
    val context = LocalContext.current
    return {
        try {
            if (updater.needsInstallPermission()) {
                onNeedsPermission()
                context.startActivity(updater.installPermissionIntent())
            } else {
                updater.installIntent()?.let(context::startActivity)
            }
        } catch (_: ActivityNotFoundException) {
            // No installer on this phone; nothing more can be done from here.
        }
    }
}

/** The "App update" card on the More page: the installed version, a check button, and download and install. */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val updater = LocalContext.current.appUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    var permissionHint by rememberSaveable { mutableStateOf(false) }
    val install = rememberInstall(updater) { permissionHint = true }
    AuxoCard(modifier = modifier.fillMaxWidth().testTag("update_card")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.update_version, updater.currentVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.update_licence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("update_licence"),
            )
            Text(
                stringResource(R.string.update_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("update_privacy_note"),
            )
            val status = when (val s = state) {
                UpdateState.Checking -> stringResource(R.string.update_checking)
                UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                UpdateState.Failed -> stringResource(R.string.update_failed)
                is UpdateState.Available -> stringResource(R.string.update_available, s.update.version)
                is UpdateState.Downloading -> stringResource(R.string.update_downloading, s.percent)
                is UpdateState.Ready -> stringResource(R.string.update_available, s.update.version)
                UpdateState.Idle -> null
            }
            if (status != null) {
                Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("update_status"))
            }
            if (permissionHint) {
                Text(stringResource(R.string.update_permission_hint), style = MaterialTheme.typography.bodySmall)
            }
            when (state) {
                is UpdateState.Available -> Button(onClick = updater::download, modifier = Modifier.fillMaxWidth().testTag("update_download")) {
                    Text(stringResource(R.string.update_download))
                }
                is UpdateState.Ready -> Button(onClick = install, modifier = Modifier.fillMaxWidth().testTag("update_install")) {
                    Text(stringResource(R.string.update_install))
                }
                is UpdateState.Downloading -> Unit
                else -> OutlinedButton(
                    onClick = { updater.checkForUpdate() },
                    enabled = state != UpdateState.Checking,
                    modifier = Modifier.fillMaxWidth().testTag("update_check"),
                ) { Text(stringResource(R.string.update_check)) }
            }
        }
    }
}

/** Offers a newer version when the app opens. Shows nothing unless the quiet check on launch found one. */
@Composable
fun UpdatePrompt() {
    val updater = LocalContext.current.appUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    val available = (state as? UpdateState.Available)?.update ?: return
    AlertDialog(
        onDismissRequest = updater::dismiss,
        title = { Text(stringResource(R.string.update_prompt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.update_available, available.version))
                if (available.notes.isNotEmpty()) {
                    Text(available.notes, style = MaterialTheme.typography.bodySmall, maxLines = 8)
                }
            }
        },
        confirmButton = { TextButton(onClick = updater::download) { Text(stringResource(R.string.update_download)) } },
        dismissButton = { TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.update_later)) } },
        modifier = Modifier.testTag("update_prompt"),
    )
}
