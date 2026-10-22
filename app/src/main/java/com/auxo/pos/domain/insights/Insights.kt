package com.auxo.pos.domain.insights

import com.auxo.pos.domain.billing.InvoiceNumbers
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.reports.PeriodPreset
import com.auxo.pos.domain.reports.ReportPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** The date filters offered above the Home charts. */
enum class InsightsPeriod(val preset: PeriodPreset) {
    TODAY(PeriodPreset.TODAY),
    LAST_7_DAYS(PeriodPreset.LAST_7_DAYS),
    THIS_MONTH(PeriodPreset.THIS_MONTH),
    THIS_YEAR(PeriodPreset.THIS_FINANCIAL_YEAR),
}

/** Where an item sold sits for the "by department" chart: its product category, or a restaurant dish. */
enum class Department { GARMENTS, FOOTWEAR, ACCESSORIES, PACKAGED_FOOD, BEVERAGES, RESTAURANT, GENERAL, ELECTRONICS, PHARMACY, COSMETICS, LOOSE, JEWELLERY, OTHER }

data class SeriesPoint(val label: String, val amount: Money)

data class ComparisonBucket(val label: String, val sales: Money, val purchases: Money)

data class MethodSlice(val method: PaymentMethod, val amount: Money)

data class DepartmentSales(val department: Department, val amount: Money)

data class ProductRank(val name: String, val quantity: Long, val amount: Money)

/**
 * Everything the Home charts show for one period, worked out from saved bills and goods
 * receipts. "Sales" are net of returns and include tax, as on the Home tiles. "Purchases"
 * are the value of goods received from suppliers: Auxo records no other expenses, so
 * nothing here is a profit or a total of costs.
 */
data class Insights(
    val period: InsightsPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val sales: Money,
    val billCount: Int,
    val purchases: Money,
    /** Net sales over time, in the period's natural steps (hours, days or months). */
    val trend: List<SeriesPoint>,
    /** Net sales next to purchases received, in coarser steps so the bars stay readable. */
    val comparison: List<ComparisonBucket>,
    /** Money taken on bills per payment method, net of refunds; methods with nothing are left out. */
    val paymentMethods: List<MethodSlice>,
    /** Net sales per department, best first; departments with no net sales are left out. */
    val departments: List<DepartmentSales>,
    /** Best sellers by net sales, at most [InsightsBuilder.TOP_PRODUCTS]. */
    val topProducts: List<ProductRank>,
) {
    val hasSales: Boolean get() = billCount > 0
    val hasPurchases: Boolean get() = purchases > Money.ZERO
}

/** A run of equal slices of time (hours, days, weeks or months), each with a short label. */
class TimeBuckets private constructor(
    val labels: List<String>,
    private val indexFor: (LocalDateTime) -> Int,
) {
    val size: Int get() = labels.size

    /** The bucket [epochMillis] falls in as seen in [zone], or null if it is outside all of them. */
    fun indexOf(epochMillis: Long, zone: ZoneId): Int? {
        val index = indexFor(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime())
        return index.takeIf { it in labels.indices }
    }

    companion object {
        private fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

        /** Steps for the line chart: two-hour slices today, days for a week or month, months for the year. */
        fun trend(period: InsightsPeriod, today: LocalDate): TimeBuckets = when (period) {
            InsightsPeriod.TODAY -> TimeBuckets((0 until 24 step 2).map { String.format(Locale.ENGLISH, "%02d", it) }) { it.hour / 2 }
            InsightsPeriod.LAST_7_DAYS -> days(today.minusDays(6), today)
            InsightsPeriod.THIS_MONTH -> TimeBuckets((1..today.dayOfMonth).map { it.toString() }) { it.dayOfMonth - 1 }
            InsightsPeriod.THIS_YEAR -> months(today)
        }

        /** Coarser steps for the two-series bars: one pair today, days for a week, weeks for a month, months for the year. */
        fun comparison(period: InsightsPeriod, today: LocalDate): TimeBuckets = when (period) {
            InsightsPeriod.TODAY -> TimeBuckets(listOf("Today")) { 0 }
            InsightsPeriod.LAST_7_DAYS -> days(today.minusDays(6), today)
            InsightsPeriod.THIS_MONTH -> {
                val weeks = (today.dayOfMonth + 6) / 7
                val length = today.lengthOfMonth()
                TimeBuckets((0 until weeks).map { week -> "${week * 7 + 1}–${minOf(week * 7 + 7, length)}" }) { (it.dayOfMonth - 1) / 7 }
            }
            InsightsPeriod.THIS_YEAR -> months(today)
        }

        private fun days(start: LocalDate, end: LocalDate): TimeBuckets {
            val count = ChronoUnit.DAYS.between(start, end).toInt() + 1
            return TimeBuckets((0 until count).map { dayName(start.plusDays(it.toLong())) }) {
                ChronoUnit.DAYS.between(start, it.toLocalDate()).toInt()
            }
        }

        /** Months from April of the financial year containing [today] up to [today]'s month. */
        private fun months(today: LocalDate): TimeBuckets {
            val first = YearMonth.of(InvoiceNumbers.financialYearStart(today), 4)
            val count = ChronoUnit.MONTHS.between(first, YearMonth.from(today)).toInt() + 1
            return TimeBuckets((0 until count).map { first.plusMonths(it.toLong()).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }) {
                ChronoUnit.MONTHS.between(first, YearMonth.from(it)).toInt()
            }
        }
    }
}

