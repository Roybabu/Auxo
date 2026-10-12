package com.auxo.pos.domain.imports

import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.domain.validation.FieldError
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProductImportTest {

    private fun table(vararg lines: String): CsvTable =
        (CsvParser.parse(lines.joinToString("\n")) as CsvParseResult.Parsed).table

    private fun preview(
        table: CsvTable,
        existing: List<Product> = emptyList(),
        options: ImportOptions = ImportOptions(),
        mapping: ColumnMapping = ColumnMapping.auto(table.header),
    ): ImportPreview = ProductImport.preview(table, mapping, options, existing)

    private fun ImportItem.issue(column: ImportColumn): ImportIssue? = issues.firstOrNull { it.column == column }

    // Column mapping

    @Test
    fun theTemplateHeader_mapsEveryColumnToItself() {
        val header = ProductImportTemplate.header.split(",")
        val mapping = ColumnMapping.auto(header)
        ImportColumn.entries.forEachIndexed { index, column -> assertEquals(column.name, index, mapping.indexOf(column)) }
        assertTrue(mapping.isComplete)
    }

    @Test
    fun commonHeadings_areMatchedWithoutExactNames() {
        val mapping = ColumnMapping.auto(listOf("Product Name", "Price", "Qty", "Color", "Item Code", "Barcode", "GST %", "Net Qty"))
        assertEquals(0, mapping.indexOf(ImportColumn.NAME))
        assertEquals(1, mapping.indexOf(ImportColumn.SELLING_PRICE))
        assertEquals(2, mapping.indexOf(ImportColumn.OPENING_STOCK))
        assertEquals(3, mapping.indexOf(ImportColumn.COLOUR))
        assertEquals(4, mapping.indexOf(ImportColumn.SKU))
        assertEquals(5, mapping.indexOf(ImportColumn.BARCODE))
        assertEquals(6, mapping.indexOf(ImportColumn.GST_RATE))
        assertEquals(7, mapping.indexOf(ImportColumn.NET_QUANTITY))
    }

    @Test
    fun aFileColumn_feedsOnlyOneField() {
        // "price" and "rate" both look like the selling price; only the first is used.
        val mapping = ColumnMapping.auto(listOf("price", "rate"))
        assertEquals(0, mapping.indexOf(ImportColumn.SELLING_PRICE))
        assertEquals(setOf(ImportColumn.SELLING_PRICE), mapping.indexes.keys)
    }

    @Test
    fun withoutANameColumn_theMappingIsIncomplete() {
        assertFalse(ColumnMapping.auto(listOf("price", "stock")).isComplete)
        assertTrue(ColumnMapping.auto(listOf("price", "stock")).with(ImportColumn.NAME, 0).isComplete)
        assertFalse(ColumnMapping(mapOf(ImportColumn.NAME to 0)).with(ImportColumn.NAME, null).isComplete)
    }

    // The template

    @Test
    fun theTemplate_importsAsFourProducts() {
        val result = preview(table(*ProductImportTemplate.csv().trim().lines().toTypedArray()))
        assertEquals(listOf("Cotton T-Shirt", "Glucose Biscuits", "Mango Juice", "Wheat Flour"), result.items.map { it.name })
        assertTrue(result.items.all { it.status == ImportStatus.READY })
        assertEquals(5, result.rowCount)
        assertEquals(5, result.readyVariants)
    }

    @Test
    fun theTemplateProducts_carryTheValuesInTheFile() {
        val products = preview(table(*ProductImportTemplate.csv().trim().lines().toTypedArray())).ready.map { it.product!! }

        val shirt = products[0]
        assertEquals(ProductCategory.GARMENT, shirt.category)
        assertEquals(GstRate.Apparel, shirt.gstRate)
        assertEquals("6109", shirt.hsnCode)
        assertEquals(listOf("M", "L"), shirt.variants.map { it.size })
        assertEquals(listOf(10, 8), shirt.variants.map { it.stockQuantity })
        assertEquals(Money(49_900), shirt.variants[0].sellingPrice)
        assertEquals(Money(59_900), shirt.variants[0].mrp)
        assertEquals(Money(25_000), shirt.variants[0].costPrice)
        assertEquals("TS-BLU-M", shirt.variants[0].sku)

        val biscuits = products[1]
        assertEquals(DietType.VEG, biscuits.dietType)
        assertEquals(GstRate.Fixed(1_800), biscuits.gstRate)
        assertEquals(200_000L, biscuits.variants[0].netQuantity!!.amountMilli)
        assertEquals(QuantityUnit.GRAM, biscuits.variants[0].netQuantity!!.unit)
        assertEquals(LocalDate.of(2027, 3, 31), biscuits.variants[0].expiryDate)
        assertEquals("B-101", biscuits.variants[0].batchNumber)

        val juice = products[2]
        assertEquals(GstRate.Fixed(1_200), juice.gstRate)
        assertEquals(LocalDate.of(2026, 12, 31), juice.variants[0].expiryDate)
        assertEquals(QuantityUnit.LITRE, juice.variants[0].netQuantity!!.unit)

        val flour = products[3]
        assertEquals(StockUnit.GRAM, flour.stockUnit)
        assertEquals(10_000, flour.variants[0].stockQuantity)
        assertEquals(2_000, flour.variants[0].lowStockThreshold)
        assertEquals(Money(4_500), flour.variants[0].costPrice)
    }

    // Grouping and reading cells

    @Test
    fun rowsWithTheSameNameAndCategory_areVariantsOfOneProduct() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price",
                "Kurta,Garment,M,Red,799",
                "kurta,garment,L,Red,799",
                "Kurta,Garment,L,Blue,799",
            ),
        )
        assertEquals(1, result.items.size)
        assertEquals(3, result.items[0].variantCount)
        assertEquals(listOf(2, 3, 4), result.items[0].rowNumbers)
        assertEquals(ImportStatus.READY, result.items[0].status)
    }

    @Test
    fun theSameNameInTwoCategories_areTwoProducts() {
        val result = preview(
            table(
                "name,category,size,colour,net_quantity,unit,selling_price",
                "Mix,Garment,M,Red,,,799",
                "Mix,Packaged food,,,100,g,30",
            ),
        )
        assertEquals(2, result.items.size)
    }

    @Test
    fun amountsWithARupeeSignOrThousandsCommas_areRead() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price,mrp,opening_stock",
                "Jacket,Garment,L,Black,\"₹1,250.50\",\"1,500\",10.0",
            ),
        )
        val variant = result.ready.single().product!!.variants.single()
        assertEquals(Money(125_050), variant.sellingPrice)
        assertEquals(Money(150_000), variant.mrp)
        assertEquals(10, variant.stockQuantity)
    }

    @Test
    fun theCategoryAndDietAndUnit_acceptCommonSpellings() {
        assertEquals(ProductCategory.PACKAGED_FOOD, ProductImport.parseCategory("Packaged Food"))
        assertEquals(ProductCategory.PACKAGED_FOOD, ProductImport.parseCategory("packaged_food"))
        assertEquals(ProductCategory.GARMENT, ProductImport.parseCategory("Clothing"))
        assertEquals(ProductCategory.BEVERAGE, ProductImport.parseCategory("drinks"))
        assertEquals(ProductCategory.INGREDIENT, ProductImport.parseCategory("Raw material"))
        assertNull(ProductImport.parseCategory("toys"))
        assertEquals(DietType.NON_VEG, ProductImport.parseDiet("Non-Veg"))
        assertEquals(DietType.VEG, ProductImport.parseDiet("vegetarian"))
        assertEquals(QuantityUnit.MILLILITRE, ProductImport.parseUnit("ML"))
        assertEquals(QuantityUnit.LITRE, ProductImport.parseUnit("ltr"))
        assertEquals(QuantityUnit.PIECE, ProductImport.parseUnit("Pcs"))
        assertNull(ProductImport.parseUnit("bucket"))
    }

    @Test
    fun datesCanBeDayFirstOrYearFirst() {
        assertEquals(LocalDate.of(2027, 1, 5), ProductImport.parseDate("05/01/2027"))
        assertEquals(LocalDate.of(2027, 1, 5), ProductImport.parseDate("5-1-2027"))
        assertEquals(LocalDate.of(2027, 1, 5), ProductImport.parseDate("2027-01-05"))
        assertNull(ProductImport.parseDate("31/02/2027"))
        assertNull(ProductImport.parseDate("soon"))
    }

    @Test
    fun aBlankGst_followsTheCategoryDefault() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price",
                "Shirt,Garment,M,Red,499",
            ),
        )
        assertEquals(GstRate.Apparel, result.ready.single().product!!.gstRate)
        val food = preview(table("name,category,net_quantity,unit,selling_price", "Rusk,Packaged food,200,g,30"))
        assertNull(food.ready.single().product!!.gstRate)
    }

    @Test
    fun aGstWithAPercentSign_isRead() {
        val result = preview(table("name,category,net_quantity,unit,selling_price,gst_rate", "Rusk,Packaged food,200,g,30,5%"))
        assertEquals(GstRate.Fixed(500), result.ready.single().product!!.gstRate)
    }

    // Problems

    @Test
    fun aRowWithoutAName_isInvalid() {
        val result = preview(table("name,category,selling_price", ",Garment,100"))
        val item = result.invalid.single()
        assertEquals(FieldError.REQUIRED, item.issue(ImportColumn.NAME)!!.fieldError)
        assertEquals(2, item.issue(ImportColumn.NAME)!!.rowNumber)
        assertNull(item.product)
    }

    @Test
    fun anUnknownCategory_isReportedOnItsRow() {
        val result = preview(table("name,category,selling_price", "Toy car,Toys,100"))
        assertEquals(ImportParseError.UNKNOWN_CATEGORY, result.invalid.single().issue(ImportColumn.CATEGORY)!!.parseError)
    }

    @Test
    fun aBlankCategory_isAnErrorUnlessADefaultIsChosen() {
        val file = table("name,category,size,colour,selling_price", "Shirt,,M,Red,499")
        val without = preview(file)
        assertEquals(FieldError.REQUIRED, without.invalid.single().issue(ImportColumn.CATEGORY)!!.fieldError)

        val with = preview(file, options = ImportOptions(defaultCategory = ProductCategory.GARMENT))
        assertEquals(ImportStatus.READY, with.items.single().status)
        assertEquals(ProductCategory.GARMENT, with.items.single().product!!.category)
    }

    @Test
    fun aFileWithoutACategoryColumn_usesTheDefault() {
        val file = table("name,size,colour,selling_price", "Shirt,M,Red,499")
        assertEquals(ImportStatus.INVALID, preview(file).items.single().status)
        assertEquals(ImportStatus.READY, preview(file, options = ImportOptions(ProductCategory.GARMENT)).items.single().status)
    }

    @Test
    fun theProductFormsOwnRules_areApplied() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price,mrp,opening_stock,barcode",
                "Shirt,Garment,M,Red,0,,5,",
                "Vest,Garment,M,Red,100,50,5,",
                "Cap,Garment,M,Red,100,,-3,",
                "Belt,Garment,M,Red,100,,5,8901000000010",
                "Tie,Garment,,Red,100,,5,",
            ),
        )
        assertEquals(5, result.invalid.size)
        assertEquals(FieldError.INVALID_PRICE, result.invalid[0].issue(ImportColumn.SELLING_PRICE)!!.fieldError)
        assertEquals(FieldError.MRP_BELOW_PRICE, result.invalid[1].issue(ImportColumn.MRP)!!.fieldError)
        assertEquals(FieldError.INVALID_QUANTITY, result.invalid[2].issue(ImportColumn.OPENING_STOCK)!!.fieldError)
        assertEquals(FieldError.INVALID_BARCODE_CHECK_DIGIT, result.invalid[3].issue(ImportColumn.BARCODE)!!.fieldError)
        assertEquals(FieldError.REQUIRED, result.invalid[4].issue(ImportColumn.SIZE)!!.fieldError)
        assertEquals(listOf(2, 3, 4, 5, 6), result.invalid.map { it.rowNumbers.single() })
    }

    @Test
    fun aProblemIsReportedOnTheRowThatHasIt() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price",
                "Shirt,Garment,M,Red,499",
                "Shirt,Garment,L,Red,abc",
            ),
        )
        val issue = result.invalid.single().issue(ImportColumn.SELLING_PRICE)!!
        assertEquals(3, issue.rowNumber)
        // One bad variant keeps the whole product out, so the product is never saved half-way.
        assertEquals(0, result.ready.size)
    }

    @Test
    fun badUnitDietStockUnitAndDate_areNamedOnce() {
        val result = preview(
            table(
                "name,category,diet,net_quantity,unit,selling_price,expiry_date,stock_unit",
                "Rusk,Packaged food,sometimes,200,bucket,30,someday,",
                "Rice,Ingredient,,,,,,tonnes",
            ),
        )
        val rusk = result.items[0]
        assertEquals(ImportParseError.UNKNOWN_DIET, rusk.issue(ImportColumn.DIET)!!.parseError)
        assertEquals(ImportParseError.UNKNOWN_UNIT, rusk.issue(ImportColumn.UNIT)!!.parseError)
        assertEquals(ImportParseError.INVALID_DATE, rusk.issue(ImportColumn.EXPIRY_DATE)!!.parseError)
        // The unit problem is not reported a second time as "unit required".
        assertEquals(1, rusk.issues.count { it.column == ImportColumn.UNIT })
        assertEquals(ImportParseError.UNKNOWN_STOCK_UNIT, result.items[1].issue(ImportColumn.STOCK_UNIT)!!.parseError)
    }

    @Test
    fun anInvalidGstRate_isReported() {
        val result = preview(table("name,category,net_quantity,unit,selling_price,gst_rate", "Rusk,Packaged food,200,g,30,abc"))
        val issues = result.invalid.single().issues.filter { it.column == ImportColumn.GST_RATE }
        assertEquals(listOf(FieldError.INVALID_GST_RATE), issues.map { it.fieldError })
    }

    @Test
    fun twoRowsOfOneIngredient_cannotBeToldApart() {
        val result = preview(
            table(
                "name,category,cost_price,opening_stock,stock_unit",
                "Flour,Ingredient,45,10,kg",
                "Flour,Ingredient,45,5,kg",
            ),
        )
        val item = result.invalid.single()
        assertEquals(FieldError.DUPLICATE_VARIANT, item.issue(ImportColumn.NAME)!!.fieldError)
        assertEquals(3, item.issue(ImportColumn.NAME)!!.rowNumber)
    }

    // Duplicates

    @Test
    fun aProductThatAlreadyExists_isLeftAlone() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price",
                "cotton t-shirt,Garment,XL,Navy,499",
                "Kurta,Garment,M,Red,799",
            ),
            existing = listOf(TestData.tShirt()),
        )
        assertEquals(listOf(ImportStatus.EXISTS, ImportStatus.READY), result.items.map { it.status })
        assertNull(result.items[0].product)
        assertEquals(1, result.ready.size)
    }

    @Test
    fun aBarcodeUsedByAnExistingProduct_isReported() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price,barcode",
                "Kurta,Garment,M,Red,799,${TestData.EAN[0]}",
            ),
            existing = listOf(TestData.tShirt()),
        )
        assertEquals(FieldError.BARCODE_IN_USE, result.invalid.single().issue(ImportColumn.BARCODE)!!.fieldError)
    }

    @Test
    fun aSkuUsedByAnExistingProduct_isReportedIgnoringCase() {
        val existing = TestData.tShirt().let { it.copy(variants = it.variants.map { v -> v.copy(sku = "TS-1") }) }
        val result = preview(
            table(
                "name,category,size,colour,selling_price,sku",
                "Kurta,Garment,M,Red,799,ts-1",
            ),
            existing = listOf(existing),
        )
        assertEquals(FieldError.SKU_IN_USE, result.invalid.single().issue(ImportColumn.SKU)!!.fieldError)
    }

    @Test
    fun aBarcodeRepeatedInTheFile_marksEveryProductThatUsesIt() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price,barcode",
                "Kurta,Garment,M,Red,799,${TestData.EAN[5]}",
                "Pyjama,Garment,M,Red,599,${TestData.EAN[5]}",
                "Cap,Garment,M,Red,199,${TestData.EAN[6]}",
            ),
        )
        assertEquals(listOf("Kurta", "Pyjama"), result.invalid.map { it.name })
        assertTrue(result.invalid.all { it.issue(ImportColumn.BARCODE)!!.fieldError == FieldError.DUPLICATE_BARCODE })
        assertEquals(listOf("Cap"), result.ready.map { it.name })
    }

    @Test
    fun aSkuRepeatedInTheFile_marksEveryProductThatUsesIt() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price,sku",
                "Kurta,Garment,M,Red,799,K-1",
                "Pyjama,Garment,M,Red,599,k-1",
            ),
        )
        assertEquals(2, result.invalid.size)
        assertTrue(result.invalid.all { it.issue(ImportColumn.SKU)!!.fieldError == FieldError.DUPLICATE_SKU })
    }

    @Test
    fun aBarcodeRepeatedInsideOneProduct_isFoundByTheFormRules() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price,barcode",
                "Kurta,Garment,M,Red,799,${TestData.EAN[5]}",
                "Kurta,Garment,L,Red,799,${TestData.EAN[5]}",
            ),
        )
        assertEquals(FieldError.DUPLICATE_BARCODE, result.invalid.single().issue(ImportColumn.BARCODE)!!.fieldError)
    }

    // Preview totals

    @Test
    fun theSummary_countsReadyInvalidAndExisting() {
        val result = preview(
            table(
                "name,category,size,colour,selling_price",
                "Kurta,Garment,M,Red,799",
                "Pyjama,Garment,M,Red,oops",
                "Cotton T-Shirt,Garment,M,Navy,499",
                ",Garment,M,Red,10",
            ),
            existing = listOf(TestData.tShirt()),
        )
        assertEquals(1, result.ready.size)
        assertEquals(2, result.invalid.size)
        assertEquals(1, result.existing.size)
        assertTrue(result.canImport)
        assertEquals(4, result.rowCount)
        assertEquals(listOf(2, 3, 4, 5), result.items.map { it.rowNumbers.first() })
    }

    @Test
    fun aFileWithOnlyProblems_cannotBeImported() {
        val result = preview(table("name,category,selling_price", "Toy,Toys,10"))
        assertFalse(result.canImport)
        assertNotNull(result.invalid.single())
    }

    @Test
    fun unmappedColumns_areReadAsBlank() {
        val file = table("name,price", "Rusk,30")
        val mapping = ColumnMapping(mapOf(ImportColumn.NAME to 0, ImportColumn.SELLING_PRICE to 1))
        val result = preview(file, mapping = mapping, options = ImportOptions(ProductCategory.PACKAGED_FOOD))
        // Packaged food needs a net quantity, which the file does not have.
        assertEquals(FieldError.REQUIRED, result.invalid.single().issue(ImportColumn.NET_QUANTITY)!!.fieldError)
    }
}
