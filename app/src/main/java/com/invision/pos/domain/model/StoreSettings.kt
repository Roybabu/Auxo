package com.invision.pos.domain.model

/** Paper sizes the receipt can be laid out for. */
enum class ReceiptPaper {
    /** 58 mm thermal roll (48 mm printable). */
    THERMAL_58,

    /** 75-76 mm roll (64 mm printable), common on 3-inch impact and some thermal printers. */
    THERMAL_76,

    /** 78-80 mm thermal roll (72 mm printable). */
    THERMAL_80,
    A5,
    A4,
}

data class ReceiptOptions(
    val paper: ReceiptPaper = ReceiptPaper.THERMAL_80,
    val footer: String = "Thank you for shopping with us!",
    val showHsn: Boolean = true,
    val showTaxBreakup: Boolean = true,
    val showSavings: Boolean = true,
    val showCustomer: Boolean = true,
    val showInvoiceBarcode: Boolean = true,
    /** Print a UPI payment QR (exact bill amount and invoice number) on sale receipts. Needs a valid UPI ID. */
    val showUpiQr: Boolean = true,
    /**
     * The shop's UPI details, copied in when settings are read so a receipt can be laid out from the options
     * alone. They are not stored here; the settings own them ([StoreSettings.upiId]).
     */
    val upiId: String = "",
    val upiPayeeName: String = "",
    val shopName: String = "",
    /**
     * The language bills and credit notes are printed and shared in. It belongs to the shop, not to a phone, so
     * every phone prints the same bill. Product and customer names are printed exactly as entered.
     */
    val language: Language = Language.DEFAULT,
)

data class StoreSettings(
    val storeName: String = "My Store",
    val address: String = "",
    val phone: String = "",
    val gstin: String = "",
    /** GST state code of the store, e.g. "19" for West Bengal. Empty until set. */
    val stateCode: String = "",
    val gstRegistered: Boolean = true,
    val pricesIncludeTax: Boolean = true,
    val roundOffTotal: Boolean = true,
    val invoicePrefix: String = "INV",
    val creditNotePrefix: String = "CN",
    val upiId: String = "",
    val upiPayeeName: String = "",
    /**
     * The most an Employee may take off a bill or a line, in basis points of its value (1000 = 10%). Zero, the
     * default, means Employees cannot give discounts. Only the Super Admin can change it or go above it.
     */
    val employeeDiscountLimitBp: Int = 0,
    val receipt: ReceiptOptions = ReceiptOptions(),
) {
    /** Tax invoices must show the seller's GSTIN. */
    val needsGstinSetup: Boolean get() = gstRegistered && gstin.isBlank()

    fun sellerDetails() = SellerDetails(
        name = storeName,
        address = address,
        phone = phone,
        gstin = if (gstRegistered) gstin else "",
        stateCode = stateCode,
    )
}

/**
 * The UPI link to print as a QR on [invoice], or null when none should be printed: the setting is off, the UPI
 * ID is missing or invalid, the document is a credit note (nothing to collect) or has no number yet.
 */
fun ReceiptOptions.upiLinkFor(invoice: Invoice): String? {
    if (!showUpiQr || invoice.type != InvoiceType.SALE || invoice.isQuotation || invoice.number.isBlank()) return null
    return com.invision.pos.domain.billing.Upi.billLink(upiId, upiPayeeName, shopName, invoice.grandTotal, invoice.number)
}
