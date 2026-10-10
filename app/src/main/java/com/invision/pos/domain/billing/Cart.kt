package com.invision.pos.domain.billing

import com.invision.pos.domain.model.GstRate
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant

/** One variant in the cart, with the product details needed for the invoice. */
data class CartLine(
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
    /** Units on hand right now, or null if the variant no longer exists. */
    val availableStock: Int?,
) {
    companion object {
        fun from(product: Product, variant: ProductVariant, quantity: Int = 1) = CartLine(
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
            availableStock = variant.stockQuantity,
        )
    }
}

/** Optional buyer details. A blank name prints as "Walk-in Customer". */
data class CustomerDetails(
    val name: String = "",
    val phone: String = "",
    val gstin: String = "",
) {
    val isWalkIn: Boolean get() = name.isBlank() && phone.isBlank() && gstin.isBlank()
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
