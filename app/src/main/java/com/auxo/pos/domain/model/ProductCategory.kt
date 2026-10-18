package com.auxo.pos.domain.model

/** The kinds of goods the store sells. Each category has its own product attributes. */
enum class ProductCategory {
    GARMENT,
    PACKAGED_FOOD,
    BEVERAGE,

    /** Dishes and drinks made or served to order. Stock is optional, per item. */
    RESTAURANT,
    ;

    /** Garments are sold in size and colour variants. */
    val usesSizeAndColour: Boolean get() = this == GARMENT

    /** Packaged food and beverages carry a net quantity (weight or volume) on each pack. */
    val usesNetQuantity: Boolean get() = this == PACKAGED_FOOD || this == BEVERAGE

    /** Food and beverages are kept in batches with expiry dates. Restaurant items are not. */
    val tracksExpiry: Boolean get() = this != GARMENT && this != RESTAURANT

    /** Restaurant items can be sold without a stock count; every other category always keeps stock. */
    val canSkipStock: Boolean get() = this == RESTAURANT

    /** Veg / non-veg marking applies to packaged food and to restaurant items. */
    val usesDietType: Boolean get() = this == PACKAGED_FOOD || this == RESTAURANT

    val allowedUnits: List<QuantityUnit>
        get() = when (this) {
            GARMENT, RESTAURANT -> emptyList()
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
