package com.auxo.pos.domain.purchasing

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseOrderLine
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.model.PurchaseReceiptLine
import com.auxo.pos.domain.model.StockUnit
import java.time.LocalDate

enum class PurchaseLineError {
    QUANTITY_REQUIRED,
    QUANTITY_INVALID,
    MORE_THAN_ORDERED,
    COST_REQUIRED,
    COST_INVALID,
    BATCH_TOO_LONG,
    ALREADY_EXPIRED,
    DUPLICATE_ITEM,
    ITEM_DELETED,
}

enum class PurchaseFormError {
    SUPPLIER_REQUIRED,
    NO_LINES,
    NOTHING_TO_RECEIVE,
    SUPPLIER_INVOICE_INVALID,
    NOTE_TOO_LONG,
    EXPECTED_DATE_IN_PAST,
}

/** One item on a purchase order or goods receipt being filled in. Quantities use the entry unit (pcs, kg or L). */
data class PurchaseLineForm(
    /** Stable key for lists and errors. */
    val key: String,
    val variantId: Long?,
    val productName: String,
    val variantLabel: String,
    val stockUnit: StockUnit = StockUnit.UNIT,
    val quantity: String = "",
    val unitCost: String = "",
    /** Goods receipts: the order line delivered, and how much of it is still due (base units). */
    val orderLineId: Long? = null,
    val remaining: Int? = null,
    /** Goods receipts of batch-tracked items. */
    val tracksExpiry: Boolean = false,
    val batchNumber: String = "",
    val expiryDate: LocalDate? = null,
) {
    companion object {
        /** A new line for [variant], priced at its cost price when known. */
        fun forVariant(key: String, product: Product, variant: ProductVariant, quantity: Int? = null) = PurchaseLineForm(
            key = key,
            variantId = variant.id,
            productName = product.name,
            variantLabel = variant.attributesLabel,
            stockUnit = product.stockUnit,
            quantity = quantity?.let { product.stockUnit.toEntryText(it) }.orEmpty(),
            unitCost = variant.costPrice?.toDecimalString().orEmpty(),
            tracksExpiry = product.category.tracksExpiry,
        )

        fun fromOrderLine(key: String, line: PurchaseOrderLine) = PurchaseLineForm(
            key = key,
            variantId = line.variantId,
            productName = line.productName,
            variantLabel = line.variantLabel,
            stockUnit = line.stockUnit,
            quantity = line.stockUnit.toEntryText(line.quantityOrdered),
            unitCost = line.unitCost.toDecimalString(),
        )
    }
}

sealed interface OrderValidation {
    data class Valid(val order: PurchaseOrder) : OrderValidation
    data class Invalid(
        val formErrors: Set<PurchaseFormError>,
        val lineErrors: Map<String, Set<PurchaseLineError>>,
    ) : OrderValidation
}

sealed interface ReceiptValidation {
    data class Valid(val receipt: PurchaseReceipt) : ReceiptValidation
    data class Invalid(
        val formErrors: Set<PurchaseFormError>,
        val lineErrors: Map<String, Set<PurchaseLineError>>,
    ) : ReceiptValidation
}

object PurchaseValidator {
    const val NOTE_MAX = 200
    const val BATCH_MAX = 32
    private val SUPPLIER_INVOICE = Regex("""[A-Za-z0-9/\-_.]{1,30}""")

    private fun lineCost(line: PurchaseLineForm, errors: MutableSet<PurchaseLineError>): Money? {
        if (line.unitCost.isBlank()) {
            errors += PurchaseLineError.COST_REQUIRED
            return null
        }
        return Money.parseOrNull(line.unitCost) ?: run {
            errors += PurchaseLineError.COST_INVALID
            null
        }
    }

    /** Checks a draft purchase order. Every line needs a quantity and a cost; an item can appear once. */
    fun validateOrder(
        supplierId: Long?,
        lines: List<PurchaseLineForm>,
        expectedDate: LocalDate?,
        note: String,
        today: LocalDate,
        existing: PurchaseOrder? = null,
    ): OrderValidation {
        val formErrors = mutableSetOf<PurchaseFormError>()
        val lineErrors = mutableMapOf<String, MutableSet<PurchaseLineError>>()
        if (supplierId == null) formErrors += PurchaseFormError.SUPPLIER_REQUIRED
        if (lines.isEmpty()) formErrors += PurchaseFormError.NO_LINES
        if (note.trim().length > NOTE_MAX) formErrors += PurchaseFormError.NOTE_TOO_LONG
        if (expectedDate != null && expectedDate.isBefore(today)) formErrors += PurchaseFormError.EXPECTED_DATE_IN_PAST

        val seen = mutableSetOf<Long>()
        val valid = lines.mapNotNull { line ->
            val errors = mutableSetOf<PurchaseLineError>()
            if (line.variantId == null) errors += PurchaseLineError.ITEM_DELETED
            if (line.variantId != null && !seen.add(line.variantId)) errors += PurchaseLineError.DUPLICATE_ITEM
            val quantity = when {
                line.quantity.isBlank() -> null.also { errors += PurchaseLineError.QUANTITY_REQUIRED }
                else -> line.stockUnit.parse(line.quantity) ?: null.also { errors += PurchaseLineError.QUANTITY_INVALID }
            }
            val cost = lineCost(line, errors)
            if (errors.isNotEmpty()) lineErrors[line.key] = errors
            if (quantity == null || cost == null || errors.isNotEmpty()) {
                null
            } else {
                PurchaseOrderLine(
                    variantId = line.variantId,
                    productName = line.productName,
                    variantLabel = line.variantLabel,
                    stockUnit = line.stockUnit,
                    quantityOrdered = quantity,
                    unitCost = cost,
                )
            }
        }
        if (formErrors.isNotEmpty() || lineErrors.isNotEmpty()) return OrderValidation.Invalid(formErrors, lineErrors)
        return OrderValidation.Valid(
            PurchaseOrder(
                id = existing?.id ?: 0,
                number = existing?.number.orEmpty(),
                supplierId = supplierId!!,
                createdAtMillis = existing?.createdAtMillis ?: 0,
                expectedDate = expectedDate,
                note = note.trim().ifEmpty { null },
                lines = valid,
            ),
        )
    }

