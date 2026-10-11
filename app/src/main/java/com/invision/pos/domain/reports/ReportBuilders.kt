package com.invision.pos.domain.reports

import com.invision.pos.domain.inventory.ReorderPlanner
import com.invision.pos.domain.model.BatchWithProduct
import com.invision.pos.domain.model.CustomerAccount
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceLine
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.PurchaseReceipt
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovementRecord
import com.invision.pos.domain.model.Supplier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Which items an item-sales report covers. */
enum class ItemKind { ALL, PRODUCTS, DISHES }

/** Which batches the expiry report lists. */
enum class ExpiryWindow(val days: Long?) { EXPIRED(0), WITHIN_7_DAYS(7), WITHIN_30_DAYS(30), ALL(null) }

/**
 * Builds report tables from saved data. Credit notes count against the sales
 * they return, so every figure is net of returns.
 */
object Reports {

    private val END = ColumnAlign.END

    private fun col(title: String, align: ColumnAlign = ColumnAlign.START) = ReportColumn(title, align)

    private fun sum(values: Iterable<Money>): Money = values.fold(Money.ZERO) { acc, m -> acc + m }

    private fun signed(invoice: Invoice, amount: Money): Money =
        if (invoice.type == InvoiceType.RETURN) Money(-amount.minorUnits) else amount

