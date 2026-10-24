// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.receipt.ReceiptOutput
import com.auxo.pos.core.receipt.ReceiptRenderer
import com.auxo.pos.core.receipt.isThermal
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CartValidator
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.billing.PaymentDraft
import com.auxo.pos.domain.billing.ReturnBuild
import com.auxo.pos.domain.billing.ReturnBuilder
import com.auxo.pos.domain.billing.SampleSales
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.ReceiptOptions
import com.auxo.pos.domain.model.ReceiptPaper
import com.auxo.pos.testutil.TestData
import com.auxo.pos.ui.screens.billing.BillingActions
import com.auxo.pos.ui.screens.billing.BillingSettingsContent
import com.auxo.pos.ui.screens.billing.CartContent
import com.auxo.pos.ui.screens.billing.InvoiceDetailContent
import com.auxo.pos.ui.screens.billing.InvoiceHistoryContent
import com.auxo.pos.ui.screens.billing.PaymentContent
import com.auxo.pos.ui.screens.billing.ReturnContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.BillingSettingsUiState
import com.auxo.pos.viewmodel.BillingStage
import com.auxo.pos.viewmodel.BillingUiState
import com.auxo.pos.viewmodel.HistoryTab
import com.auxo.pos.viewmodel.InvoiceDetailUiState
import com.auxo.pos.viewmodel.InvoiceHistoryUiState
import com.auxo.pos.viewmodel.PaymentTotals
import com.auxo.pos.viewmodel.ProductSearchResult
import com.auxo.pos.viewmodel.ReturnUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class BillingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val settings = TestData.settings
    private val shirt = TestData.tShirt()
    private val biscuits = TestData.biscuits()

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { AuxoTheme { content() } }
        composeRule.waitForIdle()
    }

    private fun screenshot(name: String) {
        val bitmap = composeRule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        save(bitmap, name)
    }

    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun cartState(cart: Cart, stage: BillingStage = BillingStage.CART, payments: List<PaymentDraft> = emptyList()): BillingUiState {
        val totals = BillCalculator.calculate(cart, settings.taxModeFor(cart))
        return BillingUiState(
            settings = settings,
            productsLoaded = true,
            productCount = 3,
            cart = cart,
            totals = totals,
            check = CartValidator.check(cart, totals, true),
            stage = stage,
            payments = payments,
        )
    }

    private val sampleCart = Cart()
        .add(CartLine.from(shirt, shirt.variants[0], quantity = 2).copy(discount = Discount.Percent(1_000)))
        .add(CartLine.from(biscuits, biscuits.variants[0], quantity = 3))

    @Test
    fun cart_showsLinesTotalsAndCustomer() {
        val added = mutableListOf<Long>()
        show {
            CartContent(
                state = cartState(sampleCart).copy(customerExpanded = true, cart = sampleCart.copy(customer = CustomerDetails(name = "Anita Das"))),
                actions = object : BillingActions {
                    override fun onIncrement(lineKey: Long) {
                        added += lineKey
                    }
                },
            )
        }
        composeRule.onNodeWithText("Cotton T-Shirt").assertIsDisplayed()
        screenshot("20_billing_cart")
        composeRule.onNodeWithTag("billing_cart").performScrollToNode(hasText("Total"))
        screenshot("21_billing_cart_totals")
    }

    @Test
    fun cart_searchResults() {
        val typed = mutableListOf<String>()
        val results = shirt.variants.map { ProductSearchResult(shirt, it) }
        show {
            CartContent(
                state = cartState(Cart()).copy(searchText = "shirt", searchResults = results),
                actions = object : BillingActions {
                    override fun onSearchTextChange(text: String) {
                        typed += text
                    }
                },
            )
        }
        composeRule.onNodeWithText("M · Navy · ${TestData.EAN[0]}").assertIsDisplayed()
        composeRule.onNodeWithTag("billing_search").performTextInput("x")
        assertTrue(typed.last().contains("x") && typed.last().contains("shirt"))
        screenshot("22_billing_search")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cart_hardwareEnterInSearch_addsTheItem() {
        var submitted = 0
        show {
            CartContent(
                state = cartState(Cart()).copy(searchText = TestData.EAN[0]),
                actions = object : BillingActions {
                    override fun onSearchSubmitted() {
                        submitted++
                    }
                },
            )
        }
        // A scanner works like a keyboard: it types the code, then presses Enter.
        composeRule.onNodeWithTag("billing_search").requestFocus()
        composeRule.onNodeWithTag("billing_search").performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        assertEquals(1, submitted)
    }

    @Test
    fun cart_emptyAndSetupBanner() {
        show { CartContent(state = cartState(Cart()).copy(settings = settings.copy(gstin = "")), actions = object : BillingActions {}) }
        composeRule.onNodeWithText("Finish billing setup").assertIsDisplayed()
        composeRule.onNodeWithText("Start a bill").assertIsDisplayed()
        screenshot("23_billing_empty_setup")
    }

    @Test
    fun cart_noProductsYet() {
        show { CartContent(state = cartState(Cart()).copy(productCount = 0), actions = object : BillingActions {}) }
        composeRule.onNodeWithText("No products to sell yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add products in Inventory first.").assertIsDisplayed()
    }

    @Test
    fun cart_empty_hasNoBestSellersSection() {
        show { CartContent(state = cartState(Cart()), actions = object : BillingActions {}) }
        composeRule.onNodeWithText("Best sellers").assertDoesNotExist()
        composeRule.onNodeWithText("Most sold in the last 30 days. Tap to add.").assertDoesNotExist()
        composeRule.onNodeWithTag("best_sellers").assertDoesNotExist()
        // The empty billing page still explains how to start, with no gap where the section was.
        composeRule.onNodeWithTag("billing_search").assertIsDisplayed()
    }

    @Test
    fun payment_splitWithUpiQr() {
        val total = BillCalculator.calculate(sampleCart, settings.taxModeFor(sampleCart)).grandTotal
        val payments = listOf(
            PaymentDraft(1, PaymentMethod.CASH, "500", tendered = "500"),
            PaymentDraft(2, PaymentMethod.UPI, (total - Money(500_00)).toDecimalString()),
        )
        var completed = false
        show {
            PaymentContent(
                state = cartState(sampleCart, BillingStage.PAYMENT, payments),
                actions = object : BillingActions {
                    override fun onCompleteSale() {
                        completed = true
                    }
                },
            )
        }
        composeRule.onNodeWithText("Amount due").assertIsDisplayed()
        screenshot("24_payment")
        composeRule.onNodeWithTag("payment").performScrollToNode(hasTestTag("payment_status"))
        screenshot("25_payment_upi_qr")
        assertTrue(!completed)
    }

    private fun upiPayment(upiId: String, role: com.auxo.pos.core.auth.Role) {
        val state = cartState(sampleCart, BillingStage.PAYMENT, listOf(PaymentDraft(1, PaymentMethod.UPI, "100.00")))
            .copy(settings = settings.copy(upiId = upiId), upiInvoiceNumber = "INV/26-27/00007")
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                com.auxo.pos.ui.LocalCapabilities provides com.auxo.pos.ui.Capabilities(role, "Asha K"),
            ) { AuxoTheme { PaymentContent(state, object : BillingActions {}) } }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun upiQr_showsTheInvoiceNumber_whenUpiIdIsValid() {
        upiPayment("samplemart@okaxis", com.auxo.pos.core.auth.Role.EMPLOYEE)
        composeRule.onNodeWithTag("payment").performScrollToNode(hasTestTag("upi_invoice_number"))
        composeRule.onNodeWithTag("upi_invoice_number").assertIsDisplayed()
        composeRule.onAllNodesWithTag("upi_hint").assertCountEquals(0)
    }

    @Test
    fun upiHint_isShownToSuperAdminOnly_whenUpiIdIsMissing() {
        upiPayment("", com.auxo.pos.core.auth.Role.SUPER_ADMIN)
        composeRule.onNodeWithTag("payment").performScrollToNode(hasTestTag("upi_hint"))
        composeRule.onNodeWithTag("upi_hint").assertIsDisplayed()
        composeRule.onAllNodesWithTag("upi_invoice_number").assertCountEquals(0)
    }

    @Test
    fun upiHint_isHiddenFromEmployees() {
        upiPayment("", com.auxo.pos.core.auth.Role.EMPLOYEE)
        composeRule.onAllNodesWithTag("upi_hint").assertCountEquals(0)
        composeRule.onAllNodesWithTag("upi_invoice_number").assertCountEquals(0)
    }

    private fun detailState(invoice: Invoice, paper: ReceiptPaper) = InvoiceDetailUiState.Ready(
        invoice = invoice,
        creditNotes = emptyList(),
        settings = settings,
        paper = paper,
    )

    private val namedSale = SampleSales.shirtsAndBiscuits(CustomerDetails(name = "Sen Traders", phone = "9831000000", gstin = "19AABCU9603R1ZK"))

    @Test
    fun invoiceDetail_previewForEachPaper() {
        var paper by mutableStateOf(ReceiptPaper.THERMAL_80)
        composeRule.setContent {
            AuxoTheme {
                InvoiceDetailContent(
                    state = detailState(namedSale, paper),
                    onPaperChange = { paper = it },
                    onPrint = {},
                    onShare = {},
                    onReturn = {},
                    onOpenInvoice = {},
                    onNewBill = {},
                    zone = ZoneOffset.UTC,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Return items").assertIsDisplayed()
        screenshot("26_invoice_detail")
        composeRule.onNodeWithTag("paper_A4").performClick()
        composeRule.waitForIdle()
        assertEquals(ReceiptPaper.A4, paper)
    }

    @Test
    fun receipts_renderForEveryPaper() {
        val creditNote = (ReturnBuilder.build(namedSale, mapOf(namedSale.lines[0].id to 1), PaymentMethod.UPI, "Wrong size", TestData.clock.millis()) as ReturnBuild.Ready)
            .creditNote.copy(id = 9, number = "CN/26-27/00001")
        val cases = listOf(
            "30_receipt_58mm" to (namedSale to ReceiptPaper.THERMAL_58),
            "31_receipt_80mm" to (namedSale to ReceiptPaper.THERMAL_80),
            "31_receipt_76mm" to (namedSale to ReceiptPaper.THERMAL_76),
            "32_receipt_a5" to (namedSale to ReceiptPaper.A5),
            "33_receipt_a4" to (namedSale to ReceiptPaper.A4),
            "34_credit_note_80mm" to (creditNote to ReceiptPaper.THERMAL_80),
            "35_credit_note_a4" to (creditNote to ReceiptPaper.A4),
        )
        for ((name, pair) in cases) {
            val (invoice, paper) = pair
            val pages = ReceiptRenderer.pages(invoice, settings.receipt.copy(paper = paper), ZoneOffset.UTC)
            assertTrue(pages.isNotEmpty())
            pages.forEachIndexed { index, page ->
                val width = if (paper.isThermal) 576 else 1240
                val bitmap = ReceiptOutput.previewBitmap(page, width)
                assertTrue("$name page $index is blank", hasInk(bitmap))
                save(bitmap, if (pages.size == 1) name else "${name}_p${index + 1}")
            }
        }
        // Page sizes in points.
        val a4 = ReceiptRenderer.pages(namedSale, ReceiptOptions(paper = ReceiptPaper.A4), ZoneOffset.UTC).first()
        assertEquals(595.28f, a4.widthPt, 0.1f)
        val roll = ReceiptRenderer.pages(namedSale, ReceiptOptions(paper = ReceiptPaper.THERMAL_58), ZoneOffset.UTC).first()
        assertEquals(164.41f, roll.widthPt, 0.1f)
        val roll76 = ReceiptRenderer.pages(namedSale, ReceiptOptions(paper = ReceiptPaper.THERMAL_76), ZoneOffset.UTC).first()
        assertEquals(215.43f, roll76.widthPt, 0.1f)

        // PdfDocument has no native backend under Robolectric, so the PDF file itself
        // is only produced on a device; it draws these same pages.
        assertEquals("INV-26-27-00007.pdf", ReceiptOutput.fileName(namedSale))
    }

    @Test
    fun longInvoice_spillsOntoMorePages() {
        val many = namedSale.copy(lines = (1..60).map { i -> namedSale.lines[0].copy(id = i.toLong(), productName = "Item number $i with a fairly long name") })
        val pages = ReceiptRenderer.pages(many, ReceiptOptions(paper = ReceiptPaper.A5), ZoneOffset.UTC)
        assertTrue("expected several pages, got ${pages.size}", pages.size >= 3)
        pages.forEachIndexed { i, page -> if (i < 2) save(ReceiptOutput.previewBitmap(page, 900), "36_a5_long_p${i + 1}") }
    }

    @Test
    fun history_invoicesAndPayments() {
        val invoices = listOf(
            InvoiceSummary(2, "CN/26-27/00001", InvoiceType.RETURN, TestData.clock.millis(), "Anita Das", null, Money(499_00), 1),
            InvoiceSummary(1, "INV/26-27/00001", InvoiceType.SALE, TestData.clock.millis(), null, null, Money(1_557_00), 5),
        )
        val payments = listOf(
            PaymentRecord(1, 1, PaymentMethod.CASH, PaymentKind.PAYMENT, Money(1_000_00), createdAtMillis = TestData.clock.millis(), invoiceNumber = "INV/26-27/00001"),
            PaymentRecord(2, 1, PaymentMethod.UPI, PaymentKind.PAYMENT, Money(557_00), reference = "415263748596", createdAtMillis = TestData.clock.millis(), invoiceNumber = "INV/26-27/00001"),
            PaymentRecord(3, 2, PaymentMethod.CASH, PaymentKind.REFUND, Money(499_00), createdAtMillis = TestData.clock.millis(), invoiceNumber = "CN/26-27/00001"),
        )
        var tab by mutableStateOf(HistoryTab.INVOICES)
        composeRule.setContent {
            AuxoTheme {
                InvoiceHistoryContent(
                    state = InvoiceHistoryUiState(
                        isLoading = false,
                        tab = tab,
                        invoices = invoices,
                        payments = payments,
                        paymentTotals = PaymentTotals.of(payments),
                        salesTotal = Money(1_557_00),
                        returnsTotal = Money(499_00),
                    ),
                    onTabChange = { tab = it },
                    onRangeChange = {},
                    onQueryChange = {},
                    onRetry = {},
                    onOpenInvoice = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("INV/26-27/00001").assertIsDisplayed()
        screenshot("27_history_invoices")
        composeRule.onNodeWithText("Payments").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Net received").assertIsDisplayed()
        screenshot("28_history_payments")
    }

    @Test
    fun returnScreen() {
        val sale = namedSale
        show {
            ReturnContent(
                state = ReturnUiState(isLoading = false, invoice = sale, quantities = mapOf(sale.lines[0].id to 1, sale.lines[1].id to 0), refund = Money(499_00)),
                onQuantityChange = { _, _ -> },
                onReturnAll = {},
                onRefundMethodChange = {},
                onReasonChange = {},
            )
        }
        composeRule.onNodeWithText("From invoice INV/26-27/00007").assertIsDisplayed()
        screenshot("29_return")
    }

    @Test
    fun settingsScreen() {
        var saved = false
        show {
            BillingSettingsContent(
                state = BillingSettingsUiState(isLoading = false, settings = settings),
                onChange = {},
                onGstinChange = {},
                onSave = { saved = true },
            )
        }
        composeRule.onNodeWithTag("settings_store_name").assertIsDisplayed()
        screenshot("37_billing_settings")
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_save"))
        composeRule.onNodeWithTag("settings_save").performClick()
        assertTrue(saved)
    }

    private fun hasInk(bitmap: Bitmap): Boolean {
        var dark = 0
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                if (Color.red(bitmap.getPixel(x, y)) < 100) dark++
                if (dark > 50) return true
            }
        }
        return false
    }
}
