package com.auxo.pos.domain.billing

import com.auxo.pos.domain.loyalty.LoyaltyRedemption
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import java.time.LocalDate

/**
 * One line in the cart: a product variant (a garment, a packaged item or a restaurant item) or a one-off
 * item typed in at the counter, with the details needed for the invoice.
 */
data class CartLine(
    /** Identifies the line: the variant id, or a negative key for a one-off item (see [CUSTOM_KEY_BASE]). */
    val variantId: Long,
    val productId: Long,
    val productName: String,
    val variantLabel: String,
    val barcode: String,
    val hsnCode: String?,
    val unitPrice: Money,
    val mrp: Money?,
    val gstRate: GstRate?,
    val quantity: Int,
    val discount: Discount? = null,
    /** Why the line is discounted; needed whenever [discount] is set. */
    val discountReason: String = "",
    /**
     * Units that can be sold right now, or null if the item no longer exists or
     * can't be sold. An item sold without a stock count has no limit.
     */
    val availableStock: Int?,
    /** A one-off item typed in at the counter: not in the shop's list, so no stock is tracked. */
    val isCustom: Boolean = false,
) {
    /** True while one more unit can still be sold: the "+" button is on only then. */
    val canIncrease: Boolean get() = availableStock != null && quantity < availableStock

    /** [wanted] kept within what can be sold. An item that cannot be sold at all is left as asked. */
    fun withinStock(wanted: Int): Int = if (availableStock == null) wanted else wanted.coerceAtMost(availableStock)

    /** The inventory variant sold, or null for a one-off item. */
    val stockVariantId: Long? get() = if (!isCustom) variantId else null

    companion object {
        /** No stock limit: one-off items and restaurant items sold without a stock count. */
        const val UNLIMITED = Int.MAX_VALUE

        /** Custom item keys count down from here, far below any variant id. */
        const val CUSTOM_KEY_BASE: Long = -(1L shl 40)

        /** A one-off item that is not in the shop's list yet. [key] must come from [CUSTOM_KEY_BASE] downwards. */
        fun custom(key: Long, draft: CustomItemDraft) = CartLine(
            variantId = key,
            productId = 0,
            productName = draft.name,
            variantLabel = "",
            barcode = "",
            hsnCode = null,
            unitPrice = draft.unitPrice,
            mrp = null,
            gstRate = GstRate.Fixed(draft.gstBasisPoints),
            quantity = draft.quantity,
            availableStock = UNLIMITED,
            isCustom = true,
        )

        /**
         * A product line. With [today], batch-tracked items can only sell stock that hasn't expired. A restaurant
         * item sold without a stock count has no limit, and is unavailable while it is switched off.
         */
        fun from(product: Product, variant: ProductVariant, quantity: Int = 1, today: LocalDate? = null) = CartLine(
            variantId = variant.id,
            productId = product.id,
            productName = product.name,
            // A restaurant item is shown under its menu section, as a dish was.
            variantLabel = if (product.category == ProductCategory.RESTAURANT) product.subcategory.orEmpty() else variant.attributesLabel,
            barcode = variant.barcode,
            hsnCode = product.hsnCode,
            unitPrice = variant.sellingPrice,
            mrp = variant.mrp,
            gstRate = product.gstRate,
            quantity = quantity,
            availableStock = when {
                !product.available -> null
                !product.tracksStock -> UNLIMITED
                today != null -> variant.sellableQuantity(today)
                else -> variant.stockQuantity
            },
        )
    }
}

/** Optional buyer details. A blank name prints as "Walk-in Customer". */
data class CustomerDetails(
    val name: String = "",
    val phone: String = "",
    val gstin: String = "",
    /** Saved customer the bill is for; needed to put the bill on account. */
    val customerId: Long? = null,
)

data class Cart(
    val lines: List<CartLine> = emptyList(),
    val billDiscount: Discount? = null,
    /** Why the bill is discounted; needed whenever [billDiscount] is set. */
    val billDiscountReason: String = "",
    /** Loyalty points being used on this bill. Taken off after [billDiscount]; needs a saved customer. */
    val loyalty: LoyaltyRedemption? = null,
    val customer: CustomerDetails = CustomerDetails(),
    /** Two-digit GST state code where the goods are supplied; null means the store's state. */
    val placeOfSupplyStateCode: String? = null,
    /** Dine-in or takeaway; null until chosen (and always null outside restaurant mode). */
    val serviceType: ServiceType? = null,
    /** The table for a dine-in order, as typed. */
    val tableNumber: String = "",
) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val itemCount: Int get() = lines.sumOf { it.quantity }

    /** Adds one unit, merging with an existing line for the same variant. */
    fun add(line: CartLine): Cart {
        val existing = lines.firstOrNull { it.variantId == line.variantId }
        return if (existing == null) {
            copy(lines = lines + line)
        } else {
            copy(lines = lines.map {
                if (it.variantId == line.variantId) {
                    val merged = it.copy(availableStock = line.availableStock)
                    merged.copy(quantity = merged.withinStock(it.quantity + line.quantity))
                } else {
                    it
                }
            })
        }
    }

    fun setQuantity(variantId: Long, quantity: Int): Cart =
        if (quantity <= 0) remove(variantId) else updateLine(variantId) { it.copy(quantity = it.withinStock(quantity)) }

    fun setLineDiscount(variantId: Long, discount: Discount?): Cart = updateLine(variantId) { it.copy(discount = discount) }

    fun setLineDiscountReason(variantId: Long, reason: String): Cart = updateLine(variantId) { it.copy(discountReason = reason) }

    fun remove(variantId: Long): Cart = copy(lines = lines.filterNot { it.variantId == variantId })

    fun updateLine(variantId: Long, transform: (CartLine) -> CartLine): Cart =
        copy(lines = lines.map { if (it.variantId == variantId) transform(it) else it })
}
