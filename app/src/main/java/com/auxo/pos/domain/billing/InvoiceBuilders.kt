// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.BuyerDetails
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.InvoiceLine
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Rounding
import com.auxo.pos.domain.model.StoreSettings

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
                variantId = line.stockVariantId,
                productName = line.productName,
                variantLabel = line.variantLabel,
                barcode = line.barcode,
                hsnCode = line.hsnCode,
                quantity = line.quantity,
                stockUnit = line.stockUnit,
                unitPrice = line.unitPrice,
                mrp = line.mrp,
                discount = amounts.discount,
                taxable = amounts.taxable,
                gstRateBasisPoints = amounts.rateBasisPoints,
                cgst = amounts.cgst,
                sgst = amounts.sgst,
                igst = amounts.igst,
                total = amounts.total,
                discountReason = if (line.discount != null) DiscountPolicy.cleanReason(line.discountReason) else null,
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
            customerId = customer.customerId,
            loyaltyPointsRedeemed = cart.loyalty?.points ?: 0,
            discountReason = if (cart.billDiscount != null) DiscountPolicy.cleanReason(cart.billDiscountReason) else null,
            loyaltyDiscount = totals.loyaltyDiscount,
            roundedToRupee = mode.roundOff,
        )
    }
}

enum class ReturnError {
    NOTHING_SELECTED,
    QUANTITY_TOO_LARGE,
    NOT_A_SALE,

    /** A refund to account needs a sale made to a customer account. */
    NO_ACCOUNT,
}

sealed interface ReturnBuild {
    data class Ready(val creditNote: Invoice) : ReturnBuild
    data class Rejected(val error: ReturnError) : ReturnBuild
}

/**
 * Builds a credit note for returned units. Amounts are taken from the original
 * line in proportion to the units returned, rounded so that returning every
 * unit (in one go or several) gives back exactly the original line amounts.
 *
 * The refund follows the original bill's rounding: it is what is due after this
 * return minus what was due before it, so all refunds together never differ
 * from what the customer paid.
 */
object ReturnBuilder {

    fun build(
        original: Invoice,
        quantities: Map<Long, Int>,
        refundMethod: PaymentMethod,
        reason: String,
        nowMillis: Long,
    ): ReturnBuild {
        if (original.type != InvoiceType.SALE) return ReturnBuild.Rejected(ReturnError.NOT_A_SALE)
        if (refundMethod == PaymentMethod.CREDIT && original.customerId == null) return ReturnBuild.Rejected(ReturnError.NO_ACCOUNT)
        val selected = original.lines.filter { (quantities[it.id] ?: 0) > 0 }
        if (selected.isEmpty()) return ReturnBuild.Rejected(ReturnError.NOTHING_SELECTED)
        if (selected.any { quantities.getValue(it.id) > it.returnableQuantity }) {
            return ReturnBuild.Rejected(ReturnError.QUANTITY_TOO_LARGE)
        }

        val units = selected.associate { it.id to quantities.getValue(it.id) }
        val lines = selected.map { line -> returnLine(line, units.getValue(line.id)) }
        val subtotal = sum(lines.map { it.gross })
        val linesTotal = sum(lines.map { it.total })
        val returnedBefore = original.lines.associate { it.id to it.returnedQuantity }
        val returnedAfter = original.lines.associate { it.id to it.returnedQuantity + (units[it.id] ?: 0) }
        val refund = refundDue(original, returnedAfter) - refundDue(original, returnedBefore)
        val accountPart = (accountShare(original, returnedAfter) - accountShare(original, returnedBefore))
            .coerceIn(Money.ZERO, refund.coerceAtLeast(Money.ZERO))

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
            roundOff = refund - linesTotal,
            grandTotal = refund,
            // Returning a few paise on a rounded bill can leave nothing to refund yet.
            payments = refundPayments(original, refundMethod, refund, accountPart, nowMillis),
            originalInvoiceId = original.id,
            originalInvoiceNumber = original.number,
            note = reason.trim().ifEmpty { null },
            customerId = original.customerId,
            roundedToRupee = original.roundedToRupee,
        )
        return ReturnBuild.Ready(creditNote)
    }

    /**
     * Total refunded on [original] once [returned] units of each line are back:
     * the bill total when everything is back, otherwise the returned share,
     * rounded to the rupee if the bill was.
     */
    fun refundDue(original: Invoice, returned: Map<Long, Int>): Money {
        if (original.lines.all { (returned[it.id] ?: 0) >= it.quantity }) return original.grandTotal
        val total = sum(original.lines.map { returnedShare(it, returned[it.id] ?: 0) })
        // Follows the round-off setting the bill was made with, not whether its total happens to be whole rupees.
        return if (original.roundedToRupee) BillCalculator.roundToRupee(total) else total
    }

    /**
     * The part of [original] that was put on the customer's account and is given back once [returned]
     * units are back: the account share of the bill, in the same proportion as the refund due. The shares
     * of all returns add up to exactly the amount that was on account, never more.
     */
    fun accountShare(original: Invoice, returned: Map<Long, Int>): Money {
        val onAccount = original.payments
            .filter { it.method == PaymentMethod.CREDIT && it.kind == PaymentKind.PAYMENT }
            .fold(Money.ZERO) { acc, payment -> acc + payment.amount }
        if (onAccount.isZero || original.grandTotal.minorUnits <= 0L) return Money.ZERO
        val due = refundDue(original, returned)
        if (due >= original.grandTotal) return onAccount
        return Money(Rounding.halfUp(onAccount.minorUnits * due.minorUnits, original.grandTotal.minorUnits))
    }

    /**
     * What a return pays back. A bill that was partly on account is first settled against that account
     * (the customer owes less) and only the rest goes back by [method]; refunding the whole bill in cash
     * when part of it was never paid would hand out money the shop did not receive.
     */
    private fun refundPayments(original: Invoice, method: PaymentMethod, refund: Money, accountPart: Money, nowMillis: Long): List<PaymentRecord> {
        if (refund <= Money.ZERO) return emptyList()
        fun record(m: PaymentMethod, amount: Money) =
            PaymentRecord(method = m, kind = PaymentKind.REFUND, amount = amount, createdAtMillis = nowMillis)
        if (method == PaymentMethod.CREDIT || original.customerId == null || accountPart.isZero) return listOf(record(method, refund))
        val rest = refund - accountPart
        return listOfNotNull(
            record(method, rest).takeIf { rest > Money.ZERO },
            record(PaymentMethod.CREDIT, accountPart),
        )
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

    /** Value of the first [units] units of [line], as the credit notes for them add up. */
    private fun returnedShare(line: InvoiceLine, units: Int): Money {
        val count = units.coerceIn(0, line.quantity)
        return Money(
            listOf(line.taxable, line.cgst, line.sgst, line.igst).sumOf { portion(it.minorUnits, count, line.quantity) },
        )
    }

    private fun portion(amount: Long, units: Int, of: Int): Long = Rounding.halfUp(amount * units, of.toLong())

    private fun sum(values: List<Money>): Money = values.fold(Money.ZERO) { acc, m -> acc + m }
}
