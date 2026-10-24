// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.imports

import com.auxo.pos.domain.barcode.InternalBarcodeGenerator
import com.auxo.pos.domain.usecase.ImportProductsUseCase
import com.auxo.pos.domain.usecase.SaveProductUseCase
import com.auxo.pos.testutil.FakeInventoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class ImportProductsUseCaseTest {

    private val repository = FakeInventoryRepository()
    private val useCase = ImportProductsUseCase(SaveProductUseCase(repository, InternalBarcodeGenerator(Random(3))))

    private fun items(vararg lines: String): List<ImportItem> {
        val table = (CsvParser.parse(lines.joinToString("\n")) as CsvParseResult.Parsed).table
        return ProductImport.preview(table, ColumnMapping.auto(table.header), ImportOptions(), emptyList()).items
    }

    @Test
    fun readyProducts_areSaved_andOthersAreSkipped() = runTest {
        val outcome = useCase(
            items(
                "name,category,size,colour,selling_price",
                "Kurta,Garment,M,Red,799",
                "Pyjama,Garment,M,Red,oops",
            ),
        )
        assertEquals(1, outcome.imported)
        assertEquals(0, outcome.notImported)
        assertEquals(listOf("Kurta"), repository.products.value.map { it.name })
    }

    @Test
    fun aSkuTakenInTheMeantime_isReportedAsAConflict_andTheRestStillGoIn() = runTest {
        val toImport = items(
            "name,category,size,colour,selling_price,sku",
            "Kurta,Garment,M,Red,799,K-1",
            "Shirt,Garment,M,Red,499,S-1",
        )
        // Another product takes K-1 after the file was checked.
        useCase(items("name,category,size,colour,selling_price,sku", "Other,Garment,M,Red,10,K-1"))

        val outcome = useCase(toImport)
        assertEquals(1, outcome.imported)
        assertEquals(listOf("Kurta"), outcome.conflicts)
        assertEquals(listOf("Other", "Shirt"), repository.products.value.map { it.name }.sorted())
    }

    @Test
    fun nothingToImport_savesNothing() = runTest {
        val outcome = useCase(emptyList())
        assertEquals(0, outcome.imported)
        assertEquals(0, repository.saveCalls)
    }
}
