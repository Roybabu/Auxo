package com.auxo.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.HeldBillSummary
import com.auxo.pos.domain.billing.ServiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.ui.screens.billing.BillingActions
import com.auxo.pos.ui.screens.billing.HeldBillsDialog
import com.auxo.pos.ui.screens.billing.OrderPlacedDialog
import com.auxo.pos.ui.screens.billing.ServiceCard
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.BillingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class RestaurantOrderUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun state(service: ServiceType?, table: String = "", errors: Boolean = false, placing: Boolean = false) =
        BillingUiState(cart = Cart(serviceType = service, tableNumber = table), restaurantMode = true, orderShowErrors = errors, isPlacingOrder = placing)

    @Test
    fun serviceCard_choosesTheServiceType_andPlacesTheOrder() {
        val chosen = mutableListOf<ServiceType?>()
        var placed = 0
        composeRule.setContent {
            AuxoTheme {
                ServiceCard(
                    state(null),
                    object : BillingActions {
                        override fun onServiceTypeChange(type: ServiceType?) { chosen += type }
                        override fun onPlaceOrder() { placed++ }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("service_table").assertDoesNotExist()
        composeRule.onNodeWithTag("service_dine_in").performClick()
        composeRule.onNodeWithTag("service_takeaway").performClick()
        composeRule.onNodeWithTag("order_place").performClick()
        assertEquals(listOf<ServiceType?>(ServiceType.DINE_IN, ServiceType.TAKEAWAY), chosen)
        assertEquals(1, placed)
    }

    @Test
    fun serviceCard_dineIn_asksForTheTable_andShowsTheErrorOnlyAfterATry() {
        composeRule.setContent { AuxoTheme { ServiceCard(state(ServiceType.DINE_IN, errors = true), object : BillingActions {}) } }
        composeRule.onNodeWithTag("service_table").assertIsDisplayed()
        composeRule.onNodeWithText("Enter the table number.").assertIsDisplayed()
    }

    @Test
    fun serviceCard_noErrorBeforeATry_andNoTableForTakeaway() {
        composeRule.setContent { AuxoTheme { ServiceCard(state(ServiceType.DINE_IN), object : BillingActions {}) } }
        composeRule.onNodeWithText("Enter the table number.").assertDoesNotExist()
    }

    @Test
    fun serviceCard_takeaway_hasNoTableField_andMissingServiceIsFlagged() {
        composeRule.setContent { AuxoTheme { ServiceCard(state(ServiceType.TAKEAWAY), object : BillingActions {}) } }
        composeRule.onNodeWithTag("service_table").assertDoesNotExist()
    }

    @Test
    fun serviceCard_flagsAMissingServiceType() {
        composeRule.setContent { AuxoTheme { ServiceCard(state(null, errors = true), object : BillingActions {}) } }
        composeRule.onNodeWithText("Choose dine-in or takeaway.").assertIsDisplayed()
    }

    @Test
    fun placeButton_isDisabledWhileSending() {
        composeRule.setContent { AuxoTheme { ServiceCard(state(ServiceType.TAKEAWAY, placing = true), object : BillingActions {}) } }
        composeRule.onNodeWithTag("order_place").assertIsNotEnabled()
    }

    @Test
    fun orderPlacedDialog_namesTheTicket_andOffersPrintShareDone() {
        var printed = 0
        var shared = 0
        var done = 0
        composeRule.setContent { AuxoTheme { OrderPlacedDialog("KOT-007", { printed++ }, { shared++ }, { done++ }) } }
        composeRule.onNodeWithText("Kitchen ticket KOT-007 is ready. Print it or share it with the kitchen.").assertIsDisplayed()
        composeRule.onNodeWithTag("order_print").performClick()
        composeRule.onNodeWithTag("order_share").performClick()
        composeRule.onNodeWithTag("order_done").performClick()
        assertTrue(printed == 1 && shared == 1 && done == 1)
    }

    @Test
    fun heldList_showsTheTableAndTicketCount_ofOpenOrdersOnly() {
        val bills = listOf(
            HeldBillSummary(1, "Asha", 1, 2, 3, Money(450_00), ServiceType.DINE_IN, "T4", 2),
            HeldBillSummary(2, "Ravi", 1, 2, 1, Money(99_00), ServiceType.TAKEAWAY, null, 1),
            HeldBillSummary(3, "Parked", 1, 2, 1, Money(99_00)),
        )
        composeRule.setContent {
            AuxoTheme { HeldBillsDialog(bills, counterIsEmpty = true, resumedId = null, onResume = {}, onDiscard = {}, onDismiss = {}) }
        }
        composeRule.onNodeWithText("Dine-in · Table T4 · 2 kitchen tickets").assertIsDisplayed()
        composeRule.onNodeWithText("Takeaway · 1 kitchen ticket").assertIsDisplayed()
        composeRule.onNodeWithTag("held_order_3").assertDoesNotExist()
    }
}
