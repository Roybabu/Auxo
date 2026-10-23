package com.auxo.pos.domain.validation

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewCategoriesValidatorTest {

    private var keys = 0
    private fun key() = "n${keys++}"

    private fun valid(form: ProductForm) = (ProductFormValidator.validate(form) as ProductValidation.Valid).product
    private fun errorsOf(form: ProductForm) = (ProductFormValidator.validate(form) as ProductValidation.Invalid).errors

    private fun general() = ProductForm(
        category = ProductCategory.GENERAL,
        name = "Notebook",
        variants = listOf(VariantForm(key = key(), sellingPrice = "40", openingStock = "10")),
    )

    private fun electronics(warranty: String, category: ProductCategory = ProductCategory.ELECTRONICS): ProductForm {
        val defaults = VariantDefaults(sellingPrice = "15999", openingStock = "3")
        val sizes = listOf("128 GB")
        val colours = listOf("Black")
        return ProductForm(
            category = category,
            name = "Phone X",
            warrantyMonths = warranty,
            sizes = sizes,
            colours = colours,
            defaults = defaults,
            variants = GarmentVariants.sync(sizes, colours, emptyList(), defaults, ::key),
        )
    }

    private fun packed(category: ProductCategory, unit: QuantityUnit, prescription: Boolean = false) = ProductForm(
        category = category,
        name = "Cough Syrup",
        prescriptionRequired = prescription,
        variants = listOf(
            VariantForm(
                key = key(), netQuantity = "100", unit = unit, sellingPrice = "85",
                batchNumber = "B7", expiryDate = TestData.today.plusDays(200),
            ),
        ),
    )

    @Test
    fun aGeneralItem_needsOnlyANamePriceAndStock() {
        val product = valid(general())
        val variant = product.variants.single()
        assertEquals(Money(40_00), variant.sellingPrice)
        assertEquals(10, variant.stockQuantity)
        assertNull(variant.size)
        assertNull(variant.colour)
        assertNull(variant.netQuantity)
        assertNull(variant.expiryDate)
        assertNull(product.material)
    }

    @Test
    fun electronics_areSoldInModelAndColour_withAWarranty() {
        val product = valid(electronics("12"))
        assertEquals("128 GB · Black", product.variants.single().attributesLabel)
        assertEquals(12, product.warrantyMonths)
        assertNull(product.material)
    }

    @Test
    fun warranty_canBeLeftBlank_butMustBeWholeMonthsFromOneTo120() {
        assertNull(valid(electronics("")).warrantyMonths)
        assertEquals(120, valid(electronics("120")).warrantyMonths)
        for (bad in listOf("0", "121", "abc", "1.5", "-3")) {
            assertEquals(bad, FieldError.INVALID_WARRANTY, errorsOf(electronics(bad)).fields[FormField.WARRANTY])
        }
    }

    @Test
    fun aWarranty_isKeptOnlyForElectronics() {
        assertNull(valid(electronics("12", ProductCategory.GARMENT).copy(material = "Cotton")).warrantyMonths)
    }

    @Test
    fun pharmacy_keepsPackSizeBatchExpiryAndThePrescriptionFlag() {
        val product = valid(packed(ProductCategory.PHARMACY, QuantityUnit.MILLILITRE, prescription = true))
        val variant = product.variants.single()
        assertEquals("100 ml", variant.netQuantity?.format())
        assertEquals("B7", variant.batchNumber)
        assertEquals(TestData.today.plusDays(200), variant.expiryDate)
        assertTrue(product.prescriptionRequired)
        assertFalse(valid(packed(ProductCategory.PHARMACY, QuantityUnit.PIECE)).prescriptionRequired)
    }

    @Test
    fun aPrescriptionFlag_isIgnoredOutsidePharmacy() {
        assertFalse(valid(packed(ProductCategory.COSMETICS, QuantityUnit.MILLILITRE, prescription = true)).prescriptionRequired)
    }

    @Test
    fun cosmetics_keepPackSizeAndExpiry() {
        val variant = valid(packed(ProductCategory.COSMETICS, QuantityUnit.GRAM)).variants.single()
        assertEquals("100 g", variant.netQuantity?.format())
        assertEquals(TestData.today.plusDays(200), variant.expiryDate)
    }

    @Test
    fun aPackUnitTheCategoryDoesNotOffer_isRefused() {
        val form = packed(ProductCategory.PHARMACY, QuantityUnit.KILOGRAM)
        assertEquals(FieldError.UNIT_REQUIRED, errorsOf(form).forVariant(form.variants.single().key)[VariantField.UNIT])
    }
}
