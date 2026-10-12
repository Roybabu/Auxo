package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Money

/** Problems that block checkout for one cart line. */
sealed interface LineIssue {
    /** The variant was deleted from inventory. */
    data object Unavailable : LineIssue
    data class NotEnoughStock(val available: Int) : LineIssue
    /** The store charges GST but the product has no GST rate. */
    data object MissingGstRate : LineIssue
    data object DiscountTooLarge : LineIssue

    /** A discount was entered without saying why. */
    data object DiscountReasonMissing : LineIssue

    /** The discount is more than the signed-in Employee may give. */
    data object DiscountAboveLimit : LineIssue
}

enum class CustomerIssue { NAME_REQUIRED_WITH_GSTIN, NAME_TOO_LONG, INVALID_PHONE, INVALID_GSTIN }

data class CartCheck(
    val lineIssues: Map<Long, LineIssue> = emptyMap(),
    val billDiscountTooLarge: Boolean = false,
    val billDiscountReasonMissing: Boolean = false,
    /** The discounts on the bill together are more than the signed-in Employee may give. */
    val billDiscountAboveLimit: Boolean = false,
    /** Loyalty points were asked for beyond the bill, or without a saved customer. */
    val loyaltyInvalid: Boolean = false,
    val customerIssues: Set<CustomerIssue> = emptySet(),
    val isEmpty: Boolean = false,
    val totalNotPositive: Boolean = false,
) {
    val canCheckout: Boolean
        get() = !isEmpty && lineIssues.isEmpty() && !billDiscountTooLarge && !billDiscountReasonMissing && !billDiscountAboveLimit && !loyaltyInvalid && customerIssues.isEmpty() && !totalNotPositive
}

object CartValidator {

    const val CUSTOMER_NAME_MAX = 60

    /**
     * [discountLimitBp] is the most the signed-in person may discount (basis points of the value), or null when
     * there is no limit for them. The Employee limit is also enforced when the sale is saved.
     */
    fun check(cart: Cart, totals: BillTotals, gstRegistered: Boolean, discountLimitBp: Int? = null): CartCheck {
        val lineIssues = buildMap {
            cart.lines.forEach { line ->
                val gross = line.unitPrice * line.quantity
                val issue = when {
                    line.availableStock == null -> LineIssue.Unavailable
                    line.quantity > line.availableStock -> LineIssue.NotEnoughStock(line.availableStock)
                    gstRegistered && line.gstRate == null -> LineIssue.MissingGstRate
                    line.discount is Discount.Amount && line.discount.value > gross -> LineIssue.DiscountTooLarge
                    line.discount != null && DiscountPolicy.cleanReason(line.discountReason) == null -> LineIssue.DiscountReasonMissing
                    line.discount != null && discountLimitBp != null && lineExceeds(totals, line.variantId, discountLimitBp) -> LineIssue.DiscountAboveLimit
                    else -> null
                }
                if (issue != null) put(line.variantId, issue)
            }
        }
        val afterLineDiscounts = totals.subtotal - totals.lineDiscountTotal
        val billDiscountTooLarge = (cart.billDiscount as? Discount.Amount)?.let { it.value > afterLineDiscounts } == true
        val manualDiscount = totals.billDiscountTotal - totals.loyaltyDiscount
        val loyaltyInvalid = cart.loyalty?.let { it.amount > afterLineDiscounts - manualDiscount || cart.customer.customerId == null } == true
        val billReasonMissing = cart.billDiscount != null && DiscountPolicy.cleanReason(cart.billDiscountReason) == null
        return CartCheck(
            lineIssues = lineIssues,
            billDiscountTooLarge = billDiscountTooLarge,
            billDiscountReasonMissing = billReasonMissing,
            billDiscountAboveLimit = discountLimitBp != null && DiscountPolicy.billExceeds(totals, discountLimitBp),
            loyaltyInvalid = loyaltyInvalid,
            customerIssues = checkCustomer(cart.customer),
            isEmpty = cart.isEmpty,
            totalNotPositive = !cart.isEmpty && totals.grandTotal <= Money.ZERO,
        )
    }

    private fun lineExceeds(totals: BillTotals, variantId: Long, limitBp: Int): Boolean =
        totals.lines.firstOrNull { it.variantId == variantId }?.let { DiscountPolicy.exceeds(it.lineDiscount, it.gross, limitBp, roundingSlack = 1) } == true

    fun checkCustomer(customer: CustomerDetails): Set<CustomerIssue> = buildSet {
        val name = customer.name.trim()
        if (name.length > CUSTOMER_NAME_MAX) add(CustomerIssue.NAME_TOO_LONG)
        if (customer.phone.isNotBlank() && IndianPhone.normalizeMobile(customer.phone) == null) add(CustomerIssue.INVALID_PHONE)
        if (customer.gstin.isNotBlank()) {
            if (!Gstin.isValid(customer.gstin)) add(CustomerIssue.INVALID_GSTIN)
            if (name.isEmpty()) add(CustomerIssue.NAME_REQUIRED_WITH_GSTIN)
        }
    }
}
