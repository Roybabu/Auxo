package com.auxo.pos.ui

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.auxo.pos.DeviceFixtures
import com.auxo.pos.AuxoApplication
import com.auxo.pos.MainActivity
import com.auxo.pos.data.repository.OrderUpdateResult
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseOrderLine
import com.auxo.pos.domain.model.PurchaseOrderStatus
import com.auxo.pos.domain.model.Supplier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Step 4 flows through the real app on a device, where text fields work:
 * receiving a purchase order, and a customer account with a payment.
 * The database is checked after each flow.
 */
@RunWith(AndroidJUnit4::class)
class OperationsDeviceFlowTest {

    @get:Rule(order = 0)
    val signedIn = com.auxo.pos.SignedInRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: AuxoApplication = ApplicationProvider.getApplicationContext()
    private val container get() = app.container
    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun waitFor(matcher: SemanticsMatcher, what: String) {
        try {
            composeRule.waitUntil(timeoutMillis = 15_000) {
                dismissNotRespondingDialogs()
                composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("$what did not appear. ${screenSummary()}", e)
        }
    }

    private fun screenSummary(): String {
        val texts = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            .distinct()
        return "In front: ${device.currentPackageName}. On screen: ${texts.joinToString(" | ").take(1_500)}"
    }

    /** A busy emulator sometimes shows "… isn't responding" for a system app; answer Wait. */
    private fun dismissNotRespondingDialogs() {
        repeat(3) {
            val wait = device.findObject(By.res("android:id/aerr_wait")) ?: return
            wait.click()
            SystemClock.sleep(500)
        }
    }

    /**
     * Opens a bottom-bar tab. Home has no bar, so from there the "More" quick action
     * is used first: it leaves Home and brings the bar up.
     */
    private fun clickTab(label: String) {
        val tab = hasText(label) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        // The app may still be drawing its first screen: wait for the bar or for Home's quick actions.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(tab).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(tab).fetchSemanticsNodes().isEmpty()) {
            composeRule.onAllNodesWithText("More").onFirst().performClick()
            composeRule.waitUntil(timeoutMillis = 15_000) { composeRule.onAllNodes(tab).fetchSemanticsNodes().isNotEmpty() }
        }
        composeRule.onAllNodes(tab).onFirst().performClick()
    }

    private fun suffix(): String = (100_000 + Random.nextInt(900_000)).toString()

    @Test
    fun receivingAPurchaseOrder_addsStock_andClosesTheOrder() {
        val tag = suffix()
        val barcode = DeviceFixtures.uniqueBarcode()
        val (productId, orderId, orderNumber) = runBlocking {
            val productId = (container.inventoryRepository.saveProduct(DeviceFixtures.shirt("PO Shirt $tag", barcode, stock = 5)) as SaveProductResult.Saved).productId
            val product = container.inventoryRepository.getProduct(productId)!!
            val supplierId = container.purchasingRepository.saveSupplier(Supplier(name = "Device Mills $tag"))
            val line = PurchaseOrderLine(
                variantId = product.variants.single().id,
                productName = product.name,
                variantLabel = product.variants.single().attributesLabel,
                quantityOrdered = 6,
                unitCost = Money(180_00),
            )
            val saved = container.purchasingRepository.saveDraftOrder(PurchaseOrder(supplierId = supplierId, lines = listOf(line))) as OrderUpdateResult.Saved
            container.purchasingRepository.setOrderStatus(saved.orderId, PurchaseOrderStatus.ORDERED)
            Triple(productId, saved.orderId, saved.number)
        }

        clickTab("More")
        // Purchase orders now live inside Suppliers.
        waitFor(hasText("Suppliers") and hasClickAction(), "Suppliers entry")
        composeRule.onNode(hasText("Suppliers") and hasClickAction()).performScrollTo().performClick()
        waitFor(hasTestTag("supplier_purchase_orders"), "Purchase orders entry")
        composeRule.onNodeWithTag("supplier_purchase_orders").performClick()
        waitFor(hasText(orderNumber) and hasClickAction(), "order $orderNumber")
        composeRule.onNode(hasText(orderNumber) and hasClickAction()).performClick()

        waitFor(hasTestTag("order_receive"), "Receive goods button")
        composeRule.onNodeWithTag("order_receive").performClick()
        waitFor(hasText("Due: 6 pcs"), "the quantity due")
        composeRule.onNodeWithTag("receive_bill_number").performTextInput("DM-$tag")
        composeRule.onNodeWithText("Save").performClick()

        waitFor(hasText("GRN/", substring = true), "the goods receipt")
        val product = runBlocking { container.inventoryRepository.getProduct(productId)!!.variants.single() }
        assertEquals(11, product.stockQuantity)
        assertEquals(Money(180_00), product.costPrice)
        val order = runBlocking { container.purchasingRepository.getOrder(orderId)!! }
        assertEquals(PurchaseOrderStatus.RECEIVED, order.status)
        val receipt = runBlocking { container.purchasingRepository.observeReceiptsForOrder(orderId).first().single() }
        assertEquals("DM-$tag", receipt.supplierInvoiceNumber)
    }

    @Test
    fun customerAccount_openingBalance_thenAPayment() {
        val tag = suffix()
        val phone = "9" + tag + "123"
        val name = "Device Customer $tag"

        clickTab("Customers")
        waitFor(hasText("Add customer") and hasClickAction(), "Add customer")
        composeRule.onAllNodes(hasText("Add customer") and hasClickAction()).onFirst().performClick()
        waitFor(hasTestTag("customer_name"), "the customer form")
        composeRule.onNodeWithTag("customer_name").performTextInput(name)
        composeRule.onNodeWithTag("customer_phone").performTextInput(phone)
        composeRule.onNodeWithTag("customer_opening_balance").performTextInput("300")
        composeRule.onNodeWithText("Save").performClick()

        waitFor(hasTestTag("customer_receive_payment"), "the customer's account")
        val customer = runBlocking { container.customerRepository.findByPhone(phone)!! }
        assertEquals(name, customer.name)
        assertEquals(Money(300_00), runBlocking { container.customerRepository.observeAccount(customer.id).first()!!.balance })

        composeRule.onNodeWithTag("customer_receive_payment").performClick()
        waitFor(hasTestTag("account_payment_amount"), "the payment form")
        composeRule.onNodeWithTag("account_payment_amount").performTextClearance()
        composeRule.onNodeWithTag("account_payment_amount").performTextInput("100")
        composeRule.onNodeWithTag("account_payment_save").performClick()

        waitFor(hasTestTag("customer_receive_payment"), "the account again")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { container.customerRepository.observeAccount(customer.id).first()!!.balance } == Money(200_00)
        }
        val ledger = runBlocking { container.customerRepository.observeLedger(customer.id).first() }
        assertEquals(2, ledger.size)
        assertEquals(Money(-100_00), ledger.last().amount)
    }
}
