// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.shop

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/** Which parts of the app this shop uses. Everything optional starts switched off. */
interface ShopPreferences {
    /** True when the shop sells from a restaurant menu (dishes). Off until the shop turns it on. */
    val restaurantMenu: StateFlow<Boolean>

    fun setRestaurantMenu(enabled: Boolean)

    /**
     * True when the shop buys from suppliers: Suppliers, Purchase orders, Receive stock and every
     * supplier detail and figure are shown. Off hides all of it; nothing is deleted. Off unless the shop turns it on.
     */
    val suppliers: StateFlow<Boolean>

    fun setSuppliers(enabled: Boolean)

    /** False until the admin has confirmed which categories the shop sells in (first-time setup). */
    val categorySetupDone: StateFlow<Boolean>

    fun setCategorySetupDone(done: Boolean)

    /**
     * True once the first-launch introduction (splash and onboarding) has been shown, so it never appears again. Read and
     * written before anyone has signed in, so it is not a business setting and needs no permission.
     */
    val onboardingSeen: StateFlow<Boolean>

    fun setOnboardingSeen(seen: Boolean)

    /** Today's rate per gram for each metal, as the shop last entered them. Empty until the shop enters some. */
    val metalRates: StateFlow<com.auxo.pos.domain.jewellery.MetalRates>
        get() = MutableStateFlow(com.auxo.pos.domain.jewellery.MetalRates())

    fun setMetalRates(rates: com.auxo.pos.domain.jewellery.MetalRates) = Unit

    /** How many tables the restaurant has; parked restaurant bills are filed against one of them. */
    val tableCount: StateFlow<Int>
        get() = MutableStateFlow(com.auxo.pos.domain.billing.HeldBills.DEFAULT_TABLES)

    fun setTableCount(count: Int) = Unit

    /** Days a customer may owe before new credit is refused to them; 0 means credit is never held. */
    val creditHoldDays: StateFlow<Int>
        get() = MutableStateFlow(0)

    fun setCreditHoldDays(days: Int) = Unit

    /** Loyalty points given when a customer clears everything they owe within [ON_TIME_DAYS] days; 0 means no bonus. */
    val onTimeBonusPoints: StateFlow<Int>
        get() = MutableStateFlow(0)

    fun setOnTimeBonusPoints(points: Int) = Unit

    companion object {
        /** How soon after the money was first owed a full payment still counts as on time. */
        const val ON_TIME_DAYS = 30
        const val MAX_HOLD_DAYS = 365
        const val MAX_BONUS_POINTS = 10_000
    }
}

/** Saves the shop's choices on the device, so they are still there after the app closes. */
class SharedPrefsShopPreferences(private val prefs: SharedPreferences) : ShopPreferences {

    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val state = MutableStateFlow(prefs.getBoolean(KEY_RESTAURANT_MENU, false))
    override val restaurantMenu: StateFlow<Boolean> = state.asStateFlow()

    override fun setRestaurantMenu(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RESTAURANT_MENU, enabled) }
        state.value = enabled
    }

    private val suppliersState = MutableStateFlow(prefs.getBoolean(KEY_SUPPLIERS, false))
    override val suppliers: StateFlow<Boolean> = suppliersState.asStateFlow()

    override fun setSuppliers(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SUPPLIERS, enabled) }
        suppliersState.value = enabled
    }

    private val categorySetupState = MutableStateFlow(prefs.getBoolean(KEY_CATEGORY_SETUP, false))
    override val categorySetupDone: StateFlow<Boolean> = categorySetupState.asStateFlow()

    override fun setCategorySetupDone(done: Boolean) {
        prefs.edit(commit = true) { putBoolean(KEY_CATEGORY_SETUP, done) }
        categorySetupState.value = done
    }

    private val onboardingState = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_SEEN, false))
    override val onboardingSeen: StateFlow<Boolean> = onboardingState.asStateFlow()

    override fun setOnboardingSeen(seen: Boolean) {
        prefs.edit(commit = true) { putBoolean(KEY_ONBOARDING_SEEN, seen) }
        onboardingState.value = seen
    }

    private val metalRatesState = MutableStateFlow(com.auxo.pos.domain.jewellery.MetalRates.decode(prefs.getString(KEY_METAL_RATES, null)))
    override val metalRates: StateFlow<com.auxo.pos.domain.jewellery.MetalRates> = metalRatesState.asStateFlow()

    override fun setMetalRates(rates: com.auxo.pos.domain.jewellery.MetalRates) {
        prefs.edit { putString(KEY_METAL_RATES, rates.encode()) }
        metalRatesState.value = rates
    }

    private val tableCountState = MutableStateFlow(prefs.getInt(KEY_TABLE_COUNT, com.auxo.pos.domain.billing.HeldBills.DEFAULT_TABLES))
    override val tableCount: StateFlow<Int> = tableCountState.asStateFlow()

    override fun setTableCount(count: Int) {
        val clean = count.coerceIn(1, com.auxo.pos.domain.billing.HeldBills.MAX_TABLES)
        prefs.edit { putInt(KEY_TABLE_COUNT, clean) }
        tableCountState.value = clean
    }

    private val creditHoldState = MutableStateFlow(prefs.getInt(KEY_CREDIT_HOLD_DAYS, 0))
    override val creditHoldDays: StateFlow<Int> = creditHoldState.asStateFlow()

    override fun setCreditHoldDays(days: Int) {
        val clean = days.coerceIn(0, ShopPreferences.MAX_HOLD_DAYS)
        prefs.edit { putInt(KEY_CREDIT_HOLD_DAYS, clean) }
        creditHoldState.value = clean
    }

    private val onTimeBonusState = MutableStateFlow(prefs.getInt(KEY_ON_TIME_BONUS, 0))
    override val onTimeBonusPoints: StateFlow<Int> = onTimeBonusState.asStateFlow()

    override fun setOnTimeBonusPoints(points: Int) {
        val clean = points.coerceIn(0, ShopPreferences.MAX_BONUS_POINTS)
        prefs.edit { putInt(KEY_ON_TIME_BONUS, clean) }
        onTimeBonusState.value = clean
    }

    companion object {
        const val KEY_CREDIT_HOLD_DAYS = "credit_hold_days"
        const val KEY_ON_TIME_BONUS = "on_time_bonus_points"
        const val KEY_TABLE_COUNT = "table_count"
        const val KEY_METAL_RATES = "metal_rates"
        const val FILE = "shop"
        const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        const val KEY_CATEGORY_SETUP = "category_setup_done"
        const val KEY_RESTAURANT_MENU = "restaurant_menu"
        const val KEY_SUPPLIERS = "suppliers"
    }
}
