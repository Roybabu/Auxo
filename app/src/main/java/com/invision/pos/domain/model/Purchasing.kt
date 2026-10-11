package com.invision.pos.domain.model

import java.time.LocalDate

/** A business the store buys stock from. Suppliers are hidden rather than deleted. */
data class Supplier(
    val id: Long = 0,
    val name: String,
    val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val gstin: String? = null,
    /** Two-digit GST state code. */
    val stateCode: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAtMillis: Long = 0,
)

enum class PurchaseOrderStatus {
    /** Being prepared; can still be edited. */
    DRAFT,

    /** Sent to the supplier. */
    ORDERED,
    PARTLY_RECEIVED,
    RECEIVED,
    CANCELLED,
    ;

    /** Goods can still arrive against the order. */
    val isOpen: Boolean get() = this == DRAFT || this == ORDERED || this == PARTLY_RECEIVED

    val canEdit: Boolean get() = this == DRAFT
}

data class PurchaseOrderLine(
    val id: Long = 0,
    /** Null if the product was deleted after ordering. */
    val variantId: Long?,
    val productName: String,
    val variantLabel: String,
    val stockUnit: StockUnit = StockUnit.UNIT,
    /** In base units (pieces, grams or millilitres). */
    val quantityOrdered: Int,
    val quantityReceived: Int = 0,
    /** Cost per piece, kg or litre. */
    val unitCost: Money,
) {
    val remaining: Int get() = (quantityOrdered - quantityReceived).coerceAtLeast(0)
    val lineTotal: Money get() = stockUnit.costOf(quantityOrdered, unitCost)
}

data class PurchaseOrder(
    val id: Long = 0,
    /** Assigned when the order is first saved, e.g. "PO/26-27/00001". */
    val number: String = "",
    val supplierId: Long,
    val supplierName: String = "",
    val status: PurchaseOrderStatus = PurchaseOrderStatus.DRAFT,
    val createdAtMillis: Long = 0,
    val expectedDate: LocalDate? = null,
    val note: String? = null,
    val lines: List<PurchaseOrderLine> = emptyList(),
) {
    val total: Money get() = lines.fold(Money.ZERO) { acc, line -> acc + line.lineTotal }
}

/** One item on a goods receipt. */
data class PurchaseReceiptLine(
    val id: Long = 0,
    val variantId: Long?,
    /** The purchase order line this delivers, if any. */
    val orderLineId: Long? = null,
    val productName: String,
    val variantLabel: String,
    val stockUnit: StockUnit = StockUnit.UNIT,
    /** In base units. */
    val quantity: Int,
    /** Cost per piece, kg or litre. */
    val unitCost: Money,
    val batchNumber: String? = null,
    val expiryDate: LocalDate? = null,
) {
    val lineTotal: Money get() = stockUnit.costOf(quantity, unitCost)
}

/** Goods received from a supplier ("GRN/26-27/00001"), with or without a purchase order. */
data class PurchaseReceipt(
    val id: Long = 0,
    val number: String = "",
    val supplierId: Long? = null,
    val supplierName: String? = null,
    val orderId: Long? = null,
    val orderNumber: String? = null,
    /** The supplier's own bill number; the same bill can't be received twice. */
    val supplierInvoiceNumber: String? = null,
    val receivedAtMillis: Long = 0,
    val note: String? = null,
    val lines: List<PurchaseReceiptLine> = emptyList(),
) {
    val total: Money get() = lines.fold(Money.ZERO) { acc, line -> acc + line.lineTotal }
}
