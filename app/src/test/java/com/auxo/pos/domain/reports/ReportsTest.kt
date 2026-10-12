package com.auxo.pos.domain.reports

import com.auxo.pos.domain.billing.ReturnBuild
import com.auxo.pos.domain.billing.ReturnBuilder
import com.auxo.pos.domain.billing.SampleSales
import com.auxo.pos.domain.customers.Ledger
import com.auxo.pos.domain.model.BatchWithProduct
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.StockBatch
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import com.auxo.pos.domain.model.StockMovementRecord
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ReportsTest {

    private val sale = SampleSales.shirtsAndBiscuits()
    private val creditNote = (ReturnBuilder.build(sale, mapOf(sale.lines[0].id to 1), PaymentMethod.UPI, "Size", TestData.clock.millis()) as ReturnBuild.Ready)
        .creditNote.copy(id = 8, number = "CN/26-27/00001")
    private val refund = creditNote.payments.single().amount

    private fun amount(value: ReportValue): Money = (value as ReportValue.Amount).value

    @Test
    fun salesSummary_netsReturnsAgainstSales() {
        val table = Reports.salesSummary(listOf(sale, creditNote), ZoneOffset.UTC)
        val row = table.rows.single()
        assertEquals(ReportValue.Day(TestData.today), row[0])
        assertEquals(ReportValue.Count(1), row[1])
        assertEquals(ReportValue.Count(1), row[2])
        assertEquals(sale.grandTotal - creditNote.grandTotal, amount(row.last()))
        // The totals row matches the only day.
        assertEquals(row.drop(1), table.totals!!.drop(1))
        assertEquals(10, table.columns.size)
    }

    @Test
    fun itemSales_areNetOfReturns() {
        val table = Reports.itemSales(listOf(sale, creditNote))
        val byName = table.rows.associateBy { (it[0] as ReportValue.Text).value }
        assertEquals(ReportValue.Count(2), byName.getValue("Cotton T-Shirt")[3])
        assertEquals(ReportValue.Count(2), byName.getValue("Butter Biscuits")[3])
        assertEquals(ReportValue.Count(4), table.totals!![3])
        assertTrue(Reports.itemSales(listOf(sale), ItemKind.DISHES).isEmpty)
    }

    @Test
    fun collections_addAccountPayments_andSubtractRefunds() {
        val accountPayment = PaymentRecord(method = PaymentMethod.CASH, amount = Money(50_000), kind = PaymentKind.PAYMENT, customerName = "Anita")
        val table = Reports.collections(listOf(sale, creditNote), listOf(accountPayment))
        val cash = table.rows[0]
        val upi = table.rows[1]
        val credit = table.rows[3]
        assertEquals(sale.grandTotal, amount(cash[1]))
        assertEquals(Money(50_000), amount(cash[3]))
        assertEquals(sale.grandTotal + Money(50_000), amount(cash[4]))
        assertEquals(refund, amount(upi[2]))
        assertEquals(Money(-refund.minorUnits), amount(upi[4]))
        assertEquals(ReportValue.Text("On account"), credit[0])
        assertEquals(ReportValue.Empty, credit[4]) // credit is not money received
        assertEquals(sale.grandTotal + Money(50_000) - refund, amount(table.totals!!.last()))
    }

    @Test
    fun gstAndHsn_areNetOfCreditNotes() {
        val gst = Reports.gstRates(listOf(sale, creditNote))
        val five = gst.rows.single()
        assertEquals(ReportValue.Rate(500), five[0])
        val taxable = sale.taxableTotal - creditNote.taxableTotal
        assertEquals(taxable, amount(five[1]))
        assertEquals(sale.taxTotal - creditNote.taxTotal, amount(five[5]))

        val hsn = Reports.hsnSummary(listOf(sale, creditNote))
        assertEquals(listOf("1905", "6109"), hsn.rows.map { (it[0] as ReportValue.Text).value })
        assertEquals(ReportValue.Count(2), hsn.rows[1][3]) // 3 shirts sold, 1 returned
    }

    @Test
    fun stockValue_lowStock_andExpiry() {
        val products = listOf(TestData.tShirt(), TestData.flour(grams = 2_500))
        val value = Reports.stockValue(products)
        val flourRow = value.rows.single { (it[0] as ReportValue.Text).value == "Wheat Flour" }
        assertEquals(ReportValue.Quantity(2_500, StockUnit.GRAM), flourRow[3])
        assertEquals(Money(11_250), amount(flourRow[4])) // 2.5 kg at ₹45/kg
        assertEquals(ReportValue.Empty, flourRow[5]) // ingredients have no selling value
        assertEquals(1, Reports.stockValue(products, ProductCategory.INGREDIENT).rows.size)

        val low = Reports.lowStock(listOf(TestData.tShirt().copy(preferredSupplierId = 3)), listOf(Supplier(id = 3, name = "Roy Textiles")))
        assertEquals(ReportValue.Text("Roy Textiles"), low.rows.single().last())

        val today = TestData.today
        fun batch(id: Long, expiry: LocalDate?) = BatchWithProduct(
            StockBatch(id = id, variantId = 41, expiryDate = expiry, quantity = 1_000), 4, "Wheat Flour", "", ProductCategory.INGREDIENT, StockUnit.GRAM, Money(4_500),
        )
        val batches = listOf(batch(1, today.minusDays(2)), batch(2, today.plusDays(5)), batch(3, today.plusDays(20)), batch(4, null))
        assertEquals(1, Reports.expiry(batches, today, ExpiryWindow.EXPIRED).rows.size)
        assertEquals(2, Reports.expiry(batches, today, ExpiryWindow.WITHIN_7_DAYS).rows.size)
        assertEquals(3, Reports.expiry(batches, today, ExpiryWindow.WITHIN_30_DAYS).rows.size)
        val all = Reports.expiry(batches, today, ExpiryWindow.ALL)
        assertEquals(4, all.rows.size)
        assertEquals(ReportValue.Count(-2), all.rows[0][4]) // days left, already passed
        assertEquals(ReportValue.Empty, all.rows[3][3]) // no expiry date goes last
        assertEquals(Money(4 * 4_500), amount(all.totals!!.last()))
    }

    @Test
    fun wastage_valuesWriteOffsAtCost_andIgnoresSales() {
        fun record(change: Int, reason: StockChangeReason, cost: Money?) = StockMovementRecord(
            movement = StockMovement(variantId = 41, quantityChange = change, reason = reason, createdAtMillis = 0),
            productId = 4, productName = "Wheat Flour", variantLabel = "", category = ProductCategory.INGREDIENT,
            stockUnit = StockUnit.GRAM, batchNumber = "F1", reference = null, unitCost = cost,
        )
        val records = listOf(
            record(-500, StockChangeReason.EXPIRED, Money(4_500)),
            record(-200, StockChangeReason.SPOILED, null),
            record(-300, StockChangeReason.USED, Money(4_500)),
        )
        val table = Reports.wastage(records)
        assertEquals(2, table.rows.size)
        assertEquals(ReportValue.Quantity(500, StockUnit.GRAM), table.rows[0][4])
        assertEquals(Money(2_250), amount(table.rows[0][6]))
        assertEquals(ReportValue.Empty, table.rows[1][6]) // cost unknown
        assertEquals(Money(2_250), amount(table.totals!![6]))
        assertEquals(1, Reports.stockMovements(records, setOf(StockChangeReason.USED)).rows.size)
    }

    @Test
    fun customerBalances_andStatement() {
        val anita = Customer(id = 1, name = "Anita", phone = "9830012345", creditLimit = Money(100_000))
        val ravi = Customer(id = 2, name = "Ravi")
        val accounts = listOf(CustomerAccount(ravi, Money.ZERO), CustomerAccount(anita, Money(25_000), lastActivityMillis = 5))
        assertEquals(1, Reports.customerBalances(accounts).rows.size)
        assertEquals(2, Reports.customerBalances(accounts, onlyOutstanding = false).rows.size)

        val entries = listOf(
            LedgerEntry(id = 1, customerId = 1, type = LedgerEntryType.SALE, amount = Money(40_000), invoiceNumber = "INV/26-27/00001", createdAtMillis = 1),
            LedgerEntry(id = 2, customerId = 1, type = LedgerEntryType.PAYMENT, amount = Money(-15_000), method = PaymentMethod.UPI, reference = "UTR1", createdAtMillis = 2),
        )
        val statement = Reports.customerStatement(anita, Ledger.statement(entries))
        assertEquals("Statement: Anita", statement.title)
        assertEquals(ReportValue.Amount(Money(40_000)), statement.rows[0][5])
        assertEquals(ReportValue.Empty, statement.rows[0][6])
        assertEquals(ReportValue.Text("UPI"), statement.rows[1][3])
        assertEquals(ReportValue.Amount(Money(25_000)), statement.totals!![7])
    }
}

