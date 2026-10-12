package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.auth.AdminSetupValidator
import com.auxo.pos.core.auth.AuthRepository
import com.auxo.pos.core.auth.ManagedUser
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.PermissionOptions
import com.auxo.pos.core.auth.SetupError
import com.auxo.pos.core.auth.SetupField
import com.auxo.pos.core.auth.UserChangeResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The fields for a new employee: the sign-in details, and which extras this person is given. There is no role choice. */
data class EmployeeForm(
    val name: String = "",
    val identifier: String = "",
    val password: String = "",
    val confirm: String = "",
    val granted: Set<Permission> = emptySet(),
    val errors: Map<SetupField, SetupError> = emptyMap(),
    val isSaving: Boolean = false,
)

/** The extras being chosen for an existing employee. */
data class AccessForm(
    val user: ManagedUser,
    val granted: Set<Permission>,
    val isSaving: Boolean = false,
)

/** A new password chosen by the Super Admin for one employee. */
data class ResetForm(
    val user: ManagedUser,
    val password: String = "",
    val confirm: String = "",
    val errors: Map<SetupField, SetupError> = emptyMap(),
    val isSaving: Boolean = false,
)

data class UserManagementUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val users: List<ManagedUser> = emptyList(),
    val createForm: EmployeeForm? = null,
    val resetForm: ResetForm? = null,
    val accessForm: AccessForm? = null,
    /** An employee the Super Admin is about to switch off, waiting for confirmation. */
    val confirmDeactivate: ManagedUser? = null,
)

sealed interface UserManagementEvent {
    data class Created(val name: String) : UserManagementEvent
    data class Activated(val name: String) : UserManagementEvent
    data class Deactivated(val name: String) : UserManagementEvent
    data class PasswordReset(val name: String) : UserManagementEvent
    data class AccessChanged(val name: String) : UserManagementEvent
    data object Failed : UserManagementEvent
}

class UserManagementViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<UserManagementEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val users = auth.listUsers()
            _uiState.update {
                if (users == null) it.copy(isLoading = false, loadFailed = true) else it.copy(isLoading = false, loadFailed = false, users = users)
            }
        }
    }

    // Adding an employee

    fun openCreate() = _uiState.update { it.copy(createForm = EmployeeForm()) }

    fun closeCreate() = _uiState.update { it.copy(createForm = null) }

    fun onNameChange(value: String) = editCreate { it.copy(name = value.take(NAME_LIMIT), errors = it.errors - SetupField.NAME) }

    fun onIdentifierChange(value: String) =
        editCreate { it.copy(identifier = value.take(AdminSetupValidator.MAX_IDENTIFIER_LENGTH + 20), errors = it.errors - SetupField.IDENTIFIER) }

    fun onPasswordChange(value: String) = editCreate { it.copy(password = value.take(PASSWORD_LIMIT), errors = it.errors - SetupField.PASSWORD) }

    fun onConfirmChange(value: String) = editCreate { it.copy(confirm = value.take(PASSWORD_LIMIT), errors = it.errors - SetupField.CONFIRM) }

    fun onCreatePermission(permission: Permission, on: Boolean) =
        editCreate { it.copy(granted = toggled(it.granted, permission, on)) }

    private fun editCreate(change: (EmployeeForm) -> EmployeeForm) = _uiState.update { state ->
        state.copy(createForm = state.createForm?.let(change))
    }

    fun submitCreate() {
        val form = _uiState.value.createForm ?: return
        if (form.isSaving) return
        _uiState.update { it.copy(createForm = form.copy(isSaving = true)) }
        viewModelScope.launch {
            when (val result = auth.createEmployee(form.name, form.identifier, form.password, form.confirm, form.granted)) {
                UserChangeResult.Done -> {
                    _uiState.update { it.copy(createForm = null) }
                    reloadUsers()
                    eventChannel.send(UserManagementEvent.Created(AdminSetupValidator.normalizeName(form.name)))
                }
                is UserChangeResult.Invalid ->
                    _uiState.update { it.copy(createForm = form.copy(errors = result.errors, isSaving = false)) }
                else -> {
                    _uiState.update { it.copy(createForm = form.copy(isSaving = false)) }
                    eventChannel.send(UserManagementEvent.Failed)
                }
            }
        }
    }

    // Switching an account on or off

    fun onActiveChange(user: ManagedUser, active: Boolean) {
        if (active) setActive(user, true) else _uiState.update { it.copy(confirmDeactivate = user) }
    }

    fun cancelDeactivate() = _uiState.update { it.copy(confirmDeactivate = null) }

    fun confirmDeactivate() {
        val user = _uiState.value.confirmDeactivate ?: return
        _uiState.update { it.copy(confirmDeactivate = null) }
        setActive(user, false)
    }

    private fun setActive(user: ManagedUser, active: Boolean) {
        viewModelScope.launch {
            if (auth.setEmployeeActive(user.id, active) == UserChangeResult.Done) {
                reloadUsers()
                eventChannel.send(if (active) UserManagementEvent.Activated(user.name) else UserManagementEvent.Deactivated(user.name))
            } else {
                eventChannel.send(UserManagementEvent.Failed)
            }
        }
    }

    // Choosing what an employee may do

    fun openAccess(user: ManagedUser) = _uiState.update { it.copy(accessForm = AccessForm(user, user.granted)) }

    fun closeAccess() = _uiState.update { it.copy(accessForm = null) }

    fun onAccessPermission(permission: Permission, on: Boolean) = _uiState.update { state ->
        state.copy(accessForm = state.accessForm?.let { it.copy(granted = toggled(it.granted, permission, on)) })
    }

    fun submitAccess() {
        val form = _uiState.value.accessForm ?: return
        if (form.isSaving) return
        _uiState.update { it.copy(accessForm = form.copy(isSaving = true)) }
        viewModelScope.launch {
            if (auth.setEmployeeAccess(form.user.id, form.granted) == UserChangeResult.Done) {
                _uiState.update { it.copy(accessForm = null) }
                reloadUsers()
                eventChannel.send(UserManagementEvent.AccessChanged(form.user.name))
            } else {
                _uiState.update { it.copy(accessForm = form.copy(isSaving = false)) }
                eventChannel.send(UserManagementEvent.Failed)
            }
        }
    }

    /** Switching an option on also switches on what it needs; switching one off also switches off what needs it. */
    private fun toggled(current: Set<Permission>, permission: Permission, on: Boolean): Set<Permission> =
        if (on) PermissionOptions.switchOn(current, permission) else PermissionOptions.switchOff(current, permission)

    // Resetting a password

    fun openReset(user: ManagedUser) = _uiState.update { it.copy(resetForm = ResetForm(user)) }

    fun closeReset() = _uiState.update { it.copy(resetForm = null) }

    fun onResetPasswordChange(value: String) =
        editReset { it.copy(password = value.take(PASSWORD_LIMIT), errors = it.errors - SetupField.PASSWORD) }

    fun onResetConfirmChange(value: String) =
        editReset { it.copy(confirm = value.take(PASSWORD_LIMIT), errors = it.errors - SetupField.CONFIRM) }

    private fun editReset(change: (ResetForm) -> ResetForm) = _uiState.update { state ->
        state.copy(resetForm = state.resetForm?.let(change))
    }

    fun submitReset() {
        val form = _uiState.value.resetForm ?: return
        if (form.isSaving) return
        _uiState.update { it.copy(resetForm = form.copy(isSaving = true)) }
        viewModelScope.launch {
            when (val result = auth.resetEmployeePassword(form.user.id, form.password, form.confirm)) {
                UserChangeResult.Done -> {
                    _uiState.update { it.copy(resetForm = null) }
                    eventChannel.send(UserManagementEvent.PasswordReset(form.user.name))
                }
                is UserChangeResult.Invalid ->
                    _uiState.update { it.copy(resetForm = form.copy(errors = result.errors, isSaving = false)) }
                else -> {
                    _uiState.update { it.copy(resetForm = form.copy(isSaving = false)) }
                    eventChannel.send(UserManagementEvent.Failed)
                }
            }
        }
    }

    private suspend fun reloadUsers() {
        auth.listUsers()?.let { users -> _uiState.update { it.copy(users = users) } }
    }

    companion object {
        private const val NAME_LIMIT = AdminSetupValidator.MAX_NAME_LENGTH + 20
        private const val PASSWORD_LIMIT = AdminSetupValidator.MAX_PASSWORD_LENGTH + 20

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { UserManagementViewModel(appContainer().authRepository) }
        }
    }
}
