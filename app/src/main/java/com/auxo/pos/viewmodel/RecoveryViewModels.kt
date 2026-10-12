package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.auth.AdminSetupValidator
import com.auxo.pos.core.auth.AuthRepository
import com.auxo.pos.core.auth.RecoveryCodes
import com.auxo.pos.core.auth.RecoveryConfirmResult
import com.auxo.pos.core.auth.RecoveryPrompt
import com.auxo.pos.core.auth.RecoveryResetResult
import com.auxo.pos.core.auth.RecoveryStartResult
import com.auxo.pos.core.auth.SetupError
import com.auxo.pos.core.auth.SetupField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the person is in creating a recovery code: ask, show it, type it back, saved. */
enum class RecoveryStep { INTRO, SHOW, CONFIRM, SAVED }

sealed interface RecoveryMessage {
    data object WrongPassword : RecoveryMessage
    data class LockedOut(val seconds: Int) : RecoveryMessage

    /** What was typed is not the code that was shown. */
    data object Mismatch : RecoveryMessage
    data object Failed : RecoveryMessage
}

data class RecoveryCodeUiState(
    val step: RecoveryStep = RecoveryStep.INTRO,
    /** False only straight after first-launch setup, when the password was typed moments ago. */
    val needsPassword: Boolean = true,
    val password: String = "",
    /** The code in groups, only while it is being shown. Never kept once saved. */
    val code: String = "",
    val typed: String = "",
    val busy: Boolean = false,
    val message: RecoveryMessage? = null,
)

/** Creates (or replaces) the Super Admin's recovery code. The code is saved only after it has been typed back. */
class RecoveryCodeViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryCodeUiState(needsPassword = auth.recoveryPrompt.value != RecoveryPrompt.Required))
    val uiState: StateFlow<RecoveryCodeUiState> = _uiState.asStateFlow()

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20), message = null) }

    fun onTypedChange(value: String) = _uiState.update { it.copy(typed = value.take(MAX_TYPED), message = null) }

    fun start() {
        val current = _uiState.value
        if (current.busy) return
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = auth.startRecoveryCode(current.password.takeIf { current.needsPassword })
            _uiState.update { state ->
                when (result) {
                    is RecoveryStartResult.Ready -> state.copy(busy = false, step = RecoveryStep.SHOW, code = result.code, password = "", typed = "")
                    RecoveryStartResult.WrongPassword -> state.copy(busy = false, password = "", message = RecoveryMessage.WrongPassword)
                    is RecoveryStartResult.LockedOut -> state.copy(busy = false, password = "", message = RecoveryMessage.LockedOut(result.seconds))
                    RecoveryStartResult.Denied -> state.copy(busy = false, message = RecoveryMessage.Failed)
                }
            }
        }
    }

    /** The person says they have written the code down: now they type it back. */
    fun onWrittenDown() = _uiState.update { it.copy(step = RecoveryStep.CONFIRM, typed = "", message = null) }

    fun showCodeAgain() = _uiState.update { it.copy(step = RecoveryStep.SHOW, message = null) }

    fun confirm() {
        val current = _uiState.value
        if (current.busy) return
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = auth.confirmRecoveryCode(current.typed)
            _uiState.update { state ->
                when (result) {
                    RecoveryConfirmResult.Saved -> state.copy(busy = false, step = RecoveryStep.SAVED, code = "", typed = "")
                    RecoveryConfirmResult.Mismatch -> state.copy(busy = false, message = RecoveryMessage.Mismatch)
                    // The shown code is gone (for example the app restarted), so start again.
                    RecoveryConfirmResult.NothingPending -> state.copy(busy = false, step = RecoveryStep.INTRO, code = "", typed = "", message = RecoveryMessage.Failed)
                    RecoveryConfirmResult.Denied, RecoveryConfirmResult.Failed -> state.copy(busy = false, message = RecoveryMessage.Failed)
                }
            }
        }
    }

    /** "Not now" on the offer made to an install that has no code yet. */
    fun skip() = auth.dismissRecoveryOffer()

    companion object {
        const val MAX_TYPED = 40

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { RecoveryCodeViewModel(appContainer().authRepository) }
        }
    }
}

enum class ResetStep { FORM, SHOW, CONFIRM, DONE }

sealed interface ResetMessage {
    data object WrongCode : ResetMessage
    data class LockedOut(val seconds: Int) : ResetMessage

    /** This install never had a recovery code. */
    data object NoCode : ResetMessage
    data object Unavailable : ResetMessage

    /** The code typed back does not match the new one. */
    data object Mismatch : ResetMessage
}

data class PasswordResetUiState(
    val step: ResetStep = ResetStep.FORM,
    val code: String = "",
    val password: String = "",
    val confirm: String = "",
    val errors: Map<SetupField, SetupError> = emptyMap(),
    val busy: Boolean = false,
    val message: ResetMessage? = null,
    /** The replacement code, in groups, from the moment the password is changed until it is typed back. */
    val newCode: String = "",
    val typed: String = "",
)

/**
 * "Forgot password?": the Super Admin proves who they are with the recovery code, picks a new password and is
 * given a new code. The password is changed at that step; typing the new code back only checks it was copied.
 */
class PasswordResetViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordResetUiState())
    val uiState: StateFlow<PasswordResetUiState> = _uiState.asStateFlow()

    fun onCodeChange(value: String) = _uiState.update { it.copy(code = value.take(RecoveryCodeViewModel.MAX_TYPED), message = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20), errors = it.errors - SetupField.PASSWORD, message = null) }

    fun onConfirmChange(value: String) =
        _uiState.update { it.copy(confirm = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20), errors = it.errors - SetupField.CONFIRM, message = null) }

    fun onTypedChange(value: String) = _uiState.update { it.copy(typed = value.take(RecoveryCodeViewModel.MAX_TYPED), message = null) }

    fun submit() {
        val current = _uiState.value
        if (current.busy) return
        _uiState.update { it.copy(busy = true, errors = emptyMap(), message = null) }
        viewModelScope.launch {
            val result = auth.resetPasswordWithRecoveryCode(current.code, current.password, current.confirm)
            _uiState.update { state ->
                when (result) {
                    // From here the password is already changed; keep nothing the person typed.
                    is RecoveryResetResult.Reset ->
                        state.copy(busy = false, step = ResetStep.SHOW, newCode = result.newCode, code = "", password = "", confirm = "", typed = "")
                    is RecoveryResetResult.Invalid -> state.copy(busy = false, errors = result.errors)
                    RecoveryResetResult.WrongCode -> state.copy(busy = false, message = ResetMessage.WrongCode)
                    is RecoveryResetResult.LockedOut -> state.copy(busy = false, message = ResetMessage.LockedOut(result.seconds))
                    RecoveryResetResult.NoCode -> state.copy(busy = false, message = ResetMessage.NoCode)
                    RecoveryResetResult.Unavailable -> state.copy(busy = false, message = ResetMessage.Unavailable)
                }
            }
        }
    }

    fun onWrittenDown() = _uiState.update { it.copy(step = ResetStep.CONFIRM, typed = "", message = null) }

    fun showCodeAgain() = _uiState.update { it.copy(step = ResetStep.SHOW, message = null) }

    fun confirmNewCode() {
        _uiState.update { state ->
            if (RecoveryCodes.same(state.newCode, state.typed)) {
                state.copy(step = ResetStep.DONE, newCode = "", typed = "", message = null)
            } else {
                state.copy(message = ResetMessage.Mismatch)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PasswordResetViewModel(appContainer().authRepository) }
        }
    }
}
