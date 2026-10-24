// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.barcode.InternalBarcodeGenerator
import com.auxo.pos.domain.usecase.DeleteProductResult
import com.auxo.pos.domain.usecase.DeleteProductUseCase
import com.auxo.pos.domain.usecase.SaveProductUseCase
import com.auxo.pos.domain.validation.ProductForm
import com.auxo.pos.domain.validation.ProductFormValidator
import com.auxo.pos.domain.validation.ProductValidation
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeProductPhotoStore
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import com.auxo.pos.testutil.record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProductPhotoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInventoryRepository(listOf(TestData.tShirt().copy(photoFile = OLD)))
    private val generator = InternalBarcodeGenerator(Random(1))
    private val photos = FakeProductPhotoStore().also { it.stored += OLD }
    private val picture = Uri.parse("content://pictures/1")

    private val store = ViewModelStore()

    private fun editor(productId: Long = 0): ProductEditorViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProductEditorViewModel(productId, null, repository, SaveProductUseCase(repository, generator), generator, photos = photos) as T
        }
        return ViewModelProvider(store, factory)[ProductEditorViewModel::class.java]
    }

    /** Leaves the editor: the ViewModel is cleared, as when its screen is closed. */
    private fun leave() = store.clear()

    private fun fillNewShirt(vm: ProductEditorViewModel) {
        vm.onNameChange("Linen Shirt")
        vm.toggleSize("M")
        vm.addColour("Beige")
        vm.onDefaultsChange(com.auxo.pos.domain.validation.VariantDefaults(sellingPrice = "899", openingStock = "6"))
        vm.applyDefaultsToAll()
    }

    // The form and the saved product

    @Test
    fun theFormCarriesThePhotoName_toTheSavedProductAndBack() {
        val product = TestData.tShirt().copy(photoFile = OLD)
        val form = ProductForm.fromProduct(product)
        assertEquals(OLD, form.photoFile)
        val valid = ProductFormValidator.validate(form) as ProductValidation.Valid
        assertEquals(OLD, valid.product.photoFile)
        assertNull((ProductFormValidator.validate(form.copy(photoFile = null)) as ProductValidation.Valid).product.photoFile)
    }

    // Choosing a photo

    @Test
    fun aChosenPhoto_becomesThePhotoOfTheForm_andIsSavedWithTheProduct() = runTest {
        val vm = editor()
        fillNewShirt(vm)
        vm.onPhotoPicked(picture)
        val name = vm.uiState.value.form.photoFile!!
        assertTrue(name in photos.stored)
        assertFalse(vm.uiState.value.photoBusy)

        vm.save()
        val saved = vm.events.first() as ProductEditorEvent.Saved
        assertEquals(name, repository.products.value.first { it.id == saved.productId }.photoFile)
        assertTrue(name in photos.stored)
    }

    @Test
    fun aPictureThatCannotBeUsed_isReported_andChangesNothing() = runTest {
        val vm = editor()
        val events = record(vm.events)
        photos.failNext = true
        vm.onPhotoPicked(picture)
        assertNull(vm.uiState.value.form.photoFile)
        assertEquals(listOf<ProductEditorEvent>(ProductEditorEvent.PhotoFailed), events)
        assertFalse(vm.uiState.value.photoBusy)
    }

    @Test
    fun noCameraApp_isReported() = runTest {
        val vm = editor()
        val events = record(vm.events)
        vm.onPhotoUnavailable()
        assertEquals(listOf<ProductEditorEvent>(ProductEditorEvent.PhotoFailed), events)
    }

    @Test
    fun choosingAnotherPhotoBeforeSaving_deletesTheFirstOne() = runTest {
        val vm = editor()
        vm.onPhotoPicked(picture)
        val first = vm.uiState.value.form.photoFile!!
        vm.onPhotoPicked(picture)
        val second = vm.uiState.value.form.photoFile!!
        assertTrue(first != second)
        assertFalse(first in photos.stored)
        assertTrue(second in photos.stored)
    }

    @Test
    fun leavingTheEditorWithoutSaving_deletesAPhotoThatWasJustChosen() = runTest {
        val vm = editor()
        vm.onPhotoPicked(picture)
        val name = vm.uiState.value.form.photoFile!!
        leave()
        assertFalse(name in photos.stored)
    }

    @Test
    fun leavingAfterSaving_keepsThePhoto() = runTest {
        val vm = editor()
        fillNewShirt(vm)
        vm.onPhotoPicked(picture)
        val name = vm.uiState.value.form.photoFile!!
        vm.save()
        vm.events.first()
        leave()
        assertTrue(name in photos.stored)
    }

    // Replacing and removing a saved photo

    @Test
    fun replacingASavedPhoto_deletesTheOldFile_onlyOnceTheProductIsSaved() = runTest {
        val vm = editor(productId = 1)
        vm.uiState.first { !it.isLoading }
        vm.onPhotoPicked(picture)
        val replacement = vm.uiState.value.form.photoFile!!
        assertTrue("old photo stays until the save", OLD in photos.stored)

        vm.save()
        vm.events.first()
        assertFalse(OLD in photos.stored)
        assertTrue(replacement in photos.stored)
        assertEquals(replacement, repository.products.value.first { it.id == 1L }.photoFile)
    }

    @Test
    fun removingASavedPhoto_changesNothingUntilSaved() = runTest {
        val vm = editor(productId = 1)
        vm.uiState.first { !it.isLoading }
        vm.onPhotoRemoved()
        assertNull(vm.uiState.value.form.photoFile)
        assertTrue(OLD in photos.stored)
        assertEquals(OLD, repository.products.value.first { it.id == 1L }.photoFile)

        leave()
        assertTrue("leaving without saving keeps the photo", OLD in photos.stored)
    }

    @Test
    fun removingASavedPhoto_thenSaving_deletesTheFile() = runTest {
        val vm = editor(productId = 1)
        vm.uiState.first { !it.isLoading }
        vm.onPhotoRemoved()
        vm.save()
        vm.events.first()
        assertFalse(OLD in photos.stored)
        assertNull(repository.products.value.first { it.id == 1L }.photoFile)
    }

    @Test
    fun removingAPhotoJustChosen_deletesItAtOnce() = runTest {
        val vm = editor()
        vm.onPhotoPicked(picture)
        val name = vm.uiState.value.form.photoFile!!
        vm.onPhotoRemoved()
        assertNull(vm.uiState.value.form.photoFile)
        assertFalse(name in photos.stored)
    }

    // Deleting a product

    @Test
    fun deletingAProduct_removesItsPhotoFile() = runTest {
        val empty = TestData.tShirt().let { it.copy(photoFile = OLD, variants = it.variants.map { v -> v.copy(stockQuantity = 0) }) }
        val repo = FakeInventoryRepository(listOf(empty))
        val delete = DeleteProductUseCase(repo) { product -> product.photoFile?.let(photos::delete) }

        assertEquals(DeleteProductResult.Deleted, delete(empty.id))
        assertFalse(OLD in photos.stored)
    }

    @Test
    fun aProductThatCannotBeDeleted_keepsItsPhoto() = runTest {
        val delete = DeleteProductUseCase(repository) { product -> product.photoFile?.let(photos::delete) }
        assertTrue(delete(1) is DeleteProductResult.StockOnHand)
        assertTrue(OLD in photos.stored)
    }

    private companion object {
        const val OLD = "0123456789abcdef0123456789abcdef.jpg"
    }
}
