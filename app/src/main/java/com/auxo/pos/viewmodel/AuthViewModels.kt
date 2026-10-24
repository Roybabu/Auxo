// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.auth.AdminSetupValidator
import com.auxo.pos.core.auth.AuthRepository
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.PasswordStrength
import com.auxo.pos.core.auth.SavedSessionVault
import com.auxo.pos.core.auth.UsernameSuggestions
import com.auxo.pos.domain.billing.Fssai
import com.auxo.pos.domain.billing.Gstin
import com.auxo.pos.domain.billing.SettingsError
import com.auxo.pos.domain.billing.SettingsField
import com.auxo.pos.domain.billing.SettingsValidator
import com.auxo.pos.domain.model.StoreSettings
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
    val businessName: String = "",
    /** The optional store details (address, GST, UPI, receipt and so on). The store name comes from [businessName]. */
    val details: StoreSettings = StoreSettings(),
    /** A logo picked on the form; it is saved once the account exists, because saving it needs a signed-in person. */
    val logo: Uri? = null,
) {
    /**
     * Problems in what was typed under the optional details. Nothing there is required, so an empty field, or a
     * GST state that is still unchosen, is never a problem; only a value that is filled in wrongly is.
     */
    val detailErrors: Map<SettingsField, SettingsError>
        get() = SettingsValidator.check(details.copy(storeName = "-"))
            .filterKeys { it != SettingsField.STORE_NAME && it != SettingsField.STATE }

    /** Username ideas from the typed name, offered only while the username is still empty. */
    val suggestions: List<String> get() = if (identifier.isBlank()) UsernameSuggestions.from(name) else emptyList()

    /** 0 to 4 filled segments for the strength meter. */
    val strength: Int get() = PasswordStrength.segments(password, identifier)
}

/**
 * [saveStore] stores the business name, the optional store details and the picked logo (if any) once the account exists.
 * The app wires it to the store settings and runs it outside this screen, so it still completes when setup closes the
 * moment the account is created.
 */
