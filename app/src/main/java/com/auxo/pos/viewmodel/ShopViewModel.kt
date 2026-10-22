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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    /** Today's metal rates, per gram. */
    val metalRates: com.auxo.pos.domain.jewellery.MetalRates = com.auxo.pos.domain.jewellery.MetalRates(),
    /** How many jewellery items the last price update changed; null until one has run. */
    val repricedCount: Int? = null,
)

class ShopViewModel(
    private val settings: SettingsRepository,
    private val shop: ShopPreferences,
    private val inventory: com.auxo.pos.data.repository.InventoryRepository? = null,
) : ViewModel() {

    private val repriced = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ShopUiState> = combine(
        settings.observeSettings().map { it.storeName.trim().ifEmpty { null } }.catch { emit(null) },
        shop.restaurantMenu,
        shop.suppliers,
        shop.metalRates,
        repriced,
    ) { name, menu, suppliers, rates, repricedCount ->
        ShopUiState(
            shopName = name,
            restaurantMenu = menu,
            suppliers = suppliers,
            metalRates = rates,
            repricedCount = repricedCount,
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ShopUiState(restaurantMenu = shop.restaurantMenu.value, suppliers = shop.suppliers.value),
        )

    fun onSuppliersChange(enabled: Boolean) = shop.setSuppliers(enabled)

    /**
     * Saves today's rates, typed in rupees per gram ("" clears a metal), and, when [updatePrices] is set, prices every
     * piece of jewellery from them. A text that is not an amount leaves that metal's rate as it was.
     */
    fun saveMetalRates(texts: Map<com.auxo.pos.domain.jewellery.JewelleryMetal, String>, updatePrices: Boolean) {
        var rates = shop.metalRates.value
        texts.forEach { (metal, text) ->
            when {
                text.isBlank() -> rates = rates.with(metal, null)
                else -> com.auxo.pos.domain.model.Money.parseOrNull(text)?.let { rates = rates.with(metal, it) }
            }
        }
        shop.setMetalRates(rates)
        if (!updatePrices || inventory == null) return
        viewModelScope.launch {
            val includeTax = settings.observeSettings().first().pricesIncludeTax
            var changed = 0
            inventory.observeProducts().first().forEach { product ->
                val variants = com.auxo.pos.domain.jewellery.JewelleryPricing.repriced(product, rates, includeTax) ?: return@forEach
                if (variants != product.variants) {
                    inventory.saveProduct(product.copy(variants = variants))
                    changed++
                }
            }
            repriced.value = changed
        }
    }

    fun dismissRepriced() {
        repriced.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ShopViewModel(container.settingsRepository, container.shopPreferences, container.inventoryRepository)
            }
        }
    }
}
