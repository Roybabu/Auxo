package com.invision.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.domain.customers.OwingSort
import com.invision.pos.domain.customers.ReminderShop
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.CustomerAccount
import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.Money
import com.invision.pos.ui.screens.customers.CustomerDetailContent
import com.invision.pos.ui.screens.customers.CustomerListContent
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.CustomerDetailUiState
import com.invision.pos.viewmodel.CustomerFilter
import com.invision.pos.viewmodel.CustomerListUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class OwingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val asha = CustomerAccount(Customer(id = 1, name = "Asha", phone = "9830012345"), Money(500_00), owingSinceMillis = 1)
    private val bimal = CustomerAccount(Customer(id = 2, name = "Bimal"), Money(300_00), owingSinceMillis = 1)
    private val shop = ReminderShop("Roy Mart", "roymart@okaxis", Language.ENGLISH)

    private fun owingState(shop: ReminderShop? = this.shop, filter: CustomerFilter = CustomerFilter.OWING) = CustomerListUiState(
        isLoading = false,
        filter = filter,
        accounts = listOf(asha, bimal),
        customerCount = 2,
        totalDue = Money(800_00),
        owingCount = 2,
        ageDays = mapOf(1L to 5, 2L to 45),
        reminderShop = shop,
    )

    @Test
    fun owingList_showsHowLongEachCustomerHasOwed() {
        composeRule.setContent {
            InvisionTheme { CustomerListContent(owingState(), {}, {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Owing 5 days").assertIsDisplayed()
        composeRule.onNodeWithText("Owing 45 days").assertIsDisplayed()
    }

    @Test
    fun sortChoices_appearOnlyOnTheOwingList_andReportTheChoice() {
        val chosen = mutableListOf<OwingSort>()
        composeRule.setContent {
            InvisionTheme { CustomerListContent(owingState(), {}, {}, {}, {}, {}, onSortChange = { chosen += it }) }
        }
        composeRule.onNodeWithTag("owing_sort_age").performClick()
        composeRule.onNodeWithTag("owing_sort_amount").performClick()
        assertEquals(listOf(OwingSort.AGE, OwingSort.AMOUNT), chosen)
    }

    @Test
    fun allCustomersList_hasNoSortNoAgeAndNoReminderButtons() {
        composeRule.setContent {
            InvisionTheme { CustomerListContent(owingState(filter = CustomerFilter.ALL), {}, {}, {}, {}, {}) }
        }
        composeRule.onNodeWithTag("owing_sort_age").assertDoesNotExist()
        composeRule.onNodeWithTag("owing_age_1").assertDoesNotExist()
        composeRule.onNodeWithTag("remind_1").assertDoesNotExist()
    }

    @Test
    fun reminderButton_reportsTheCustomer_andIsAbsentWithoutShopSettings() {
        val reminded = mutableListOf<Long>()
        composeRule.setContent {
            InvisionTheme { CustomerListContent(owingState(), {}, {}, {}, {}, {}, onRemind = { reminded += it.customer.id }) }
        }
        composeRule.onNodeWithTag("remind_2").performClick()
        assertEquals(listOf(2L), reminded)
    }

    @Test
    fun noReminderButtons_untilTheShopSettingsAreKnown() {
        composeRule.setContent { InvisionTheme { CustomerListContent(owingState(shop = null), {}, {}, {}, {}, {}) } }
        composeRule.onNodeWithTag("remind_1").assertDoesNotExist()
    }

    private fun detail(balance: Long, shop: ReminderShop? = this.shop) = CustomerDetailUiState.Ready(
        account = CustomerAccount(asha.customer, Money(balance)),
        statement = emptyList(),
        invoices = emptyList(),
        charged = Money(500_00),
        paid = Money.ZERO,
        reminderShop = shop,
    )

    @Test
    fun customerPage_offersTheReminder_onlyWhileMoneyIsOwed_andSaysNothingIsSentAutomatically() {
        var reminded = 0
        composeRule.setContent {
            InvisionTheme { CustomerDetailContent(detail(500_00), onReceivePayment = {}, onOpenInvoice = {}, onRemind = { reminded++ }) }
        }
        composeRule.onNodeWithTag("customer_detail").performScrollToNode(hasTestTag("customer_remind"))
        composeRule.onNodeWithText("Nothing is sent automatically. WhatsApp opens with the message ready and you press send.").assertIsDisplayed()
        composeRule.onNodeWithTag("customer_remind").performClick()
        assertEquals(1, reminded)
    }

    @Test
    fun customerPage_hasNoReminder_whenSettledOrWithoutSettings() {
        composeRule.setContent {
            InvisionTheme { CustomerDetailContent(detail(0), onReceivePayment = {}, onOpenInvoice = {}, onRemind = {}) }
        }
        composeRule.onNodeWithTag("customer_remind").assertDoesNotExist()
    }
}
