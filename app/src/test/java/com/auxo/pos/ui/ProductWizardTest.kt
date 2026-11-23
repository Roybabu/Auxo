// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.validation.FieldError
import com.auxo.pos.domain.validation.FormField
import com.auxo.pos.domain.validation.ProductForm
import com.auxo.pos.domain.validation.ProductFormErrors
import com.auxo.pos.domain.validation.VariantDefaults
import com.auxo.pos.domain.validation.VariantField
import com.auxo.pos.domain.validation.VariantForm
import com.auxo.pos.ui.screens.inventory.ProductEditorActions
import com.auxo.pos.ui.screens.inventory.ProductWizard
import com.auxo.pos.ui.screens.inventory.WizardStep
import com.auxo.pos.ui.screens.inventory.WizardValidation
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.ProductEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The three-step Add product flow on a phone, and its side-by-side layout on a tablet. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class ProductWizardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Recorder : ProductEditorActions {
        var saves = 0
        var reveals = 0
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
        override fun onSave() { saves++ }
        override fun onRevealErrors() { reveals++ }
    }

    private val shirt = ProductForm(
        name = "Linen Shirt",
        brand = "Nike",
        category = ProductCategory.GARMENT,
        variants = listOf(
            VariantForm(key = "a", size = "M", colour = "Beige", sellingPrice = "899", openingStock = "5"),
            VariantForm(key = "b", size = "L", colour = "Navy", sellingPrice = "899", openingStock = "3"),
        ),
    )

    private fun show(state: ProductEditorUiState, actions: ProductEditorActions) {
        composeRule.setContent { AuxoTheme { ProductWizard(state, actions) } }
        composeRule.waitForIdle()
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun startsOnDetails_withProgress_andNoBackOrSave() {
        show(ProductEditorUiState(form = shirt), Recorder())
        composeRule.onNodeWithTag("wizard_progress").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_name").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_back").assertDoesNotExist()
        composeRule.onNodeWithTag("editor_save").assertDoesNotExist()
    }

    @Test
    fun next_withAProblemOnTheStep_staysPut_andShowsTheErrors() {
        val actions = Recorder()
        val errors = ProductFormErrors(fields = mapOf(FormField.NAME to FieldError.REQUIRED))
        show(ProductEditorUiState(form = shirt.copy(name = ""), errors = errors), actions)
        tap("wizard_next")
        assertEquals(1, actions.reveals)
        composeRule.onNodeWithTag("editor_name").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_back").assertDoesNotExist()
    }

    @Test
    fun walkingForwardAndBack_keepsWhatWasEntered() {
        show(ProductEditorUiState(form = shirt), Recorder())
        tap("wizard_next")
        composeRule.onNodeWithTag("editor_name").assertDoesNotExist()
        composeRule.onNodeWithTag("wizard_back").assertIsDisplayed()
        tap("wizard_back")
        composeRule.onNodeWithTag("editor_name").assertIsDisplayed()
        // The form lives in the screen's state, not in a step, so the name is still there.
        composeRule.onNodeWithText("Linen Shirt").assertIsDisplayed()
    }

    @Test
    fun theReviewStep_showsWhatWillBeSaved_andSavesFromThere() {
        val actions = Recorder()
        show(ProductEditorUiState(form = shirt), actions)
        tap("wizard_next")
        tap("wizard_next")
        composeRule.onNodeWithTag("wizard_review_name").assertTextEquals("Linen Shirt")
        composeRule.onNodeWithText("M / Beige").assertIsDisplayed()
        composeRule.onNodeWithText("L / Navy").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_next").assertDoesNotExist()
        tap("editor_save")
        assertEquals(1, actions.saves)
    }

    @Test
    fun theReviewStep_editButtonsGoBackToTheRightStep() {
        show(ProductEditorUiState(form = shirt), Recorder())
        tap("wizard_next")
        tap("wizard_next")
        tap("wizard_edit_details")
        composeRule.onNodeWithTag("editor_name").assertIsDisplayed()
        tap("wizard_next")
        tap("wizard_next")
        tap("wizard_edit_pricing")
        composeRule.onNodeWithTag("wizard_review_name").assertDoesNotExist()
        composeRule.onNodeWithTag("wizard_next").assertIsDisplayed()
    }

    @Test
    fun theReviewStep_namesWhatIsWrong_whenASaveFindsAProblem() {
        var state by mutableStateOf(ProductEditorUiState(form = shirt))
        composeRule.setContent { AuxoTheme { ProductWizard(state, Recorder()) } }
        composeRule.waitForIdle()
        tap("wizard_next")
        tap("wizard_next")
        composeRule.onNodeWithTag("wizard_fix").assertDoesNotExist()

        // The save found a barcode another product already uses.
        state = state.copy(errors = ProductFormErrors(variants = mapOf("a" to mapOf(VariantField.BARCODE to FieldError.INVALID_PRICE))))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wizard_fix").assertIsDisplayed()
        tap("wizard_fix")
        composeRule.onNodeWithTag("wizard_review_name").assertDoesNotExist()
        composeRule.onNodeWithTag("wizard_next").assertIsDisplayed()
    }

    @Test
    fun theSaveButton_isDisabledWhileSaving() {
        show(ProductEditorUiState(form = shirt, isSaving = true), Recorder())
        tap("wizard_next")
        tap("wizard_next")
        composeRule.onNodeWithTag("editor_save").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_save").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w900dp-h3200dp-xhdpi")
    fun onATablet_detailsAndVariantsShowTogether_withOneSaveButton() {
        show(ProductEditorUiState(form = shirt), Recorder())
        composeRule.onNodeWithTag("product_wizard_wide").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_name").assertIsDisplayed()
        composeRule.onNodeWithText("M / Beige").assertDoesNotExist()
        composeRule.onNodeWithTag("editor_save").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_next").assertDoesNotExist()
        composeRule.onNodeWithTag("wizard_progress").assertDoesNotExist()
    }

    // The step each error belongs to.

    @Test
    fun nameAndBrandErrors_belongToDetails_priceAndTaxErrorsToPricing() {
        val details = ProductFormErrors(fields = mapOf(FormField.BRAND to FieldError.TOO_LONG))
        assertTrue(WizardValidation.stepHasErrors(WizardStep.DETAILS, details))
        assertFalse(WizardValidation.stepHasErrors(WizardStep.PRICING, details))

        val pricing = ProductFormErrors(
            fields = mapOf(FormField.HSN to FieldError.INVALID_QUANTITY),
            variants = mapOf("a" to mapOf(VariantField.SELLING_PRICE to FieldError.INVALID_PRICE)),
        )
        assertFalse(WizardValidation.stepHasErrors(WizardStep.DETAILS, pricing))
        assertTrue(WizardValidation.stepHasErrors(WizardStep.PRICING, pricing))
    }

    @Test
    fun aVariantOnlyError_isAPricingStepProblem() {
        val errors = ProductFormErrors(variants = mapOf("a" to mapOf(VariantField.OPENING_STOCK to FieldError.INVALID_QUANTITY)))
        assertEquals(WizardStep.PRICING, WizardValidation.firstStepWithErrors(errors))
    }

    @Test
    fun noErrors_meansNothingToFix() {
        assertNull(WizardValidation.firstStepWithErrors(ProductFormErrors()))
        assertFalse(WizardValidation.stepHasErrors(WizardStep.REVIEW, ProductFormErrors()))
    }

    @Test
    fun detailsComeBeforePricingWhenBothHaveErrors() {
        val both = ProductFormErrors(
            fields = mapOf(FormField.NAME to FieldError.REQUIRED, FormField.GST_RATE to FieldError.INVALID_QUANTITY),
        )
        assertEquals(WizardStep.DETAILS, WizardValidation.firstStepWithErrors(both))
    }
}
