// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.auxo.pos.core.shop.SharedPrefsShopPreferences
import com.auxo.pos.core.shop.ShopPreferences
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.usecase.CheckoutUseCase
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import com.auxo.pos.testutil.keepActive
import com.auxo.pos.testutil.record
import com.auxo.pos.ui.LocalSuppliersEnabled
import com.auxo.pos.ui.screens.more.MoreActions
import com.auxo.pos.ui.screens.more.MoreScreen
import com.auxo.pos.ui.screens.more.ShopSettingsContent
import com.auxo.pos.ui.theme.AuxoTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal class FakeShopPreferences(initial: Boolean = false, suppliersOn: Boolean = true) : ShopPreferences {
    private val state = MutableStateFlow(initial)
    override val restaurantMenu: StateFlow<Boolean> = state
    override fun setRestaurantMenu(enabled: Boolean) {
        state.value = enabled
    }

    private val suppliersState = MutableStateFlow(suppliersOn)
    override val suppliers: StateFlow<Boolean> = suppliersState
    override fun setSuppliers(enabled: Boolean) {
        suppliersState.value = enabled
    }

    private val setupState = MutableStateFlow(false)
    override val categorySetupDone: StateFlow<Boolean> = setupState
    override fun setCategorySetupDone(done: Boolean) {
        setupState.value = done
    }

    private val onboardingState = MutableStateFlow(false)
    override val onboardingSeen: StateFlow<Boolean> = onboardingState
    override fun setOnboardingSeen(seen: Boolean) {
        onboardingState.value = seen
    }
}

