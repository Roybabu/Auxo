// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auxo.pos.AuxoApplication
import com.auxo.pos.R
import com.auxo.pos.core.update.AppUpdater
import com.auxo.pos.core.update.UpdateState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
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

/** The "App update" card on the More page: the installed version, what the last check found, and download and install. */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val updater = LocalContext.current.appUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    var permissionHint by rememberSaveable { mutableStateOf(false) }
    val install = rememberInstall(updater) { permissionHint = true }
    val colors = MaterialTheme.colorScheme
    val newer = state is UpdateState.Available || state is UpdateState.Ready || state is UpdateState.Downloading
    AuxoCard(modifier = modifier.fillMaxWidth().testTag("update_card")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuxoIcon(
                    if (newer) R.drawable.ic_download else R.drawable.ic_autorenew,
                    contentDescription = null,
                    size = AuxoIconSize.Medium,
                )
                Column(modifier = Modifier.weight(1f).padding(start = Spacing.lg)) {
                    Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.update_version, updater.currentVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
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
                val failed = state == UpdateState.Failed
                val upToDate = state == UpdateState.UpToDate
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        failed -> colors.errorContainer
                        newer -> colors.primaryContainer
                        else -> colors.surfaceVariant
                    },
                    contentColor = when {
                        failed -> colors.onErrorContainer
                        newer -> colors.onPrimaryContainer
                        else -> colors.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (upToDate) {
                                AuxoIcon(R.drawable.ic_check, contentDescription = null, size = AuxoIconSize.Mini, modifier = Modifier.padding(end = Spacing.sm))
                            }
                            Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("update_status"))
                        }
                        (state as? UpdateState.Downloading)?.let {
                            LinearProgressIndicator(
                                progress = { it.percent / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                            )
                        }
                    }
                }
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
            Text(
                stringResource(R.string.update_licence),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.testTag("update_licence"),
            )
            Text(
                stringResource(R.string.update_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.testTag("update_privacy_note"),
            )
        }
    }
}

/**
 * Offers a newer version when the app opens. Shows nothing unless the quiet check on launch found one. After Download is
 * tapped the same popup shows the progress, then an Install button, so the update can be finished without leaving the screen.
 */
@Composable
fun UpdatePrompt() {
    val updater = LocalContext.current.appUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    var hidden by rememberSaveable { mutableStateOf(false) }
    var started by rememberSaveable { mutableStateOf(false) }
    var permissionHint by rememberSaveable { mutableStateOf(false) }
    val install = rememberInstall(updater) { permissionHint = true }
    val update = when (val s = state) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.Ready -> s.update
        else -> null
    }
    val failedAfterStart = started && state == UpdateState.Failed
    if (hidden || (update == null && !failedAfterStart)) return
    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) { hidden = true; updater.dismiss() } },
        title = { Text(stringResource(R.string.update_prompt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when {
                    failedAfterStart -> Text(stringResource(R.string.update_failed))
                    else -> {
                        Text(stringResource(R.string.update_available, update!!.version))
                        (state as? UpdateState.Downloading)?.let {
                            Text(stringResource(R.string.update_downloading, it.percent), style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { it.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                        if (permissionHint) {
                            Text(stringResource(R.string.update_permission_hint), style = MaterialTheme.typography.bodySmall)
                        }
                        if (update.notes.isNotEmpty()) {
                            Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall)
                            Text(
                                update.notes.toPlainChangelog(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState())
                                    .testTag("update_prompt_notes"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                failedAfterStart -> TextButton(onClick = { updater.checkForUpdate() }) { Text(stringResource(R.string.update_check)) }
                state is UpdateState.Ready -> TextButton(onClick = install, modifier = Modifier.testTag("update_prompt_install")) {
                    Text(stringResource(R.string.update_install))
                }
                state is UpdateState.Downloading -> Unit
                else -> TextButton(onClick = { started = true; updater.download() }, modifier = Modifier.testTag("update_prompt_download")) {
                    Text(stringResource(R.string.update_download))
                }
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading) {
                TextButton(onClick = { hidden = true; updater.dismiss() }) { Text(stringResource(R.string.update_later)) }
            }
        },
        modifier = Modifier.testTag("update_prompt"),
    )
}

/** The release notes are Markdown; show them as plain lines with the heading marks and bold marks taken out. */
internal fun String.toPlainChangelog(): String = lines().joinToString("\n") { line ->
    line.trimEnd().replace(Regex("^#{1,6}\\s*"), "").replace("**", "").replace("`", "")
        .replace(Regex("^[-*]\\s+"), "• ")
}.replace(Regex("\\n{3,}"), "\n\n").trim()
