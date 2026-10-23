package com.auxo.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.imports.ColumnMapping
import com.auxo.pos.domain.imports.CsvParseResult
import com.auxo.pos.domain.imports.CsvParser
import com.auxo.pos.domain.imports.ImportColumn
import com.auxo.pos.domain.imports.ImportOptions
import com.auxo.pos.domain.imports.ProductImport
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.usecase.ImportOutcome
import com.auxo.pos.testutil.TestData
import com.auxo.pos.ui.screens.stock.ProductImportActions
import com.auxo.pos.ui.screens.stock.ProductImportContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.ImportReadError
import com.auxo.pos.viewmodel.ProductImportUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class ProductImportUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val csv = listOf(
        "name,category,size,colour,selling_price",
        "Kurta,Garment,M,Red,799",
        "Pyjama,Garment,M,Red,oops",
        "Cotton T-Shirt,Garment,M,Navy,499",
    ).joinToString("\n")

    private fun loaded(
        mapping: (ColumnMapping) -> ColumnMapping = { it },
        build: (ProductImportUiState) -> ProductImportUiState = { it },
    ): ProductImportUiState {
        val table = (CsvParser.parse(csv) as CsvParseResult.Parsed).table
        val map = mapping(ColumnMapping.auto(table.header))
        val preview = if (map.isComplete) {
            ProductImport.preview(table, map, ImportOptions(), listOf(TestData.tShirt()))
        } else {
            com.auxo.pos.domain.imports.ImportPreview(emptyList(), table.rows.size)
        }
        return build(ProductImportUiState(fileName = "products.csv", table = table, mapping = map, preview = preview))
    }

    private fun show(state: ProductImportUiState, actions: ProductImportActions = object : ProductImportActions {}) {
        composeRule.setContent { AuxoTheme { ProductImportContent(state, actions) } }
        composeRule.waitForIdle()
    }

    @Test
    fun beforeAFileIsChosen_theScreenExplainsTheSteps_andOffersTheTemplate() {
        var template = 0
        var choose = 0
        show(
            ProductImportUiState(),
            object : ProductImportActions {
                override fun onDownloadTemplate() { template++ }
                override fun onChooseFile() { choose++ }
            },
        )
        composeRule.onNodeWithTag("import_pick").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing is saved until you confirm.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("import_template").performClick()
        composeRule.onNodeWithTag("import_choose").performClick()
        assertEquals(1, template)
        assertEquals(1, choose)
    }

    @Test
    fun beforeAFileIsChosen_theGuideShowsAnExampleForEveryImportableCategory() {
        show(ProductImportUiState())
        composeRule.onNodeWithTag("import_guide").assertExists()
        com.auxo.pos.domain.imports.ProductImportGuide.examples.forEach { example ->
            composeRule.onNodeWithTag("import_guide_${example.category.name}").assertExists()
        }
        composeRule.onNodeWithText("name: Smartphone X1", substring = true).assertExists()
    }

    @Test
    fun aFileThatCannotBeUsed_saysWhy() {
        show(ProductImportUiState(readError = ImportReadError.TOO_LARGE))
        composeRule.onNodeWithText("This file is too large. Keep it under 1 MB.").assertIsDisplayed()
    }

    @Test
    fun whileReading_theChooseButtonWaits() {
        show(ProductImportUiState(isReading = true))
        composeRule.onNodeWithTag("import_reading").assertIsDisplayed()
        composeRule.onNodeWithTag("import_choose").assertIsNotEnabled()
    }

    @Test
    fun thePreview_summarisesTheFile_andListsProblemsFirst() {
        show(loaded())
        composeRule.onNodeWithTag("import_summary").assertIsDisplayed()
        composeRule.onNodeWithText("1 ready · 1 need fixing · 1 already exist").assertIsDisplayed()
        composeRule.onNodeWithTag("import_item_3").assertIsDisplayed()
        composeRule.onNodeWithText("Row 3 · selling_price:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("import_item_2").assertIsDisplayed()
        composeRule.onNodeWithTag("import_item_4").assertIsDisplayed()
        composeRule.onNodeWithText("Needs fixing").assertIsDisplayed()
        composeRule.onNodeWithText("Already exists").assertIsDisplayed()
    }

    @Test
    fun theImportButton_countsWhatWillBeAdded_andAsksToConfirm() {
        var imports = 0
        show(loaded(), object : ProductImportActions { override fun onImport() { imports++ } })
        composeRule.onNodeWithTag("import_button").assertIsEnabled()
        composeRule.onNodeWithText("Import 1 product").assertIsDisplayed()
        composeRule.onNodeWithTag("import_button").performClick()
        assertEquals(1, imports)
    }

    @Test
    fun theImportButton_isOffWhenNothingIsReady() {
        show(loaded(mapping = { it.with(ImportColumn.SELLING_PRICE, null) }))
        composeRule.onNodeWithTag("import_button").assertIsNotEnabled()
    }

    @Test
    fun withoutANameColumn_thePreviewAsksForOne() {
        show(loaded(mapping = { it.with(ImportColumn.NAME, null) }))
        composeRule.onNodeWithTag("import_need_name").assertIsDisplayed()
        composeRule.onNodeWithTag("import_button").assertDoesNotExist()
    }

    @Test
    fun aColumnCanBeChosenOrLeftOut() {
        val changes = mutableListOf<Pair<ImportColumn, Int?>>()
        show(loaded(), object : ProductImportActions { override fun onMappingChange(column: ImportColumn, index: Int?) { changes += column to index } })
        composeRule.onNodeWithTag("map_brand").performClick()
        composeRule.onNodeWithTag("map_brand_2").performClick()
        composeRule.onNodeWithTag("map_category").performClick()
        composeRule.onNodeWithTag("map_category_none").performClick()
        assertEquals(listOf(ImportColumn.BRAND to 2, ImportColumn.CATEGORY to null), changes)
    }

    @Test
    fun aDefaultCategoryCanBeChosen() {
        val chosen = mutableListOf<ProductCategory?>()
        show(loaded(), object : ProductImportActions { override fun onDefaultCategoryChange(category: ProductCategory?) { chosen += category } })
        composeRule.onNodeWithTag("import_default_BEVERAGE").performClick()
        composeRule.onNodeWithTag("import_default_none").performClick()
        assertEquals(listOf(ProductCategory.BEVERAGE, null), chosen)
    }

    @Test
    fun theConfirmDialog_saysHowManyWillBeAdded() {
        var confirmed = 0
        show(
            loaded { it.copy(confirmOpen = true) },
            object : ProductImportActions { override fun onConfirmImport() { confirmed++ } },
        )
        composeRule.onNodeWithText("1 product will be added to your inventory.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("import_confirm").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun afterTheImport_theResultIsShown() {
        var done = 0
        show(
            loaded { it.copy(outcome = ImportOutcome(imported = 2, failed = listOf("Kurta"))) },
            object : ProductImportActions { override fun onDone() { done++ } },
        )
        composeRule.onNodeWithTag("import_result").assertIsDisplayed()
        composeRule.onNodeWithText("2 products added").assertIsDisplayed()
        composeRule.onNodeWithText("1 not added: Kurta").assertIsDisplayed()
        composeRule.onNodeWithTag("import_done").performClick()
        assertEquals(1, done)
    }
}
