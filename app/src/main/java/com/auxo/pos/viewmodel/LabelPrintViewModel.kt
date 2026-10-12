package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.label.LabelSheetLayout
import com.auxo.pos.core.label.LabelTemplate
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.domain.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LabelPrintUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val product: Product? = null,
    /** Copies to print per variant id. */
    val copies: Map<Long, Int> = emptyMap(),
    val template: LabelTemplate = LabelTemplate.A4_24,
    val showMrp: Boolean = true,
) {
    val totalLabels: Int get() = copies.values.sum()
    val pageCount: Int get() = LabelSheetLayout.pageCount(template, totalLabels)
}

class LabelPrintViewModel(
    private val productId: Long,
    private val repository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LabelPrintUiState())
    val uiState: StateFlow<LabelPrintUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val product = runCatching { repository.getProduct(productId) }.getOrNull()
            _uiState.update { state ->
                if (product == null) {
                    state.copy(isLoading = false, loadFailed = true)
                } else {
                    state.copy(
                        isLoading = false,
                        product = product,
                        copies = product.variants.associate { it.id to 1 },
                    )
                }
            }
        }
    }

    fun setCopies(variantId: Long, count: Int) =
        _uiState.update { it.copy(copies = it.copies + (variantId to count.coerceIn(0, MAX_COPIES))) }

    /** One label for every unit in stock. */
    fun matchStock() = _uiState.update { state ->
        val product = state.product ?: return@update state
        state.copy(copies = product.variants.associate { it.id to it.stockQuantity.coerceIn(0, MAX_COPIES) })
    }

    fun setTemplate(template: LabelTemplate) = _uiState.update { it.copy(template = template) }

    fun setShowMrp(show: Boolean) = _uiState.update { it.copy(showMrp = show) }

    companion object {
        const val ARG_PRODUCT_ID = "productId"
        const val MAX_COPIES = 500

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                LabelPrintViewModel(
                    productId = createSavedStateHandle().get<Long>(ARG_PRODUCT_ID) ?: 0L,
                    repository = container.inventoryRepository,
                )
            }
        }
    }
}
