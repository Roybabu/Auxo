package com.auxo.pos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCategoryTest {

    @Test
    fun footwearAndAccessories_areSoldInSizeAndColour_withNoExpiryOrUnits() {
        for (category in listOf(ProductCategory.FOOTWEAR, ProductCategory.ACCESSORIES)) {
            assertTrue(category.usesSizeAndColour)
            assertTrue(category.usesMaterial)
            assertFalse(category.tracksExpiry)
            assertFalse(category.usesNetQuantity)
            assertTrue(category.allowedUnits.isEmpty())
        }
    }

    @Test
    fun footwear_offersShoeSizes_notClothingSizes() {
        assertEquals(listOf("4", "5", "6", "7", "8", "9", "10", "11", "12"), ProductCategory.FOOTWEAR.presetSizes)
        assertTrue("M" in ProductCategory.GARMENT.presetSizes)
        assertFalse("M" in ProductCategory.FOOTWEAR.presetSizes)
    }

    @Test
    fun accessories_offerFreeSize() {
        assertEquals("Free size", ProductCategory.ACCESSORIES.presetSizes.first())
    }

    @Test
    fun theApparelGstRule_coversGarmentsAndFootwearOnly() {
        assertEquals(
            setOf(ProductCategory.GARMENT, ProductCategory.FOOTWEAR),
            ProductCategory.entries.filter { it.usesApparelGst }.toSet(),
        )
    }

    @Test
    fun foodAndBeverages_stillTrackExpiry() {
        assertEquals(
            setOf(ProductCategory.PACKAGED_FOOD, ProductCategory.BEVERAGE),
            ProductCategory.entries.filter { it.tracksExpiry }.toSet(),
        )
    }
}