class ReportCsvTest {

    @Test
    fun csv_quotesWhereNeeded_andKeepsNumbersPlain() {
        val table = ReportTable(
            title = "Test",
            columns = listOf(ReportColumn("Item"), ReportColumn("Amount", ColumnAlign.END), ReportColumn("Qty", ColumnAlign.END)),
            rows = listOf(
                listOf(ReportValue.Text("Shirt, \"blue\""), ReportValue.Amount(Money(-1_234)), ReportValue.Quantity(1_250, StockUnit.GRAM)),
                listOf(ReportValue.Text("=SUM(A1)"), ReportValue.Empty, ReportValue.Count(3)),
            ),
            totals = listOf(ReportValue.Text("Total"), ReportValue.Amount(Money(-1_234)), ReportValue.Empty),
        )
        val csv = ReportCsv.write(table, ZoneOffset.UTC)
        assertEquals(
            "Item,Amount,Qty\r\n\"Shirt, \"\"blue\"\"\",-12.34,1.25 kg\r\n'=SUM(A1),,3\r\nTotal,-12.34,\r\n",
            csv,
        )
    }

    @Test
    fun csv_writesDatesTimesAndRates() {
        val millis = TestData.clock.millis() // 10 Oct 2026, 10:00 UTC
        assertEquals("2026-10-10 10:00", ReportCsv.cell(ReportValue.Moment(millis), ZoneOffset.UTC))
        assertEquals("2026-10-10 15:30", ReportCsv.cell(ReportValue.Moment(millis), ZoneId.of("Asia/Kolkata")))
        assertEquals("2026-10-10", ReportCsv.cell(ReportValue.Day(TestData.today), ZoneOffset.UTC))
        assertEquals("5%", ReportCsv.cell(ReportValue.Rate(500), ZoneOffset.UTC))
        assertEquals("12.5%", ReportCsv.cell(ReportValue.Rate(1_250), ZoneOffset.UTC))
        assertEquals("plain", ReportCsv.escape("plain"))
        assertEquals("'+91", ReportCsv.escape("+91"))
    }
}

