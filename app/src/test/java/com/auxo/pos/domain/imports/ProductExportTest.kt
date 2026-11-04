// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.imports

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.StockBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProductExportTest {

    private fun parse(csv: String): CsvTable = (CsvParser.parse(csv) as CsvParseResult.Parsed).table

    /** The products the template's example rows make: one of every category that can be imported. */
    private fun templateProducts(): List<Product> {
        val table = parse(ProductImportTemplate.csv())
        val preview = ProductImport.preview(table, ColumnMapping.auto(table.header), ImportOptions(), emptyList())
        assertTrue(preview.invalid.isEmpty())
        return preview.ready.mapNotNull { it.product }
    }

    @Test
    fun theFirstColumns_areTheImportColumns_inOrder() {
        val header = ProductExport.header.split(",")
        assertEquals(ImportColumn.entries.map { it.key }, header.take(ImportColumn.entries.size))
    }

    @Test
    fun anExportedInventory_importsAgainWithoutProblems() {
        val products = templateProducts()
        val table = parse(ProductExport.csv(products))
        val again = ProductImport.preview(table, ColumnMapping.auto(table.header), ImportOptions(), emptyList())
        assertEquals(products.size, again.ready.size)
        assertTrue(again.invalid.isEmpty())
        assertEquals(products.sumOf { it.variants.size }, table.rows.size)
    }

    @Test
    fun importingTheExportOnTheSamePhone_findsEveryProductAlreadyThere() {
        val products = templateProducts()
        val table = parse(ProductExport.csv(products))
        val again = ProductImport.preview(table, ColumnMapping.auto(table.header), ImportOptions(), products)
        assertEquals(products.size, again.existing.size)
        assertTrue(again.ready.isEmpty())
    }

    @Test
    fun oneRowPerVariant_withTheProductsDetailsRepeated() {
        val shirt = templateProducts().first { it.name == "Cotton T-Shirt" }
        val table = parse(ProductExport.csv(listOf(shirt)))
        assertEquals(2, table.rows.size)
        val sizes = table.rows.map { it.cells[table.header.indexOf("size")] }
        assertEquals(listOf("M", "L"), sizes)
        assertEquals(listOf("Cotton T-Shirt"), table.rows.map { it.cells[0] }.distinct())
        assertEquals("499.00", table.rows[0].cells[table.header.indexOf("selling_price")])
        assertEquals("Apparel", table.rows[0].cells[table.header.indexOf("gst_rate")])
    }

    @Test
    fun allBatches_arePutInOneCell() {
        val variant = ProductVariant(
            barcode = "b1", sellingPrice = Money(2000), costPrice = Money(1500), stockQuantity = 25,
            batchNumber = "B-1", expiryDate = LocalDate.of(2027, 3, 31),
            batches = listOf(
                StockBatch(variantId = 1, batchNumber = "B-1", expiryDate = LocalDate.of(2027, 3, 31), quantity = 20),
                StockBatch(variantId = 1, batchNumber = null, quantity = 5),
            ),
        )
        val product = Product(name = "Biscuits", category = ProductCategory.PACKAGED_FOOD, variants = listOf(variant))
        val table = parse(ProductExport.csv(listOf(product)))
        val row = table.rows.single().cells
        assertEquals("B-1 x20 exp 2027-03-31; no batch no. x5", row[table.header.indexOf("batches")])
        assertEquals("375.00", row[table.header.indexOf("stock_value_at_cost")])
        assertEquals("2027-03-31", row[table.header.indexOf("expiry_date")])
    }

    @Test
    fun textThatLooksLikeAFormula_isMadeSafe_andCommasAndQuotesSurvive() {
        val variant = ProductVariant(barcode = "b2", sellingPrice = Money(100), stockQuantity = 1)
        val product = Product(
            name = "=SUM(A1)", brand = "Tom, \"Dick\" & Co", category = ProductCategory.GENERAL, variants = listOf(variant),
        )
        val table = parse(ProductExport.csv(listOf(product)))
        val row = table.rows.single().cells
        assertEquals("'=SUM(A1)", row[0])
        assertEquals("Tom, \"Dick\" & Co", row[table.header.indexOf("brand")])
    }

    @Test
    fun aProductWithoutVariants_stillGetsARow() {
        val table = parse(ProductExport.csv(listOf(Product(name = "Empty", category = ProductCategory.GENERAL))))
        assertEquals(1, table.rows.size)
        assertEquals("Empty", table.rows[0].cells[0])
    }

    @Test
    fun productsAreSortedByName_andTheFileNameCarriesTheDate() {
        val a = Product(name = "apple", category = ProductCategory.GENERAL)
        val b = Product(name = "Banana", category = ProductCategory.GENERAL)
        val table = parse(ProductExport.csv(listOf(b, a)))
        assertEquals(listOf("apple", "Banana"), table.rows.map { it.cells[0] })
        assertEquals("auxo-inventory-2026-11-04.csv", ProductExport.fileName(LocalDate.of(2026, 11, 4)))
    }
}
