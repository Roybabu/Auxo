package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.shop.ShopPreferences
import com.auxo.pos.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the More page and Shop settings show about the shop. */
data class ShopUiState(
    /** The shop's name from the store details, or null until it has loaded. */
    val shopName: String? = null,
    val restaurantMenu: Boolean = false,
    /** Suppliers, purchase orders and receiving stock are shown. */
    val suppliers: Boolean = true,
)

class ShopViewModel(
    settings: SettingsRepository,
    private val shop: ShopPreferences,
) : ViewModel() {

    val uiState: StateFlow<ShopUiState> = combine(
        settings.observeSettings().map { it.storeName.trim().ifEmpty { null } }.catch { emit(null) },
        shop.restaurantMenu,
        shop.suppliers,
    ) { name, menu, suppliers -> ShopUiState(shopName = name, restaurantMenu = menu, suppliers = suppliers) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ShopUiState(restaurantMenu = shop.restaurantMenu.value, suppliers = shop.suppliers.value),
        )

    fun onSuppliersChange(enabled: Boolean) = shop.setSuppliers(enabled)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ShopViewModel(container.settingsRepository, container.shopPreferences)
            }
        }
    }
}