class AdminSetupViewModel(
    private val auth: AuthRepository,
    private val saveStore: (businessName: String, details: StoreSettings, logo: Uri?) -> Unit = { _, _, _ -> },
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminSetupUiState())
    val uiState: StateFlow<AdminSetupUiState> = _uiState.asStateFlow()

    fun onBusinessChange(value: String) = edit { it.copy(businessName = value.take(AdminSetupValidator.MAX_BUSINESS_NAME_LENGTH + 20)) }

    fun onDetailsChange(transform: (StoreSettings) -> StoreSettings) = edit { it.copy(details = transform(it.details)) }

    /** Fills the GST state from the GSTIN's first two digits when it is not chosen yet, as Store details does. */
    fun onGstinChange(value: String) = onDetailsChange { settings ->
        val gstin = Gstin.normalize(value).take(15)
        val inferred = if (settings.stateCode.isEmpty() && Gstin.isValid(gstin)) Gstin.stateCode(gstin) else settings.stateCode
        settings.copy(gstin = gstin, stateCode = inferred)
    }

    fun onLogoPicked(uri: Uri?) = edit { it.copy(logo = uri ?: it.logo) }

    fun onLogoRemoved() = edit { it.copy(logo = null) }

    fun useSuggestion(username: String) = edit { it.copy(identifier = username) }

    fun onNameChange(value: String) = edit { it.copy(name = value.take(AdminSetupValidator.MAX_NAME_LENGTH + 20)) }

    fun onIdentifierChange(value: String) = edit { it.copy(identifier = value.take(AdminSetupValidator.MAX_IDENTIFIER_LENGTH + 20)) }

    fun onPasswordChange(value: String) = edit { it.copy(password = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20)) }

    fun onConfirmChange(value: String) = edit { it.copy(confirm = value.take(AdminSetupValidator.MAX_PASSWORD_LENGTH + 20)) }

    private fun edit(change: (AdminSetupUiState) -> AdminSetupUiState) = _uiState.update { state ->
        val next = change(state).copy(saveFailed = false)
        if (next.submitted) next.copy(errors = validate(next)) else next
    }

    private fun validate(state: AdminSetupUiState): Map<SetupField, SetupError> {
        val errors = LinkedHashMap<SetupField, SetupError>()
        AdminSetupValidator.businessNameError(state.businessName)?.let { errors[SetupField.BUSINESS] = it }
        errors.putAll(AdminSetupValidator.validate(accountName(state), state.identifier, state.password, state.confirm))
        // The person's own name is optional; the username stands in for it (see accountName).
        if (state.name.isBlank()) errors.remove(SetupField.NAME)
        return errors
    }

    /** The name the account is created with: the typed name, or the username when none was given. */
    private fun accountName(state: AdminSetupUiState): String = state.name.ifBlank { state.identifier.trim() }

    fun submit() {
        val current = _uiState.value
        // Ignore a second tap while the first is still being saved.
        if (current.isSubmitting) return
        val errors = validate(current)
        if (errors.isNotEmpty() || current.detailErrors.isNotEmpty()) {
            _uiState.update { it.copy(submitted = true, errors = errors, saveFailed = false) }
            return
        }
        _uiState.update { it.copy(submitted = true, errors = emptyMap(), isSubmitting = true, saveFailed = false) }
        viewModelScope.launch {
            val result = auth.createAdministrator(accountName(current), current.identifier, current.password, current.confirm)
            _uiState.update { state ->
                when (result) {
                    // The app moves on by itself once the account exists; do not keep the password around.
                    SetupResult.Created -> {
                        saveStore(AdminSetupValidator.normalizeName(current.businessName), current.details.trimmed(), current.logo)
                        state.copy(isSubmitting = false, password = "", confirm = "")
                    }
                    SetupResult.AlreadyInitialized -> state.copy(isSubmitting = false, password = "", confirm = "")
                    is SetupResult.Invalid -> state.copy(isSubmitting = false, errors = result.errors)
                    SetupResult.Failed -> state.copy(isSubmitting = false, saveFailed = true)
                }
            }
        }
    }

    private fun StoreSettings.trimmed() = copy(
        address = address.trim(),
        phone = phone.trim(),
        gstin = Gstin.normalize(gstin),
        fssai = Fssai.normalize(fssai),
        invoicePrefix = invoicePrefix.trim().uppercase(),
        creditNotePrefix = creditNotePrefix.trim().uppercase(),
        upiId = upiId.trim(),
        upiPayeeName = upiPayeeName.trim(),
        receipt = receipt.copy(footer = receipt.footer.trim()),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                AdminSetupViewModel(container.authRepository, container::rememberStore)
            }
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

data class ResumeUiState(
    val name: String,
    /** A finger was not recognised on the last try. */
    val failed: Boolean = false,
    /** The saved session can no longer be opened (the phone's fingerprints changed, or the account was turned off): sign in with the password. */
    val unavailable: Boolean = false,
)

/**
 * The screen shown when the app reopens and a fingerprint-protected session was saved. It never signs anyone in by itself:
 * the phone's fingerprint check has to release the saved key first, and the account is checked again before the app opens.
 */
class ResumeViewModel(
    private val auth: AuthRepository,
    private val vault: SavedSessionVault,
    name: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResumeUiState(name))
    val uiState: StateFlow<ResumeUiState> = _uiState.asStateFlow()

    /** The key to hand to the fingerprint check, or null (and the screen falls back to the password) when it cannot be used. */
    fun cipher(): javax.crypto.Cipher? {
        val cipher = vault.unlockCipher()
        if (cipher == null) _uiState.update { it.copy(unavailable = true) }
        return cipher
    }

    fun fingerprintAccepted(cipher: javax.crypto.Cipher) {
        val userId = vault.open(cipher)
        if (userId == null || !auth.resumeSession(userId)) {
            vault.clear()
            _uiState.update { it.copy(unavailable = true) }
        }
    }

    fun fingerprintRejected() = _uiState.update { it.copy(failed = true) }

    companion object {
        fun factory(name: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ResumeViewModel(container.authRepository, container.sessionVault, name)
            }
        }
    }
}
