package com.invision.pos.domain.model

enum class InvoiceType {
    SALE,

    /** Credit note issued for returned goods. */
    RETURN,
}

enum class PaymentMethod { CASH, UPI, CARD }

enum class PaymentKind {
    /** Money received from the customer. */
    PAYMENT,

    /** Money given back for a return. */
    REFUND,
}

data class PaymentRecord(
    val id: Long = 0,
    val invoiceId: Long = 0,
    val method: PaymentMethod,
    val kind: PaymentKind = PaymentKind.PAYMENT,
    val amount: Money,
    /** UPI transaction reference or last four card digits. */
    val reference: String? = null,
    /** Cash handed over by the customer, when more than [amount]. */
    val tendered: Money? = null,
    val change: Money? = null,
    val createdAtMillis: Long = 0,
    /** Invoice number, filled when listing payments. */
    val invoiceNumber: String? = null,
)

/** Seller details as printed on the invoice at the time of sale. */
data class SellerDetails(
    val name: String,
    val address: String,
    val phone: String,
    val gstin: String,
    val stateCode: String,
)

/** Buyer details as printed on the invoice. A null name prints as "Walk-in Customer". */
data class BuyerDetails(
    val name: String? = null,
    val phone: String? = null,
    val gstin: String? = null,
)

data class InvoiceLine(
    val id: Long = 0,
    val variantId: Long?,
    val productName: String,
    val variantLabel: String,
    val barcode: String,
    val hsnCode: String?,
    val quantity: Int,
    val unitPrice: Money,
    val mrp: Money?,
    /** Line and bill discount on this line. */
    val discount: Money,
    val taxable: Money,
    val gstRateBasisPoints: Int,
    val cgst: Money,
    val sgst: Money,
    val igst: Money,
    val total: Money,
    /** Units already returned. Always 0 on credit notes. */
    val returnedQuantity: Int = 0,
    /** On a credit note: the sale line this return is for. */
    val originalLineId: Long? = null,
) {
    val tax: Money get() = cgst + sgst + igst
    val returnableQuantity: Int get() = quantity - returnedQuantity
}

data class Invoice(
    val id: Long = 0,
    /** Assigned when the invoice is saved. */
    val number: String = "",
    val type: InvoiceType,
    val createdAtMillis: Long,
    val seller: SellerDetails,
    val buyer: BuyerDetails,
    val placeOfSupplyStateCode: String,
    val gstRegistered: Boolean,
    val pricesIncludeTax: Boolean,
    val interState: Boolean,
    val lines: List<InvoiceLine>,
    val subtotal: Money,
    val discountTotal: Money,
    val taxableTotal: Money,
    val cgstTotal: Money,
    val sgstTotal: Money,
    val igstTotal: Money,
    val roundOff: Money,
    val grandTotal: Money,
    val savings: Money = Money.ZERO,
    val payments: List<PaymentRecord> = emptyList(),
    /** For credit notes: the sale being returned. */
    val originalInvoiceId: Long? = null,
    val originalInvoiceNumber: String? = null,
    /** Return reason or other note. */
    val note: String? = null,
) {
    val taxTotal: Money get() = cgstTotal + sgstTotal + igstTotal
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val isFullyReturned: Boolean get() = type == InvoiceType.SALE && lines.all { it.returnableQuantity <= 0 }
}

/** Short row for invoice history. */
data class InvoiceSummary(
    val id: Long,
    val number: String,
    val type: InvoiceType,
    val createdAtMillis: Long,
    val buyerName: String?,
    val buyerPhone: String?,
    val grandTotal: Money,
    val itemCount: Int,
)
