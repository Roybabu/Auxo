// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.backup.auto.AutoBackupFailure
import com.auxo.pos.core.backup.auto.AutoBackupSettings
import com.auxo.pos.core.backup.auto.BackupSchedule
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.DropdownField
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.AutoBackupUiState
import com.auxo.pos.viewmodel.AutoBackupViewModel

/** Automatic backup on the Backup page: how often, and how many to keep. Backups go to Documents/Auxo by themselves. */
@Composable
fun AutoBackupSection(viewModel: AutoBackupViewModel = viewModel(factory = AutoBackupViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Android 9 and older need the storage permission to save into Documents/Auxo; it is asked for before a schedule starts.
    var pendingSchedule by remember { mutableStateOf<BackupSchedule?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingSchedule?.let(viewModel::onScheduleChange)
        pendingSchedule = null
    }
    AutoBackupCardContent(
        state = state,
        onSchedule = { option ->
            val needsPermission = option != BackupSchedule.OFF && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                pendingSchedule = option
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                viewModel.onScheduleChange(option)
            }
        },
        onInterval = viewModel::onIntervalChange,
        onKeep = viewModel::onKeepChange,
    )
    // Asked for as soon as a schedule is chosen and no passphrase exists yet; cancelling leaves the schedule as it was.
    if (state.choosingPassphrase) ChoosePassphraseDialog(onSave = viewModel::onPassphraseSet, onCancel = viewModel::onPassphraseCancelled)
}

@Composable
fun AutoBackupCardContent(
    state: AutoBackupUiState,
    onSchedule: (BackupSchedule) -> Unit,
    onInterval: (String) -> Unit,
    onKeep: (String) -> Unit,
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

            // When
            val scheduleLabels = BackupSchedule.entries.associateWith { stringResource(it.labelRes()) }
            DropdownField(
                label = stringResource(R.string.auto_backup_when),
                value = scheduleLabels.getValue(settings.schedule),
                options = BackupSchedule.entries,
                optionText = { scheduleLabels.getValue(it) },
                onSelect = onSchedule,
                modifier = Modifier.fillMaxWidth().testTag("auto_schedule_picker"),
            )
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
        }
    }
}

private fun BackupSchedule.labelRes(): Int = when (this) {
    BackupSchedule.OFF -> R.string.auto_backup_off
    BackupSchedule.MINUTES_15 -> R.string.auto_backup_every_15m
    BackupSchedule.MINUTES_30 -> R.string.auto_backup_every_30m
    BackupSchedule.HOURS_1 -> R.string.auto_backup_every_1h
    BackupSchedule.HOURS_2 -> R.string.auto_backup_every_2h
    BackupSchedule.HOURS_3 -> R.string.auto_backup_every_3h
    BackupSchedule.HOURS_6 -> R.string.auto_backup_every_6h
    BackupSchedule.HOURS_12 -> R.string.auto_backup_every_12h
    BackupSchedule.DAILY -> R.string.auto_backup_daily
    BackupSchedule.WEEKLY -> R.string.auto_backup_weekly
    BackupSchedule.EVERY_N_INVOICES -> R.string.auto_backup_after_invoices
}

fun AutoBackupFailure.messageRes(): Int = when (this) {
    AutoBackupFailure.FOLDER_UNAVAILABLE -> R.string.auto_backup_failed_folder
    AutoBackupFailure.SAVE_FAILED -> R.string.auto_backup_failed_save
    AutoBackupFailure.PASSPHRASE_REQUIRED -> R.string.auto_backup_failed_passphrase
}
