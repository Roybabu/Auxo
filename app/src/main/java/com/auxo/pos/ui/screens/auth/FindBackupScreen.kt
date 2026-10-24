// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.FoundBackup
import com.auxo.pos.core.backup.RestartApp
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.GlassButton
import com.auxo.pos.ui.screens.more.UnlockForm
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.FindBackupUiState
import com.auxo.pos.viewmodel.FindBackupViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Shown on a phone with no business yet, before sign-up. The person chooses the folder their backups are in; if
 * Auxo backups are there they are listed by shop, with the date and what each holds, and one can be restored.
 * With none, the person is offered set-up instead.
 */
@Composable
fun FindBackupScreen(
    onNewShop: () -> Unit,
    onBack: () -> Unit,
    viewModel: FindBackupViewModel = viewModel(factory = FindBackupViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            // Keep read access for as long as the phone allows, so a restore that starts later can still open the file.
            runCatching {
                context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.onFolderChosen(tree)
        }
    }
    LaunchedEffect(state.restored) { if (state.restored) RestartApp.restart(context) }
    BackHandler(enabled = state.found != null && !state.restoring) { viewModel.onChooseAnother() }

    AuthLayout {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("find_backup")) {
            when {
                state.scanning || state.restoring -> Working(stringResource(if (state.restoring) R.string.find_restoring else R.string.find_scanning))
                state.found == null -> Intro(
                    problem = state.problem,
                    onChoose = { picker.launch(null) },
                    onNewShop = onNewShop,
                    onBack = onBack,
                )
                state.found.orEmpty().isEmpty() -> {
                    Text(stringResource(R.string.find_none_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.find_none_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GlassButton(onClick = onNewShop, modifier = Modifier.testTag("find_new_shop")) { Text(stringResource(R.string.find_new)) }
                    TextButton(onClick = { picker.launch(null) }, modifier = Modifier.testTag("find_another")) {
                        Text(stringResource(R.string.find_another))
                    }
                }
                else -> BackupList(state, viewModel, onChooseAnother = { picker.launch(null) })
            }
        }
    }

    state.selected?.takeIf { !state.restoring }?.let { backup ->
        if (state.unlocking) {
            Dialog(onDismissRequest = viewModel::onSelectionCancelled) {
                Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                    UnlockForm(onUnlock = viewModel::onPassphrase, onCancel = viewModel::onSelectionCancelled)
                }
            }
        } else {
            val shop = backup.manifest?.shopName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.find_this_backup)
            AlertDialog(
                onDismissRequest = viewModel::onSelectionCancelled,
                title = { Text(stringResource(R.string.find_confirm_title)) },
                text = { Text(stringResource(R.string.find_confirm_body, shop, formatDate(backup.takenAtMillis))) },
                confirmButton = {
                    TextButton(onClick = viewModel::onConfirmed, modifier = Modifier.testTag("find_confirm")) { Text(stringResource(R.string.find_confirm_action)) }
                },
                dismissButton = { TextButton(onClick = viewModel::onSelectionCancelled) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun Working(message: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CircularProgressIndicator(modifier = Modifier.padding(Spacing.xs))
        Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("find_working"))
    }
}

@Composable
private fun Intro(problem: BackupProblem?, onChoose: () -> Unit, onNewShop: () -> Unit, onBack: () -> Unit) {
    Text(stringResource(R.string.find_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.find_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    problem?.let {
        Text(stringResource(problemText(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("find_problem"))
    }
    GlassButton(onClick = onChoose, modifier = Modifier.testTag("find_choose")) { Text(stringResource(R.string.find_choose)) }
    TextButton(onClick = onNewShop, modifier = Modifier.testTag("find_new_shop")) { Text(stringResource(R.string.find_new)) }
    TextButton(onClick = onBack, modifier = Modifier.testTag("find_back")) { Text(stringResource(R.string.auth_back_to_login)) }
}

/** The backups, grouped by shop with the newest first, so each shop's latest copy is easy to find. */
@Composable
private fun BackupList(state: FindBackupUiState, viewModel: FindBackupViewModel, onChooseAnother: () -> Unit) {
    val groups = remember(state.found) { groupByShop(state.found.orEmpty()) }
    Text(stringResource(R.string.find_pick_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.find_pick_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.problem?.let {
        Text(stringResource(problemText(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("find_problem"))
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).testTag("find_list"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        groups.forEach { group ->
            item(key = "head_${group.key}") {
                Text(
                    group.title.ifBlank { stringResource(R.string.find_unknown_shop) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            items(group.backups, key = { it.uri.toString() }) { backup ->
                BackupCard(backup, latest = group.backups.size > 1 && backup === group.backups.first(), onClick = { viewModel.onSelected(backup) })
            }
        }
    }
    TextButton(onClick = onChooseAnother, modifier = Modifier.testTag("find_another")) { Text(stringResource(R.string.find_another)) }
}

@Composable
private fun BackupCard(backup: FoundBackup, latest: Boolean, onClick: () -> Unit) {
    AuxoCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("find_backup_${backup.fileName}")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.find_taken, formatDate(backup.takenAtMillis)) + if (latest) " · " + stringResource(R.string.find_latest) else "",
                style = MaterialTheme.typography.titleSmall,
            )
            val manifest = backup.manifest
            when {
                backup.locked -> Text(stringResource(R.string.find_locked_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                manifest?.products != null && manifest.customers != null && manifest.invoices != null -> Text(
                    stringResource(R.string.find_counts, manifest.products, manifest.customers, manifest.invoices),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                backup.fileName + " · " + android.text.format.Formatter.formatShortFileSize(LocalContext.current, backup.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal class ShopGroup(val key: String, val title: String, val backups: List<FoundBackup>)

/** Groups [found] by shop name (locked ones together, since their name is hidden), shops with the newest backup first. */
internal fun groupByShop(found: List<FoundBackup>): List<ShopGroup> =
    found.groupBy { backup ->
        when {
            backup.locked -> LOCKED_KEY
            else -> backup.manifest?.shopName?.trim()?.lowercase().orEmpty()
        }
    }.map { (key, backups) ->
        val sorted = backups.sortedByDescending { it.takenAtMillis }
        ShopGroup(
            key = key.ifEmpty { "unnamed" },
            title = if (key == LOCKED_KEY) "" else sorted.first().manifest?.shopName?.trim().orEmpty(),
            backups = sorted,
        )
    }.sortedByDescending { it.backups.first().takenAtMillis }

private const val LOCKED_KEY = "\u0000locked"

private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatDate(millis: Long): String = DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun problemText(problem: BackupProblem): Int = when (problem) {
    BackupProblem.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
    BackupProblem.WRONG_APP -> R.string.backup_error_wrong_app
    BackupProblem.NEWER_BACKUP -> R.string.backup_error_newer
    BackupProblem.CORRUPT -> R.string.backup_error_corrupt
    BackupProblem.UNREADABLE -> R.string.backup_error_unreadable
    BackupProblem.PASSPHRASE_NEEDED, BackupProblem.WRONG_PASSPHRASE -> R.string.backup_error_wrong_passphrase
    else -> R.string.backup_error_restore
}
