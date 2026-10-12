package com.invision.pos.domain.model

enum class InvoiceType {
    SALE,

    /** Credit note issued for returned goods. */
    RETURN,
}

enum class PaymentMethod {
    CASH,
    UPI,
    CARD,

    /** Put on the customer's account, to be paid later. */
    CREDIT,
    ;

    /** True when money actually changes hands now; credit goes on the customer's account instead. */
    val movesMoney: Boolean get() = this != CREDIT

    companion object {
        /** Ways to take or give back money now. */
        val MONEY: List<PaymentMethod> = listOf(CASH, UPI, CARD)
    }
}

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
    /** For payments received against a customer account (not tied to an invoice). */
    val customerName: String? = null,
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
    /** Set for restaurant dishes instead of [variantId]. */
    val menuItemId: Long? = null,
    /** Why this line was discounted at the counter; null when it was not (a bill discount has its own reason). */
    val discountReason: String? = null,
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
    /** Customer account the invoice belongs to, if any. */
    val customerId: Long? = null,
    /**
     * Loyalty points used on this sale. Their value is already inside [discountTotal]. Not stored on the
     * invoice itself: the points are recorded on the customer's ledger when the sale is saved.
     */
    val loyaltyPointsRedeemed: Int = 0,
    /** Why the bill as a whole was discounted; null when it was not. Per-line reasons are on the lines. */
    val discountReason: String? = null,
    /**
     * The part of [discountTotal] that is loyalty points. Not stored: the sale is checked against the
     * employee discount limit with it left out, because points are not a discount given at the counter.
     */
    val loyaltyDiscount: Money = Money.ZERO,
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
