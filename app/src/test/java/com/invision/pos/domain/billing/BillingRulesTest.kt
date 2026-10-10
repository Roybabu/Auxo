package com.invision.pos.domain.billing

import com.invision.pos.domain.model.GstRate
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.StoreSettings
import com.invision.pos.domain.model.formatRatePercent
import com.invision.pos.domain.model.parseRatePercent
import com.invision.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IndianFormatsTest {

    @Test
    fun indianGrouping() {
        assertEquals("0.05", IndianNumberFormat.amount(Money(5)))
        assertEquals("999.00", IndianNumberFormat.amount(Money(99_900)))
        assertEquals("1,000.00", IndianNumberFormat.amount(Money(1_000_00)))
        assertEquals("1,23,456.78", IndianNumberFormat.amount(Money(1_23_456_78)))
        assertEquals("12,34,56,789.00", IndianNumberFormat.amount(Money(12_34_56_789_00)))
        assertEquals("-₹49.00", IndianNumberFormat.rupees(Money(-49_00)))
    }

    @Test
    fun amountInWords() {
        assertEquals("Rupees Zero Only", AmountInWords.rupees(Money.ZERO))
        assertEquals("Rupees One Hundred Eighteen Only", AmountInWords.rupees(Money(118_00)))
        assertEquals("Rupees One Lakh Five Hundred and Fifty Paise Only", AmountInWords.rupees(Money(1_00_500_50)))
        assertEquals("Twelve Crore Thirty Four Lakh Fifty Six Thousand Seven Hundred Eighty Nine", AmountInWords.words(12_34_56_789))
        assertEquals("Twenty", AmountInWords.words(20))
    }
}

class IdentifiersTest {

    @Test
    fun gstin_checksumAndState() {
        assertTrue(Gstin.isValid("19AABCU9603R1ZK"))
        assertTrue(Gstin.isValid(" 27aapfu0939f1zv "))
        assertFalse(Gstin.isValid("19AABCU9603R1ZX")) // wrong check character
        assertFalse(Gstin.isValid("00AABCU9603R1ZK")) // no state 00
        assertFalse(Gstin.isValid("19AABCU9603R1Z"))
        assertEquals("27", Gstin.stateCode("27AAPFU0939F1ZV"))
    }

    @Test
    fun mobileNumbers() {
        assertEquals("9830012345", IndianPhone.normalizeMobile("98300 12345"))
        assertEquals("9830012345", IndianPhone.normalizeMobile("+91 98300-12345"))
        assertEquals("9830012345", IndianPhone.normalizeMobile("09830012345"))
        assertNull(IndianPhone.normalizeMobile("5830012345"))
        assertNull(IndianPhone.normalizeMobile("98300"))
        assertTrue(IndianPhone.isValidContactNumber("033 2229 1234"))
        assertFalse(IndianPhone.isValidContactNumber("12ab"))
    }

    @Test
    fun upi() {
        assertTrue(Upi.isValidVpa("roymart@okaxis"))
        assertTrue(Upi.isValidVpa("9830012345@ybl"))
        assertFalse(Upi.isValidVpa("roymart"))
        assertFalse(Upi.isValidVpa("roy mart@okaxis"))
        val uri = Upi.paymentUri("roymart@okaxis", "Roy Mart", Money(1_250_50), "Bill INV/26-27/00001", "")
        assertEquals("upi://pay?pa=roymart%40okaxis&pn=Roy%20Mart&am=1250.50&cu=INR&tn=Bill%20INV%2F26-27%2F00001", uri)
    }

    @Test
    fun states() {
        assertEquals("West Bengal", IndianStates.byCode("19")?.name)
        assertEquals(37, IndianStates.ALL.size)
        assertEquals(IndianStates.ALL.size, IndianStates.ALL.map { it.code }.toSet().size)
    }
}

class GstRateTest {