/** The restaurant menu starts off, and turning it on or off changes what the shop is offered. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class ShopSettingsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val dosa = TestData.dish(id = 5, name = "Masala Dosa", price = Money(12_000))

    @Test
    fun theRestaurantMenu_isOffUntilTheShopTurnsItOn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(SharedPrefsShopPreferences.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = SharedPrefsShopPreferences(context)
        assertFalse(prefs.restaurantMenu.value)

        prefs.setRestaurantMenu(true)
        assertTrue(prefs.restaurantMenu.value)
        // It is still on after the app is opened again.
        assertTrue(SharedPrefsShopPreferences(context).restaurantMenu.value)
    }

    @Test
    fun theIntroduction_isUnseenUntilFinished_andStaysSeenAfterARestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(SharedPrefsShopPreferences.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = SharedPrefsShopPreferences(context)
        assertFalse(prefs.onboardingSeen.value)

        prefs.setOnboardingSeen(true)
        assertTrue(prefs.onboardingSeen.value)
        assertTrue(SharedPrefsShopPreferences(context).onboardingSeen.value)
    }

    @Test
    fun suppliers_areOnUntilTheShopTurnsThemOff_andStayOffAfterARestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(SharedPrefsShopPreferences.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = SharedPrefsShopPreferences(context)
        assertTrue(prefs.suppliers.value)

        prefs.setSuppliers(false)
        assertFalse(prefs.suppliers.value)
        assertFalse(SharedPrefsShopPreferences(context).suppliers.value)
        prefs.setSuppliers(true)
        assertTrue(SharedPrefsShopPreferences(context).suppliers.value)
    }

    @Test
    fun shopViewModel_showsTheShopName_andFollowsRestaurantMode() = runTest {
        val prefs = FakeShopPreferences()
        val viewModel = ShopViewModel(FakeSettingsRepository(TestData.settings.copy(storeName = "Das Sweets")), prefs)
        val seen = record(viewModel.uiState)
        assertEquals("Das Sweets", viewModel.uiState.value.shopName)
        assertFalse(viewModel.uiState.value.restaurantMenu)

        prefs.setRestaurantMenu(true)
        assertTrue(viewModel.uiState.value.restaurantMenu)
        assertTrue(seen.isNotEmpty())
    }

    @Test
    fun billing_offersTheRestaurantQuickListOnlyWhileRestaurantModeIsOn() = runTest {
        val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), dosa))
        val settings = FakeSettingsRepository()
        val prefs = FakeShopPreferences(initial = false)
        val viewModel = BillingViewModel(
            inventory = inventory,
            settingsRepository = settings,
            checkout = CheckoutUseCase(FakeBillingRepository(inventory), settings, TestData.clock),
            clock = TestData.clock,
            shop = prefs,
        )
        keepActive(viewModel.uiState)

        assertTrue(viewModel.uiState.value.quickItems.isEmpty())

        prefs.setRestaurantMenu(true)
        assertEquals(listOf("Masala Dosa"), viewModel.uiState.value.quickItems.map { it.product.name })
        assertEquals(listOf("Breakfast"), viewModel.uiState.value.quickSections)

        prefs.setRestaurantMenu(false)
        assertTrue(viewModel.uiState.value.quickItems.isEmpty())
    }

    @Test
    fun billing_canAddAnItemThatIsNotInTheList() = runTest {
        val inventory = FakeInventoryRepository(listOf(TestData.tShirt()))
        val settings = FakeSettingsRepository()
        val viewModel = BillingViewModel(
            inventory = inventory,
            settingsRepository = settings,
            checkout = CheckoutUseCase(FakeBillingRepository(inventory), settings, TestData.clock),
            clock = TestData.clock,
        )
        keepActive(viewModel.uiState)

        assertFalse(viewModel.addCustomItem("", "10", "1", 0))
        assertTrue(viewModel.uiState.value.cart.isEmpty)

        assertTrue(viewModel.addCustomItem("Alteration", "150", "1", 1_800))
        assertTrue(viewModel.addCustomItem("Alteration", "150", "1", 1_800))
        val lines = viewModel.uiState.value.cart.lines
        assertEquals(2, lines.size) // each one-off item is its own line
        assertTrue(lines.all { it.isCustom })
        assertTrue(viewModel.uiState.value.check.canCheckout)

        viewModel.increment(lines[0].variantId)
        assertEquals(2, viewModel.uiState.value.cart.lines[0].quantity)
        viewModel.removeLine(lines[1].variantId)
        assertEquals(1, viewModel.uiState.value.cart.lines.size)
    }

    @Test
    fun morePage_showsTheShopName_andHidesTheMenuUntilItIsOn() {
        var opened = false
        composeRule.setContent {
            AuxoTheme {
                MoreScreen(
                    shop = ShopUiState(shopName = "Das Sweets", restaurantMenu = false),
                    actions = object : MoreActions {
                        override fun onOpenShopSettings() {
                            opened = true
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithText("Das Sweets").assertIsDisplayed()
        composeRule.onNodeWithText("Shop settings").assertIsDisplayed()
        composeRule.onNodeWithText("Restaurant").assertDoesNotExist()
        composeRule.onNodeWithText("Restaurant menu").assertDoesNotExist()
        composeRule.onNodeWithText("Dishes and prices").assertDoesNotExist()
        composeRule.onNodeWithTag("more_shop_settings").performClick()
        assertTrue(opened)
    }

    @Test
    fun moreAndInsights_leaveOutSuppliers_whenTheyAreOff() {
        composeRule.setContent {
            AuxoTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalSuppliersEnabled provides false) {
                    MoreScreen(shop = ShopUiState(shopName = "Das Sweets", suppliers = false), actions = object : MoreActions {})
                }
            }
        }
        composeRule.onNodeWithText("Stock alerts").assertIsDisplayed()
        composeRule.onNodeWithText("Suppliers").assertDoesNotExist()
        composeRule.onNodeWithText("Purchase orders").assertDoesNotExist()
        composeRule.onNodeWithText("Receive stock").assertDoesNotExist()
    }

    @Test
    fun lowStockReport_hasNoSupplierColumn_whenSuppliersAreOff() {
        val products = listOf(TestData.tShirt())
        val on = com.auxo.pos.domain.reports.Reports.lowStock(products, emptyList())
        val off = com.auxo.pos.domain.reports.Reports.lowStock(products, emptyList(), withSupplier = false)
        assertEquals(on.columns.size - 1, off.columns.size)
        assertFalse(off.columns.any { it.title == "Supplier" })
        assertTrue(off.rows.all { it.size == off.columns.size })
    }

    @Test
    fun shopSettings_opensProductCategories_andHasNoRestaurantSwitch() {
        var opened = 0
        composeRule.setContent {
            AuxoTheme {
                ShopSettingsContent(
                    state = ShopUiState(shopName = "Das Sweets"),
                    onBack = {},
                    onOpenCategories = { opened++ },
                )
            }
        }
        composeRule.onNodeWithTag("shop_categories").assertIsDisplayed()
        composeRule.onNodeWithTag("shop_categories").performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithTag("restaurant_menu_switch").assertDoesNotExist()
    }
}
