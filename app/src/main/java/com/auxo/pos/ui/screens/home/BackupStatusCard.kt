// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.backup.auto.AutoBackupStatus
import com.auxo.pos.ui.can
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.screens.more.messageRes
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.AutoBackupUiState
import com.auxo.pos.viewmodel.AutoBackupViewModel

/**
 * Home's backup line for the Super Admin: when the last automatic backup was made, or a warning when it is
 * failing or overdue. Anyone else sees nothing, and nothing of it is read for them.
 */
@Composable
fun BackupStatusNotice(onOpenBackup: () -> Unit) {
    if (!can(Permission.BACKUP_AND_RESTORE)) return
    val viewModel: AutoBackupViewModel = viewModel(factory = AutoBackupViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackupStatusCard(state, onOpenBackup)
}

@Composable
fun BackupStatusCard(state: AutoBackupUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val warn = state.status == AutoBackupStatus.FAILED || state.status == AutoBackupStatus.OVERDUE
    val title = when (state.status) {
        AutoBackupStatus.OFF -> R.string.home_backup_off_title
        AutoBackupStatus.OK -> R.string.home_backup_ok_title
        AutoBackupStatus.FAILED -> R.string.home_backup_failed_title
        AutoBackupStatus.OVERDUE -> R.string.home_backup_overdue_title
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).testTag("home_backup_status"),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            val content = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = content)
            val detail: String = when (state.status) {
                AutoBackupStatus.OFF -> stringResource(R.string.home_backup_off_body)
                AutoBackupStatus.OK -> state.settings.lastSuccessMillis?.let { stringResource(R.string.auto_backup_last, DateFormats.dateTime(it)) }
                    ?: stringResource(R.string.auto_backup_never)
                AutoBackupStatus.FAILED -> (state.settings.lastFailure?.let { stringResource(it.messageRes()) }.orEmpty()) + lastSuccessSuffix(state)
                AutoBackupStatus.OVERDUE -> state.settings.lastSuccessMillis?.let { stringResource(R.string.auto_backup_last, DateFormats.dateTime(it)) }.orEmpty()
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = content, modifier = Modifier.testTag("home_backup_detail"))
        }
    }
}

@Composable
private fun lastSuccessSuffix(state: AutoBackupUiState): String =
    state.settings.lastSuccessMillis?.let { " " + stringResource(R.string.auto_backup_last, DateFormats.dateTime(it)) }.orEmpty()
