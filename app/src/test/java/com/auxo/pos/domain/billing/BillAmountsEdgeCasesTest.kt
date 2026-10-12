package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edge cases of GST, discounts, round-off and split payments that bills hit at the counter. */
class BillAmountsEdgeCasesTest {

    private val inclusive = TaxMode(pricesIncludeTax = true, roundOff = false)

    private fun line(id: Long, price: Long, qty: Int = 1, rate: GstRate? = GstRate.Fixed(500), discount: Discount? = null) = CartLine(
        variantId = id, productId = id, productName = "Item $id", variantLabel = "", barcode = "", hsnCode = null,
        unitPrice = Money(price), mrp = null, gstRate = rate, quantity = qty, discount = discount, availableStock = 100,
    )

    private fun calc(vararg lines: CartLine, mode: TaxMode = inclusive, billDiscount: Discount? = null) =
        BillCalculator.calculate(Cart(lines = lines.toList(), billDiscount = billDiscount), mode)

    @Test
    fun billDiscount_canLowerAnApparelPieceIntoTheLowerSlab() {
        // ₹2,700 is taxed at 18%; a ₹200 bill discount takes it to ₹2,500, which is 5%.
        assertEquals(1_800, calc(line(1, 2_700_00, rate = GstRate.Apparel)).lines.single().rateBasisPoints)
        assertEquals(500, calc(line(1, 2_700_00, rate = GstRate.Apparel), billDiscount = Discount.Amount(Money(200_00))).lines.single().rateBasisPoints)
        // Shared across two lines, the discount only lowers the line that crosses the threshold.
        val both = calc(
            line(1, 2_700_00, rate = GstRate.Apparel),
            line(2, 1_000_00, rate = GstRate.Apparel),
            billDiscount = Discount.Amount(Money(200_00)),
        )
        assertEquals(listOf(500, 500), both.lines.map { it.rateBasisPoints })
    }

    @Test
    fun aFullDiscount_leavesNothingToPayAndNoTax() {
        val totals = calc(line(1, 49_900, qty = 2), billDiscount = Discount.Percent(10_000))
        assertTrue(totals.grandTotal.isZero)
        assertTrue(totals.taxTotal.isZero)
        assertTrue(totals.taxableTotal.isZero)
        assertEquals(Money(99_800), totals.discountTotal)
    }

    @Test
    fun roundOff_canGoUpOrDown_andIsSeparateFromTax() {
        val rounded = TaxMode(pricesIncludeTax = false, roundOff = true)
        // ₹100.20 + 5% = ₹105.21 -> ₹105.00
        val down = calc(line(1, 10_020), mode = rounded)
        assertEquals(Money(10_521), down.totalBeforeRounding)
        assertEquals(Money(-21), down.roundOff)
        assertEquals(Money(10_500), down.grandTotal)
        // ₹100.50 + 5% = ₹105.53 -> ₹106.00
        val up = calc(line(1, 10_050), mode = rounded)
        assertEquals(Money(10_553), up.totalBeforeRounding)
        assertEquals(Money(47), up.roundOff)
        assertEquals(Money(10_600), up.grandTotal)
        assertEquals(up.taxTotal, calc(line(1, 10_050), mode = TaxMode(pricesIncludeTax = false, roundOff = false)).taxTotal)
    }

    @Test
    fun percentDiscounts_roundHalfUpOnThePaisa() {
        // 12.5% of ₹0.99 = 12.375 paise -> 12 paise, 12.5% of ₹1.00 = 12.5 -> 13 paise.
        assertEquals(Money(12), Discount.Percent(1_250).amountOn(Money(99)))
        assertEquals(Money(13), Discount.Percent(1_250).amountOn(Money(100)))
        assertEquals(Money(100), Discount.Amount(Money(500)).amountOn(Money(100)))
    }

    @Test
    fun discountInput_isParsedStrictly() {
        assertEquals(DiscountParse.Invalid, Discount.parse("100.01", isPercent = true))
        assertEquals(DiscountParse.Invalid, Discount.parse("-5", isPercent = true))
        assertEquals(DiscountParse.Invalid, Discount.parse("1,5", isPercent = true))
        assertEquals(DiscountParse.Invalid, Discount.parse("abc", isPercent = false))
        assertEquals(DiscountParse.None, Discount.parse("0", isPercent = true))
        assertEquals(DiscountParse.None, Discount.parse("   ", isPercent = false))
        assertEquals(DiscountParse.Parsed(Discount.Percent(10_000)), Discount.parse("100", isPercent = true))
        assertEquals(DiscountParse.Parsed(Discount.Percent(1_250)), Discount.parse("12.5", isPercent = true))
        assertEquals(DiscountParse.Parsed(Discount.Amount(Money(5_050))), Discount.parse("50.50", isPercent = false))
    }