/** Money actually collected on bills in one period, net of refunds. Credit (on account) is not money collected. */
data class Collected(val cash: Money, val upi: Money)

object InsightsBuilder {

    /**
     * Cash and UPI taken on [invoices], each payment counted once (split payments are separate
     * records) and refunds taken off the same method.
     */
    fun collected(invoices: List<Invoice>): Collected {
        fun net(method: PaymentMethod): Money {
            val records = invoices.flatMap { it.payments }.filter { it.method == method }
            val taken = records.filter { it.kind == PaymentKind.PAYMENT }.sumOf { it.amount.minorUnits }
            val refunded = records.filter { it.kind == PaymentKind.REFUND }.sumOf { it.amount.minorUnits }
            return Money(taken - refunded)
        }
        return Collected(cash = net(PaymentMethod.CASH), upi = net(PaymentMethod.UPI))
    }


    const val TOP_PRODUCTS = 5

    /**
     * Builds the charts' data for [period] as of [today]. [invoices] holds the sales and
     * credit notes in the period, [receipts] the goods received in it, and
     * [categoryByVariant] the category of each product variant, to place sales in a department.
     */
    fun build(
        period: InsightsPeriod,
        today: LocalDate,
        zone: ZoneId,
        invoices: List<Invoice>,
        receipts: List<PurchaseReceipt>,
        categoryByVariant: Map<Long, ProductCategory>,
    ): Insights {
        val range = ReportPeriod.of(period.preset, today)
        val trendBuckets = TimeBuckets.trend(period, today)
        val compareBuckets = TimeBuckets.comparison(period, today)
        val trend = LongArray(trendBuckets.size)
        val sales = LongArray(compareBuckets.size)
        val purchases = LongArray(compareBuckets.size)

        var salesTotal = 0L
        var bills = 0
        for (invoice in invoices) {
            val amount = sign(invoice) * invoice.grandTotal.minorUnits
            salesTotal += amount
            if (invoice.type == InvoiceType.SALE) bills++
            trendBuckets.indexOf(invoice.createdAtMillis, zone)?.let { trend[it] += amount }
            compareBuckets.indexOf(invoice.createdAtMillis, zone)?.let { sales[it] += amount }
        }

        var purchasesTotal = 0L
        for (receipt in receipts) {
            val amount = receipt.total.minorUnits
            purchasesTotal += amount
            compareBuckets.indexOf(receipt.receivedAtMillis, zone)?.let { purchases[it] += amount }
        }

        return Insights(
            period = period,
            from = range.from ?: today,
            to = range.to ?: today,
            sales = Money(salesTotal),
            billCount = bills,
            purchases = Money(purchasesTotal),
            trend = trendBuckets.labels.mapIndexed { i, label -> SeriesPoint(label, Money(trend[i])) },
            comparison = compareBuckets.labels.mapIndexed { i, label -> ComparisonBucket(label, Money(sales[i]), Money(purchases[i])) },
            paymentMethods = paymentMethods(invoices),
            departments = departments(invoices, categoryByVariant),
            topProducts = topProducts(invoices),
        )
    }

