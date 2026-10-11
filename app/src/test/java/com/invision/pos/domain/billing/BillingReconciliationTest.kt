package com.invision.pos.domain.billing

import com.invision.pos.domain.model.GstRate
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.StoreSettings
import com.invision.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Many random bills, in every tax mode, must add up to the paisa: lines to totals, tax to its
 * parts, payments to the grand total, and returns in any order back to the original bill.
 * The random numbers are seeded, so a failure repeats.
 */
class BillingReconciliationTest {

    private val rates = listOf<GstRate?>(GstRate.Fixed(0), GstRate.Fixed(500), GstRate.Fixed(1200), GstRate.Fixed(1800), GstRate.Fixed(2800), GstRate.Apparel)

    private fun randomCart(random: Random): Cart {
        val lines = (1..random.nextInt(1, 6)).map { i ->
            val price = Money(random.nextLong(1, 600_000))
            CartLine(
                variantId = i.toLong(),
                productId = i.toLong(),
                productName = "Item $i",
                variantLabel = "",
                barcode = "",
                hsnCode = "6109",
                unitPrice = price,
                mrp = if (random.nextBoolean()) price + Money(random.nextLong(0, 5_000)) else null,
                gstRate = rates.random(random),
                quantity = random.nextInt(1, 10),
                discount = when (random.nextInt(4)) {
                    0 -> Discount.Percent(random.nextInt(1, 10_001))
                    1 -> Discount.Amount(Money(random.nextLong(1, 700_000)))
                    else -> null
                },
                availableStock = 100,
            )
        }
        val billDiscount = when (random.nextInt(4)) {
            0 -> Discount.Percent(random.nextInt(1, 10_001))
            1 -> Discount.Amount(Money(random.nextLong(1, 2_000_000)))
            else -> null
        }
        return Cart(lines = lines, billDiscount = billDiscount)
    }

    private fun settingsFor(random: Random) = TestData.settings.copy(
        pricesIncludeTax = random.nextBoolean(),
        roundOffTotal = random.nextBoolean(),
    )

