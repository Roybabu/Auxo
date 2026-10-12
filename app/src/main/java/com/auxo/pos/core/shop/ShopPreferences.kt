package com.auxo.pos.core.shop

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which parts of the app this shop uses. Everything optional starts switched off. */
interface ShopPreferences {
    /** True when the shop sells from a restaurant menu (dishes). Off until the shop turns it on. */
    val restaurantMenu: StateFlow<Boolean>

    fun setRestaurantMenu(enabled: Boolean)

    /**
     * True when the shop buys from suppliers: Suppliers, Purchase orders, Receive stock and every
     * supplier detail and figure are shown. Off hides all of it; nothing is deleted. On unless the shop turns it off.
     */
    val suppliers: StateFlow<Boolean>

    fun setSuppliers(enabled: Boolean)
}

/** Saves the shop's choices on the device, so they are still there after the app closes. */
class SharedPrefsShopPreferences(private val prefs: SharedPreferences) : ShopPreferences {

    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val state = MutableStateFlow(prefs.getBoolean(KEY_RESTAURANT_MENU, false))
    override val restaurantMenu: StateFlow<Boolean> = state.asStateFlow()

    override fun setRestaurantMenu(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESTAURANT_MENU, enabled).apply()
        state.value = enabled
    }

    private val suppliersState = MutableStateFlow(prefs.getBoolean(KEY_SUPPLIERS, true))
    override val suppliers: StateFlow<Boolean> = suppliersState.asStateFlow()

    override fun setSuppliers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUPPLIERS, enabled).apply()
        suppliersState.value = enabled
    }

    companion object {
        const val FILE = "shop"
        const val KEY_RESTAURANT_MENU = "restaurant_menu"
        const val KEY_SUPPLIERS = "suppliers"
    }
}
