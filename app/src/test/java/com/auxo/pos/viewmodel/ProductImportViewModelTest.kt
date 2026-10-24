// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.barcode.InternalBarcodeGenerator
import com.auxo.pos.domain.imports.FileReadResult
import com.auxo.pos.domain.imports.ImportColumn
import com.auxo.pos.domain.imports.ImportFileReader
import com.auxo.pos.domain.imports.ProductImportTemplate
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.usecase.ImportProductsUseCase
import com.auxo.pos.domain.usecase.SaveProductUseCase
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProductImportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt()))
    private val generator = InternalBarcodeGenerator(Random(1))

    private var fileResult: FileReadResult = FileReadResult.Text(simpleCsv())
    private val reader = ImportFileReader { fileResult }

    private fun simpleCsv() = """
        name,category,size,colour,selling_price,opening_stock
        Kurta,Garment,M,Red,799,5
        Kurta,Garment,L,Red,799,3
        Pyjama,Garment,M,Red,oops,1
        Cotton T-Shirt,Garment,M,Navy,499,1
    """.trimIndent()

    private fun viewModel(repository: InventoryRepository = inventory): ProductImportViewModel =
        ProductImportViewModel(repository, ImportProductsUseCase(SaveProductUseCase(repository, generator)), reader)

    private fun pick(vm: ProductImportViewModel, text: String = simpleCsv()) {
        fileResult = FileReadResult.Text(text)
        vm.onFilePicked("content://file", "products.csv")
    }

    @Test
    fun beforeAFileIsPicked_thereIsNothingToSee() {
        val state = viewModel().uiState.value
        assertNull(state.table)
        assertNull(state.preview)
        assertFalse(state.canImport)
    }

    @Test
    fun pickingAFile_readsItAndShowsAPreview() = runTest {
        val vm = viewModel()
        pick(vm)
        val state = vm.uiState.value
        assertEquals("products.csv", state.fileName)
        assertNull(state.readError)
        assertFalse(state.isReading)
        val preview = state.preview!!
        assertEquals(1, preview.ready.size)
        assertEquals(1, preview.invalid.size)
        assertEquals(1, preview.existing.size)
        assertTrue(state.canImport)
    }

    @Test
    fun nothingIsSaved_untilTheImportIsConfirmed() = runTest {
        val vm = viewModel()
        pick(vm)
        vm.onMappingChange(ImportColumn.OPENING_STOCK, null)
        vm.onDefaultCategoryChange(ProductCategory.GARMENT)
        vm.onImport()
        assertTrue(vm.uiState.value.confirmOpen)
        assertEquals(0, inventory.saveCalls)
        vm.onDismissConfirm()
        assertFalse(vm.uiState.value.confirmOpen)
        assertEquals(0, inventory.saveCalls)
        assertEquals(1, inventory.products.value.size)
    }

    @Test
    fun confirming_savesOnlyTheProductsWithNoProblems() = runTest {
        val vm = viewModel()
        pick(vm)
        vm.onImport()
        vm.onConfirmImport()

        val outcome = vm.uiState.value.outcome!!
        assertEquals(1, outcome.imported)
        assertEquals(0, outcome.notImported)
        assertEquals(1, inventory.saveCalls)
        val kurta = inventory.products.value.single { it.name == "Kurta" }
        assertEquals(listOf(5, 3), kurta.variants.map { it.stockQuantity })
        // Variants without a barcode get an in-store one, as when a product is added by hand.
        assertTrue(kurta.variants.all { InternalBarcodeGenerator.isInStoreCode(it.barcode) })
        assertEquals(2, inventory.products.value.size)
        assertFalse(vm.uiState.value.confirmOpen)
        assertFalse(vm.uiState.value.isImporting)
    }

    @Test
    fun confirmingTwice_importsOnce() = runTest {
        val vm = viewModel()
        pick(vm)
        vm.onImport()
        vm.onConfirmImport()
        vm.onConfirmImport()
        assertEquals(1, inventory.saveCalls)
    }

    @Test
    fun theImportButton_doesNothingWhenNothingIsReady() = runTest {
        val vm = viewModel()
        pick(vm, "name,category,selling_price\nToy,Scrap,10")
        assertFalse(vm.uiState.value.canImport)
        vm.onImport()
        assertFalse(vm.uiState.value.confirmOpen)
    }

    @Test
    fun aProductAddedWhileReviewing_isNotAddedAgain() = runTest {
        val vm = viewModel()
        pick(vm)
        vm.onImport()
        // Someone adds the same product on another phone before this one confirms.
        inventory.products.value += TestData.tShirt(id = 9).copy(name = "Kurta", variants = emptyList())
        vm.onConfirmImport()

        assertEquals(0, vm.uiState.value.outcome!!.imported)
        assertEquals(0, inventory.saveCalls)
    }

    @Test
    fun changingTheMapping_updatesThePreview() = runTest {
        val vm = viewModel()
        pick(vm)
        // Without a price column nothing is valid.
        vm.onMappingChange(ImportColumn.SELLING_PRICE, null)
        assertTrue(vm.uiState.value.preview!!.ready.isEmpty())
        assertFalse(vm.uiState.value.canImport)
        // The stock column can be read as the price instead.
        vm.onMappingChange(ImportColumn.SELLING_PRICE, 5)
        assertNull(vm.uiState.value.mapping.indexOf(ImportColumn.OPENING_STOCK))
        assertEquals(5, vm.uiState.value.mapping.indexOf(ImportColumn.SELLING_PRICE))
    }

    @Test
    fun withoutTheNameColumn_thereIsNoPreview() = runTest {
        val vm = viewModel()
        pick(vm)
        vm.onMappingChange(ImportColumn.NAME, null)
        assertFalse(vm.uiState.value.mapping.isComplete)
        assertTrue(vm.uiState.value.preview!!.items.isEmpty())
        assertFalse(vm.uiState.value.canImport)
        vm.onMappingChange(ImportColumn.NAME, 0)
        assertTrue(vm.uiState.value.canImport)
    }

    @Test
    fun aDefaultCategory_fillsInBlankCategories() = runTest {
        val vm = viewModel()
        pick(vm, "name,size,colour,selling_price\nKurta,M,Red,799")
        assertFalse(vm.uiState.value.canImport)
        vm.onDefaultCategoryChange(ProductCategory.GARMENT)
        assertTrue(vm.uiState.value.canImport)
        vm.onDefaultCategoryChange(null)
        assertFalse(vm.uiState.value.canImport)
    }

    @Test
    fun theTemplate_canBeImportedAsIs() = runTest {
        val empty = FakeInventoryRepository()
        val vm = viewModel(empty)
        pick(vm, ProductImportTemplate.csv())
        assertEquals(9, vm.uiState.value.preview!!.ready.size)
        vm.onImport()
        vm.onConfirmImport()
        assertEquals(9, vm.uiState.value.outcome!!.imported)
        assertNotNull(empty.products.value.firstOrNull { it.name == "Mango Juice" })
    }

    @Test
    fun problemFiles_areNamed() = runTest {
        val cases = listOf(
            FileReadResult.Unreadable to ImportReadError.UNREADABLE,
            FileReadResult.TooLarge to ImportReadError.TOO_LARGE,
            FileReadResult.Text("") to ImportReadError.EMPTY,
            FileReadResult.Text("name,price\n") to ImportReadError.EMPTY,
            FileReadResult.Text("name,price\n\"open,1") to ImportReadError.MALFORMED,
            FileReadResult.Text("name,price\n" + (1..1001).joinToString("\n") { "a$it,1" }) to ImportReadError.TOO_MANY_ROWS,
        )
        for ((read, expected) in cases) {
            val vm = viewModel()
            fileResult = read
            vm.onFilePicked("content://file")
            assertEquals(read.toString(), expected, vm.uiState.value.readError)
            assertNull(vm.uiState.value.table)
            assertFalse(vm.uiState.value.isReading)
        }
    }

    @Test
    fun whenTheProductListCannotBeRead_theFileIsNotUsed() = runTest {
        inventory.failReads = true
        val vm = viewModel()
        pick(vm)
        assertEquals(ImportReadError.UNREADABLE, vm.uiState.value.readError)
        assertNull(vm.uiState.value.preview)
    }

    @Test
    fun aProductThatFailsToSave_doesNotStopTheOthers() = runTest {
        val failing = object : InventoryRepository by inventory {
            override suspend fun saveProduct(product: Product): SaveProductResult {
                if (product.name == "Kurta") throw IOException("disk full")
                return inventory.saveProduct(product)
            }
        }
        val vm = viewModel(failing)
        pick(vm, "name,category,size,colour,selling_price\nKurta,Garment,M,Red,799\nShirt,Garment,M,Red,499")
        vm.onImport()
        vm.onConfirmImport()
        val outcome = vm.uiState.value.outcome!!
        assertEquals(1, outcome.imported)
        assertEquals(listOf("Kurta"), outcome.failed)
        assertEquals(1, outcome.notImported)
    }

    @Test
    fun startingOver_clearsTheFile() = runTest {
        val vm = viewModel()
        pick(vm)
        vm.onStartOver()
        assertEquals(ProductImportUiState(), vm.uiState.value)
    }
}
