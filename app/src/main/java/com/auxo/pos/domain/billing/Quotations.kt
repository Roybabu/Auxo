package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import java.time.LocalDate

enum class QuotationStatus { OPEN, CONVERTED }

/** A quotation as listed. It is not a tax invoice: it has its own number and takes nothing out of stock. */
data class QuotationSummary(
    val id: Long,
    /** For example "QT/26-27/00001". */
    val number: String,
    val createdAtMillis: Long,
    /** The last day the prices are offered. */
    val validUntil: LocalDate,
    val customerName: String,
    val customerPhone: String,
    val itemCount: Int,
    /** The total when the quotation was made. */
    val total: Money,
    val status: QuotationStatus,
    /** The invoice this quotation became, once converted. */
    val convertedInvoiceId: Long?,
) {
    /** True once the validity date has passed and the quotation was not used. */
    fun isExpired(today: LocalDate): Boolean = status == QuotationStatus.OPEN && today.isAfter(validUntil)
}

/** A quotation with everything needed to put it back on the counter. */
data class Quotation(val summary: QuotationSummary, val cart: Cart)

object Quotations {
    /** Quotation numbers are "QT/26-27/00001": their own counter, separate from invoices and credit notes. */
    const val PREFIX = "QT"

    /** Validity choices offered, in days. */
    val VALIDITY_DAYS: List<Int> = listOf(7, 15, 30)
    const val DEFAULT_VALIDITY_DAYS = 15

    /** The last valid day: [days] after the day the quotation is made. */
    fun validUntil(created: LocalDate, days: Int): LocalDate = created.plusDays(days.coerceAtLeast(0).toLong())

    fun sequenceKey(financialYearStart: Int): String = InvoiceNumbers.sequenceKey(PREFIX, financialYearStart)

    fun number(financialYearStart: Int, sequence: Long): String = InvoiceNumbers.format(PREFIX, financialYearStart, sequence)
}

/** A quotation's cart brought up to today's shop prices. [changed] names the items whose price or tax rate moved. */
data class Repriced(val cart: Cart, val changed: List<String>)

object QuotationRepricing {

    /**
     * Prices every line of a saved quotation at today's selling price, MRP and tax rate before it becomes a
     * bill. Quantities and discounts are kept. Lines whose product no longer exists are left as they
     * were; the stock check then marks them unavailable. One-off items keep their typed price.
     */
    fun reprice(cart: Cart, products: List<Product>): Repriced {
        val variants = products.flatMap { product -> product.variants.map { product to it } }.associateBy { it.second.id }
        val changed = mutableListOf<String>()
        val lines = cart.lines.map { line ->
            val now = when {
                line.isCustom -> null
                else -> variants[line.variantId]?.let { (product, variant) -> CartLine.from(product, variant, line.quantity) }
            }
            if (now == null) {
                line
            } else {
                if (now.unitPrice != line.unitPrice || now.gstRate != line.gstRate) changed += line.productName
                line.copy(
                    productName = now.productName,
                    variantLabel = now.variantLabel,
                    hsnCode = now.hsnCode,
                    unitPrice = now.unitPrice,
                    mrp = now.mrp,
                    gstRate = now.gstRate,
                )
            }
        }
        return Repriced(cart.copy(lines = lines), changed)
    }
}
