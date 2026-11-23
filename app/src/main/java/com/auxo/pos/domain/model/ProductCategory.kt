// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

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

    /** Anything that needs no size, colour or expiry: stationery, toys, hardware, household goods, gifts. One price and one stock count. */
    GENERAL,

    /** Phones, TVs, appliances and the like. Sold by model or capacity and colour, with a warranty period. */
    ELECTRONICS,

    /** Medicines and health products. Sold in packs (strips, bottles) with batch and expiry, and a prescription flag. */
    PHARMACY,

    /** Skin, hair and make-up products. Sold in packs with batch and expiry. */
    COSMETICS,

    /** Rice, sugar, oil, spices and the like, sold by weight or volume. Stock is kept in grams or millilitres and the price is per kilogram or litre. */
    LOOSE,

    /** Gold, silver and other metal pieces. Each piece has its own weight; the price comes from the metal rate and a making charge. */
    JEWELLERY,

    /** Dishes and drinks made or served to order. Stock is optional, per item. */
    RESTAURANT,
    ;

    /** Garments, footwear and accessories are sold in size and colour variants. */
    val usesSizeAndColour: Boolean get() = this == GARMENT || this == FOOTWEAR || this == ACCESSORIES || this == ELECTRONICS

    /** Wearable goods carry a material (cotton, leather, ...). */
    val usesMaterial: Boolean get() = this == GARMENT || this == FOOTWEAR || this == ACCESSORIES

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
    val usesNetQuantity: Boolean get() = this == PACKAGED_FOOD || this == BEVERAGE || this == PHARMACY || this == COSMETICS || this == JEWELLERY

    /** Food and beverages are kept in batches with expiry dates. Clothing and restaurant items are not. */
    val tracksExpiry: Boolean get() = this == PACKAGED_FOOD || this == BEVERAGE || this == PHARMACY || this == COSMETICS

    /** Electronics carry a warranty period in months. */
    val usesWarranty: Boolean get() = this == ELECTRONICS

    /** Jewellery carries the metal it is made of and a making charge. */
    val usesMetal: Boolean get() = this == JEWELLERY

    /** Loose goods are counted and sold by weight or volume rather than by the piece. */
    val soldByMeasure: Boolean get() = this == LOOSE

    /** Medicines can be marked as needing a prescription. */
    val usesPrescription: Boolean get() = this == PHARMACY

    /** Restaurant items can be sold without a stock count; every other category always keeps stock. */
    val canSkipStock: Boolean get() = this == RESTAURANT

    /** A dish has no brand; every other kind of product can have one. */
    val usesBrand: Boolean get() = this != RESTAURANT

    /** Veg / non-veg marking applies to packaged food and to restaurant items. */
    val usesDietType: Boolean get() = this == PACKAGED_FOOD || this == RESTAURANT

    val allowedUnits: List<QuantityUnit>
        get() = when (this) {
            GARMENT, FOOTWEAR, ACCESSORIES, ELECTRONICS, GENERAL, LOOSE, RESTAURANT -> emptyList()
            PHARMACY -> listOf(QuantityUnit.PIECE, QuantityUnit.MILLILITRE, QuantityUnit.GRAM)
            COSMETICS -> listOf(QuantityUnit.MILLILITRE, QuantityUnit.GRAM, QuantityUnit.PIECE)
            JEWELLERY -> listOf(QuantityUnit.GRAM)
            PACKAGED_FOOD -> listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.PIECE)
            BEVERAGE -> listOf(QuantityUnit.MILLILITRE, QuantityUnit.LITRE)
        }

    val defaultUnit: QuantityUnit?
        get() = allowedUnits.firstOrNull()

    /** How stock can be counted: by unit (pieces or packs). */
    val allowedStockUnits: List<StockUnit>
        get() = if (this == LOOSE) listOf(StockUnit.GRAM, StockUnit.MILLILITRE) else listOf(StockUnit.UNIT)

    companion object {
        /** Restaurant service is usually taxed at 5% GST. */
        const val RESTAURANT_GST_BASIS_POINTS = 500

        /** Service accounting code for restaurant and food serving services, printed instead of an HSN code. */
        const val RESTAURANT_SAC = "996331"

        /** Jewellery is taxed at 3% GST. */
        const val JEWELLERY_GST_BASIS_POINTS = 300
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
