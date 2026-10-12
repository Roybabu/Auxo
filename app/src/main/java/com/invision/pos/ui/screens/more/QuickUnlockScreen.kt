package com.invision.pos.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.core.auth.IdleTimeout
import com.invision.pos.core.auth.PinError
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.lock.LocalFingerprint
import com.invision.pos.ui.screens.auth.PasswordToggle
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.QuickUnlockForm
import com.invision.pos.viewmodel.QuickUnlockMessage
import com.invision.pos.viewmodel.QuickUnlockUiState
import com.invision.pos.viewmodel.QuickUnlockViewModel

/** PIN, fingerprint and lock now for the signed-in person; the idle time for the Super Admin. */
@Composable
fun QuickUnlockScreen(onBack: () -> Unit, viewModel: QuickUnlockViewModel = viewModel(factory = QuickUnlockViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fingerprintAvailable = LocalFingerprint.current?.isAvailable() == true
    QuickUnlockContent(
        state = state,
        fingerprintAvailable = fingerprintAvailable,
        onBack = onBack,
        onOpenPinForm = viewModel::openPinForm,
        onOpenFingerprintForm = viewModel::openFingerprintForm,
        onCloseForm = viewModel::closeForm,
        onPasswordChange = viewModel::onPasswordChange,
        onPinChange = viewModel::onPinChange,
        onConfirmPinChange = viewModel::onConfirmPinChange,
        onSavePin = viewModel::savePin,
        onRemovePin = viewModel::removePin,
        onConfirmFingerprintOn = viewModel::confirmFingerprintOn,
        onTurnFingerprintOff = viewModel::turnFingerprintOff,
        onChooseIdleTime = viewModel::chooseIdleTime,
        onLockNow = viewModel::lockNow,
    )
}

@Composable
fun QuickUnlockContent(
    state: QuickUnlockUiState,
    fingerprintAvailable: Boolean,
    onBack: () -> Unit,
    onOpenPinForm: () -> Unit,
    onOpenFingerprintForm: () -> Unit,
    onCloseForm: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onSavePin: () -> Unit,
    onRemovePin: () -> Unit,
    onConfirmFingerprintOn: () -> Unit,
    onTurnFingerprintOff: () -> Unit,
    onChooseIdleTime: (IdleTimeout) -> Unit,
    onLockNow: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.quick_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .testTag("quick_unlock"),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.quick_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(onClick = onLockNow, modifier = Modifier.fillMaxWidth().testTag("quick_lock_now")) { Text(stringResource(R.string.quick_lock_now)) }

            SectionHeader(stringResource(R.string.quick_pin_header))
            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(if (state.status.hasPin) R.string.quick_pin_on else R.string.quick_pin_off),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.testTag("quick_pin_status"),
                    )
                    if (state.form == QuickUnlockForm.PIN) {
                        PasswordField(state.password, onPasswordChange, showPassword, { showPassword = !showPassword }, !state.busy, ImeAction.Next, "quick_password")
                        PinField(state.pin, onPinChange, R.string.quick_field_pin, ImeAction.Next, !state.busy, "quick_pin")
                        PinField(state.confirmPin, onConfirmPinChange, R.string.quick_field_confirm_pin, ImeAction.Done, !state.busy, "quick_confirm_pin", onDone = onSavePin)
                        MessageLine(state.message)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = onSavePin, enabled = !state.busy, modifier = Modifier.testTag("quick_pin_save")) { Text(stringResource(R.string.quick_save)) }
                            TextButton(onClick = onCloseForm, modifier = Modifier.testTag("quick_cancel")) { Text(stringResource(R.string.quick_cancel)) }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedButton(onClick = onOpenPinForm, modifier = Modifier.testTag("quick_pin_set")) {
                                Text(stringResource(if (state.status.hasPin) R.string.quick_pin_change else R.string.quick_pin_set))
                            }
                            if (state.status.hasPin) {
                                TextButton(onClick = onRemovePin, modifier = Modifier.testTag("quick_pin_remove")) { Text(stringResource(R.string.quick_pin_remove)) }
                            }
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.quick_fingerprint_header))
            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.quick_fingerprint_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(if (fingerprintAvailable) R.string.quick_fingerprint_body else R.string.quick_fingerprint_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("quick_fingerprint_note"),
                            )
                        }
                        Switch(
                            checked = state.status.fingerprint,
                            onCheckedChange = { on -> if (on) onOpenFingerprintForm() else onTurnFingerprintOff() },
                            // It can always be switched off; switching on needs a phone that can check fingerprints.
                            enabled = fingerprintAvailable || state.status.fingerprint,
                            modifier = Modifier.testTag("quick_fingerprint_switch"),
                        )
                    }
                    if (state.form == QuickUnlockForm.FINGERPRINT) {
                        PasswordField(state.password, onPasswordChange, showPassword, { showPassword = !showPassword }, !state.busy, ImeAction.Done, "quick_password", onDone = onConfirmFingerprintOn)
                        MessageLine(state.message)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = onConfirmFingerprintOn, enabled = !state.busy, modifier = Modifier.testTag("quick_fingerprint_confirm")) {
                                Text(stringResource(R.string.quick_fingerprint_turn_on))
                            }
                            TextButton(onClick = onCloseForm, modifier = Modifier.testTag("quick_cancel")) { Text(stringResource(R.string.quick_cancel)) }
                        }
                    }
                }
            }

            if (state.form == QuickUnlockForm.NONE) MessageLine(state.message)

            if (state.canChooseIdleTime) {
                SectionHeader(stringResource(R.string.quick_idle_header))
                Text(stringResource(R.string.quick_idle_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InvisionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                        for (option in IdleTimeout.entries) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = state.idleTimeout == option, role = Role.RadioButton, onClick = { onChooseIdleTime(option) })
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                                    .testTag("quick_idle_${option.name}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = state.idleTimeout == option, onClick = null)
                                Text(idleLabel(option), modifier = Modifier.padding(start = Spacing.md), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun idleLabel(option: IdleTimeout): String =
    if (option == IdleTimeout.OFF) stringResource(R.string.quick_idle_off) else pluralStringResource(R.plurals.quick_idle_minutes, option.minutes, option.minutes)

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    visible: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    ime: ImeAction,
    tag: String,
    onDone: () -> Unit = {},
) {
    InvisionTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(R.string.quick_field_password),
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { PasswordToggle(visible, onToggle) },
        imeAction = ime,
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: Int, ime: ImeAction, enabled: Boolean, tag: String, onDone: () -> Unit = {}) {
    InvisionTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(label),
        keyboardType = KeyboardType.NumberPassword,
        visualTransformation = PasswordVisualTransformation(),
        imeAction = ime,
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun MessageLine(message: QuickUnlockMessage?) {
    val text = when (message) {
        null -> return
        QuickUnlockMessage.WrongPassword -> stringResource(R.string.quick_wrong_password)
        is QuickUnlockMessage.LockedOut -> pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds)
        is QuickUnlockMessage.PinProblem -> stringResource(
            when (message.error) {
                PinError.WRONG_LENGTH -> R.string.quick_pin_length
                PinError.NOT_DIGITS -> R.string.quick_pin_digits
                PinError.TOO_SIMPLE -> R.string.quick_pin_simple
            },
        )
        QuickUnlockMessage.PinMismatch -> stringResource(R.string.quick_pin_mismatch)
        QuickUnlockMessage.Failed -> stringResource(R.string.quick_failed)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("quick_error"))
}