    private fun day(epochMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    private fun text(value: String?): ReportValue = if (value.isNullOrBlank()) ReportValue.Empty else ReportValue.Text(value)

    // Sales

    /** One row per day: bills, returns, sales before discount, discounts, tax and the net total. */
    fun salesSummary(invoices: List<Invoice>, zone: ZoneId): ReportTable {
        val byDay = invoices.groupBy { day(it.createdAtMillis, zone) }.toSortedMap()
        val rows = byDay.map { (date, list) -> salesRow(ReportValue.Day(date), list) }
        return ReportTable(
            title = "Sales summary",
            columns = listOf(
                col("Date"), col("Bills", END), col("Returns", END), col("Gross sales", END), col("Discounts", END),
                col("Taxable value", END), col("CGST", END), col("SGST", END), col("IGST", END), col("Net total", END),
            ),
            rows = rows,
            totals = if (rows.isEmpty()) null else salesRow(ReportValue.Text("Total"), invoices),
        )
    }

    private fun salesRow(label: ReportValue, invoices: List<Invoice>): List<ReportValue> {
        val sales = invoices.filter { it.type == InvoiceType.SALE }
        return listOf(
            label,
            ReportValue.Count(sales.size.toLong()),
            ReportValue.Count(invoices.count { it.type == InvoiceType.RETURN }.toLong()),
            ReportValue.Amount(sum(invoices.map { signed(it, it.subtotal) })),
            ReportValue.Amount(sum(invoices.map { signed(it, it.discountTotal) })),
            ReportValue.Amount(sum(invoices.map { signed(it, it.taxableTotal) })),
            ReportValue.Amount(sum(invoices.map { signed(it, it.cgstTotal) })),
            ReportValue.Amount(sum(invoices.map { signed(it, it.sgstTotal) })),
            ReportValue.Amount(sum(invoices.map { signed(it, it.igstTotal) })),
            ReportValue.Amount(sum(invoices.map { signed(it, it.grandTotal) })),
        )
    }

    /** Quantity and value sold per item, net of returns, best sellers first. */
    fun itemSales(invoices: List<Invoice>, kind: ItemKind = ItemKind.ALL): ReportTable {
        data class Key(val name: String, val label: String, val dish: Boolean)
        data class Totals(var quantity: Long = 0, var taxable: Money = Money.ZERO, var total: Money = Money.ZERO)

        val totals = linkedMapOf<Key, Totals>()
        for (invoice in invoices) {
            val sign = if (invoice.type == InvoiceType.RETURN) -1 else 1
            for (line in invoice.lines) {
                val dish = line.menuItemId != null
                if (kind == ItemKind.PRODUCTS && dish || kind == ItemKind.DISHES && !dish) continue
                val entry = totals.getOrPut(Key(line.productName, line.variantLabel, dish)) { Totals() }
                entry.quantity += sign * line.quantity.toLong()
                entry.taxable += signed(invoice, line.taxable)
                entry.total += signed(invoice, line.total)
            }
        }
        val sorted = totals.entries.sortedWith(compareByDescending<Map.Entry<Key, Totals>> { it.value.total }.thenBy { it.key.name.lowercase() })
        return ReportTable(
            title = "Item sales",
            columns = listOf(col("Item"), col("Variant or section"), col("Type"), col("Quantity", END), col("Taxable value", END), col("Net sales", END)),
            rows = sorted.map { (key, value) ->
                listOf(
                    ReportValue.Text(key.name),
                    text(key.label),
                    ReportValue.Text(if (key.dish) "Dish" else "Product"),
                    ReportValue.Count(value.quantity),
                    ReportValue.Amount(value.taxable),
                    ReportValue.Amount(value.total),
                )
            },
            totals = if (sorted.isEmpty()) {
                null
            } else {
                listOf(
                    ReportValue.Text("Total"), ReportValue.Empty, ReportValue.Empty,
                    ReportValue.Count(sorted.sumOf { it.value.quantity }),
                    ReportValue.Amount(sum(sorted.map { it.value.taxable })),
                    ReportValue.Amount(sum(sorted.map { it.value.total })),
                )
            },
        )
    }

    /**
     * Money in and out per payment method: taken at the till, refunded, and
     * received against customer accounts. "On account" sales are listed but are
     * not money received.
     */
    fun collections(invoices: List<Invoice>, accountPayments: List<PaymentRecord>): ReportTable {
        val payments = invoices.flatMap { it.payments }
        val rows = PaymentMethod.entries.map { method ->
            val sales = sum(payments.filter { it.method == method && it.kind == PaymentKind.PAYMENT }.map { it.amount })
            val refunds = sum(payments.filter { it.method == method && it.kind == PaymentKind.REFUND }.map { it.amount })
            val account = sum(accountPayments.filter { it.method == method }.map { it.amount })
            val net = if (method.movesMoney) sales - refunds + account else Money.ZERO
            listOf(
                ReportValue.Text(methodName(method)),
                ReportValue.Amount(sales),
                ReportValue.Amount(refunds),
                ReportValue.Amount(account),
                if (method.movesMoney) ReportValue.Amount(net) else ReportValue.Empty,
            )
        }
        val moneyMethods = PaymentMethod.MONEY
        val totalNet = sum(
            moneyMethods.map { method ->
                sum(payments.filter { it.method == method && it.kind == PaymentKind.PAYMENT }.map { it.amount }) -
                    sum(payments.filter { it.method == method && it.kind == PaymentKind.REFUND }.map { it.amount }) +
                    sum(accountPayments.filter { it.method == method }.map { it.amount })
            },
        )
        return ReportTable(
            title = "Collections",
            columns = listOf(col("Method"), col("Taken on bills", END), col("Refunded", END), col("Account payments", END), col("Net received", END)),
            rows = rows,
            totals = listOf(ReportValue.Text("Total received"), ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Amount(totalNet)),
        )
    }

    fun methodName(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.UPI -> "UPI"
        PaymentMethod.CARD -> "Card"
        PaymentMethod.CREDIT -> "On account"
    }

    /** Taxable value and tax per GST rate, net of credit notes. */
    fun gstRates(invoices: List<Invoice>): ReportTable {
        val lines = invoices.flatMap { invoice -> invoice.lines.map { invoice to it } }
        val byRate = lines.groupBy { it.second.gstRateBasisPoints }.toSortedMap()
        fun row(label: ReportValue, group: List<Pair<Invoice, InvoiceLine>>): List<ReportValue> {
            val taxable = sum(group.map { (inv, line) -> signed(inv, line.taxable) })
            val cgst = sum(group.map { (inv, line) -> signed(inv, line.cgst) })
            val sgst = sum(group.map { (inv, line) -> signed(inv, line.sgst) })
            val igst = sum(group.map { (inv, line) -> signed(inv, line.igst) })
            return listOf(
                label, ReportValue.Amount(taxable), ReportValue.Amount(cgst), ReportValue.Amount(sgst), ReportValue.Amount(igst),
                ReportValue.Amount(cgst + sgst + igst), ReportValue.Amount(taxable + cgst + sgst + igst),
            )
        }
        return ReportTable(
            title = "GST by rate",
            columns = listOf(col("GST rate"), col("Taxable value", END), col("CGST", END), col("SGST", END), col("IGST", END), col("Total tax", END), col("Invoice value", END)),
            rows = byRate.map { (rate, group) -> row(ReportValue.Rate(rate), group) },
            totals = if (byRate.isEmpty()) null else row(ReportValue.Text("Total"), lines),
        )
    }

    /** HSN / SAC summary as needed for GSTR-1, net of credit notes. */
    fun hsnSummary(invoices: List<Invoice>): ReportTable {
        val lines = invoices.flatMap { invoice -> invoice.lines.map { invoice to it } }
        val groups = lines.groupBy { it.second.hsnCode.orEmpty() to it.second.gstRateBasisPoints }
            .toSortedMap(compareBy<Pair<String, Int>>({ it.first.isEmpty() }, { it.first }, { it.second }))
        return ReportTable(
            title = "HSN summary",
            columns = listOf(
                col("HSN / SAC"), col("Description"), col("GST rate"), col("Quantity", END), col("Taxable value", END),
                col("CGST", END), col("SGST", END), col("IGST", END), col("Total value", END),
            ),
            rows = groups.map { (key, group) ->
                val taxable = sum(group.map { (inv, line) -> signed(inv, line.taxable) })
                val cgst = sum(group.map { (inv, line) -> signed(inv, line.cgst) })
                val sgst = sum(group.map { (inv, line) -> signed(inv, line.sgst) })
                val igst = sum(group.map { (inv, line) -> signed(inv, line.igst) })
                listOf(
                    if (key.first.isEmpty()) ReportValue.Text("Not set") else ReportValue.Text(key.first),
                    ReportValue.Text(group.map { it.second.productName }.distinct().take(3).joinToString(", ")),
                    ReportValue.Rate(key.second),
                    ReportValue.Count(group.sumOf { (inv, line) -> if (inv.type == InvoiceType.RETURN) -line.quantity.toLong() else line.quantity.toLong() }),
                    ReportValue.Amount(taxable),
                    ReportValue.Amount(cgst),
                    ReportValue.Amount(sgst),
                    ReportValue.Amount(igst),
                    ReportValue.Amount(taxable + cgst + sgst + igst),
                )
            },
        )
    }

    // Purchasing and stock

    /** Goods receipts, newest first, optionally for one supplier. */
    fun purchases(receipts: List<PurchaseReceipt>, supplierId: Long? = null): ReportTable {
        val list = receipts.filter { supplierId == null || it.supplierId == supplierId }.sortedByDescending { it.receivedAtMillis }
        return ReportTable(
            title = "Purchases",
            columns = listOf(col("Received"), col("Receipt"), col("Supplier"), col("Supplier bill"), col("Order"), col("Lines", END), col("Total", END)),
            rows = list.map {
                listOf(
                    ReportValue.Moment(it.receivedAtMillis),
                    ReportValue.Text(it.number),
                    text(it.supplierName),
                    text(it.supplierInvoiceNumber),
                    text(it.orderNumber),
                    ReportValue.Count(it.lines.size.toLong()),
                    ReportValue.Amount(it.total),
                )
            },
            totals = if (list.isEmpty()) {
                null
            } else {
                listOf(ReportValue.Text("Total"), ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Count(list.sumOf { it.lines.size }.toLong()), ReportValue.Amount(sum(list.map { it.total })))
            },
        )
    }

    /** Stock on hand and its value at cost and at selling price. */
    fun stockValue(products: List<Product>, category: ProductCategory? = null): ReportTable {
        val rows = products.filter { category == null || it.category == category }.flatMap { product ->
            product.variants.map { variant ->
                val unit = product.stockUnit
                listOf(
                    ReportValue.Text(product.name),
                    text(variant.attributesLabel),
                    ReportValue.Text(categoryName(product.category)),
                    ReportValue.Quantity(variant.stockQuantity, unit),
                    variant.costPrice?.let { ReportValue.Amount(unit.costOf(variant.stockQuantity, it)) } ?: ReportValue.Empty,
                    if (product.category.isSold) ReportValue.Amount(variant.sellingPrice * variant.stockQuantity) else ReportValue.Empty,
                ) to (variant.costPrice?.let { unit.costOf(variant.stockQuantity, it) } to if (product.category.isSold) variant.sellingPrice * variant.stockQuantity else null)
            }
        }
        return ReportTable(
            title = "Stock value",
            columns = listOf(col("Item"), col("Variant"), col("Category"), col("In stock", END), col("Value at cost", END), col("Value at price", END)),
            rows = rows.map { it.first },
            totals = if (rows.isEmpty()) {
                null
            } else {
                listOf(
                    ReportValue.Text("Total"), ReportValue.Empty, ReportValue.Empty, ReportValue.Empty,
                    ReportValue.Amount(sum(rows.mapNotNull { it.second.first })),
                    ReportValue.Amount(sum(rows.mapNotNull { it.second.second })),
                )
            },
        )
    }

    fun categoryName(category: ProductCategory): String = when (category) {
        ProductCategory.GARMENT -> "Garment"
        ProductCategory.PACKAGED_FOOD -> "Packaged food"
        ProductCategory.BEVERAGE -> "Beverage"
        ProductCategory.INGREDIENT -> "Ingredient"
    }

    fun reasonName(reason: StockChangeReason): String = when (reason) {
        StockChangeReason.INITIAL -> "Opening stock"
        StockChangeReason.RECEIVED -> "Received"
        StockChangeReason.RETURNED -> "Returned"
        StockChangeReason.SOLD -> "Sold"
        StockChangeReason.DAMAGED -> "Damaged"
        StockChangeReason.CORRECTION -> "Count correction"
        StockChangeReason.EXPIRED -> "Expired"
        StockChangeReason.SPOILED -> "Spoiled"
        StockChangeReason.USED -> "Used in kitchen"
    }

    /** Every stock change, newest first, filtered by reason and category. */
    fun stockMovements(
        movements: List<StockMovementRecord>,
        reasons: Set<StockChangeReason> = emptySet(),
        category: ProductCategory? = null,
    ): ReportTable {
        val list = movements.filter { (reasons.isEmpty() || it.movement.reason in reasons) && (category == null || it.category == category) }
        return ReportTable(
            title = "Stock movements",
            columns = listOf(col("When"), col("Item"), col("Variant"), col("Reason"), col("Change", END), col("Batch"), col("Reference"), col("Note")),
            rows = list.map {
                listOf(
                    ReportValue.Moment(it.movement.createdAtMillis),
                    ReportValue.Text(it.productName),
                    text(it.variantLabel),
                    ReportValue.Text(reasonName(it.movement.reason)),
                    ReportValue.Quantity(it.movement.quantityChange, it.stockUnit),
                    text(it.batchNumber),
                    text(it.reference),
                    text(it.movement.note),
                )
            },
        )
    }

    /** Stock written off as expired, damaged or spoiled, with its value at cost. */
    fun wastage(movements: List<StockMovementRecord>, category: ProductCategory? = null): ReportTable {
        val list = movements.filter { it.movement.reason.isWastage && (category == null || it.category == category) }
        val values = list.map { record -> record.valueAtCost?.let { Money(-it.minorUnits) } }
        return ReportTable(
            title = "Wastage",
            columns = listOf(col("When"), col("Item"), col("Variant"), col("Reason"), col("Quantity", END), col("Batch"), col("Value at cost", END), col("Note")),
            rows = list.mapIndexed { index, it ->
                listOf(
                    ReportValue.Moment(it.movement.createdAtMillis),
                    ReportValue.Text(it.productName),
                    text(it.variantLabel),
                    ReportValue.Text(reasonName(it.movement.reason)),
                    ReportValue.Quantity(-it.movement.quantityChange, it.stockUnit),
                    text(it.batchNumber),
                    values[index]?.let { value -> ReportValue.Amount(value) } ?: ReportValue.Empty,
                    text(it.movement.note),
                )
            },
            totals = if (list.isEmpty()) {
                null
            } else {
                listOf(
                    ReportValue.Text("Total"), ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Empty,
                    ReportValue.Amount(sum(values.filterNotNull())), ReportValue.Empty,
                )
            },
        )
    }

    /** Items out of stock or at their low-stock level, with a suggested order. */
    fun lowStock(products: List<Product>, suppliers: List<Supplier>): ReportTable {
        val names = suppliers.associate { it.id to it.name }
        val suggestions = ReorderPlanner.suggestions(products)
        return ReportTable(
            title = "Low stock",
            columns = listOf(col("Item"), col("Variant"), col("In stock", END), col("Low-stock level", END), col("Suggested order", END), col("Supplier")),
            rows = suggestions.map {
                val unit = it.product.stockUnit
                listOf(
                    ReportValue.Text(it.product.name),
                    text(it.variant.attributesLabel),
                    ReportValue.Quantity(it.variant.stockQuantity, unit),
                    ReportValue.Quantity(it.variant.lowStockThreshold, unit),
                    ReportValue.Quantity(it.suggestedQuantity, unit),
                    text(it.supplierId?.let(names::get)),
                )
            },
        )
    }

    /** Batches with stock that have expired or expire within the window, soonest first. */
    fun expiry(batches: List<BatchWithProduct>, today: LocalDate, window: ExpiryWindow = ExpiryWindow.WITHIN_30_DAYS): ReportTable {
        val list = batches.filter { item ->
            val expiry = item.batch.expiryDate
            when (window) {
                ExpiryWindow.ALL -> true
                ExpiryWindow.EXPIRED -> item.batch.isExpired(today)
                else -> expiry != null && !expiry.isAfter(today.plusDays(window.days ?: 0))
            }
        }.sortedWith(compareBy({ it.batch.expiryDate == null }, { it.batch.expiryDate }, { it.productName.lowercase() }))
        return ReportTable(
            title = "Expiry",
            columns = listOf(col("Item"), col("Variant"), col("Batch"), col("Expiry"), col("Days left", END), col("Quantity", END), col("Value at cost", END)),
            rows = list.map {
                val expiry = it.batch.expiryDate
                listOf(
                    ReportValue.Text(it.productName),
                    text(it.variantLabel),
                    text(it.batch.batchNumber),
                    expiry?.let { date -> ReportValue.Day(date) } ?: ReportValue.Empty,
                    expiry?.let { date -> ReportValue.Count(ChronoUnit.DAYS.between(today, date)) } ?: ReportValue.Empty,
                    ReportValue.Quantity(it.batch.quantity, it.stockUnit),
                    it.valueAtCost?.let { value -> ReportValue.Amount(value) } ?: ReportValue.Empty,
                )
            },
            totals = if (list.isEmpty()) {
                null
            } else {
                listOf(
                    ReportValue.Text("Total"), ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Empty,
                    ReportValue.Amount(sum(list.mapNotNull { it.valueAtCost })),
                )
            },
        )
    }

    /** What each customer owes; with [onlyOutstanding], settled accounts are left out. */
    fun customerBalances(accounts: List<CustomerAccount>, onlyOutstanding: Boolean = true): ReportTable {
        val list = accounts.filter { !onlyOutstanding || !it.balance.isZero }.sortedByDescending { it.balance }
        return ReportTable(
            title = "Customer balances",
            columns = listOf(col("Customer"), col("Phone"), col("Balance due", END), col("Credit limit", END), col("Last activity")),
            rows = list.map {
                listOf(
                    ReportValue.Text(it.customer.name),
                    text(it.customer.phone),
                    ReportValue.Amount(it.balance),
                    it.customer.creditLimit?.let { limit -> ReportValue.Amount(limit) } ?: ReportValue.Empty,
                    it.lastActivityMillis?.let { millis -> ReportValue.Moment(millis) } ?: ReportValue.Empty,
                )
            },
            totals = if (list.isEmpty()) {
                null
            } else {
                listOf(ReportValue.Text("Total"), ReportValue.Empty, ReportValue.Amount(sum(list.map { it.balance })), ReportValue.Empty, ReportValue.Empty)
            },
        )
    }
}
