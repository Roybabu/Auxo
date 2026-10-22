package com.auxo.pos.domain.jewellery

import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.NetQuantity
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JewelleryTest {

    private val rates = MetalRates(mapOf(JewelleryMetal.GOLD_22K to Money(6_500_00), JewelleryMetal.SILVER to Money(95_00)))

    private fun piece(id: Long, grams: String, price: Long = 1) = ProductVariant(
        id = id, barcode = "J$id", netQuantity = NetQuantity.parseOrNull(grams, QuantityUnit.GRAM),
        sellingPrice = Money(price), mrp = Money(99_999_00), stockQuantity = 1,
    )

    private fun ring(vararg variants: ProductVariant, metal: JewelleryMetal? = JewelleryMetal.GOLD_22K, making: Int? = 1200, gst: GstRate? = GstRate.Fixed(300)) = Product(
        name = "Ring", category = ProductCategory.JEWELLERY, metal = metal, makingChargeBasisPoints = making, gstRate = gst,
        variants = variants.toList(),
    )

    @Test
    fun theMetalValue_isWeightTimesRate() {
        // 10 g of gold at 6,500 a gram, no making charge and no tax.
        assertEquals(Money(65_000_00), JewelleryPricing.price(10_000, Money(6_500_00), 0, 300, false))
    }

    @Test
    fun theMakingCharge_isAShareOfTheMetalValue() {
        // 10 g at 6,500 = 65,000; 12% making = 7,800; total 72,800.
        assertEquals(Money(72_800_00), JewelleryPricing.price(10_000, Money(6_500_00), 1200, 300, false))
    }

    @Test
    fun whenPricesIncludeGst_theTaxIsAddedToTheWholeValue() {
        // 72,800 plus 3% = 74,984.
        assertEquals(Money(74_984_00), JewelleryPricing.price(10_000, Money(6_500_00), 1200, 300, true))
    }

    @Test
    fun theTag_isRoundedToTheNearestRupee() {
        // 2.345 g at 6,500 = 15,242.50 -> 15,243.
        assertEquals(Money(15_243_00), JewelleryPricing.price(2_345, Money(6_500_00), 0, 300, false))
        // 2.344 g gives 15,236.00 exactly.
        assertEquals(Money(15_236_00), JewelleryPricing.price(2_344, Money(6_500_00), 0, 300, false))
    }

    @Test
    fun milligramWeights_arePricedExactly() {
        // 0.001 g at 6,500 a gram is 6.50.
        assertEquals(Money(7_00), JewelleryPricing.price(1, Money(6_500_00), 0, 0, false))
    }

    @Test
    fun repricing_setsEachPiecePrice_fromItsOwnWeight_andClearsTheMrp() {
        val repriced = JewelleryPricing.repriced(ring(piece(1, "5"), piece(2, "10")), rates, pricesIncludeTax = false)!!
        assertEquals(listOf(Money(36_400_00), Money(72_800_00)), repriced.map { it.sellingPrice })
        assertTrue(repriced.all { it.mrp == null })
    }

    @Test
    fun repricing_usesTheShopGstWhenPricesIncludeTax() {
        val repriced = JewelleryPricing.repriced(ring(piece(1, "10")), rates, pricesIncludeTax = true)!!
        assertEquals(Money(74_984_00), repriced.single().sellingPrice)
    }

    @Test
    fun repricing_defaultsToThreePercentGst_andNoMakingCharge() {
        val product = ring(piece(1, "10"), making = null, gst = null)
        assertEquals(Money(66_950_00), JewelleryPricing.repriced(product, rates, pricesIncludeTax = true)!!.single().sellingPrice)
    }

    @Test
    fun aPieceWithoutAWeight_keepsItsPrice() {
        val noWeight = ProductVariant(id = 3, barcode = "J3", sellingPrice = Money(5_000_00))
        val repriced = JewelleryPricing.repriced(ring(noWeight), rates, pricesIncludeTax = false)!!
        assertEquals(Money(5_000_00), repriced.single().sellingPrice)
    }

    @Test
    fun nothingIsRepriced_withoutARate_aMetal_orForOtherGoods() {
        assertNull(JewelleryPricing.repriced(ring(piece(1, "5"), metal = JewelleryMetal.PLATINUM), rates, false))
        assertNull(JewelleryPricing.repriced(ring(piece(1, "5"), metal = null), rates, false))
        assertNull(JewelleryPricing.repriced(ring(piece(1, "5")).copy(category = ProductCategory.GENERAL), rates, false))
    }

    @Test
    fun ratesSurviveBeingSavedAsText() {
        val text = rates.encode()
        assertEquals("GOLD_22K=650000;SILVER=9500", text)
        assertEquals(rates, MetalRates.decode(text))
    }

    @Test
    fun brokenRateText_isIgnoredPiecePerPiece() {
        val decoded = MetalRates.decode("GOLD_22K=650000;NOPE=5;SILVER=abc;GOLD_24K=0;PLATINUM")
        assertEquals(mapOf(JewelleryMetal.GOLD_22K to Money(650000)), decoded.perGram)
        assertEquals(MetalRates(), MetalRates.decode(null))
        assertEquals(MetalRates(), MetalRates.decode("  "))
    }

    @Test
    fun aRateCanBeSetAndCleared() {
        val set = MetalRates().with(JewelleryMetal.SILVER, Money(100_00))
        assertEquals(Money(100_00), set.rateFor(JewelleryMetal.SILVER))
        assertNull(set.with(JewelleryMetal.SILVER, null).rateFor(JewelleryMetal.SILVER))
        assertNull(set.with(JewelleryMetal.SILVER, Money.ZERO).rateFor(JewelleryMetal.SILVER))
    }
}
