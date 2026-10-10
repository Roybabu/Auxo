package com.invision.pos.domain.billing

import com.invision.pos.domain.model.BuyerDetails
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceLine
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Rounding
import com.invision.pos.domain.model.StoreSettings

/** Tax mode for a cart under the store's settings. */
fun StoreSettings.taxModeFor(cart: Cart): TaxMode {
    val placeOfSupply = cart.placeOfSupplyStateCode ?: stateCode
    return TaxMode(
        gstRegistered = gstRegistered,
        pricesIncludeTax = pricesIncludeTax,
        interState = gstRegistered && stateCode.isNotEmpty() && placeOfSupply.isNotEmpty() && placeOfSupply != stateCode,
        roundOff = roundOffTotal,
    )
}

object SaleBuilder {

    /** Builds an unsaved sale invoice. The number and ids are assigned when it is saved. */
    fun build(
        cart: Cart,
        totals: BillTotals,
        settings: StoreSettings,
        payments: List<PaymentRecord>,
        nowMillis: Long,
    ): Invoice {
        val mode = settings.taxModeFor(cart)
        val lines = cart.lines.zip(totals.lines).map { (line, amounts) ->
            InvoiceLine(
                variantId = line.variantId,
                productName = line.productName,
                variantLabel = line.variantLabel,
                barcode = line.barcode,
                hsnCode = line.hsnCode,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
                mrp = line.mrp,
                discount = amounts.discount,
                taxable = amounts.taxable,
                gstRateBasisPoints = amounts.rateBasisPoints,
                cgst = amounts.cgst,
                sgst = amounts.sgst,
                igst = amounts.igst,
                total = amounts.total,
            )
        }
        val customer = cart.customer
        return Invoice(
            type = InvoiceType.SALE,
            createdAtMillis = nowMillis,
            seller = settings.sellerDetails(),
            buyer = BuyerDetails(
                name = customer.name.trim().ifEmpty { null },
                phone = IndianPhone.normalizeMobile(customer.phone),
                gstin = Gstin.normalize(customer.gstin).ifEmpty { null },
            ),
            placeOfSupplyStateCode = cart.placeOfSupplyStateCode ?: settings.stateCode,
            gstRegistered = mode.gstRegistered,
            pricesIncludeTax = mode.pricesIncludeTax,
            interState = mode.interState,
            lines = lines,
            subtotal = totals.subtotal,
            discountTotal = totals.discountTotal,
            taxableTotal = totals.taxableTotal,
            cgstTotal = totals.cgstTotal,
            sgstTotal = totals.sgstTotal,
            igstTotal = totals.igstTotal,
            roundOff = totals.roundOff,
            grandTotal = totals.grandTotal,
            savings = totals.savings,
            payments = payments.map { it.copy(kind = PaymentKind.PAYMENT, createdAtMillis = nowMillis) },
        )
    }
}

enum class ReturnError { NOTHING_SELECTED, QUANTITY_TOO_LARGE, NOT_A_SALE }

sealed interface ReturnBuild {
    data class Ready(val creditNote: Invoice) : ReturnBuild
    data class Rejected(val error: ReturnError) : ReturnBuild
}

/**
 * Builds a credit note for returned units. Amounts are taken from the original
 * line in proportion to the units returned, rounded so that returning every
 * unit (in one go or several) gives back exactly the original line amounts.
 */
object ReturnBuilder {

    fun build(
        original: Invoice,
        quantities: Map<Long, Int>,
        refundMethod: PaymentMethod,
        reason: String,
        roundOff: Boolean,
        nowMillis: Long,
    ): ReturnBuild {
        if (original.type != InvoiceType.SALE) return ReturnBuild.Rejected(ReturnError.NOT_A_SALE)
        val selected = original.lines.filter { (quantities[it.id] ?: 0) > 0 }
        if (selected.isEmpty()) return ReturnBuild.Rejected(ReturnError.NOTHING_SELECTED)
        if (selected.any { quantities.getValue(it.id) > it.returnableQuantity }) {
            return ReturnBuild.Rejected(ReturnError.QUANTITY_TOO_LARGE)
        }

        val lines = selected.map { line -> returnLine(line, quantities.getValue(line.id)) }
        val subtotal = sum(lines.map { it.unitPrice * it.quantity })
        val total = sum(lines.map { it.total })
        val rounded = if (roundOff) BillCalculator.roundToRupee(total) else total

        val creditNote = Invoice(
            type = InvoiceType.RETURN,
            createdAtMillis = nowMillis,
            seller = original.seller,
            buyer = original.buyer,
            placeOfSupplyStateCode = original.placeOfSupplyStateCode,
            gstRegistered = original.gstRegistered,
            pricesIncludeTax = original.pricesIncludeTax,
            interState = original.interState,
            lines = lines,
            subtotal = subtotal,
            discountTotal = sum(lines.map { it.discount }),
            taxableTotal = sum(lines.map { it.taxable }),
            cgstTotal = sum(lines.map { it.cgst }),
            sgstTotal = sum(lines.map { it.sgst }),
            igstTotal = sum(lines.map { it.igst }),
            roundOff = rounded - total,
            grandTotal = rounded,
            payments = listOf(
                PaymentRecord(
                    method = refundMethod,
                    kind = PaymentKind.REFUND,
                    amount = rounded,
                    createdAtMillis = nowMillis,
                ),
            ),
            originalInvoiceId = original.id,
            originalInvoiceNumber = original.number,
            note = reason.trim().ifEmpty { null },
        )
        return ReturnBuild.Ready(creditNote)
    }

    /** Credit note line for [units] more units of [line]. */
    fun returnLine(line: InvoiceLine, units: Int): InvoiceLine {
        val before = line.returnedQuantity
        val after = before + units
        fun share(amount: Money): Money =
            Money(portion(amount.minorUnits, after, line.quantity) - portion(amount.minorUnits, before, line.quantity))

        val taxable = share(line.taxable)
        val cgst = share(line.cgst)
        val sgst = share(line.sgst)
        val igst = share(line.igst)
        return line.copy(
            id = 0,
            quantity = units,
            discount = share(line.discount),
            taxable = taxable,
            cgst = cgst,
            sgst = sgst,
            igst = igst,
            total = taxable + cgst + sgst + igst,
            returnedQuantity = 0,
            originalLineId = line.id,
        )
    }

    private fun portion(amount: Long, units: Int, of: Int): Long = Rounding.halfUp(amount * units, of.toLong())

    private fun sum(values: List<Money>): Money = values.fold(Money.ZERO) { acc, m -> acc + m }
}
