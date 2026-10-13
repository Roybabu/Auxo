package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.auth.AdminSetupValidator
import com.auxo.pos.core.auth.AuthRepository
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.SetupError
import com.auxo.pos.core.auth.SetupField
import com.auxo.pos.core.auth.SetupResult
import com.auxo.pos.core.auth.SignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminSetupUiState(
    val name: String = "",
    val identifier: String = "",
    val password: String = "",
    val confirm: String = "",
    /** Shown only after the first attempt to create the account, so a person is not scolded while typing. */
    val errors: Map<SetupField, SetupError> = emptyMap(),
    val submitted: Boolean = false,
    val isSubmitting: Boolean = false,
    val saveFailed: Boolean = false,
)

class AdminSetupViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminSetupUiState())
    val uiState: StateFlow<AdminSetupUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = edit { it.copy(name = value.take(AdminSetupValidator.MAX_NAME_LENGTH + 20)) }

    fun onIdentifierChange(value: String) = edit { it.copy(identifier = value.take(AdminSetupValidator.MAX_IDENTIFIER_LENGTH + 20)) }

    fun onPasswordChange(value: String) = edit { it.copy(password = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20)) }

    fun onConfirmChange(value: String) = edit { it.copy(confirm = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20)) }

    private fun edit(change: (AdminSetupUiState) -> AdminSetupUiState) = _uiState.update { state ->
        val next = change(state).copy(saveFailed = false)
        if (next.submitted) next.copy(errors = validate(next)) else next
    }

    private fun validate(state: AdminSetupUiState) =
        AdminSetupValidator.validate(state.name, state.identifier, state.password, state.confirm)

    fun submit() {
        val current = _uiState.value
        // Ignore a second tap while the first is still being saved.
        if (current.isSubmitting) return
        val errors = validate(current)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(submitted = true, errors = errors, saveFailed = false) }
            return
        }
        _uiState.update { it.copy(submitted = true, errors = emptyMap(), isSubmitting = true, saveFailed = false) }
        viewModelScope.launch {
            val result = auth.createAdministrator(current.name, current.identifier, current.password, current.confirm)
            _uiState.update { state ->
                when (result) {
                    // The app moves on by itself once the account exists; do not keep the password around.
                    SetupResult.Created, SetupResult.AlreadyInitialized -> state.copy(isSubmitting = false, password = "", confirm = "")
                    is SetupResult.Invalid -> state.copy(isSubmitting = false, errors = result.errors)
                    SetupResult.Failed -> state.copy(isSubmitting = false, saveFailed = true)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AdminSetupViewModel(appContainer().authRepository) }
        }
    }
}

sealed interface LoginMessage {
    data object Rejected : LoginMessage
    data object Deactivated : LoginMessage
    data class LockedOut(val seconds: Int) : LoginMessage
    /** No business has been set up on this device yet. */
    data object NoAccount : LoginMessage
    data object Unavailable : LoginMessage
}

data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val message: LoginMessage? = null,
    /** Set when Sign in was tapped with the field empty; cleared as soon as the person types in it. */
    val identifierMissing: Boolean = false,
    val passwordMissing: Boolean = false,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onIdentifierChange(value: String) =
        _uiState.update { it.copy(identifier = value.take(AdminSetupValidator.MAX_IDENTIFIER_LENGTH), message = null, identifierMissing = false) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20), message = null, passwordMissing = false) }

    fun signIn() {
        val current = _uiState.value
        if (current.isSigningIn) return
        val identifierMissing = current.identifier.isBlank()
        val passwordMissing = current.password.isEmpty()
        if (identifierMissing || passwordMissing) {
            _uiState.update { it.copy(identifierMissing = identifierMissing, passwordMissing = passwordMissing, message = null) }
            return
        }
        _uiState.update { it.copy(isSigningIn = true, message = null) }
        viewModelScope.launch {
            val result = auth.signIn(current.identifier, current.password)
            _uiState.update { state ->
                when (result) {
                    // The app moves on by itself once signed in.
                    SignInResult.Success -> state.copy(isSigningIn = false, password = "")
                    SignInResult.Rejected -> state.copy(isSigningIn = false, password = "", message = LoginMessage.Rejected)
                    SignInResult.Deactivated -> state.copy(isSigningIn = false, password = "", message = LoginMessage.Deactivated)
                    is SignInResult.LockedOut -> state.copy(isSigningIn = false, password = "", message = LoginMessage.LockedOut(result.seconds))
                    SignInResult.Unavailable -> state.copy(
                        isSigningIn = false,
                        message = if (auth.state.value == AuthState.NeedsSetup) LoginMessage.NoAccount else LoginMessage.Unavailable,
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(appContainer().authRepository) }
        }
    }
}
