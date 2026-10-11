package com.invision.pos.viewmodel

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
import com.invision.pos.core.shop.SharedPrefsShopPreferences
import com.invision.pos.core.shop.ShopPreferences
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.usecase.CheckoutUseCase
import com.invision.pos.testutil.FakeBillingRepository
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.FakeMenuRepository
import com.invision.pos.testutil.FakeSettingsRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import com.invision.pos.testutil.keepActive
import com.invision.pos.testutil.record
import com.invision.pos.ui.LocalSuppliersEnabled
import com.invision.pos.ui.screens.more.MoreActions
import com.invision.pos.ui.screens.more.MoreScreen
import com.invision.pos.ui.screens.more.ShopSettingsContent
import com.invision.pos.ui.theme.InvisionTheme
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

private class FakeShopPreferences(initial: Boolean = false, suppliersOn: Boolean = true) : ShopPreferences {
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
}

/** The restaurant menu starts off, and turning it on or off changes what the shop is offered. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class ShopSettingsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val dosa = MenuItem(id = 5, name = "Masala Dosa", section = "Breakfast", price = Money(12_000))

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
    fun shopViewModel_showsTheShopName_andTheSwitch() = runTest {
        val prefs = FakeShopPreferences()
        val viewModel = ShopViewModel(FakeSettingsRepository(TestData.settings.copy(storeName = "Das Sweets")), prefs)
        val seen = record(viewModel.uiState)
        assertEquals("Das Sweets", viewModel.uiState.value.shopName)
        assertFalse(viewModel.uiState.value.restaurantMenu)

        viewModel.onRestaurantMenuChange(true)
        assertTrue(viewModel.uiState.value.restaurantMenu)
        assertTrue(seen.isNotEmpty())
    }

    @Test
    fun billing_offersDishesOnlyWhileTheMenuIsOn() = runTest {
        val inventory = FakeInventoryRepository(listOf(TestData.tShirt()))
        val settings = FakeSettingsRepository()
        val prefs = FakeShopPreferences(initial = false)
        val viewModel = BillingViewModel(
            inventory = inventory,
            settingsRepository = settings,
            checkout = CheckoutUseCase(FakeBillingRepository(inventory), settings, TestData.clock),
            menuRepository = FakeMenuRepository(listOf(dosa)),
            clock = TestData.clock,
            shop = prefs,
        )
        keepActive(viewModel.uiState)

        assertTrue(viewModel.uiState.value.menu.isEmpty())
        viewModel.onSearchTextChange("dosa")
        assertTrue(viewModel.uiState.value.menuResults.isEmpty())

        prefs.setRestaurantMenu(true)
        assertEquals(listOf("Masala Dosa"), viewModel.uiState.value.menu.map { it.name })
        assertEquals(listOf("Masala Dosa"), viewModel.uiState.value.menuResults.map { it.name })

        prefs.setRestaurantMenu(false)
        assertTrue(viewModel.uiState.value.menu.isEmpty())
        assertTrue(viewModel.uiState.value.menuResults.isEmpty())
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
            InvisionTheme {
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
            InvisionTheme {
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
        val on = com.invision.pos.domain.reports.Reports.lowStock(products, emptyList())
        val off = com.invision.pos.domain.reports.Reports.lowStock(products, emptyList(), withSupplier = false)
        assertEquals(on.columns.size - 1, off.columns.size)
        assertFalse(off.columns.any { it.title == "Supplier" })
        assertTrue(off.rows.all { it.size == off.columns.size })
    }

    @Test
    fun shopSettings_switchTurnsTheMenuOn() {
        val prefs = FakeShopPreferences()
        composeRule.setContent {
            InvisionTheme {
                val on by prefs.restaurantMenu.collectAsState()
                ShopSettingsContent(
                    state = ShopUiState(shopName = "Das Sweets", restaurantMenu = on),
                    onBack = {},
                    onRestaurantMenuChange = prefs::setRestaurantMenu,
                )
            }
        }
        composeRule.onNodeWithTag("restaurant_menu_switch").assertIsOff()
        composeRule.onNodeWithTag("restaurant_menu_switch").performClick()
        composeRule.onNodeWithTag("restaurant_menu_switch").assertIsOn()
        assertTrue(prefs.restaurantMenu.value)
    }
}
