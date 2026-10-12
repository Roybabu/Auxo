package com.invision.pos.data.local

import com.invision.pos.data.local.entity.InvoiceEntity
import com.invision.pos.data.local.entity.InvoiceLineEntity
import com.invision.pos.data.local.entity.InvoiceSummaryRow
import com.invision.pos.data.local.entity.InvoiceWithDetails
import com.invision.pos.data.local.entity.PaymentEntity
import com.invision.pos.data.local.entity.PaymentWithInvoice
import com.invision.pos.data.local.entity.StoreSettingsEntity
import com.invision.pos.domain.model.BuyerDetails
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceLine
import com.invision.pos.domain.model.InvoiceSummary
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.domain.model.SellerDetails
import com.invision.pos.domain.model.StoreSettings

fun InvoiceWithDetails.toDomain(): Invoice = Invoice(
    id = invoice.id,
    number = invoice.number,
    type = enumValueOrNull<InvoiceType>(invoice.type) ?: InvoiceType.SALE,
    createdAtMillis = invoice.createdAt,
    seller = SellerDetails(
        name = invoice.sellerName,
        address = invoice.sellerAddress,
        phone = invoice.sellerPhone,
        gstin = invoice.sellerGstin,
        stateCode = invoice.sellerStateCode,
    ),
    buyer = BuyerDetails(name = invoice.buyerName, phone = invoice.buyerPhone, gstin = invoice.buyerGstin),
    placeOfSupplyStateCode = invoice.placeOfSupply,
    gstRegistered = invoice.gstRegistered,
    pricesIncludeTax = invoice.pricesIncludeTax,
    interState = invoice.interState,
    lines = lines.sortedBy { it.id }.map { it.toDomain() },
    subtotal = Money(invoice.subtotalMinor),
    discountTotal = Money(invoice.discountMinor),
    taxableTotal = Money(invoice.taxableMinor),
    cgstTotal = Money(invoice.cgstMinor),
    sgstTotal = Money(invoice.sgstMinor),
    igstTotal = Money(invoice.igstMinor),
    roundOff = Money(invoice.roundOffMinor),
    grandTotal = Money(invoice.grandTotalMinor),
    savings = Money(invoice.savingsMinor),
    payments = payments.sortedBy { it.id }.map { it.toDomain(invoice.number) },
    originalInvoiceId = invoice.originalInvoiceId,
    originalInvoiceNumber = invoice.originalInvoiceNumber,
    note = invoice.note,
    customerId = invoice.customerId,
    discountReason = invoice.discountReason,
    serviceType = invoice.serviceType?.let { enumValueOrNull<com.invision.pos.domain.billing.ServiceType>(it) },
    tableNumber = invoice.tableNumber,
)

fun InvoiceLineEntity.toDomain(): InvoiceLine = InvoiceLine(
    id = id,
    variantId = variantId,
    productName = productName,
    variantLabel = variantLabel,
    barcode = barcode,
    hsnCode = hsnCode,
    quantity = quantity,
    unitPrice = Money(unitPriceMinor),
    mrp = mrpMinor?.let { Money(it) },
    discount = Money(discountMinor),
    taxable = Money(taxableMinor),
    gstRateBasisPoints = gstRateBasisPoints,
    cgst = Money(cgstMinor),
    sgst = Money(sgstMinor),
    igst = Money(igstMinor),
    total = Money(totalMinor),
    returnedQuantity = returnedQuantity,
    originalLineId = originalLineId,
    menuItemId = menuItemId,
    discountReason = discountReason,
)

fun PaymentEntity.toDomain(invoiceNumber: String? = null): PaymentRecord = PaymentRecord(
    id = id,
    invoiceId = invoiceId,
    method = enumValueOrNull<PaymentMethod>(method) ?: PaymentMethod.CASH,
    kind = enumValueOrNull<PaymentKind>(kind) ?: PaymentKind.PAYMENT,
    amount = Money(amountMinor),
    reference = reference,
    tendered = tenderedMinor?.let { Money(it) },
    change = changeMinor?.let { Money(it) },
    createdAtMillis = createdAt,
    invoiceNumber = invoiceNumber,
)

fun PaymentWithInvoice.toDomain(): PaymentRecord = payment.toDomain(invoiceNumber)

fun InvoiceSummaryRow.toDomain(): InvoiceSummary = InvoiceSummary(
    id = id,
    number = number,
    type = enumValueOrNull<InvoiceType>(type) ?: InvoiceType.SALE,
    createdAtMillis = createdAt,
    buyerName = buyerName,
    buyerPhone = buyerPhone,
    grandTotal = Money(grandTotalMinor),
    itemCount = itemCount,
)

