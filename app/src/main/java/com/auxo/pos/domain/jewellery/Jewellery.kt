package com.auxo.pos.domain.jewellery

import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.domain.model.Rounding

/** The metal (and purity) a piece of jewellery is made of. The shop keeps one rate per gram for each. */
enum class JewelleryMetal {
    GOLD_24K,
    GOLD_22K,
    GOLD_18K,
    SILVER,
    PLATINUM,
}

/**
 * Today's rate per gram for each metal, as the owner typed them. A metal with no rate is not priced.
 * Kept as text of the form "GOLD_22K=612300;SILVER=9500" (paise per gram) so it is easy to store.
 */
data class MetalRates(val perGram: Map<JewelleryMetal, Money> = emptyMap()) {

    fun rateFor(metal: JewelleryMetal): Money? = perGram[metal]?.takeIf { it > Money.ZERO }

    fun with(metal: JewelleryMetal, rate: Money?): MetalRates =
        copy(perGram = if (rate == null || rate <= Money.ZERO) perGram - metal else perGram + (metal to rate))

    fun encode(): String = perGram.entries.sortedBy { it.key.ordinal }.joinToString(";") { "${it.key.name}=${it.value.minorUnits}" }

    companion object {
        fun decode(text: String?): MetalRates {
            if (text.isNullOrBlank()) return MetalRates()
            val rates = text.split(';').mapNotNull { part ->
                val pieces = part.split('=')
                if (pieces.size != 2) return@mapNotNull null
                val metal = JewelleryMetal.entries.firstOrNull { it.name == pieces[0].trim() } ?: return@mapNotNull null
                val minor = pieces[1].trim().toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                metal to Money(minor)
            }
            return MetalRates(rates.toMap())
        }
    }
}

/** How a piece of jewellery is priced: the metal in it at today's rate, plus a making charge, plus GST. */
object JewelleryPricing {

    /** The most the making charge can be: the whole value of the metal again. */
    const val MAKING_MAX_BASIS_POINTS = 10_000

    /**
     * The selling price of a piece of [weightMilligrams] at [ratePerGram] (paise per gram), with the making charge
     * ([makingBasisPoints] of the metal value) and, when the shop's prices include GST, the tax at [gstBasisPoints].
     * Rounded to the nearest rupee, as tags are written.
     */
    fun price(weightMilligrams: Long, ratePerGram: Money, makingBasisPoints: Int, gstBasisPoints: Int, pricesIncludeTax: Boolean): Money {
        require(weightMilligrams > 0 && ratePerGram > Money.ZERO && makingBasisPoints >= 0 && gstBasisPoints >= 0)
        val metal = Rounding.halfUp(weightMilligrams * ratePerGram.minorUnits, 1000L)
        val making = Rounding.halfUp(metal * makingBasisPoints, 10_000L)
        var value = metal + making
        if (pricesIncludeTax) value += Rounding.halfUp(value * gstBasisPoints, 10_000L)
        return BillCalculator.roundToRupee(Money(value))
    }

    /**
     * The variants of [product] priced at [rates], or null when it is not jewellery or its metal has no rate yet.
     * A variant without a weight keeps its price. The MRP is cleared: jewellery has none.
     */
    fun repriced(product: Product, rates: MetalRates, pricesIncludeTax: Boolean): List<ProductVariant>? {
        if (product.category != ProductCategory.JEWELLERY) return null
        val metal = product.metal ?: return null
        val rate = rates.rateFor(metal) ?: return null
        val gst = (product.gstRate as? GstRate.Fixed)?.basisPoints ?: ProductCategory.JEWELLERY_GST_BASIS_POINTS
        val making = product.makingChargeBasisPoints ?: 0
        return product.variants.map { variant ->
            val weight = variant.netQuantity?.takeIf { it.unit == QuantityUnit.GRAM }?.amountMilli
            if (weight == null) {
                variant
            } else {
                variant.copy(sellingPrice = price(weight, rate, making, gst, pricesIncludeTax), mrp = null)
            }
        }
    }
}