    private fun sign(invoice: Invoice): Long = if (invoice.type == InvoiceType.RETURN) -1L else 1L

    private fun paymentMethods(invoices: List<Invoice>): List<MethodSlice> {
        val payments = invoices.flatMap { it.payments }
        return PaymentMethod.entries.mapNotNull { method ->
            val taken = payments.filter { it.method == method && it.kind == PaymentKind.PAYMENT }.sumOf { it.amount.minorUnits }
            val refunded = payments.filter { it.method == method && it.kind == PaymentKind.REFUND }.sumOf { it.amount.minorUnits }
            (taken - refunded).takeIf { it > 0 }?.let { MethodSlice(method, Money(it)) }
        }
    }

    private fun departments(invoices: List<Invoice>, categoryByVariant: Map<Long, ProductCategory>): List<DepartmentSales> {
        val totals = linkedMapOf<Department, Long>()
        for (invoice in invoices) {
            for (line in invoice.lines) {
                val department = when {
                    line.menuItemId != null -> Department.RESTAURANT
                    else -> line.variantId?.let { categoryByVariant[it] }.toDepartment()
                }
                totals[department] = (totals[department] ?: 0L) + sign(invoice) * line.total.minorUnits
            }
        }
        return totals.filterValues { it > 0 }
            .entries.sortedWith(compareByDescending<Map.Entry<Department, Long>> { it.value }.thenBy { it.key.ordinal })
            .map { DepartmentSales(it.key, Money(it.value)) }
    }

    private fun ProductCategory?.toDepartment(): Department = when (this) {
        ProductCategory.GARMENT -> Department.GARMENTS
        ProductCategory.FOOTWEAR -> Department.FOOTWEAR
        ProductCategory.ACCESSORIES -> Department.ACCESSORIES
        ProductCategory.RESTAURANT -> Department.RESTAURANT
        ProductCategory.PACKAGED_FOOD -> Department.PACKAGED_FOOD
        ProductCategory.BEVERAGE -> Department.BEVERAGES
        ProductCategory.GENERAL -> Department.GENERAL
        ProductCategory.ELECTRONICS -> Department.ELECTRONICS
        ProductCategory.PHARMACY -> Department.PHARMACY
        ProductCategory.COSMETICS -> Department.COSMETICS
        ProductCategory.LOOSE -> Department.LOOSE
        ProductCategory.JEWELLERY -> Department.JEWELLERY
        null -> Department.OTHER
    }

    private fun topProducts(invoices: List<Invoice>): List<ProductRank> {
        class Totals(var quantity: Long = 0, var amount: Long = 0)

        val totals = linkedMapOf<String, Totals>()
        for (invoice in invoices) {
            val sign = sign(invoice)
            for (line in invoice.lines) {
                val entry = totals.getOrPut(line.productName) { Totals() }
                entry.quantity += sign * line.quantity
                entry.amount += sign * line.total.minorUnits
            }
        }
        return totals.entries
            .filter { it.value.quantity > 0 && it.value.amount > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Totals>> { it.value.amount }.thenBy { it.key.lowercase() })
            .take(TOP_PRODUCTS)
            .map { ProductRank(it.key, it.value.quantity, Money(it.value.amount)) }
    }
}

/** Short rupee amounts for chart axes, in the Indian style: ₹950, ₹1.2K, ₹3.5L, ₹2.1Cr. */
object CompactMoney {

    fun format(money: Money): String {
        val rupees = abs(money.minorUnits) / 100.0
        val text = when {
            rupees < 1_000 -> "₹${rupees.roundToLong()}"
            rupees < 100_000 -> "₹${trim(rupees / 1_000)}K"
            rupees < 10_000_000 -> "₹${trim(rupees / 100_000)}L"
            else -> "₹${trim(rupees / 10_000_000)}Cr"
        }
        return if (money.minorUnits < 0 && text != "₹0") "-$text" else text
    }

    private fun trim(value: Double): String = "%.1f".format(Locale.ENGLISH, value).removeSuffix(".0")
}
