package com.invision.pos.ui.screens.more

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.core.backup.BackupProblem
import com.invision.pos.core.backup.RestartApp
import com.invision.pos.core.backup.SavedBackup
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.format.DateFormats
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.BackupUiState
import com.invision.pos.viewmodel.BackupViewModel

/** Backing up to the Invision folder and restoring from a backup file. */
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onRestoreFilePicked(uri)
    }
    // The restored data is only picked up by a fresh start of the app.
    LaunchedEffect(state.restored) {
        if (state.restored) RestartApp.restart(context)
    }
    BackupContent(
        state = state,
        onBack = onBack,
        onBackUp = viewModel::onBackUp,
        onChooseFile = { picker.launch(arrayOf("*/*")) },
        onShare = { saved -> shareBackup(context, saved) },
        onConfirmRestore = viewModel::onRestoreConfirmed,
        onCancelRestore = viewModel::onRestoreCancelled,
        onDismissProblem = viewModel::onProblemDismissed,
    )
}

@Composable
fun BackupContent(
    state: BackupUiState,
    onBack: () -> Unit,
    onBackUp: () -> Unit,
    onChooseFile: () -> Unit,
    onShare: (SavedBackup) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.backup_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.restored) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(Spacing.xl).testTag("backup_restoring"),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.backup_restoring), style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .testTag("backup_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.problem?.let { ProblemCard(it, onDismissProblem) }

            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.backup_make_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.backup_make_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.lastBackupMillis?.let { stringResource(R.string.backup_last, DateFormats.dateTime(it)) }
                            ?: stringResource(R.string.backup_never),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("backup_last"),
                    )
                    Button(onClick = onBackUp, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth().testTag("backup_now")) {
                        if (state.isWorking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.backup_working), modifier = Modifier.padding(start = Spacing.sm))
                        } else {
                            Icon(painterResource(R.drawable.ic_backup), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.backup_now), modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                }
            }

            state.saved?.let { saved ->
                InvisionCard(modifier = Modifier.fillMaxWidth().testTag("backup_saved")) {
                    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.backup_saved_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.backup_saved_where, saved.fileName, saved.folder), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { onShare(saved) }, modifier = Modifier.fillMaxWidth().testTag("backup_share")) {
                            Text(stringResource(R.string.backup_share))
                        }
                    }
                }
            }

            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.backup_restore_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onChooseFile, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth().testTag("backup_choose")) {
                        Text(stringResource(R.string.backup_restore_choose))
                    }
                }
            }

            Text(
                text = stringResource(R.string.backup_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.pendingRestore != null) {
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmRestore, modifier = Modifier.testTag("backup_confirm")) {
                    Text(stringResource(R.string.backup_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ProblemCard(problem: BackupProblem, onDismiss: () -> Unit) {
    val message = when (problem) {
        BackupProblem.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
        BackupProblem.WRONG_APP -> R.string.backup_error_wrong_app
        BackupProblem.NEWER_BACKUP -> R.string.backup_error_newer
        BackupProblem.CORRUPT -> R.string.backup_error_corrupt
        BackupProblem.UNREADABLE -> R.string.backup_error_unreadable
        BackupProblem.SAVE_FAILED -> R.string.backup_error_save
        BackupProblem.RESTORE_FAILED -> R.string.backup_error_restore
    }
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().testTag("backup_problem"),
    ) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_dismiss)) }
        }
    }
}

private fun shareBackup(context: Context, saved: SavedBackup) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, saved.uri)
        putExtra(Intent.EXTRA_SUBJECT, saved.fileName)
        clipData = ClipData.newRawUri(saved.fileName, saved.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.backup_share_title)))
    } catch (e: ActivityNotFoundException) {
        // No app can take the file; it is still in the Invision folder.
    }
}
