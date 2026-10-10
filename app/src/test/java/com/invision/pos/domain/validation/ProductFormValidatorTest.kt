package com.invision.pos.domain.validation

import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.QuantityUnit
import com.invision.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductFormValidatorTest {

    private var keys = 0
    private fun key() = "k${keys++}"

    private fun garmentForm(): ProductForm {
        val defaults = VariantDefaults(sellingPrice = "499", mrp = "599", costPrice = "250", openingStock = "5", lowStockThreshold = "2")
        val sizes = listOf("M", "L")
        val colours = listOf("Navy")
        return ProductForm(
            category = ProductCategory.GARMENT,
            name = "Cotton T-Shirt",
            brand = "Roy Wear",
            material = "Cotton",
            sizes = sizes,
            colours = colours,
            defaults = defaults,
            variants = GarmentVariants.sync(sizes, colours, emptyList(), defaults, ::key),
        )
    }

    private fun foodForm(vararg rows: VariantForm) = ProductForm(
        category = ProductCategory.PACKAGED_FOOD,
        name = "Butter Biscuits",
        variants = rows.toList().ifEmpty {
            listOf(VariantForm(key = key(), netQuantity = "200", unit = QuantityUnit.GRAM, sellingPrice = "30", mrp = "35"))
        },
    )

    private fun errorsOf(form: ProductForm): ProductFormErrors =
        (ProductFormValidator.validate(form) as ProductValidation.Invalid).errors

    @Test
    fun validGarment_buildsProductWithOneVariantPerSizeAndColour() {
        val result = ProductFormValidator.validate(garmentForm())
        assertTrue(result is ProductValidation.Valid)
        val product = (result as ProductValidation.Valid).product
        assertEquals("Cotton T-Shirt", product.name)
        assertEquals(listOf("M · Navy", "L · Navy"), product.variants.map { it.attributesLabel })
        assertTrue(product.variants.all { it.sellingPrice == Money(49_900) && it.stockQuantity == 5 })
        // Blank barcodes are left for the save step to generate.
        assertTrue(product.variants.all { it.barcode.isEmpty() })
        assertNull(product.dietType)
    }

    @Test
    fun validFood_keepsNetQuantityAndExpiry() {
        val expiry = TestData.today.plusDays(60)
        val row = VariantForm(
            key = key(), netQuantity = "1.5", unit = QuantityUnit.KILOGRAM, sellingPrice = "120",
            barcode = TestData.EAN[2], batchNumber = "B12", expiryDate = expiry,
        )
        val product = (ProductFormValidator.validate(foodForm(row)) as ProductValidation.Valid).product
        val variant = product.variants.single()
        assertEquals("1.5 kg", variant.netQuantity?.format())
        assertEquals(expiry, variant.expiryDate)
        assertEquals("B12", variant.batchNumber)
        assertEquals(TestData.EAN[2], variant.barcode)
    }

    @Test
    fun missingName_andShortName() {
        assertEquals(FieldError.REQUIRED, errorsOf(garmentForm().copy(name = "  ")).fields[FormField.NAME])
        assertEquals(FieldError.TOO_SHORT, errorsOf(garmentForm().copy(name = "A")).fields[FormField.NAME])
        assertEquals(FieldError.TOO_LONG, errorsOf(garmentForm().copy(name = "A".repeat(81))).fields[FormField.NAME])
    }

    @Test
    fun garmentWithoutSizesOrColours() {
        val errors = errorsOf(garmentForm().copy(sizes = emptyList(), colours = emptyList(), variants = emptyList()))
        assertEquals(FieldError.REQUIRED, errors.fields[FormField.SIZES])
        assertEquals(FieldError.REQUIRED, errors.fields[FormField.COLOURS])
        assertEquals(FieldError.REQUIRED, errors.fields[FormField.VARIANTS])
    }

    @Test
    fun priceRules() {
        fun priceError(price: String, mrp: String = ""): Map<VariantField, FieldError> {
            val row = VariantForm(key = "p", netQuantity = "200", unit = QuantityUnit.GRAM, sellingPrice = price, mrp = mrp)
            return errorsOf(foodForm(row)).forVariant("p")
        }
        assertEquals(FieldError.REQUIRED, priceError("")[VariantField.SELLING_PRICE])
        assertEquals(FieldError.INVALID_PRICE, priceError("0")[VariantField.SELLING_PRICE])
        assertEquals(FieldError.INVALID_PRICE, priceError("12.345")[VariantField.SELLING_PRICE])
        assertEquals(FieldError.MRP_BELOW_PRICE, priceError("50", mrp = "45")[VariantField.MRP])
        assertEquals(FieldError.INVALID_PRICE, priceError("50", mrp = "abc")[VariantField.MRP])
    }

    @Test
    fun netQuantityAndUnitRequiredForFood() {
        val row = VariantForm(key = "q", netQuantity = "", unit = null, sellingPrice = "10")
        val errors = errorsOf(foodForm(row)).forVariant("q")
        assertEquals(FieldError.REQUIRED, errors[VariantField.NET_QUANTITY])
        assertEquals(FieldError.UNIT_REQUIRED, errors[VariantField.UNIT])

        // Litres are not a food unit.
        val wrongUnit = VariantForm(key = "u", netQuantity = "1", unit = QuantityUnit.LITRE, sellingPrice = "10")
        assertEquals(FieldError.UNIT_REQUIRED, errorsOf(foodForm(wrongUnit)).forVariant("u")[VariantField.UNIT])
    }

    @Test
    fun barcodeRules() {
        fun barcodeError(code: String): FieldError? {
            val row = VariantForm(key = "b", netQuantity = "200", unit = QuantityUnit.GRAM, sellingPrice = "10", barcode = code)
            return (ProductFormValidator.validate(foodForm(row)) as? ProductValidation.Invalid)
                ?.errors?.forVariant("b")?.get(VariantField.BARCODE)
        }
        assertNull(barcodeError(TestData.EAN[0]))
        assertNull(barcodeError("SHIRT-01"))
        assertEquals(FieldError.INVALID_BARCODE_CHECK_DIGIT, barcodeError("8901000000010"))
        assertEquals(FieldError.INVALID_BARCODE_CHARACTERS, barcodeError("AB CD"))
    }

    @Test
    fun duplicatesWithinTheForm() {
        val a = VariantForm(key = "a", netQuantity = "200", unit = QuantityUnit.GRAM, sellingPrice = "10", barcode = TestData.EAN[0], sku = "BIS-1")
        val b = VariantForm(key = "b", netQuantity = "200", unit = QuantityUnit.GRAM, sellingPrice = "12", barcode = TestData.EAN[0], sku = "bis-1")
        val errors = errorsOf(foodForm(a, b))
        assertEquals(FieldError.DUPLICATE_BARCODE, errors.forVariant("a")[VariantField.BARCODE])
        assertEquals(FieldError.DUPLICATE_SKU, errors.forVariant("b")[VariantField.SKU])
        assertEquals(FieldError.DUPLICATE_VARIANT, errors.forVariant("b")[VariantField.NET_QUANTITY])
    }

    @Test
    fun countsMustBeWholeNumbers() {
        val row = VariantForm(key = "c", netQuantity = "200", unit = QuantityUnit.GRAM, sellingPrice = "10", openingStock = "1.5", lowStockThreshold = "x")
        val errors = errorsOf(foodForm(row)).forVariant("c")
        assertEquals(FieldError.INVALID_QUANTITY, errors[VariantField.OPENING_STOCK])
        assertEquals(FieldError.INVALID_QUANTITY, errors[VariantField.LOW_STOCK])
    }

    @Test
    fun savedVariantsKeepTheirStock_andCannotBeRemovedWithStock() {
        val saved = TestData.tShirt()
        val form = ProductForm.fromProduct(saved)
        val product = (ProductFormValidator.validate(form, saved.variants) as ProductValidation.Valid).product
        assertEquals(listOf(8, 1), product.variants.map { it.stockQuantity })

        val withoutL = form.copy(sizes = listOf("M"), variants = form.variants.filter { it.size == "M" })
        val errors = (ProductFormValidator.validate(withoutL, saved.variants) as ProductValidation.Invalid).errors
        assertEquals(FieldError.REMOVED_VARIANT_HAS_STOCK, errors.fields[FormField.VARIANTS])
    }
}