    @Test
    fun parseAndFormatPercent() {
        assertEquals(500, parseRatePercent("5"))
        assertEquals(1_250, parseRatePercent("12.5"))
        assertEquals(25, parseRatePercent("0.25"))
        assertNull(parseRatePercent("101"))
        assertNull(parseRatePercent("5.555"))
        assertNull(parseRatePercent("abc"))
        assertEquals("12.5", formatRatePercent(1_250))
        assertEquals("0", formatRatePercent(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun fixedRate_rejectsOutOfRange() {
        GstRate.Fixed(10_001)
    }
}

class DiscountParseTest {

    @Test
    fun parse() {
        assertEquals(DiscountParse.None, Discount.parse("", isPercent = false))
        assertEquals(DiscountParse.None, Discount.parse("0", isPercent = true))
        assertEquals(DiscountParse.Parsed(Discount.Amount(Money(5_050))), Discount.parse("50.5", isPercent = false))
        assertEquals(DiscountParse.Parsed(Discount.Percent(1_050)), Discount.parse("10.5", isPercent = true))
        assertEquals(DiscountParse.Invalid, Discount.parse("101", isPercent = true))
        assertEquals(DiscountParse.Invalid, Discount.parse("abc", isPercent = false))
    }
}

class PaymentValidatorTest {

    private val total = Money(1_000_00)

    @Test
    fun exactCash() {
        val check = PaymentValidator.check(listOf(PaymentDraft(1, PaymentMethod.CASH, "1000")), total)
        assertTrue(check is PaymentCheck.Valid)
        assertEquals(Money.ZERO, (check as PaymentCheck.Valid).change)
    }

    @Test
    fun cashWithChange() {
        val check = PaymentValidator.check(listOf(PaymentDraft(1, PaymentMethod.CASH, "1000", tendered = "2000")), total) as PaymentCheck.Valid
        assertEquals(Money(1_000_00), check.change)
        assertEquals(Money(2_000_00), check.payments.single().tendered)
        assertEquals(Money(1_000_00), check.payments.single().change)
    }

    @Test
    fun splitPayment() {
        val drafts = listOf(
            PaymentDraft(1, PaymentMethod.CASH, "400"),
            PaymentDraft(2, PaymentMethod.UPI, "350", reference = "415263748596"),
            PaymentDraft(3, PaymentMethod.CARD, "250", reference = "4242"),
        )
        val check = PaymentValidator.check(drafts, total) as PaymentCheck.Valid
        assertEquals(listOf(PaymentMethod.CASH, PaymentMethod.UPI, PaymentMethod.CARD), check.payments.map { it.method })
        assertEquals("4242", check.payments[2].reference)
    }

    @Test
    fun shortOrOverpaid() {
        val short = PaymentValidator.check(listOf(PaymentDraft(1, PaymentMethod.UPI, "900")), total) as PaymentCheck.Invalid
        assertEquals(Money(100_00), short.remaining)
        val over = PaymentValidator.check(listOf(PaymentDraft(1, PaymentMethod.CARD, "1100")), total) as PaymentCheck.Invalid
        assertEquals(Money(-100_00), over.remaining)
    }

    @Test
    fun fieldErrors() {
        val check = PaymentValidator.check(
            listOf(
                PaymentDraft(1, PaymentMethod.CASH, ""),
                PaymentDraft(2, PaymentMethod.CASH, "500", tendered = "100"),
                PaymentDraft(3, PaymentMethod.CARD, "500", reference = "12"),
                PaymentDraft(4, PaymentMethod.UPI, "abc"),
            ),
            total,
        ) as PaymentCheck.Invalid
        assertEquals(PaymentError.AMOUNT_REQUIRED, check.errors[1])
        assertEquals(PaymentError.TENDERED_TOO_LOW, check.errors[2])
        assertEquals(PaymentError.REFERENCE_INVALID, check.errors[3])
        assertEquals(PaymentError.AMOUNT_INVALID, check.errors[4])
        assertTrue((PaymentValidator.check(emptyList(), total) as PaymentCheck.Invalid).noPayments)
    }
}

class InvoiceNumbersTest {

    @Test
    fun financialYear() {
        assertEquals(2026, InvoiceNumbers.financialYearStart(LocalDate.of(2026, 4, 1)))
        assertEquals(2025, InvoiceNumbers.financialYearStart(LocalDate.of(2026, 3, 31)))
        assertEquals("26-27", InvoiceNumbers.financialYearLabel(2026))
        assertEquals("99-00", InvoiceNumbers.financialYearLabel(2099))
    }

    @Test
    fun format_isAtMost16Characters() {
        val number = InvoiceNumbers.format("INV", 2026, 42)
        assertEquals("INV/26-27/00042", number)
        assertTrue(InvoiceNumbers.format("ABCD", 2026, 99_999).length <= 16)
        assertTrue(InvoiceNumbers.isValidPrefix("CN"))
        assertFalse(InvoiceNumbers.isValidPrefix("inv"))
        assertFalse(InvoiceNumbers.isValidPrefix("TOOLONG"))
    }
}

class CartValidatorTest {

    private val settings = TestData.settings

    private fun check(cart: Cart, registered: Boolean = true): CartCheck {
        val totals = BillCalculator.calculate(cart, settings.copy(gstRegistered = registered).taxModeFor(cart))
        return CartValidator.check(cart, totals, registered)
    }

    @Test
    fun validCart() {
        val product = TestData.biscuits()
        val cart = Cart().add(CartLine.from(product, product.variants.single()))
        assertTrue(check(cart).canCheckout)
    }

    @Test
    fun stockAndGstProblems() {
        val product = TestData.tShirt()
        val large = product.variants[1] // 1 in stock
        val noGst = TestData.biscuits().copy(gstRate = null)
        val cart = Cart()
            .add(CartLine.from(product, large, quantity = 2))
            .add(CartLine.from(noGst, noGst.variants.single()))
            .add(CartLine.from(product, product.variants[0]).copy(availableStock = null))
        val result = check(cart)
        assertEquals(LineIssue.NotEnoughStock(1), result.lineIssues[large.id])
        assertEquals(LineIssue.MissingGstRate, result.lineIssues[noGst.variants.single().id])
        assertEquals(LineIssue.Unavailable, result.lineIssues[product.variants[0].id])
        assertFalse(result.canCheckout)
        // An unregistered store doesn't need GST rates.
        assertFalse(check(cart, registered = false).lineIssues.containsKey(noGst.variants.single().id))
    }

    @Test
    fun customerDetails() {
        assertEquals(emptySet<CustomerIssue>(), CartValidator.checkCustomer(CustomerDetails()))
        assertEquals(setOf(CustomerIssue.INVALID_PHONE), CartValidator.checkCustomer(CustomerDetails(phone = "12345")))
        assertEquals(
            setOf(CustomerIssue.INVALID_GSTIN, CustomerIssue.NAME_REQUIRED_WITH_GSTIN),
            CartValidator.checkCustomer(CustomerDetails(gstin = "19AABCU9603R1ZX")),
        )
        assertTrue(CartValidator.checkCustomer(CustomerDetails(name = "Sen Traders", gstin = "19AABCU9603R1ZK")).isEmpty())
    }

    @Test
    fun emptyCartCannotCheckout() {
        assertFalse(check(Cart()).canCheckout)
    }
}

class SettingsValidatorTest {

    @Test
    fun validSettings() {
        assertTrue(SettingsValidator.check(TestData.settings).isEmpty())
    }

    @Test
    fun problems() {
        val errors = SettingsValidator.check(
            StoreSettings(
                storeName = " ",
                gstin = "27AAPFU0939F1ZV", // Maharashtra
                stateCode = "19", // West Bengal
                invoicePrefix = "INV",
                creditNotePrefix = "INV",
                upiId = "not-an-id",
                phone = "x",
            ),
        )
        assertEquals(SettingsError.REQUIRED, errors[SettingsField.STORE_NAME])
        assertEquals(SettingsError.GSTIN_STATE_MISMATCH, errors[SettingsField.GSTIN])
        assertEquals(SettingsError.SAME_PREFIX, errors[SettingsField.CREDIT_NOTE_PREFIX])
        assertEquals(SettingsError.INVALID_UPI_ID, errors[SettingsField.UPI_ID])
        assertEquals(SettingsError.INVALID_PHONE, errors[SettingsField.PHONE])
    }

    @Test
    fun registeredStoreNeedsState_unregisteredDoesNot() {
        assertEquals(SettingsError.REQUIRED, SettingsValidator.check(StoreSettings())[SettingsField.STATE])
        assertNull(SettingsValidator.check(StoreSettings(gstRegistered = false))[SettingsField.STATE])
    }
}