    private fun saleFor(cart: Cart, settings: StoreSettings): Invoice {
        val totals = BillCalculator.calculate(cart, settings.taxModeFor(cart))
        // Split the payment in two, as at the counter.
        val first = Money(totals.grandTotal.minorUnits / 3)
        val payments = listOfNotNull(
            first.takeIf { !it.isZero }?.let { PaymentRecord(method = PaymentMethod.UPI, amount = it) },
            PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal - first).takeIf { !(totals.grandTotal - first).isZero },
        )
        val built = SaleBuilder.build(cart, totals, settings, payments, TestData.clock.millis())
        return built.copy(id = 1, number = "INV/26-27/00001", lines = built.lines.mapIndexed { i, l -> l.copy(id = 10L + i) })
    }

    @Test
    fun totals_addUpForRandomBills() {
        val random = Random(2026)
        repeat(1_000) { round ->
            val cart = randomCart(random)
            val settings = settingsFor(random)
            val inter = cart.copy(placeOfSupplyStateCode = if (random.nextInt(4) == 0) "27" else null)
            val mode = settings.taxModeFor(inter)
            val t = BillCalculator.calculate(inter, mode)
            val where = "round $round, mode $mode"

            assertEquals(where, t.lines.sumOf { it.total.minorUnits }, t.totalBeforeRounding.minorUnits)
            assertEquals(where, t.totalBeforeRounding + t.roundOff, t.grandTotal)
            if (mode.roundOff) {
                assertEquals(where, 0L, t.grandTotal.minorUnits % 100)
                assertTrue(where, kotlin.math.abs(t.roundOff.minorUnits) <= 50)
            } else {
                assertTrue(where, t.roundOff.isZero)
            }
            assertEquals(where, t.lines.sumOf { it.discount.minorUnits }, t.discountTotal.minorUnits)
            assertEquals(where, t.subtotal - t.discountTotal, Money(t.lines.sumOf { it.net.minorUnits }))
            assertEquals(where, t.lines.sumOf { it.taxable.minorUnits }, t.taxableTotal.minorUnits)
            assertEquals(where, t.taxSummary.sumOf { it.taxable.minorUnits }, t.taxableTotal.minorUnits)
            assertEquals(where, t.taxSummary.sumOf { it.cgst.minorUnits + it.sgst.minorUnits + it.igst.minorUnits }, t.taxTotal.minorUnits)
            for (line in t.lines) {
                assertEquals(where, line.taxable + line.tax, line.total)
                assertEquals(where, line.gross - line.lineDiscount - line.billDiscountShare, line.net)
                if (mode.pricesIncludeTax || !mode.gstRegistered) assertEquals(where, line.net, line.total) else assertEquals(where, line.net, line.taxable)
                assertTrue(where, line.taxable.minorUnits >= 0 && line.tax.minorUnits >= 0 && line.total.minorUnits >= 0)
                if (mode.interState) assertTrue(where, line.cgst.isZero && line.sgst.isZero) else assertTrue(where, kotlin.math.abs(line.cgst.minorUnits - line.sgst.minorUnits) <= 1)
            }
            if (!mode.gstRegistered) assertTrue(where, t.taxTotal.isZero)
        }
    }

    @Test
    fun allocate_alwaysSharesTheWholeAmount() {
        val random = Random(7)
        repeat(1_000) {
            val weights = (1..random.nextInt(1, 8)).map { Money(random.nextLong(0, 1_000_000)) }
            val amount = Money(random.nextLong(0, weights.sumOf { it.minorUnits } + 1))
            val shares = BillCalculator.allocate(amount, weights)
            val expected = if (weights.sumOf { it.minorUnits } == 0L) 0L else amount.minorUnits
            assertEquals(expected, shares.sumOf { it.minorUnits })
            assertTrue(shares.indices.all { shares[it] <= weights[it] })
        }
    }

    @Test
    fun everyBuiltSale_balances_withItsSplitPayments() {
        val random = Random(99)
        repeat(1_000) { round ->
            val cart = randomCart(random)
            val sale = saleFor(cart, settingsFor(random))
            assertEquals("round $round", emptyList<String>(), InvoiceIntegrity.problems(sale))
            assertEquals("round $round", sale.grandTotal, Money(sale.payments.sumOf { it.amount.minorUnits }))
        }
    }

    @Test
    fun returningEveryUnit_inAnyOrder_givesBackExactlyTheBill() {
        val random = Random(31)
        repeat(500) { round ->
            val settings = settingsFor(random)
            val sale = saleFor(randomCart(random), settings)
            var current = sale
            val notes = mutableListOf<Invoice>()
            var guard = 0
            while (!current.isFullyReturned && guard++ < 200) {
                val open = current.lines.filter { it.returnableQuantity > 0 }
                val picked = open.shuffled(random).take(random.nextInt(1, open.size + 1))
                val quantities = picked.associate { it.id to random.nextInt(1, it.returnableQuantity + 1) }
                val method = if (random.nextBoolean()) PaymentMethod.CASH else PaymentMethod.UPI
                val note = (ReturnBuilder.build(current, quantities, method, "x", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
                assertEquals("round $round", emptyList<String>(), InvoiceIntegrity.problems(note))
                notes += note
                current = current.copy(lines = current.lines.map { it.copy(returnedQuantity = it.returnedQuantity + (quantities[it.id] ?: 0)) })
            }
            assertTrue("round $round: all units came back", current.isFullyReturned)

            // The refunds add up to what was paid, and the credit notes to the original lines, to the paisa.
            assertEquals("round $round", sale.grandTotal.minorUnits, notes.sumOf { it.grandTotal.minorUnits })
            assertEquals("round $round", sale.grandTotal.minorUnits, notes.sumOf { n -> n.payments.sumOf { it.amount.minorUnits } })
            assertTrue("round $round", notes.all { n -> n.payments.all { it.kind == PaymentKind.REFUND } })
            for (line in sale.lines) {
                val back = notes.flatMap { it.lines }.filter { it.originalLineId == line.id }
                assertEquals("round $round", line.quantity, back.sumOf { it.quantity })
                assertEquals("round $round", line.taxable.minorUnits, back.sumOf { it.taxable.minorUnits })
                assertEquals("round $round", line.cgst.minorUnits, back.sumOf { it.cgst.minorUnits })
                assertEquals("round $round", line.sgst.minorUnits, back.sumOf { it.sgst.minorUnits })
                assertEquals("round $round", line.igst.minorUnits, back.sumOf { it.igst.minorUnits })
                assertEquals("round $round", line.discount.minorUnits, back.sumOf { it.discount.minorUnits })
                assertEquals("round $round", line.total.minorUnits, back.sumOf { it.total.minorUnits })
            }
        }
    }

    @Test
    fun aPartialReturn_neverRefundsMoreThanTheBill() {
        val random = Random(5)
        repeat(500) { round ->
            val sale = saleFor(randomCart(random), settingsFor(random))
            val quantities = sale.lines.associate { it.id to random.nextInt(0, it.quantity + 1) }.filterValues { it > 0 }
            if (quantities.isEmpty()) return@repeat
            val build = ReturnBuilder.build(sale, quantities, PaymentMethod.CASH, "x", TestData.clock.millis()) as ReturnBuild.Ready
            assertTrue("round $round", build.creditNote.grandTotal <= sale.grandTotal)
            assertTrue("round $round", build.creditNote.grandTotal.minorUnits >= 0)
        }
    }
}
