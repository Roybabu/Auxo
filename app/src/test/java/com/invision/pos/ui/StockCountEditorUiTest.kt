package com.invision.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.domain.inventory.CountLine
import com.invision.pos.domain.inventory.CountReason
import com.invision.pos.domain.inventory.StockCount
import com.invision.pos.domain.inventory.StockCounts
import com.invision.pos.testutil.TestData
import com.invision.pos.ui.screens.stock.StockCountActions
import com.invision.pos.ui.screens.stock.CountEditorForm
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.CountCandidate
import com.invision.pos.viewmodel.CountEditor
import com.invision.pos.viewmodel.CountStage
import com.invision.pos.viewmodel.StockCountUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The count editor is a dialog with a text field; those hang on the very tall screen the other tests use. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class StockCountEditorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val shirt = TestData.tShirt()
    private val m = shirt.variants[0]
    private val l = shirt.variants[1]
    private val products = listOf(shirt)

    private fun state(
        lines: List<CountLine> = emptyList(),
        stage: CountStage = CountStage.COUNTING,
        started: Boolean = true,
        build: (StockCountUiState) -> StockCountUiState = { it },
    ): StockCountUiState {
        val count = if (started) StockCount(1, 0, lines) else null
        val rows = count?.let { StockCounts.rows(it, products) }.orEmpty().asReversed()
        return build(
            StockCountUiState(
                isLoading = false,
                count = count,
                stage = stage,
                rows = rows,
                summary = StockCounts.summary(rows),
            ),
        )
    }

    private fun show(editor: CountEditor, onText: (String) -> Unit = {}, onSave: () -> Unit = {}) {
        // The form is tested without the dialog window: text fields inside a dialog never become idle in these tests.
        composeRule.setContent { InvisionTheme { CountEditorForm(editor, alreadyCounted = false, onTextChange = onText, onSave = onSave, onRemove = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun theEditor_showsTheStock_andReportsTypingAndSaving() {
        val typed = mutableListOf<String>()
        var saved = 0
        show(CountEditor(shirt, m, text = ""), onText = { typed += it }, onSave = { saved++ })
        composeRule.onNodeWithText("In stock now: 8").assertIsDisplayed()
        composeRule.onNodeWithTag("count_input").performTextReplacement("5")
        composeRule.onNodeWithTag("count_input").performImeAction()
        assertEquals(listOf("5"), typed)
        assertEquals(1, saved)
    }

    @Test
    fun theEditor_explainsAnUnusableQuantity() {
        show(CountEditor(shirt, m, text = "abc", error = true))
        composeRule.onNodeWithText("Enter the quantity you counted (0 or more).").assertIsDisplayed()
    }
}