class GarmentVariantsTest {

    private var keys = 0
    private fun key() = "k${keys++}"

    @Test
    fun sync_createsMatrixGroupedByColour() {
        val rows = GarmentVariants.sync(listOf("S", "M"), listOf("Red", "Blue"), emptyList(), VariantDefaults(sellingPrice = "299"), ::key)
        assertEquals(
            listOf("S|Red", "M|Red", "S|Blue", "M|Blue"),
            rows.map { "${it.size}|${it.colour}" },
        )
        assertTrue(rows.all { it.sellingPrice == "299" })
    }

    @Test
    fun sync_keepsEditedRowsAndDropsRemovedOnes() {
        val first = GarmentVariants.sync(listOf("S", "M"), listOf("Red"), emptyList(), VariantDefaults(), ::key)
        val edited = first.map { if (it.size == "M") it.copy(sellingPrice = "350", barcode = "M-RED") else it }

        val next = GarmentVariants.sync(listOf("M", "L"), listOf("Red"), edited, VariantDefaults(sellingPrice = "300"), ::key)
        assertEquals(listOf("M", "L"), next.map { it.size })
        assertEquals("350", next[0].sellingPrice)
        assertEquals("M-RED", next[0].barcode)
        assertEquals("300", next[1].sellingPrice)
    }

    @Test
    fun applyDefaults_setsPricesAndOnlyNewRowsOpeningStock() {
        val rows = listOf(
            VariantForm(key = "new", size = "S", colour = "Red"),
            VariantForm(key = "old", id = 5, size = "M", colour = "Red", openingStock = "", currentStock = 3),
        )
        val defaults = VariantDefaults(sellingPrice = "199", mrp = "249", costPrice = "90", openingStock = "4", lowStockThreshold = "1")
        val updated = GarmentVariants.applyDefaults(rows, defaults)
        assertTrue(updated.all { it.sellingPrice == "199" && it.mrp == "249" && it.lowStockThreshold == "1" })
        assertEquals("4", updated[0].openingStock)
        assertEquals("", updated[1].openingStock)
    }
}
