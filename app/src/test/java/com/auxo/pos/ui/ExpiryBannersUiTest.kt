package com.auxo.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.format.MoneyFormatter
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartValidator
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.inventory.InventorySummaryCalculator
import com.auxo.pos.domain.inventory.StockFilter
import com.auxo.pos.domain.model.StoreSettings
import com.auxo.pos.testutil.TestData
import com.auxo.pos.ui.screens.billing.BillingActions
import com.auxo.pos.ui.screens.billing.BillingSettingsContent
import com.auxo.pos.ui.screens.billing.CartContent
import com.auxo.pos.ui.screens.home.HomeContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.BillingSettingsUiState
import com.auxo.pos.viewmodel.BillingUiState
import com.auxo.pos.viewmodel.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class ExpiryBannersUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val formatter = MoneyFormatter()

    private fun home(products: List<com.auxo.pos.domain.model.Product>, onOpenInventory: (StockFilter) -> Unit = {}) {
        val summary = InventorySummaryCalculator.calculate(products, TestData.today)
        composeRule.setContent {
            AuxoTheme {
                HomeContent(
                    uiState = HomeUiState.Ready(
                        storeName = "Sample Mart",
                        salesToday = formatter.format(com.auxo.pos.domain.model.Money.ZERO),
                        billsToday = 0,
                        inventory = summary,
                        stockValueAtCost = formatter.format(summary.stockValueAtCost),
                        stockValueAtPrice = formatter.format(summary.stockValueAtPrice),
                    ),
                    onNavigate = {},
                    onAddProduct = {},
                    onOpenProduct = {},
                    onOpenInventory = onOpenInventory,
                    onRetry = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun home_warnsAboutExpiredStock_andOpensTheExpiringList() {
        val opened = mutableListOf<StockFilter>()
        home(listOf(TestData.tShirt(), TestData.biscuits(expiry = TestData.today.minusDays(2))), opened::add)
        composeRule.onNodeWithTag("home_expired_banner").assertExists()
        composeRule.onNodeWithTag("home_expired_banner").assertTextContains("1 item has expired stock", substring = true)
        composeRule.onNodeWithTag("home_expired_banner").performClick()
        assertEquals(listOf(StockFilter.EXPIRING_SOON), opened)
    }

    @Test
    fun home_hasNoExpiredBanner_whenNothingHasExpired() {
        home(listOf(TestData.tShirt(), TestData.juice(stock = 5)))
        composeRule.onNodeWithTag("home_expired_banner").assertDoesNotExist()
    }

    private fun billing(expired: Int) {
        val settings = TestData.settings
        val cart = Cart()
        val totals = BillCalculator.calculate(cart, settings.taxModeFor(cart))
        composeRule.setContent {
            AuxoTheme {
                CartContent(
                    state = BillingUiState(
                        settings = settings,
                        productsLoaded = true,
                        productCount = 3,
                        cart = cart,
                        totals = totals,
                        check = CartValidator.check(cart, totals, true),
                        expiredItemCount = expired,
                    ),
                    actions = object : BillingActions {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun billing_warnsThatExpiredStockIsNeverBilled() {
        billing(expired = 2)
        composeRule.onNodeWithTag("billing_expired_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("billing_expired_banner").assertTextContains("2 items have expired stock", substring = true)
    }

    @Test
    fun billing_hasNoBanner_whenNothingHasExpired() {
        billing(expired = 0)
        composeRule.onNodeWithTag("billing_expired_banner").assertDoesNotExist()
    }

    @Test
    fun settings_offerSevenFifteenAndThirty_andReportTheChoice() {
        var saved: StoreSettings = TestData.settings
        composeRule.setContent {
            AuxoTheme {
                BillingSettingsContent(
                    state = BillingSettingsUiState(isLoading = false, settings = saved),
                    onChange = { change -> saved = change(saved) },
                    onGstinChange = {},
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("expiry_days_15"))
        for (days in listOf(7, 15, 30)) composeRule.onNodeWithTag("expiry_days_$days").assertExists()
        assertEquals(30, saved.expiryWarningDays)
        composeRule.onNodeWithTag("expiry_days_7").performClick()
        assertEquals(7, saved.expiryWarningDays)
    }
}
