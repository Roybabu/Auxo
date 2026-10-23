// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.validation

import com.auxo.pos.domain.jewellery.JewelleryMetal
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.domain.model.StockUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JewelleryValidatorTest {

    private fun valid(form: ProductForm) = (ProductFormValidator.validate(form) as ProductValidation.Valid).product
    private fun errorsOf(form: ProductForm) = (ProductFormValidator.validate(form) as ProductValidation.Invalid).errors

    private fun ring(metal: JewelleryMetal? = JewelleryMetal.GOLD_22K, making: String = "12") = ProductForm(
        category = ProductCategory.JEWELLERY,
        name = "Gold ring",
        metal = metal,
        makingChargePercent = making,
        variants = listOf(
            VariantForm(key = "a", netQuantity = "5.250", unit = QuantityUnit.GRAM, sellingPrice = "35000", openingStock = "1"),
        ),
    )

    @Test
    fun aRing_keepsItsMetalAndMakingCharge() {
        val product = valid(ring())
        assertEquals(JewelleryMetal.GOLD_22K, product.metal)
        assertEquals(1200, product.makingChargeBasisPoints)
        assertEquals(5250L, product.variants.single().netQuantity?.amountMilli)
    }

    @Test
    fun theMetalIsRequired() {
        assertEquals(FieldError.REQUIRED, errorsOf(ring(metal = null)).fields[FormField.METAL])
    }

    @Test
    fun theMakingChargeIsOptional_butMustBeAPercentageUpToOneHundred() {
        assertNull(valid(ring(making = "")).makingChargeBasisPoints)
        assertEquals(10_000, valid(ring(making = "100")).makingChargeBasisPoints)
        assertEquals(850, valid(ring(making = "8.5")).makingChargeBasisPoints)
        assertEquals(FieldError.INVALID_MAKING_CHARGE, errorsOf(ring(making = "101")).fields[FormField.MAKING_CHARGE])
        assertEquals(FieldError.INVALID_MAKING_CHARGE, errorsOf(ring(making = "abc")).fields[FormField.MAKING_CHARGE])
    }

    @Test
    fun otherGoodsIgnoreTheMetal() {
        val product = valid(ring().copy(category = ProductCategory.GENERAL, variants = listOf(VariantForm(key = "b", sellingPrice = "40", openingStock = "1"))))
        assertNull(product.metal)
        assertNull(product.makingChargeBasisPoints)
    }

    @Test
    fun looseGoods_areCountedInWeightOrVolume() {
        val form = ProductForm(
            category = ProductCategory.LOOSE,
            name = "Basmati rice",
            stockUnit = StockUnit.GRAM,
            variants = listOf(VariantForm(key = "c", sellingPrice = "80", openingStock = "25.5")),
        )
        val product = valid(form)
        assertEquals(StockUnit.GRAM, product.stockUnit)
        assertEquals(25_500, product.variants.single().stockQuantity)
    }

    @Test
    fun looseGoods_cannotBeCountedInPieces_andOthersCannotUseWeight() {
        val loose = ProductForm(category = ProductCategory.LOOSE, name = "Sugar", stockUnit = StockUnit.UNIT, variants = listOf(VariantForm(key = "d", sellingPrice = "45", openingStock = "2")))
        assertEquals(StockUnit.GRAM, valid(loose).stockUnit)
        val general = loose.copy(category = ProductCategory.GENERAL, stockUnit = StockUnit.GRAM)
        assertEquals(StockUnit.UNIT, valid(general).stockUnit)
    }
}
