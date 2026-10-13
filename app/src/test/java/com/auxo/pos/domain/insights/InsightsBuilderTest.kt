package com.auxo.pos.domain.insights

import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.model.PurchaseReceiptLine
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class InsightsBuilderTest {

    private val zone = TestData.clock.zone
    private val today = TestData.today
    private val shirt = TestData.tShirt()
    private val biscuits = TestData.biscuits()
    private val categories = listOf(shirt, biscuits).flatMap { p -> p.variants.map { it.id to p.category } }.toMap()

    private fun at(date: LocalDate, hour: Int = 10): Long = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun sale(
        product: Product,
        quantity: Int,
        millis: Long,
        payments: (Money) -> List<PaymentRecord> = { listOf(PaymentRecord(method = PaymentMethod.CASH, amount = it)) },
    ): Invoice {
        val cart = Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity = quantity)))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        return SaleBuilder.build(cart, totals, TestData.settings, payments(totals.grandTotal), millis)
    }

    /** A credit note for the whole of [sale], refunded in [method]. */
    private fun creditNote(sale: Invoice, method: PaymentMethod = PaymentMethod.CASH): Invoice = sale.copy(
        type = InvoiceType.RETURN,
        originalInvoiceId = 1,
        payments = listOf(PaymentRecord(method = method, kind = PaymentKind.REFUND, amount = sale.grandTotal)),
    )

    private fun build(
        period: InsightsPeriod,
        invoices: List<Invoice>,
        receipts: List<PurchaseReceipt> = emptyList(),
        categoryByVariant: Map<Long, com.auxo.pos.domain.model.ProductCategory> = categories,
        asOf: LocalDate = today,
    ) = InsightsBuilder.build(period, asOf, zone, invoices, receipts, categoryByVariant)

    private fun receipt(date: LocalDate, quantity: Int, unitCostMinor: Long) = PurchaseReceipt(
        receivedAtMillis = at(date),
        lines = listOf(
            PurchaseReceiptLine(variantId = 11, productName = "Cotton T-Shirt", variantLabel = "M", quantity = quantity, unitCost = Money(unitCostMinor)),
        ),
    )

    @Test
    fun collected_countsSplitPaymentsOnce_andTakesRefundsOffTheSameMethod() {
        val split = sale(shirt, 2, at(today)) { total ->
            val upi = Money(total.minorUnits / 4)
            listOf(
                PaymentRecord(method = PaymentMethod.UPI, amount = upi),
                PaymentRecord(method = PaymentMethod.CASH, amount = total - upi),
            )
        }
        val refund = creditNote(sale(shirt, 1, at(today)), PaymentMethod.CASH)
        val onAccount = sale(biscuits, 1, at(today)) { listOf(PaymentRecord(method = PaymentMethod.CREDIT, amount = it)) }

        val collected = InsightsBuilder.collected(listOf(split, refund, onAccount))

        val upiPart = split.payments.single { it.method == PaymentMethod.UPI }.amount
        val cashPart = split.payments.single { it.method == PaymentMethod.CASH }.amount
        assertEquals(upiPart, collected.upi)
        assertEquals(cashPart - refund.grandTotal, collected.cash)
    }

    @Test
    fun collected_withNoBills_isZero() {
        assertEquals(Collected(Money.ZERO, Money.ZERO), InsightsBuilder.collected(emptyList()))
    }

    @Test
    fun noActivity_isAllZero() {
        val insights = build(InsightsPeriod.LAST_7_DAYS, emptyList())
        assertEquals(Money.ZERO, insights.sales)
        assertEquals(0, insights.billCount)
        assertFalse(insights.hasSales)
        assertFalse(insights.hasPurchases)
        assertEquals(7, insights.trend.size)
        assertTrue(insights.trend.all { it.amount == Money.ZERO })
        assertTrue(insights.paymentMethods.isEmpty())
        assertTrue(insights.departments.isEmpty())
        assertTrue(insights.topProducts.isEmpty())
    }

    @Test
    fun week_putsEachBillOnItsOwnDay() {
        val recent = sale(shirt, 1, at(today))
        val older = sale(shirt, 2, at(today.minusDays(3)))
        val insights = build(InsightsPeriod.LAST_7_DAYS, listOf(older, recent))

        assertEquals(7, insights.trend.size)
        assertEquals(today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), insights.trend.last().label)
        assertEquals(recent.grandTotal, insights.trend[6].amount)
        assertEquals(older.grandTotal, insights.trend[3].amount)
        assertEquals(listOf(0, 1, 2, 4, 5), listOf(0, 1, 2, 4, 5).filter { insights.trend[it].amount == Money.ZERO })
        assertEquals(recent.grandTotal + older.grandTotal, insights.sales)
        assertEquals(2, insights.billCount)
        assertEquals(insights.sales, insights.comparison.fold(Money.ZERO) { acc, b -> acc + b.sales })
    }

    @Test
    fun aReturn_takesOffTheSale_butIsNotCountedAsABill() {
        val bill = sale(shirt, 2, at(today))
        val insights = build(InsightsPeriod.LAST_7_DAYS, listOf(bill, creditNote(bill)))
        assertEquals(Money.ZERO, insights.sales)
        assertEquals(1, insights.billCount)
        assertEquals(Money.ZERO, insights.trend.last().amount)
        assertTrue(insights.departments.isEmpty())
        assertTrue(insights.topProducts.isEmpty())
    }

    @Test
    fun today_usesTwoHourSlices() {
        val insights = build(
            InsightsPeriod.TODAY,
            listOf(sale(shirt, 1, at(today, 10)), sale(shirt, 1, at(today, 11)), sale(shirt, 1, at(today, 23))),
        )
        assertEquals(12, insights.trend.size)
        assertEquals("00", insights.trend.first().label)
        assertEquals("22", insights.trend.last().label)
        val one = sale(shirt, 1, 0).grandTotal
        assertEquals(one + one, insights.trend[5].amount)
        assertEquals(one, insights.trend[11].amount)
        assertEquals(1, insights.comparison.size)
        assertEquals(insights.sales, insights.comparison.single().sales)
    }

    @Test
    fun month_hasADayPerDaySoFar_andWeeksForTheBars() {
        val first = sale(shirt, 1, at(LocalDate.of(2026, 10, 1)))
        val purchase = receipt(LocalDate.of(2026, 10, 9), quantity = 10, unitCostMinor = 20_000)
        val insights = build(InsightsPeriod.THIS_MONTH, listOf(first), listOf(purchase))

        assertEquals((1..10).map { it.toString() }, insights.trend.map { it.label })
        assertEquals(first.grandTotal, insights.trend[0].amount)
        assertEquals(listOf("1–7", "8–14"), insights.comparison.map { it.label })
        assertEquals(first.grandTotal, insights.comparison[0].sales)
        assertEquals(Money(200_000), insights.comparison[1].purchases)
    }

    @Test
    fun financialYear_runsFromAprilByMonth() {
        val april = sale(shirt, 1, at(LocalDate.of(2026, 4, 5)))
        val october = sale(shirt, 1, at(LocalDate.of(2026, 10, 1)))
        val insights = build(InsightsPeriod.THIS_YEAR, listOf(april, october))
        assertEquals(listOf("Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct"), insights.trend.map { it.label })
        assertEquals(april.grandTotal, insights.trend[0].amount)
        assertEquals(october.grandTotal, insights.trend[6].amount)
        assertEquals(LocalDate.of(2026, 4, 1), insights.from)
    }

    @Test
    fun financialYear_inJanuary_stillStartsInAprilOfTheYearBefore() {
        val insights = build(InsightsPeriod.THIS_YEAR, emptyList(), asOf = LocalDate.of(2027, 1, 15))
        assertEquals(10, insights.trend.size)
        assertEquals("Apr", insights.trend.first().label)
        assertEquals("Jan", insights.trend.last().label)
        assertEquals(LocalDate.of(2026, 4, 1), insights.from)
    }

    @Test
    fun purchases_areTotalledFromGoodsReceived() {
        val insights = build(
            InsightsPeriod.LAST_7_DAYS,
            emptyList(),
            listOf(receipt(today, 10, 20_000), receipt(today.minusDays(1), 5, 10_000)),
        )
        assertEquals(Money(250_000), insights.purchases)
        assertTrue(insights.hasPurchases)
        assertFalse(insights.hasSales)
        assertEquals(Money(200_000), insights.comparison[6].purchases)
        assertEquals(Money(50_000), insights.comparison[5].purchases)
    }

    @Test
    fun payments_areGroupedByMethod_netOfRefunds() {
        val split = sale(shirt, 2, at(today)) { total ->
            val cash = Money(total.minorUnits / 4)
            listOf(
                PaymentRecord(method = PaymentMethod.UPI, amount = total - cash),
                PaymentRecord(method = PaymentMethod.CASH, amount = cash),
            )
        }
        val onAccount = sale(biscuits, 1, at(today)) { listOf(PaymentRecord(method = PaymentMethod.CREDIT, amount = it)) }
        val refund = Money(1_000)
        val returned = split.copy(
            type = InvoiceType.RETURN,
            payments = listOf(PaymentRecord(method = PaymentMethod.UPI, kind = PaymentKind.REFUND, amount = refund)),
        )
        val insights = build(InsightsPeriod.LAST_7_DAYS, listOf(split, onAccount, returned))

        val byMethod = insights.paymentMethods.associate { it.method to it.amount }
        assertEquals(listOf(PaymentMethod.CASH, PaymentMethod.UPI, PaymentMethod.CREDIT), insights.paymentMethods.map { it.method })
        assertEquals(Money(split.grandTotal.minorUnits / 4), byMethod.getValue(PaymentMethod.CASH))
        assertEquals(split.grandTotal - Money(split.grandTotal.minorUnits / 4) - refund, byMethod.getValue(PaymentMethod.UPI))
        assertEquals(onAccount.grandTotal, byMethod.getValue(PaymentMethod.CREDIT))
        assertFalse(byMethod.containsKey(PaymentMethod.CARD))
    }

    @Test
    fun aFullRefund_leavesNoSlice() {
        val bill = sale(shirt, 1, at(today))
        val insights = build(InsightsPeriod.LAST_7_DAYS, listOf(bill, creditNote(bill).copy(grandTotal = Money(1))), emptyList())
        // Cash taken equals the refund, so nothing is left to show.
        assertTrue(insights.paymentMethods.isEmpty())
    }

    @Test
    fun departments_followProductCategory_bestFirst() {
        val shirts = sale(shirt, 3, at(today))
        val food = sale(biscuits, 1, at(today))
        val insights = build(InsightsPeriod.LAST_7_DAYS, listOf(food, shirts))
        val expected = listOf(Department.GARMENTS to shirts.grandTotal, Department.PACKAGED_FOOD to food.grandTotal)
            .sortedByDescending { it.second }
        assertEquals(expected.map { it.first }, insights.departments.map { it.department })
        assertEquals(shirts.lines.sumOf { it.total.minorUnits }, insights.departments.first { it.department == Department.GARMENTS }.amount.minorUnits)
    }

    @Test
    fun itemsWithNoKnownCategory_goUnderOther() {
        val bill = sale(shirt, 1, at(today))
        val insights = build(InsightsPeriod.LAST_7_DAYS, listOf(bill), categoryByVariant = emptyMap())
        assertEquals(listOf(Department.OTHER), insights.departments.map { it.department })
    }

    @Test
    fun restaurantDishes_areTheirOwnDepartment() {
        val bill = sale(shirt, 1, at(today))
        val dish = bill.copy(lines = bill.lines.map { it.copy(variantId = null, menuItemId = 7, productName = "Masala Dosa") })
        val insights = build(InsightsPeriod.LAST_7_DAYS, listOf(dish))
        assertEquals(listOf(Department.RESTAURANT), insights.departments.map { it.department })
        assertEquals("Masala Dosa", insights.topProducts.single().name)
    }

    @Test
    fun restaurantProducts_areTheRestaurantDepartment() {
        val bill = sale(shirt, 1, at(today))
        val variantId = bill.lines.single().variantId!!
        val insights = build(
            InsightsPeriod.LAST_7_DAYS, listOf(bill),
            categoryByVariant = mapOf(variantId to com.auxo.pos.domain.model.ProductCategory.RESTAURANT),
        )
        assertEquals(listOf(Department.RESTAURANT), insights.departments.map { it.department })
    }

    @Test
    fun topProducts_areRankedByNetSales_andReturnedOnesDropOut() {
        val shirts = sale(shirt, 3, at(today))
        val food = sale(biscuits, 1, at(today))
        val returnedFood = creditNote(food)
        val insights = build(InsightsPeriod.LAST_7_DAYS, listOf(shirts, food, returnedFood))
        assertEquals(listOf("Cotton T-Shirt"), insights.topProducts.map { it.name })
        assertEquals(3L, insights.topProducts.single().quantity)
    }

    @Test
    fun topProducts_stopAtFive() {
        val base = sale(shirt, 1, at(today))
        val bills = (1..6).map { n ->
            base.copy(lines = base.lines.map { it.copy(productName = "Item $n", total = Money(n * 1_000L)) })
        }
        val insights = build(InsightsPeriod.LAST_7_DAYS, bills)
        assertEquals(InsightsBuilder.TOP_PRODUCTS, insights.topProducts.size)
        assertEquals("Item 6", insights.topProducts.first().name)
        assertEquals("Item 2", insights.topProducts.last().name)
    }
}

class CompactMoneyTest {

    private fun format(minor: Long) = CompactMoney.format(Money(minor))

    @Test
    fun smallAmounts_areWholeRupees() {
        assertEquals("₹0", format(0))
        assertEquals("₹950", format(95_000))
        assertEquals("₹999", format(99_949))
    }

    @Test
    fun thousands_useK() {
        assertEquals("₹1.2K", format(120_000))
        assertEquals("₹1K", format(100_000))
        assertEquals("₹12.5K", format(1_250_000))
    }

    @Test
    fun lakhsAndCrores_followTheIndianStyle() {
        assertEquals("₹1.5L", format(15_000_000))
        assertEquals("₹1Cr", format(1_000_000_000))
        assertEquals("₹2.5Cr", format(2_500_000_000))
    }

    @Test
    fun negativeAmounts_keepTheirSign_butNotAtZero() {
        assertEquals("-₹1.2K", format(-120_000))
        assertEquals("₹0", format(-10))
    }
}