    /**
     * Checks a goods receipt. Lines from a purchase order may be left at 0 to skip
     * them but can't exceed what is still due; at least one line must bring stock in.
     * Batch-tracked goods can't arrive already expired.
     */
    fun validateReceipt(
        supplierId: Long?,
        orderId: Long?,
        supplierInvoiceNumber: String,
        lines: List<PurchaseLineForm>,
        note: String,
        today: LocalDate,
    ): ReceiptValidation {
        val formErrors = mutableSetOf<PurchaseFormError>()
        val lineErrors = mutableMapOf<String, MutableSet<PurchaseLineError>>()
        if (supplierId == null && orderId == null) formErrors += PurchaseFormError.SUPPLIER_REQUIRED
        if (lines.isEmpty()) formErrors += PurchaseFormError.NO_LINES
        val invoice = supplierInvoiceNumber.trim()
        if (invoice.isNotEmpty() && !SUPPLIER_INVOICE.matches(invoice)) formErrors += PurchaseFormError.SUPPLIER_INVOICE_INVALID
        if (note.trim().length > NOTE_MAX) formErrors += PurchaseFormError.NOTE_TOO_LONG

        val seen = mutableSetOf<Long>()
        val valid = lines.mapNotNull { line ->
            val errors = mutableSetOf<PurchaseLineError>()
            if (line.variantId == null) errors += PurchaseLineError.ITEM_DELETED
            // The same item twice is fine on an order receipt (two order lines), not on a direct one.
            if (line.orderLineId == null && line.variantId != null && !seen.add(line.variantId)) errors += PurchaseLineError.DUPLICATE_ITEM
            val skipped = line.orderLineId != null && (line.quantity.isBlank() || line.quantity.trim().matches(ZERO))
            val quantity = when {
                skipped -> 0
                line.quantity.isBlank() -> null.also { errors += PurchaseLineError.QUANTITY_REQUIRED }
                else -> line.stockUnit.parse(line.quantity) ?: null.also { errors += PurchaseLineError.QUANTITY_INVALID }
            }
            if (quantity != null && line.remaining != null && quantity > line.remaining) errors += PurchaseLineError.MORE_THAN_ORDERED
            val cost = if (skipped) Money.ZERO else lineCost(line, errors)
            if (!skipped && line.batchNumber.trim().length > BATCH_MAX) errors += PurchaseLineError.BATCH_TOO_LONG
            if (!skipped && line.tracksExpiry && line.expiryDate != null && line.expiryDate.isBefore(today)) {
                errors += PurchaseLineError.ALREADY_EXPIRED
            }
            if (errors.isNotEmpty()) lineErrors[line.key] = errors
            if (skipped || quantity == null || quantity == 0 || cost == null || errors.isNotEmpty()) {
                null
            } else {
                PurchaseReceiptLine(
                    variantId = line.variantId,
                    orderLineId = line.orderLineId,
                    productName = line.productName,
                    variantLabel = line.variantLabel,
                    stockUnit = line.stockUnit,
                    quantity = quantity,
                    unitCost = cost,
                    batchNumber = if (line.tracksExpiry) line.batchNumber.trim().ifEmpty { null } else null,
                    expiryDate = if (line.tracksExpiry) line.expiryDate else null,
                )
            }
        }
        if (lines.isNotEmpty() && valid.isEmpty() && lineErrors.isEmpty()) formErrors += PurchaseFormError.NOTHING_TO_RECEIVE
        if (formErrors.isNotEmpty() || lineErrors.isNotEmpty()) return ReceiptValidation.Invalid(formErrors, lineErrors)
        return ReceiptValidation.Valid(
            PurchaseReceipt(
                supplierId = supplierId,
                orderId = orderId,
                supplierInvoiceNumber = invoice.uppercase().ifEmpty { null },
                note = note.trim().ifEmpty { null },
                lines = valid,
            ),
        )
    }

    private val ZERO = Regex("""0+(\.0+)?""")
}
