package com.invision.pos.ui

import android.os.SystemClock
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.invision.pos.DeviceFixtures
import com.invision.pos.InvisionApplication
import com.invision.pos.MainActivity
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A complete sale and return through the real app on a device: billing by search
 * and by a scanner-style barcode entry, the system share sheet and print dialog,
 * then a return, checking the database after each step.
 */
@RunWith(AndroidJUnit4::class)
class BillingDeviceFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: InvisionApplication = ApplicationProvider.getApplicationContext()
    private val container get() = app.container
    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun waitForText(text: String, substring: Boolean = false) {
        try {
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("'$text' did not appear. ${screenSummary()}", e)
        }
    }

    /** Search box contents and the texts on screen, to explain a failure. */
    private fun screenSummary(): String {
        val search = composeRule.onAllNodes(hasTestTag("billing_search")).fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text
        val texts = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            .distinct()
        return "In front: ${device.currentPackageName}. Search box: ${search ?: "not shown"}. " +
            "On screen: ${texts.joinToString(" | ").take(1_500)}"
    }

    /**
     * Polls the app in front until [accept] is true; returns it, or null on timeout.
     * Compose work is let run between polls: in tests, coroutines started by the
     * screen (such as writing the PDF before sharing) only run when the test syncs.
     */
    private fun packageInFront(timeoutMillis: Long, accept: (String?) -> Boolean): String? {
        val end = SystemClock.uptimeMillis() + timeoutMillis
        while (true) {
            composeRule.waitForIdle()
            dismissNotRespondingDialogs()
            val current = device.currentPackageName
            if (accept(current)) return current
            if (SystemClock.uptimeMillis() > end) return null
            SystemClock.sleep(250)
        }
    }

    /**
     * A busy emulator sometimes shows "… isn't responding" for one of its own
     * system apps. The dialog takes focus, so it is answered with Wait.
     */
    private fun dismissNotRespondingDialogs() {
        repeat(5) {
            val wait = device.findObject(By.res("android:id/aerr_wait")) ?: return
            wait.click()
            SystemClock.sleep(500)
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
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

    /**
     * Expects another app's window ([what]: the share sheet or print dialog) to
     * come in front of ours, then goes back.
     */
    private fun expectSystemWindow(what: String): String {
        val opened = packageInFront(20_000) { it != null && it != app.packageName }
            ?: throw AssertionError("$what did not open (in front: ${device.currentPackageName}). ${screenSummary()}")
        device.pressBack()
        assertTrue(
            "app did not come back from $opened (in front: ${device.currentPackageName})",
            packageInFront(10_000) { it == app.packageName } != null,
        )
        composeRule.waitForIdle()
        return opened
    }

    private fun stockOf(productId: Long): Int = runBlocking {
        container.inventoryRepository.getProduct(productId)!!.variants.single().stockQuantity
    }

    /** Newest invoice of [type] that has a line for [productName]. */
    private fun latest(type: InvoiceType, productName: String): Invoice? = runBlocking {
        val billing = container.billingRepository
        billing.observeInvoices(0, Long.MAX_VALUE).first()
            .filter { it.type == type }
            .firstNotNullOfOrNull { summary -> billing.getInvoice(summary.id)?.takeIf { invoice -> invoice.lines.any { it.productName == productName } } }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun sellShareAndPrintThenReturn() {
        val barcode = DeviceFixtures.uniqueBarcode()
        val name = "Device Shirt ${barcode.takeLast(6)}"
        val productId = runBlocking {
            container.settingsRepository.saveSettings(DeviceFixtures.settings)
            (container.inventoryRepository.saveProduct(DeviceFixtures.shirt(name, barcode, stock = 5)) as SaveProductResult.Saved).productId
        }

        // Add one shirt from search results.
        clickTab("Billing")
        waitForTag("billing_search")
        composeRule.onNodeWithTag("billing_search").performTextInput(name)
        waitForText("M · Navy · $barcode")
        composeRule.onNodeWithText("M · Navy · $barcode").performClick()

        // A USB or Bluetooth scanner works like a keyboard: it types the code as
        // key presses, then presses Enter.
        val digits = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        composeRule.onNodeWithTag("billing_search").requestFocus()
        composeRule.onNodeWithTag("billing_search").performKeyInput {
            barcode.forEach { pressKey(digits[it - '0']) }
            pressKey(Key.Enter)
        }
        waitForText("998.00", substring = true)

        // Pay the full amount in cash and complete the sale.
        composeRule.onNodeWithTag("billing_charge").performClick()
        waitForTag("payment_complete")
        // The amount starts at 0.00, so clear it and type the full bill.
        composeRule.onNodeWithTag("payment_amount_0").performTextClearance()
        composeRule.onNodeWithTag("payment_amount_0").performTextInput("998")
        composeRule.onNodeWithTag("payment_complete").performClick()
        waitForTag("receipt_preview")

        // Invoice, payment and stock were saved together.
        val sale = requireNotNull(latest(InvoiceType.SALE, name)) { "sale not saved" }
        assertEquals(2, sale.lines.single().quantity)
        assertEquals(Money(998_00), sale.grandTotal)
        assertEquals(listOf(PaymentMethod.CASH to Money(998_00)), sale.payments.map { it.method to it.amount })
        assertEquals(3, stockOf(productId))
        waitForText("Invoice ${sale.number}")

        // Share opens the system share sheet; Print opens the system print dialog.
        composeRule.onNodeWithText("Share PDF").performClick()
        expectSystemWindow("share sheet")
        composeRule.onNodeWithText("Print").performClick()
        assertEquals("com.android.printspooler", expectSystemWindow("print dialog"))

        // Return both shirts.
        composeRule.onNodeWithText("Return items").performClick()
        waitForText("Return all")
        composeRule.onNodeWithText("Return all").performClick()
        composeRule.onNodeWithTag("return_confirm").performClick()
        waitForText("Credit note CN/", substring = true)

        val creditNote = requireNotNull(latest(InvoiceType.RETURN, name)) { "credit note not saved" }
        assertEquals(sale.id, creditNote.originalInvoiceId)
        assertEquals(sale.grandTotal, creditNote.grandTotal)
        assertEquals(listOf(PaymentKind.REFUND to sale.grandTotal), creditNote.payments.map { it.kind to it.amount })
        assertEquals(5, stockOf(productId))
        assertTrue(runBlocking { container.billingRepository.getInvoice(sale.id)!!.isFullyReturned })
    }
}
