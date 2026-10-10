package com.invision.pos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.ui.theme.InvisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Temporary: finds which dialog content never reports idle under Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class DialogBisectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun alert(content: @Composable () -> Unit) {
        composeRule.setContent {
            InvisionTheme {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Title") },
                    text = content,
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun a_alertWithChipsOnly() {
        alert { FlowRow { listOf("One", "Two", "Three", "Four", "Five").forEach { FilterChip(selected = it == "One", onClick = {}, label = { Text(it) }) } } }
        composeRule.onNodeWithText("Two").assertExists()
    }

    @Test
    fun b_alertWithTextFieldOnly() {
        alert { OutlinedTextField(value = "3", onValueChange = {}, label = { Text("Quantity") }) }
        composeRule.onNodeWithText("Quantity").assertExists()
    }

    @Test
    fun c_alertWithTextFieldAndSupportingText() {
        alert { OutlinedTextField(value = "3", onValueChange = {}, label = { Text("Quantity") }, supportingText = { Text("New stock: 5") }) }
        composeRule.onNodeWithText("New stock: 5").assertExists()
    }

    @Test
    fun d_plainDialogWithTextField() {
        composeRule.setContent {
            InvisionTheme { Dialog(onDismissRequest = {}) { Surface { OutlinedTextField(value = "3", onValueChange = {}, label = { Text("Qty") }) } } }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Qty").assertExists()
    }

    @Test
    fun e_alertWithTextOnlyColumn() {
        alert { Column { Text("Current stock: 8"); Text("New stock: 5") } }
        composeRule.onNodeWithText("New stock: 5").assertExists()
    }
}
