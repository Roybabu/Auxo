package com.invision.pos.domain.billing

import com.invision.pos.domain.model.GstRate
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant
import java.time.LocalDate

/**
 * One line in the cart: a shop product variant or a restaurant dish, with the
 * details needed for the invoice.
 */
data class CartLine(
    /**
     * Identifies the line. For shop products this is the variant id; for dishes it
     * is minus the menu item id (see [forMenuItem]), so the two never clash.
     */
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
    /**
     * Units that can be sold right now, or null if the item no longer exists or
     * can't be sold. Dishes have no stock limit of their own.
     */
    val availableStock: Int?,
    /** Set for restaurant dishes. */
    val menuItemId: Long? = null,
) {
    val isMenuItem: Boolean get() = menuItemId != null

    /** The inventory variant sold, or null for a dish. */
    val stockVariantId: Long? get() = if (menuItemId == null) variantId else null

    companion object {
        /** No stock limit for dishes; ingredients are checked when the sale is saved. */
        const val UNLIMITED = Int.MAX_VALUE

        /** Line key for a dish. */
        fun keyForMenuItem(menuItemId: Long): Long = -menuItemId

        /**
         * A shop product line. With [today], batch-tracked items can only sell
         * stock that hasn't expired.
         */
        fun from(product: Product, variant: ProductVariant, quantity: Int = 1, today: LocalDate? = null) = CartLine(
            variantId = variant.id,
            productId = product.id,
            productName = product.name,
            variantLabel = variant.attributesLabel,
            barcode = variant.barcode,
            hsnCode = product.hsnCode,
            unitPrice = variant.sellingPrice,
            mrp = variant.mrp,
            gstRate = product.gstRate,
            quantity = quantity,
            availableStock = if (today != null) variant.sellableQuantity(today) else variant.stockQuantity,
        )

        fun forMenuItem(item: MenuItem, quantity: Int = 1) = CartLine(
            variantId = keyForMenuItem(item.id),
            productId = 0,
            productName = item.name,
            variantLabel = item.section,
            barcode = "",
            hsnCode = item.sacCode,
            unitPrice = item.price,
            mrp = null,
            gstRate = GstRate.Fixed(item.gstRateBasisPoints),
            quantity = quantity,
            availableStock = if (item.available) UNLIMITED else null,
            menuItemId = item.id,
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
) {
    val isWalkIn: Boolean get() = customerId == null && name.isBlank() && phone.isBlank() && gstin.isBlank()
}

data class Cart(
    val lines: List<CartLine> = emptyList(),
    val billDiscount: Discount? = null,
    val customer: CustomerDetails = CustomerDetails(),
    /** Two-digit GST state code where the goods are supplied; null means the store's state. */
    val placeOfSupplyStateCode: String? = null,
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
                    it.copy(quantity = it.quantity + line.quantity, availableStock = line.availableStock)
                } else {
                    it
                }
            })
        }
    }

    fun setQuantity(variantId: Long, quantity: Int): Cart =
        if (quantity <= 0) remove(variantId) else updateLine(variantId) { it.copy(quantity = quantity) }

    fun setLineDiscount(variantId: Long, discount: Discount?): Cart = updateLine(variantId) { it.copy(discount = discount) }

    fun remove(variantId: Long): Cart = copy(lines = lines.filterNot { it.variantId == variantId })

    fun updateLine(variantId: Long, transform: (CartLine) -> CartLine): Cart =
        copy(lines = lines.map { if (it.variantId == variantId) transform(it) else it })
}
