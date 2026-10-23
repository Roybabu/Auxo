// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.backup.auto.AutoBackupFailure
import com.auxo.pos.core.backup.auto.AutoBackupResult
import com.auxo.pos.core.backup.auto.AutoBackupSettings
import com.auxo.pos.core.backup.auto.BackupSchedule
import com.auxo.pos.core.backup.auto.DocumentTreeFolder
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.AutoBackupUiState
import com.auxo.pos.viewmodel.AutoBackupViewModel

/** Automatic backup on the Backup page: where, how often and how many to keep. */
@Composable
fun AutoBackupSection(viewModel: AutoBackupViewModel = viewModel(factory = AutoBackupViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        try {
            // Keep access across restarts, and let go of the folder used before so the phone's limit is not used up.
            resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            state.settings.folderUri?.let(Uri::parse)?.takeIf { it != tree }?.let { old ->
                runCatching { resolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            }
            val name = DocumentTreeFolder(context, tree).label.ifEmpty { tree.lastPathSegment.orEmpty() }
            viewModel.onFolderPicked(tree.toString(), name)
        } catch (e: SecurityException) {
            viewModel.onFolderAccessProblem()
        }
    }
    AutoBackupCardContent(
        state = state,
        onChooseFolder = { picker.launch(null) },
        onSchedule = viewModel::onScheduleChange,
        onInterval = viewModel::onIntervalChange,
        onKeep = viewModel::onKeepChange,
        onBackUpNow = viewModel::onBackUpNow,
    )
}

@Composable
fun AutoBackupCardContent(
    state: AutoBackupUiState,
    onChooseFolder: () -> Unit,
    onSchedule: (BackupSchedule) -> Unit,
    onInterval: (String) -> Unit,
    onKeep: (String) -> Unit,
    onBackUpNow: () -> Unit,
) {
    val settings = state.settings
    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("auto_backup_card")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.auto_backup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.auto_backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Where
            Text(stringResource(R.string.auto_backup_folder), style = MaterialTheme.typography.labelLarge)
            Text(
                text = if (settings.folderUri.isNullOrEmpty()) stringResource(R.string.auto_backup_no_folder) else settings.folderName.ifEmpty { stringResource(R.string.auto_backup_folder_chosen) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("auto_backup_folder"),
            )
            OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth().testTag("auto_backup_choose_folder")) {
                Text(stringResource(if (settings.folderUri.isNullOrEmpty()) R.string.auto_backup_choose else R.string.auto_backup_change))
            }
            if (state.folderAccessProblem) {
                Text(
                    stringResource(R.string.auto_backup_access_problem),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("auto_backup_access_problem"),
                )
            }

            // When
            Text(stringResource(R.string.auto_backup_when), style = MaterialTheme.typography.labelLarge)
            BackupSchedule.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.schedule == option, role = Role.RadioButton, onClick = { onSchedule(option) })
                        .testTag("auto_schedule_${option.name}"),
                ) {
                    RadioButton(selected = settings.schedule == option, onClick = null)
                    Text(stringResource(option.labelRes()), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
            if (settings.schedule == BackupSchedule.EVERY_N_INVOICES) {
                AuxoTextField(
                    value = state.intervalText,
                    onValueChange = onInterval,
                    label = stringResource(R.string.auto_backup_every_invoices),
                    supportingText = stringResource(R.string.auto_backup_range, AutoBackupSettings.MIN_INVOICE_INTERVAL, AutoBackupSettings.MAX_INVOICE_INTERVAL),
                    error = if (state.intervalValid) null else stringResource(R.string.auto_backup_range, AutoBackupSettings.MIN_INVOICE_INTERVAL, AutoBackupSettings.MAX_INVOICE_INTERVAL),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth().testTag("auto_backup_interval"),
                )
            }

            // How many
            AuxoTextField(
                value = state.keepText,
                onValueChange = onKeep,
                label = stringResource(R.string.auto_backup_keep),
                supportingText = stringResource(R.string.auto_backup_keep_hint),
                error = if (state.keepValid) null else stringResource(R.string.auto_backup_range, AutoBackupSettings.MIN_KEEP, AutoBackupSettings.MAX_KEEP),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth().testTag("auto_backup_keep"),
            )

            if (settings.schedule != BackupSchedule.OFF && settings.folderUri.isNullOrEmpty()) {
                Text(
                    stringResource(R.string.auto_backup_needs_folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("auto_backup_needs_folder"),
                )
            }

            // How it is going
            Text(
                text = settings.lastSuccessMillis?.let { stringResource(R.string.auto_backup_last, DateFormats.dateTime(it)) }
                    ?: stringResource(R.string.auto_backup_never),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("auto_backup_last"),
            )
            settings.lastFailure?.let { failure ->
                Text(
                    stringResource(failure.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("auto_backup_failure"),
                )
            }
            state.lastResult?.let { result -> ResultLine(result) }

            Button(
                onClick = onBackUpNow,
                enabled = !state.isWorking && !settings.folderUri.isNullOrEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("auto_backup_now"),
            ) {
                if (state.isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.backup_working), modifier = Modifier.padding(start = Spacing.sm))
                } else {
                    Text(stringResource(R.string.auto_backup_now))
                }
            }
        }
    }
}

@Composable
private fun ResultLine(result: AutoBackupResult) {
    val (text, isError) = when (result) {
        is AutoBackupResult.Saved -> stringResource(R.string.auto_backup_saved, result.fileName) to false
        is AutoBackupResult.Failed -> stringResource(result.reason.messageRes()) to true
        AutoBackupResult.NotDue, AutoBackupResult.WaitingToRetry -> stringResource(R.string.auto_backup_not_run) to true
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("auto_backup_result"),
    )
}

private fun BackupSchedule.labelRes(): Int = when (this) {
    BackupSchedule.OFF -> R.string.auto_backup_off
    BackupSchedule.DAILY -> R.string.auto_backup_daily
    BackupSchedule.WEEKLY -> R.string.auto_backup_weekly
    BackupSchedule.EVERY_N_INVOICES -> R.string.auto_backup_after_invoices
}

fun AutoBackupFailure.messageRes(): Int = when (this) {
    AutoBackupFailure.FOLDER_UNAVAILABLE -> R.string.auto_backup_failed_folder
    AutoBackupFailure.SAVE_FAILED -> R.string.auto_backup_failed_save
}
