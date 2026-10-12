package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.auth.AdminSetupValidator
import com.auxo.pos.core.auth.AuthRepository
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.IdleTimeout
import com.auxo.pos.core.auth.LockInfo
import com.auxo.pos.core.auth.PinError
import com.auxo.pos.core.auth.PinPolicy
import com.auxo.pos.core.auth.QuickUnlockChange
import com.auxo.pos.core.auth.QuickUnlockStatus
import com.auxo.pos.core.auth.Role
import com.auxo.pos.core.auth.UnlockResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LockMessage {
    data class WrongPin(val triesLeft: Int) : LockMessage

    /** The last allowed wrong PIN was used: the PIN and fingerprint are off until the password is used. */
    data object PinOff : LockMessage
    data object WrongPassword : LockMessage
    data class LockedOut(val seconds: Int) : LockMessage
    data object Unavailable : LockMessage
}

data class LockUiState(
    val pin: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val message: LockMessage? = null,
)

/** The lock screen: resumes the signed-in person with a PIN, a fingerprint or their password. */
class LockViewModel(private val auth: AuthRepository) : ViewModel() {

    val lock: StateFlow<LockInfo> = auth.lock

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    fun onPinChange(value: String) = _uiState.update { it.copy(pin = value.filter(Char::isDigit).take(PinPolicy.MAX_LENGTH), message = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20), message = null) }

    fun unlockWithPin() {
        val current = _uiState.value
        if (current.busy || current.pin.isEmpty()) return
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = auth.unlockWithPin(current.pin)
            _uiState.update { state -> state.copy(busy = false, pin = "", message = result.toPinMessage()) }
        }
    }

    fun unlockWithPassword() {
        val current = _uiState.value
        if (current.busy || current.password.isEmpty()) return
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = auth.unlockWithPassword(current.password)
            _uiState.update { state -> state.copy(busy = false, password = "", message = result.toPasswordMessage()) }
        }
    }

    /** The phone's fingerprint check has just passed. */
    fun fingerprintAccepted() {
        if (!auth.unlockWithFingerprint()) _uiState.update { it.copy(message = LockMessage.Unavailable) }
    }

    /** "Sign out": the next person signs in with their own password. */
    fun signOut() = auth.signOut()

    private fun UnlockResult.toPinMessage(): LockMessage? = when (this) {
        UnlockResult.Unlocked -> null
        is UnlockResult.WrongPin -> LockMessage.WrongPin(triesLeft)
        UnlockResult.PinLockedOut -> LockMessage.PinOff
        UnlockResult.WrongPassword -> LockMessage.WrongPassword
        is UnlockResult.LockedOut -> LockMessage.LockedOut(seconds)
        UnlockResult.Unavailable -> LockMessage.Unavailable
    }

    private fun UnlockResult.toPasswordMessage(): LockMessage? = toPinMessage()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LockViewModel(appContainer().authRepository) }
        }
    }
}

/** Which small form is open on the Quick unlock page. */
enum class QuickUnlockForm { NONE, PIN, FINGERPRINT }

sealed interface QuickUnlockMessage {
    data object WrongPassword : QuickUnlockMessage
    data class LockedOut(val seconds: Int) : QuickUnlockMessage
    data class PinProblem(val error: PinError) : QuickUnlockMessage
    data object PinMismatch : QuickUnlockMessage
    data object Failed : QuickUnlockMessage
}

data class QuickUnlockUiState(
    val status: QuickUnlockStatus = QuickUnlockStatus(hasPin = false, fingerprint = false),
    val idleTimeout: IdleTimeout = IdleTimeout.OFF,
    /** Only the Super Admin chooses the idle time. */
    val canChooseIdleTime: Boolean = false,
    val form: QuickUnlockForm = QuickUnlockForm.NONE,
    val password: String = "",
    val pin: String = "",
    val confirmPin: String = "",
    val busy: Boolean = false,
    val message: QuickUnlockMessage? = null,
)

class QuickUnlockViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        QuickUnlockUiState(
            idleTimeout = auth.idleTimeout.value,
            canChooseIdleTime = (auth.state.value as? AuthState.SignedIn)?.role == Role.SUPER_ADMIN,
        ),
    )
    val uiState: StateFlow<QuickUnlockUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { auth.idleTimeout.collect { value -> _uiState.update { it.copy(idleTimeout = value) } } }
    }

    private fun refresh() {
        viewModelScope.launch {
            auth.quickUnlockStatus()?.let { status -> _uiState.update { it.copy(status = status) } }
        }
    }

    fun openPinForm() = _uiState.update { it.copy(form = QuickUnlockForm.PIN, password = "", pin = "", confirmPin = "", message = null) }

    fun openFingerprintForm() = _uiState.update { it.copy(form = QuickUnlockForm.FINGERPRINT, password = "", message = null) }

    fun closeForm() = _uiState.update { it.copy(form = QuickUnlockForm.NONE, password = "", pin = "", confirmPin = "", message = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20), message = null) }

    fun onPinChange(value: String) = _uiState.update { it.copy(pin = value.filter(Char::isDigit).take(PinPolicy.MAX_LENGTH), message = null) }

    fun onConfirmPinChange(value: String) = _uiState.update { it.copy(confirmPin = value.filter(Char::isDigit).take(PinPolicy.MAX_LENGTH), message = null) }

    fun savePin() {
        val current = _uiState.value
        if (current.busy) return
        val problem = PinPolicy.validate(current.pin)
        if (problem != null) {
            _uiState.update { it.copy(message = QuickUnlockMessage.PinProblem(problem)) }
            return
        }
        if (current.pin != current.confirmPin) {
            _uiState.update { it.copy(message = QuickUnlockMessage.PinMismatch) }
            return
        }
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = auth.setPin(current.password, current.pin)
            if (result == QuickUnlockChange.Done) {
                closeForm()
                _uiState.update { it.copy(busy = false) }
                refresh()
            } else {
                _uiState.update { it.copy(busy = false, password = "", message = result.toMessage()) }
            }
        }
    }

    fun removePin() {
        viewModelScope.launch {
            val result = auth.removePin()
            _uiState.update { it.copy(message = if (result == QuickUnlockChange.Done) null else result.toMessage()) }
            refresh()
        }
    }

    /** Turns fingerprint unlock on after the password is typed. */
    fun confirmFingerprintOn() {
        val current = _uiState.value
        if (current.busy) return
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = auth.setFingerprint(true, current.password)
            if (result == QuickUnlockChange.Done) {
                closeForm()
                _uiState.update { it.copy(busy = false) }
                refresh()
            } else {
                _uiState.update { it.copy(busy = false, password = "", message = result.toMessage()) }
            }
        }
    }

    fun turnFingerprintOff() {
        viewModelScope.launch {
            auth.setFingerprint(false, null)
            refresh()
        }
    }

    fun chooseIdleTime(value: IdleTimeout) {
        viewModelScope.launch {
            auth.setIdleTimeout(value)
        }
    }

    fun lockNow() {
        auth.lockNow()
    }

    private fun QuickUnlockChange.toMessage(): QuickUnlockMessage = when (this) {
        QuickUnlockChange.Done -> QuickUnlockMessage.Failed
        is QuickUnlockChange.InvalidPin -> QuickUnlockMessage.PinProblem(error)
        QuickUnlockChange.WrongPassword -> QuickUnlockMessage.WrongPassword
        is QuickUnlockChange.LockedOut -> QuickUnlockMessage.LockedOut(seconds)
        QuickUnlockChange.Denied, QuickUnlockChange.Failed -> QuickUnlockMessage.Failed
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { QuickUnlockViewModel(appContainer().authRepository) }
        }
    }
}
