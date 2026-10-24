// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.backup.BackupCrypto
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.PassphraseStrength
import com.auxo.pos.core.backup.RestartApp
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.BackupUiState
import com.auxo.pos.viewmodel.BackupViewModel

/** Backing up to the Auxo folder and restoring from a backup file. */
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onRestoreFilePicked(uri)
    }
    // Android 9 and older need the storage permission to save into Documents/Auxo, where a backup survives
    // uninstalling the app. If it is refused the backup is still made, in the app's own folder, and says so.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onBackUp() }
    val onBackUp = {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.onBackUp()
    }
    // The restored data is only picked up by a fresh start of the app.
    LaunchedEffect(state.restored) {
        if (state.restored) RestartApp.restart(context)
    }
    BackupContent(
        state = state,
        onBack = onBack,
        onBackUp = onBackUp,
        onChooseFile = { picker.launch(arrayOf("*/*")) },
        onConfirmRestore = viewModel::onRestoreConfirmed,
        onCancelRestore = viewModel::onRestoreCancelled,
        onDismissProblem = viewModel::onProblemDismissed,
        onPassphraseSet = viewModel::onPassphraseSet,
        onChoosePassphraseCancelled = viewModel::onChoosePassphraseCancelled,
        onPassphraseForRestore = viewModel::onPassphraseForRestore,
        onUnlockCancelled = viewModel::onUnlockCancelled,
        extra = { AutoBackupSection() },
    )
}

@Composable
fun BackupContent(
    state: BackupUiState,
    onBack: () -> Unit,
    onBackUp: () -> Unit,
    onChooseFile: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onDismissProblem: () -> Unit,
    onPassphraseSet: (String) -> Unit = {},
    onChoosePassphraseCancelled: () -> Unit = {},
    onPassphraseForRestore: (String) -> Unit = {},
    onUnlockCancelled: () -> Unit = {},
    /** More cards shown between making a backup and restoring one. */
    extra: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.backup_title), onBack = onBack) },
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

            AuxoCard(modifier = Modifier.fillMaxWidth()) {
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

            extra()

            state.saved?.let { saved ->
                AuxoCard(modifier = Modifier.fillMaxWidth().testTag("backup_saved")) {
                    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.backup_saved_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.backup_saved_where, saved.fileName, saved.folder), style = MaterialTheme.typography.bodyMedium)
                        if (saved.appFolderOnly) {
                            Text(
                                stringResource(R.string.backup_saved_app_only),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("backup_app_only_warning"),
                            )
                        }
                    }
                }
            }

            AuxoCard(modifier = Modifier.fillMaxWidth()) {
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
        }
    }

    if (state.choosingPassphrase) ChoosePassphraseDialog(onSave = onPassphraseSet, onCancel = onChoosePassphraseCancelled)
    if (state.unlocking != null) UnlockDialog(onUnlock = onPassphraseForRestore, onCancel = onUnlockCancelled)

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

/** Asks for a new passphrase twice, with a plain warning that it cannot be recovered. */
@Composable
private fun ChoosePassphraseDialog(onSave: (String) -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) { ChoosePassphraseForm(onSave, onCancel) }
    }
}

/** The contents of the box above, on its own so it can be tested without a dialog window. */
@Composable
internal fun ChoosePassphraseForm(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val problem = PassphraseStrength.problem(first)
    val weak = first.isNotEmpty() && problem != null
    val differs = first != second
    Column(
        modifier = Modifier.padding(Spacing.xl).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.backup_pass_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.backup_pass_warning), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = first,
            onValueChange = { first = it },
            label = { Text(stringResource(R.string.backup_pass_field)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = {
                when {
                    !weak -> Text(stringResource(R.string.backup_pass_tip))
                    problem == PassphraseStrength.Problem.TOO_SHORT -> Text(stringResource(R.string.backup_pass_short, BackupCrypto.MIN_PASSPHRASE_LENGTH))
                    problem == PassphraseStrength.Problem.TOO_COMMON -> Text(stringResource(R.string.backup_pass_common))
                    else -> Text(stringResource(R.string.backup_pass_simple))
                }
            },
            isError = weak,
            modifier = Modifier.fillMaxWidth().testTag("backup_pass_first"),
        )
        if (first.isNotEmpty()) PassphraseMeter(PassphraseStrength.segments(first))
        OutlinedTextField(
            value = second,
            onValueChange = { second = it },
            label = { Text(stringResource(R.string.backup_pass_repeat)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { if (second.isNotEmpty() && differs) Text(stringResource(R.string.backup_pass_mismatch)) },
            isError = second.isNotEmpty() && differs,
            modifier = Modifier.fillMaxWidth().testTag("backup_pass_second"),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            TextButton(onClick = { onSave(first) }, enabled = problem == null && !differs, modifier = Modifier.testTag("backup_pass_save")) {
                Text(stringResource(R.string.backup_pass_save))
            }
        }
    }
}

/** Four bars that fill as the passphrase gets stronger. Only a guide: weak passphrases are refused whatever it shows. */
@Composable
private fun PassphraseMeter(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth().testTag("backup_pass_meter")) {
        repeat(PassphraseStrength.SEGMENTS) { index ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (index < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).height(6.dp),
            ) {}
        }
    }
}

/** Asks for the passphrase of a locked backup that is being restored. */
@Composable
private fun UnlockDialog(onUnlock: (String) -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) { UnlockForm(onUnlock, onCancel) }
    }
}

/** The contents of the box above, on its own so it can be tested without a dialog window. */
@Composable
internal fun UnlockForm(onUnlock: (String) -> Unit, onCancel: () -> Unit, error: String? = null, actionLabel: String? = null) {
    var passphrase by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.padding(Spacing.xl).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.backup_unlock_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.backup_unlock_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(R.string.backup_pass_field)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("backup_unlock_field"),
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("backup_unlock_error"))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            TextButton(onClick = { onUnlock(passphrase) }, enabled = passphrase.isNotEmpty(), modifier = Modifier.testTag("backup_unlock_action")) {
                Text(actionLabel ?: stringResource(R.string.backup_unlock_action))
            }
        }
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
        BackupProblem.PASSPHRASE_NEEDED, BackupProblem.WRONG_PASSPHRASE -> R.string.backup_error_wrong_passphrase
        BackupProblem.PASSPHRASE_UNAVAILABLE -> R.string.backup_error_passphrase_unavailable
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
