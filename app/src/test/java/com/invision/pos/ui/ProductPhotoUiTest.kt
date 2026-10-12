package com.invision.pos.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.photos.ProductPhotoStore
import com.invision.pos.domain.model.DietType
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.validation.ProductForm
import com.invision.pos.domain.validation.VariantDefaults
import com.invision.pos.domain.validation.VariantForm
import com.invision.pos.testutil.FakeProductPhotoStore
import com.invision.pos.testutil.TestData
import com.invision.pos.ui.screens.inventory.ProductEditorActions
import com.invision.pos.ui.screens.inventory.ProductEditorContent
import com.invision.pos.ui.screens.inventory.ProductRow
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.ProductEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class ProductPhotoUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val photoName = "0123456789abcdef0123456789abcdef.jpg"

    private class PhotoActions(
        val taken: MutableList<Unit> = mutableListOf(),
        val chosen: MutableList<Unit> = mutableListOf(),
        val removed: MutableList<Unit> = mutableListOf(),
    ) : ProductEditorActions {
        override fun onCategoryChange(category: ProductCategory) = Unit
        override fun onNameChange(value: String) = Unit
        override fun onBrandChange(value: String) = Unit
        override fun onDescriptionChange(value: String) = Unit
        override fun onMaterialChange(value: String) = Unit
        override fun onDietTypeChange(dietType: DietType?) = Unit
        override fun onToggleSize(size: String) = Unit
        override fun onAddColour(colour: String) = Unit
        override fun onRemoveColour(colour: String) = Unit
        override fun onDefaultsChange(defaults: VariantDefaults) = Unit
        override fun onApplyDefaults() = Unit
        override fun onAddPackSize() = Unit
        override fun onRemoveVariant(key: String) = Unit
        override fun onUpdateVariant(key: String, transform: (VariantForm) -> VariantForm) = Unit
        override fun onGenerateBarcode(key: String) = Unit
        override fun onScanBarcode(key: String) = Unit
        override fun onPickExpiry(key: String) = Unit
        override fun onSave() = Unit
        override fun onTakePhoto() { taken += Unit }
        override fun onChoosePhoto() { chosen += Unit }
        override fun onRemovePhoto() { removed += Unit }
    }

    /** A store that always has a small picture, so a photo can be seen in a screen. */
    private val pictureStore: ProductPhotoStore = object : ProductPhotoStore by FakeProductPhotoStore() {
        override suspend fun thumbnail(fileName: String, maxSidePx: Int): Bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    }

    private fun showEditor(form: ProductForm, busy: Boolean, actions: ProductEditorActions) {
        composeRule.setContent {
            InvisionTheme { ProductEditorContent(ProductEditorUiState(form = form, photoBusy = busy), actions) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theEditor_offersCameraAndGallery_andNoRemoveWithoutAPhoto() {
        val actions = PhotoActions()
        showEditor(ProductForm(), busy = false, actions = actions)
        composeRule.onNodeWithTag("editor_photo").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_photo_take").performClick()
        composeRule.onNodeWithTag("editor_photo_choose").performClick()
        composeRule.onNodeWithTag("editor_photo_remove").assertDoesNotExist()
        assertEquals(1, actions.taken.size)
        assertEquals(1, actions.chosen.size)
    }

    @Test
    fun withAPhoto_theEditorOffersToRemoveIt() {
        val actions = PhotoActions()
        showEditor(ProductForm(photoFile = photoName), busy = false, actions = actions)
        composeRule.onNodeWithTag("editor_photo_remove").performClick()
        assertEquals(1, actions.removed.size)
    }

    @Test
    fun whileAPhotoIsBeingPrepared_theButtonsWait() {
        showEditor(ProductForm(), busy = true, actions = PhotoActions())
        composeRule.onNodeWithTag("editor_photo_busy").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_photo_take").assertIsNotEnabled()
        composeRule.onNodeWithTag("editor_photo_choose").assertIsNotEnabled()
    }

    @Test
    fun theButtons_areEnabledWhenIdle() {
        showEditor(ProductForm(), busy = false, actions = PhotoActions())
        composeRule.onNodeWithTag("editor_photo_take").assertIsEnabled()
        composeRule.onNodeWithTag("editor_photo_busy").assertDoesNotExist()
    }

    @Test
    fun aProductInTheList_showsItsPhoto_whenThereIsOne() {
        val product = TestData.tShirt().copy(photoFile = photoName)
        composeRule.setContent {
            InvisionTheme {
                CompositionLocalProvider(LocalProductPhotos provides pictureStore) {
                    ProductRow(product, TestData.today, onClick = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Cotton T-Shirt").assertIsDisplayed()
    }

    @Test
    fun aProductWithoutAPhoto_showsItsCategoryIcon() {
        composeRule.setContent {
            InvisionTheme {
                CompositionLocalProvider(LocalProductPhotos provides pictureStore) {
                    ProductRow(TestData.tShirt(), TestData.today, onClick = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Garment").assertIsDisplayed()
    }

    @Test
    fun aPhotoThatCannotBeLoaded_fallsBackToTheCategoryIcon() {
        val product = TestData.tShirt().copy(photoFile = photoName)
        composeRule.setContent {
            InvisionTheme {
                // No photo store at all, as in previews: the icon is shown.
                ProductRow(product, TestData.today, onClick = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Garment").assertIsDisplayed()
    }
}