fun Invoice.toEntity(): InvoiceEntity = InvoiceEntity(
    id = id,
    number = number,
    type = type.name,
    createdAt = createdAtMillis,
    sellerName = seller.name,
    sellerAddress = seller.address,
    sellerPhone = seller.phone,
    sellerGstin = seller.gstin,
    sellerStateCode = seller.stateCode,
    buyerName = buyer.name,
    buyerPhone = buyer.phone,
    buyerGstin = buyer.gstin,
    placeOfSupply = placeOfSupplyStateCode,
    gstRegistered = gstRegistered,
    pricesIncludeTax = pricesIncludeTax,
    interState = interState,
    subtotalMinor = subtotal.minorUnits,
    discountMinor = discountTotal.minorUnits,
    taxableMinor = taxableTotal.minorUnits,
    cgstMinor = cgstTotal.minorUnits,
    sgstMinor = sgstTotal.minorUnits,
    igstMinor = igstTotal.minorUnits,
    roundOffMinor = roundOff.minorUnits,
    grandTotalMinor = grandTotal.minorUnits,
    savingsMinor = savings.minorUnits,
    originalInvoiceId = originalInvoiceId,
    originalInvoiceNumber = originalInvoiceNumber,
    note = note,
    customerId = customerId,
    discountReason = discountReason,
    serviceType = serviceType?.name,
    tableNumber = tableNumber,
)

fun InvoiceLine.toEntity(invoiceId: Long): InvoiceLineEntity = InvoiceLineEntity(
    id = id,
    invoiceId = invoiceId,
    variantId = variantId,
    productName = productName,
    variantLabel = variantLabel,
    barcode = barcode,
    hsnCode = hsnCode,
    quantity = quantity,
    unitPriceMinor = unitPrice.minorUnits,
    mrpMinor = mrp?.minorUnits,
    discountMinor = discount.minorUnits,
    taxableMinor = taxable.minorUnits,
    gstRateBasisPoints = gstRateBasisPoints,
    cgstMinor = cgst.minorUnits,
    sgstMinor = sgst.minorUnits,
    igstMinor = igst.minorUnits,
    totalMinor = total.minorUnits,
    returnedQuantity = returnedQuantity,
    originalLineId = originalLineId,
    menuItemId = menuItemId,
    discountReason = discountReason,
)

fun PaymentRecord.toEntity(invoiceId: Long): PaymentEntity = PaymentEntity(
    id = id,
    invoiceId = invoiceId,
    method = method.name,
    kind = kind.name,
    amountMinor = amount.minorUnits,
    reference = reference,
    tenderedMinor = tendered?.minorUnits,
    changeMinor = change?.minorUnits,
    createdAt = createdAtMillis,
)

fun StoreSettingsEntity.toDomain(): StoreSettings = StoreSettings(
    storeName = storeName,
    address = address,
    phone = phone,
    gstin = gstin,
    stateCode = stateCode,
    gstRegistered = gstRegistered,
    pricesIncludeTax = pricesIncludeTax,
    roundOffTotal = roundOff,
    invoicePrefix = invoicePrefix,
    creditNotePrefix = creditNotePrefix,
    upiId = upiId,
    upiPayeeName = upiPayeeName,
    employeeDiscountLimitBp = employeeDiscountLimitBp,
    receipt = ReceiptOptions(
        paper = enumValueOrNull<ReceiptPaper>(receiptPaper) ?: ReceiptPaper.THERMAL_80,
        footer = receiptFooter,
        showHsn = receiptShowHsn,
        showTaxBreakup = receiptShowTaxBreakup,
        showSavings = receiptShowSavings,
        showCustomer = receiptShowCustomer,
        showInvoiceBarcode = receiptShowBarcode,
        showUpiQr = receiptShowUpiQr,
        upiId = upiId,
        upiPayeeName = upiPayeeName,
        shopName = storeName,
        language = Language.fromTag(receiptLanguage) ?: Language.DEFAULT,
    ),
)

fun StoreSettings.toEntity(): StoreSettingsEntity = StoreSettingsEntity(
    storeName = storeName,
    address = address,
    phone = phone,
    gstin = gstin,
    stateCode = stateCode,
    gstRegistered = gstRegistered,
    pricesIncludeTax = pricesIncludeTax,
    roundOff = roundOffTotal,
    invoicePrefix = invoicePrefix,
    creditNotePrefix = creditNotePrefix,
    upiId = upiId,
    upiPayeeName = upiPayeeName,
    receiptPaper = receipt.paper.name,
    receiptFooter = receipt.footer,
    receiptShowHsn = receipt.showHsn,
    receiptShowTaxBreakup = receipt.showTaxBreakup,
    receiptShowSavings = receipt.showSavings,
    receiptShowCustomer = receipt.showCustomer,
    receiptShowBarcode = receipt.showInvoiceBarcode,
    receiptShowUpiQr = receipt.showUpiQr,
    receiptLanguage = receipt.language.tag,
    employeeDiscountLimitBp = employeeDiscountLimitBp,
)
