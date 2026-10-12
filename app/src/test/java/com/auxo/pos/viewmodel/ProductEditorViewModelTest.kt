package com.auxo.pos.viewmodel

import com.auxo.pos.domain.barcode.Gs1
import com.auxo.pos.domain.barcode.InternalBarcodeGenerator
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.usecase.SaveProductUseCase
import com.auxo.pos.domain.validation.FieldError
import com.auxo.pos.domain.validation.FormField
import com.auxo.pos.domain.validation.VariantDefaults
import com.auxo.pos.domain.validation.VariantField
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ProductEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInventoryRepository(listOf(TestData.tShirt()))
    private val generator = InternalBarcodeGenerator(Random(1))

    private fun createViewModel(productId: Long = 0, barcode: String? = null) =
        ProductEditorViewModel(productId, barcode, repository, SaveProductUseCase(repository, generator), generator)

    @Test
    fun newProduct_startsAsGarmentWithNoVariants() {
        val state = createViewModel().uiState.value
        assertTrue(state.form.isNew)
        assertEquals(ProductCategory.GARMENT, state.form.category)
        assertTrue(state.form.variants.isEmpty())
        assertFalse(state.showErrors)
    }

    @Test
    fun scannedBarcode_startsAsPackagedFoodWithThatBarcode() {
        val state = createViewModel(barcode = TestData.EAN[5]).uiState.value
        assertEquals(ProductCategory.PACKAGED_FOOD, state.form.category)
        assertEquals(TestData.EAN[5], state.form.variants.single().barcode)
        assertEquals(QuantityUnit.GRAM, state.form.variants.single().unit)
    }

    @Test
    fun sizesAndColours_buildTheVariantMatrix() {
        val viewModel = createViewModel()
        viewModel.toggleSize("L")
        viewModel.toggleSize("S")
        viewModel.addColour("Black")
        viewModel.addColour("black") // duplicate, ignored
        viewModel.addColour("White")

        val form = viewModel.uiState.value.form
        assertEquals(listOf("S", "L"), form.sizes)
        assertEquals(listOf("Black", "White"), form.colours)
        assertEquals(4, form.variants.size)

        viewModel.toggleSize("S")
        assertEquals(listOf("L|Black", "L|White"), viewModel.uiState.value.form.variants.map { "${it.size}|${it.colour}" })
    }

    @Test
    fun saveWithErrors_showsThemAndDoesNotSave() = runTest {
        val viewModel = createViewModel()
        viewModel.save()

        val state = viewModel.uiState.value
        assertTrue(state.showErrors)
        assertEquals(FieldError.REQUIRED, state.visibleErrors.fields[FormField.NAME])
        assertEquals(FieldError.REQUIRED, state.visibleErrors.fields[FormField.SIZES])
        assertTrue(viewModel.events.first() is ProductEditorEvent.InvalidForm)
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun validGarment_savesWithGeneratedBarcodesAndOpeningStock() = runTest {
        val viewModel = createViewModel()
        viewModel.onNameChange("Linen Shirt")
        viewModel.toggleSize("M")
        viewModel.addColour("Beige")
        viewModel.onDefaultsChange(VariantDefaults(sellingPrice = "899", openingStock = "6", lowStockThreshold = "2"))
        viewModel.applyDefaultsToAll()
        viewModel.save()

        val saved = viewModel.events.first() as ProductEditorEvent.Saved
        val product = repository.products.value.first { it.id == saved.productId }
        assertEquals("Linen Shirt", product.name)
        val variant = product.variants.single()
        assertEquals(6, variant.stockQuantity)
        assertTrue(InternalBarcodeGenerator.isInStoreCode(variant.barcode))
        assertEquals(StockChangeReason.INITIAL, repository.movements.value.single().reason)
    }

    @Test
    fun beverageWithTwoPackSizes_saves() = runTest {
        val viewModel = createViewModel()
        viewModel.onCategoryChange(ProductCategory.BEVERAGE)
        viewModel.onNameChange("Lemon Soda")
        val first = viewModel.uiState.value.form.variants.single().key
        viewModel.updateVariant(first) { it.copy(netQuantity = "500", sellingPrice = "40", expiryDate = TestData.today.plusDays(120)) }
        viewModel.addPackSize()
        val second = viewModel.uiState.value.form.variants.last().key
        viewModel.updateVariant(second) { it.copy(netQuantity = "1", unit = QuantityUnit.LITRE, sellingPrice = "70") }
        viewModel.save()

        val saved = viewModel.events.first() as ProductEditorEvent.Saved
        val product = repository.products.value.first { it.id == saved.productId }
        assertEquals(listOf("500 ml", "1 L"), product.variants.map { it.attributesLabel })
    }

    @Test
    fun barcodeUsedByAnotherProduct_isFlaggedOnTheVariant() = runTest {
        val viewModel = createViewModel(barcode = TestData.EAN[0]) // EAN[0] belongs to the T-shirt
        viewModel.onNameChange("Cookies")
        val key = viewModel.uiState.value.form.variants.single().key
        viewModel.updateVariant(key) { it.copy(netQuantity = "100", sellingPrice = "25") }
        viewModel.save()

        assertTrue(viewModel.events.first() is ProductEditorEvent.InvalidForm)
        assertEquals(FieldError.BARCODE_IN_USE, viewModel.uiState.value.visibleErrors.forVariant(key)[VariantField.BARCODE])
    }

    @Test
    fun editingLoadsTheProduct_andKeepsStock() = runTest {
        val viewModel = createViewModel(productId = 1)
        val form = viewModel.uiState.value.form
        assertFalse(form.isNew)
        assertEquals("Cotton T-Shirt", form.name)
        assertEquals(listOf(8, 1), form.variants.map { it.currentStock })

        viewModel.onNameChange("Cotton Crew T-Shirt")
        viewModel.save()
        assertEquals(ProductEditorEvent.Saved(1), viewModel.events.first())
        val saved = repository.products.value.single { it.id == 1L }
        assertEquals("Cotton Crew T-Shirt", saved.name)
        assertEquals(listOf(8, 1), saved.variants.map { it.stockQuantity })
    }

    @Test
    fun generateBarcode_fillsAValidCode() {
        val viewModel = createViewModel()
        viewModel.toggleSize("M")
        viewModel.addColour("Red")
        val key = viewModel.uiState.value.form.variants.single().key
        viewModel.generateBarcode(key)
        assertTrue(Gs1.isValid(viewModel.uiState.value.form.variants.single().barcode))
    }

    @Test
    fun missingProduct_reportsLoadFailure() {
        val state = createViewModel(productId = 99).uiState.value
        assertTrue(state.loadFailed)
    }
}
