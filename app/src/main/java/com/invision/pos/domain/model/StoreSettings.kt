package com.invision.pos.domain.model

/** Paper sizes the receipt can be laid out for. */
enum class ReceiptPaper {
    /** 58 mm thermal roll (48 mm printable). */
    THERMAL_58,

    /** 80 mm thermal roll (72 mm printable); also fits 75-76 mm rolls. */
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
