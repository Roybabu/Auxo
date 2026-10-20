package com.auxo.pos.domain.model

/** The kinds of goods the store sells. Each category has its own product attributes. */
enum class ProductCategory {
    GARMENT,

    /** Shoes and sandals. Sold in size and colour variants, with shoe sizes instead of S/M/L. */
    FOOTWEAR,

    /** Bags, belts, caps and the like. Sold in colour variants; "Free size" is one tap when there is no size. */
    ACCESSORIES,
    PACKAGED_FOOD,
    BEVERAGE,

    /** Dishes and drinks made or served to order. Stock is optional, per item. */
    RESTAURANT,
    ;

    /** Garments, footwear and accessories are sold in size and colour variants. */
    val usesSizeAndColour: Boolean get() = this == GARMENT || this == FOOTWEAR || this == ACCESSORIES

    /** Wearable goods carry a material (cotton, leather, ...). */
    val usesMaterial: Boolean get() = usesSizeAndColour

    /** The 5% / 18% apparel and footwear GST rule. Accessories are taxed at their own rate. */
    val usesApparelGst: Boolean get() = this == GARMENT || this == FOOTWEAR

    /** The size chips offered first in the editor; any other size can still be typed in. */
    val presetSizes: List<String>
        get() = when (this) {
            GARMENT -> listOf("XS", "S", "M", "L", "XL", "XXL", "3XL")
            FOOTWEAR -> listOf("4", "5", "6", "7", "8", "9", "10", "11", "12")
            ACCESSORIES -> listOf("Free size", "S", "M", "L")
            else -> emptyList()
        }

    /** Packaged food and beverages carry a net quantity (weight or volume) on each pack. */
    val usesNetQuantity: Boolean get() = this == PACKAGED_FOOD || this == BEVERAGE

    /** Food and beverages are kept in batches with expiry dates. Clothing and restaurant items are not. */
    val tracksExpiry: Boolean get() = this == PACKAGED_FOOD || this == BEVERAGE

    /** Restaurant items can be sold without a stock count; every other category always keeps stock. */
    val canSkipStock: Boolean get() = this == RESTAURANT

    /** Veg / non-veg marking applies to packaged food and to restaurant items. */
    val usesDietType: Boolean get() = this == PACKAGED_FOOD || this == RESTAURANT

    val allowedUnits: List<QuantityUnit>
        get() = when (this) {
            GARMENT, FOOTWEAR, ACCESSORIES, RESTAURANT -> emptyList()
            PACKAGED_FOOD -> listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.PIECE)
            BEVERAGE -> listOf(QuantityUnit.MILLILITRE, QuantityUnit.LITRE)
        }

    val defaultUnit: QuantityUnit?
        get() = allowedUnits.firstOrNull()

    /** How stock can be counted: by unit (pieces or packs). */
    val allowedStockUnits: List<StockUnit>
        get() = listOf(StockUnit.UNIT)

    companion object {
        /** Restaurant service is usually taxed at 5% GST. */
        const val RESTAURANT_GST_BASIS_POINTS = 500

        /** Service accounting code for restaurant and food serving services, printed instead of an HSN code. */
        const val RESTAURANT_SAC = "996331"
    }
}

/** Veg / non-veg marking shown on packaged food in India. */
enum class DietType { VEG, NON_VEG }

enum class QuantityUnit(val symbol: String) {
    GRAM("g"),
    KILOGRAM("kg"),
    MILLILITRE("ml"),
    LITRE("L"),
    PIECE("pcs"),
}