    @Test
    fun billsThatCantBeInvoiced_areCaughtBeforeSaving() {
        // A line with no GST rate is taxed at 0% by the calculator, so the validator must stop it.
        val cart = Cart(lines = listOf(line(1, 10_000, rate = null)))
        val totals = BillCalculator.calculate(cart, inclusive)
        assertTrue(totals.taxTotal.isZero)
        assertTrue(!CartValidator.check(cart, totals, true).canCheckout)
        assertTrue(CartValidator.check(cart, totals, false).canCheckout)
    }

    // Payments

    private val total = Money(99_800)

    private fun draft(key: Int, method: PaymentMethod, amount: String, reference: String = "", tendered: String = "") =
        PaymentDraft(key, method, amount, reference, tendered)

    @Test
    fun splitPayment_acrossThreeMethods_mustAddUpExactly() {
        val drafts = listOf(
            draft(1, PaymentMethod.CASH, "300", tendered = "500"),
            draft(2, PaymentMethod.UPI, "400", reference = "UTR1234"),
            draft(3, PaymentMethod.CARD, "298", reference = "4242"),
        )
        val valid = PaymentValidator.check(drafts, total) as PaymentCheck.Valid
        assertEquals(Money(99_800), Money(valid.payments.sumOf { it.amount.minorUnits }))
        assertEquals(Money(20_000), valid.change)
        assertEquals("UTR1234", valid.payments[1].reference)
        assertTrue(valid.payments.all { it.kind == PaymentKind.PAYMENT })

        val short = PaymentValidator.check(drafts.dropLast(1) + draft(3, PaymentMethod.CARD, "297.99", reference = "4242"), total)
        assertEquals(Money(1), (short as PaymentCheck.Invalid).remaining)
        val over = PaymentValidator.check(drafts.dropLast(1) + draft(3, PaymentMethod.CARD, "298.01", reference = "4242"), total)
        assertEquals(Money(-1), (over as PaymentCheck.Invalid).remaining)
    }

    @Test
    fun onAccount_countsEveryCreditLineAgainstTheLimit() {
        val account = CreditAccount(balance = Money(10_000), limit = Money(60_000))
        fun check(first: String, second: String) = PaymentValidator.check(
            listOf(draft(1, PaymentMethod.CREDIT, first), draft(2, PaymentMethod.CREDIT, second), draft(3, PaymentMethod.CASH, "0.01")),
            Money(first.toBigDecimal().multiply(100.toBigDecimal()).toLong() + second.toBigDecimal().multiply(100.toBigDecimal()).toLong() + 1),
            account,
        )
        // 250 + 250 = 500 is exactly what is left of the limit.
        assertTrue(check("250", "250") is PaymentCheck.Valid)
        // One paisa more across the two lines goes over.
        val over = check("250", "250.01") as PaymentCheck.Invalid
        assertEquals(PaymentError.CREDIT_OVER_LIMIT, over.errors[2])
    }

    @Test
    fun onAccount_needsACustomer_andNoLimitMeansNoCap() {
        val noCustomer = PaymentValidator.check(listOf(draft(1, PaymentMethod.CREDIT, "998")), total, account = null) as PaymentCheck.Invalid
        assertEquals(PaymentError.CREDIT_NEEDS_CUSTOMER, noCustomer.errors[1])
        val unlimited = PaymentValidator.check(listOf(draft(1, PaymentMethod.CREDIT, "998")), total, CreditAccount(balance = Money(5_000_000), limit = null))
        assertTrue(unlimited is PaymentCheck.Valid)
    }

    @Test
    fun cashTendered_belowTheAmount_isRefused_andChangeIsPerLine() {
        val low = PaymentValidator.check(listOf(draft(1, PaymentMethod.CASH, "998", tendered = "997.99")), total) as PaymentCheck.Invalid
        assertEquals(PaymentError.TENDERED_TOO_LOW, low.errors[1])
        val exact = PaymentValidator.check(listOf(draft(1, PaymentMethod.CASH, "998", tendered = "998")), total) as PaymentCheck.Valid
        assertTrue(exact.change.isZero)
        assertEquals(null, exact.payments.single().tendered)
        val withChange = PaymentValidator.check(listOf(draft(1, PaymentMethod.CASH, "998", tendered = "1000")), total) as PaymentCheck.Valid
        assertEquals(Money(200), withChange.change)
    }

    @Test
    fun amountsMustBePositiveMoney() {
        for (bad in listOf("0", "0.00", "-1", "abc", "9.999", "1e3")) {
            val result = PaymentValidator.check(listOf(draft(1, PaymentMethod.CASH, bad)), total) as PaymentCheck.Invalid
            assertEquals("input '$bad'", PaymentError.AMOUNT_INVALID, result.errors[1])
        }
        val blank = PaymentValidator.check(listOf(draft(1, PaymentMethod.CASH, "")), total) as PaymentCheck.Invalid
        assertEquals(PaymentError.AMOUNT_REQUIRED, blank.errors[1])
        assertTrue((PaymentValidator.check(emptyList(), total) as PaymentCheck.Invalid).noPayments)
    }
}
