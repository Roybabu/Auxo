package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.shop.ShopPreferences
import com.auxo.pos.data.repository.CategoryRepository
import com.auxo.pos.data.repository.CategoryResult
import com.auxo.pos.domain.model.ShopCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategorySetupUiState(
    val categories: List<ShopCategory> = emptyList(),
    /** Ids of the categories ticked. Starts as the ones currently on. */
    val selected: Set<Long> = emptySet(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    /** Nothing that can be sold is ticked. */
    val needsOne: Boolean = false,
    val failed: Boolean = false,
)

/** The first-time step where the admin ticks the categories the shop sells in. */
class CategorySetupViewModel(
    private val repository: CategoryRepository,
    private val shop: ShopPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategorySetupUiState())
    val uiState: StateFlow<CategorySetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val categories = repository.getCategories()
                _uiState.update { it.copy(categories = categories, selected = categories.filter { c -> c.enabled }.map { c -> c.id }.toSet(), loaded = true) }
            } catch (e: com.auxo.pos.core.auth.PermissionDeniedException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loaded = true, failed = true) }
            }
        }
    }

    fun onToggle(id: Long) = _uiState.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id, needsOne = false, failed = false)
    }

    fun onConfirm() {
        val state = _uiState.value
        if (state.saving) return
        _uiState.update { it.copy(saving = true, needsOne = false, failed = false) }
        viewModelScope.launch {
            val result = try {
                repository.applySetup(state.selected)
            } catch (e: com.auxo.pos.core.auth.PermissionDeniedException) {
                throw e
            } catch (e: Exception) {
                null
            }
            when (result) {
                is CategoryResult.Done -> shop.setCategorySetupDone(true)
                CategoryResult.LastOne -> _uiState.update { it.copy(saving = false, needsOne = true) }
                else -> _uiState.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                CategorySetupViewModel(container.categoryRepository, container.shopPreferences)
            }
        }
    }
}