class ReportPeriodTest {

    private val today = TestData.today // Saturday 10 October 2026

    @Test
    fun presets_coverWholeDays() {
        assertEquals(ReportPeriod(PeriodPreset.TODAY, today, today), ReportPeriod.of(PeriodPreset.TODAY, today))
        assertEquals(LocalDate.of(2026, 10, 4), ReportPeriod.of(PeriodPreset.LAST_7_DAYS, today).from)
        assertEquals(LocalDate.of(2026, 10, 1), ReportPeriod.of(PeriodPreset.THIS_MONTH, today).from)
        val lastMonth = ReportPeriod.of(PeriodPreset.LAST_MONTH, today)
        assertEquals(LocalDate.of(2026, 9, 1), lastMonth.from)
        assertEquals(LocalDate.of(2026, 9, 30), lastMonth.to)
        assertEquals(LocalDate.of(2026, 4, 1), ReportPeriod.of(PeriodPreset.THIS_FINANCIAL_YEAR, today).from)
        assertEquals(LocalDate.of(2025, 4, 1), ReportPeriod.of(PeriodPreset.THIS_FINANCIAL_YEAR, LocalDate.of(2026, 2, 1)).from)
        val all = ReportPeriod.of(PeriodPreset.ALL, today)
        assertNull(all.from)
        assertEquals(0L to Long.MAX_VALUE, all.bounds(ZoneOffset.UTC))
        assertEquals("all", all.slug)
    }

    @Test
    fun customRange_isSwappedWhenBackwards_andBoundsEndAtMidnightAfter() {
        val period = ReportPeriod.of(PeriodPreset.CUSTOM, today, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1))
        assertEquals(LocalDate.of(2026, 10, 1), period.from)
        assertEquals(LocalDate.of(2026, 10, 5), period.to)
        val (start, end) = period.bounds(ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), start)
        assertEquals(LocalDate.of(2026, 10, 6).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), end)
        assertEquals("2026-10-01_2026-10-05", period.slug)
        assertEquals("2026-10-10", ReportPeriod.of(PeriodPreset.TODAY, today).slug)
    }

    @Test
    fun expiryWindows_alwaysIncludeExpiredStock() {
        val expired = StockBatch(variantId = 1, quantity = 1, expiryDate = today.minusDays(1))
        val soon = StockBatch(variantId = 1, quantity = 1, expiryDate = today.plusDays(7))
        val noDate = StockBatch(variantId = 1, quantity = 1)
        assertTrue(ExpiryWindow.EXPIRED.includes(expired, today))
        assertTrue(!ExpiryWindow.EXPIRED.includes(soon, today))
        assertTrue(ExpiryWindow.WITHIN_7_DAYS.includes(expired, today))
        assertTrue(ExpiryWindow.WITHIN_7_DAYS.includes(soon, today))
        assertTrue(!ExpiryWindow.WITHIN_30_DAYS.includes(noDate, today))
        assertTrue(ExpiryWindow.ALL.includes(noDate, today))
    }
}
