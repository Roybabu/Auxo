// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.PurchasingRepository
import com.auxo.pos.domain.contacts.ContactError
import com.auxo.pos.domain.contacts.ContactField
import com.auxo.pos.domain.contacts.SupplierForm
import com.auxo.pos.domain.contacts.SupplierValidation
import com.auxo.pos.domain.contacts.SupplierValidator
import com.auxo.pos.domain.model.Supplier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Supplier list

data class SupplierListUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val query: String = "",
    /** Also list suppliers that were hidden. */
    val showHidden: Boolean = false,
    val suppliers: List<Supplier> = emptyList(),
    val activeCount: Int = 0,
    val hiddenCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SupplierListViewModel(private val purchasing: PurchasingRepository) : ViewModel() {

    private val filters = MutableStateFlow(false to "")
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<SupplierListUiState> = reload
        .flatMapLatest {
            combine(purchasing.observeSuppliers(), filters) { suppliers, (showHidden, query) ->
                SupplierListUiState(
                    isLoading = false,
                    query = query,
                    showHidden = showHidden,
                    suppliers = filterSuppliers(suppliers, showHidden, query),
                    activeCount = suppliers.count { it.active },
                    hiddenCount = suppliers.count { !it.active },
                )
            }.catch { emit(SupplierListUiState(isLoading = false, loadFailed = true)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SupplierListUiState())

    fun onQueryChange(query: String) = filters.update { it.copy(second = query.take(40)) }

    fun onShowHiddenChange(show: Boolean) = filters.update { it.copy(first = show) }

    fun retry() = reload.update { it + 1 }

    companion object {
        /** Name, contact, phone or GSTIN matches; hidden suppliers only when asked for. */
        fun filterSuppliers(suppliers: List<Supplier>, showHidden: Boolean, query: String): List<Supplier> {
            val text = query.trim()
            val digits = text.filter { it.isDigit() }
            return suppliers.filter { supplier ->
                (showHidden || supplier.active) && (
                    text.isEmpty() ||
                        supplier.name.contains(text, ignoreCase = true) ||
                        supplier.contactPerson?.contains(text, ignoreCase = true) == true ||
                        (digits.length >= 3 && supplier.phone?.filter { it.isDigit() }?.contains(digits) == true) ||
                        supplier.gstin?.contains(text, ignoreCase = true) == true
                    )
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SupplierListViewModel(appContainer().purchasingRepository) }
        }
    }
}

// Supplier editor

data class SupplierEditorUiState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isNew: Boolean = true,
    /** False once the supplier has been hidden. */
    val active: Boolean = true,
    val form: SupplierForm = SupplierForm(),
    val errors: Map<ContactField, ContactError> = emptyMap(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
) {
    val visibleErrors: Map<ContactField, ContactError> get() = if (showErrors) errors else emptyMap()
}

sealed interface SupplierEditorEvent {
    data class Saved(val supplierId: Long) : SupplierEditorEvent

    /** The supplier was hidden ([active] false) or shown again. */
    data class ActiveChanged(val active: Boolean) : SupplierEditorEvent
    data class Invalid(val count: Int) : SupplierEditorEvent
    data object Failed : SupplierEditorEvent
}

class SupplierEditorViewModel(
    private val supplierId: Long,
    private val purchasing: PurchasingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupplierEditorUiState(isLoading = supplierId != 0L, isNew = supplierId == 0L))
    val uiState: StateFlow<SupplierEditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<SupplierEditorEvent>(Channel.BUFFERED)
    val events: Flow<SupplierEditorEvent> = eventChannel.receiveAsFlow()

    private var existing: Supplier? = null

    init {
        if (supplierId != 0L) load() else setForm(SupplierForm())
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val supplier = runCatching { purchasing.getSupplier(supplierId) }.getOrNull()
            existing = supplier
            if (supplier == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, active = supplier.active) }
                setForm(SupplierForm.from(supplier))
            }
        }
    }

    private fun setForm(form: SupplierForm) {
        val errors = when (val result = SupplierValidator.validate(form, existing)) {
            is SupplierValidation.Invalid -> result.errors
            is SupplierValidation.Valid -> emptyMap()
        }
        _uiState.update { it.copy(form = form, errors = errors) }
    }

    fun onFormChange(form: SupplierForm) = setForm(form)

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.loadFailed) return
        val supplier = when (val result = SupplierValidator.validate(state.form, existing)) {
            is SupplierValidation.Invalid -> {
                _uiState.update { it.copy(errors = result.errors, showErrors = true) }
                eventChannel.trySend(SupplierEditorEvent.Invalid(result.errors.size))
                return
            }
            is SupplierValidation.Valid -> result.supplier.copy(active = state.active)
        }
        _uiState.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            val id = runCatching { purchasing.saveSupplier(supplier) }.getOrNull()
            _uiState.update { it.copy(isSaving = false) }
            if (id == null) {
                eventChannel.send(SupplierEditorEvent.Failed)
            } else {
                existing = supplier.copy(id = id)
                eventChannel.send(SupplierEditorEvent.Saved(id))
            }
        }
    }

    /**
     * Hides the supplier from pickers, or shows it again. Past orders and receipts
     * keep their supplier. Only for saved suppliers.
     */
    fun setActive(active: Boolean) {
        val saved = existing ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val ok = runCatching { purchasing.saveSupplier(saved.copy(active = active)) }.isSuccess
            if (ok) {
                existing = saved.copy(active = active)
                _uiState.update { it.copy(isSaving = false, active = active) }
                eventChannel.send(SupplierEditorEvent.ActiveChanged(active))
            } else {
                _uiState.update { it.copy(isSaving = false) }
                eventChannel.send(SupplierEditorEvent.Failed)
            }
        }
    }

    companion object {
        const val ARG_SUPPLIER_ID = "supplierId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SupplierEditorViewModel(
                    supplierId = createSavedStateHandle().get<Long>(ARG_SUPPLIER_ID) ?: 0L,
                    purchasing = appContainer().purchasingRepository,
                )
            }
        }
    }
}
