// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.backup.BackupManifest
import com.auxo.pos.core.backup.BackupService
import com.auxo.pos.core.backup.FoundBackup
import com.auxo.pos.core.backup.SavedBackup
import com.auxo.pos.ui.screens.auth.FindBackupDialog
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.FindBackupViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class FindBackupDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val restored = mutableListOf<Uri>()

    private val service = object : BackupService {
        override suspend fun backUp(): SavedBackup = error("not used")
        override suspend fun restore(source: Uri, passphrase: String?) { restored += source }
        override fun isProtected() = false
        override fun setPassphrase(passphrase: String) = Unit
        override fun clearPassphrase() = Unit
        override fun lastBackupMillis(): Long? = null
    }

    private fun backup(name: String, shop: String, taken: Long) = FoundBackup(
        uri = Uri.parse("content://test/$name"),
        fileName = name,
        modifiedMillis = taken,
        sizeBytes = 2048,
        locked = false,
        manifest = BackupManifest(createdAtMillis = taken, databaseVersion = 1, appVersion = "1.0.0", shopName = shop, products = 5, customers = 2, invoices = 9),
    )

    @Test
    fun foundBackups_areBlocksInAPopUp_withANewShopChoice() {
        val viewModel = FindBackupViewModel({ listOf(backup("a.zip", "Roy Stores", 2_000), backup("b.zip", "Green Mart", 1_000)) }, service, kotlinx.coroutines.Dispatchers.Unconfined)
        var newShop = 0
        composeRule.setContent {
            AuxoTheme { FindBackupDialog(viewModel, onSetUpNewShop = { newShop++ }, onChooseAnother = {}) }
        }
        viewModel.onFolderChosen(Uri.parse("content://tree"))
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.found != null }
        composeRule.onNodeWithTag("find_backup").assertIsDisplayed()
        composeRule.onNodeWithText("Roy Stores").assertIsDisplayed()
        composeRule.onNodeWithText("Green Mart").assertIsDisplayed()
        composeRule.onNodeWithTag("find_new_shop").assertIsDisplayed().performClick()
        assertEquals(1, newShop)
    }

    @Test
    fun restoreStaysOff_untilACardIsTicked_andRestoresOnlyThatBackup() {
        val viewModel = FindBackupViewModel({ listOf(backup("a.zip", "Roy Stores", 2_000), backup("b.zip", "Roy Stores", 1_000)) }, service, kotlinx.coroutines.Dispatchers.Unconfined)
        composeRule.setContent {
            AuxoTheme { FindBackupDialog(viewModel, onSetUpNewShop = {}, onChooseAnother = {}) }
        }
        viewModel.onFolderChosen(Uri.parse("content://tree"))
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.found != null }
        composeRule.onNodeWithTag("find_restore").assertIsNotEnabled()
        composeRule.onNodeWithTag("find_latest", useUnmergedTree = true).assertExists()
        // The older card is ticked, not the latest: that is the one restored.
        composeRule.onNodeWithTag("find_backup_b.zip").performClick()
        composeRule.onNodeWithTag("find_backup_b.zip").assertIsSelected()
        composeRule.onNodeWithTag("find_backup_a.zip").assertIsNotSelected()
        composeRule.onNodeWithTag("find_restore").assertIsEnabled().performClick()
        assertEquals(emptyList<Uri>(), restored)
        composeRule.onNodeWithTag("find_confirm_step").assertIsDisplayed()
        composeRule.onNodeWithTag("find_confirm").performClick()
        // One more warning comes first: nothing is restored until it is accepted.
        composeRule.onNodeWithTag("find_warning_dialog").assertIsDisplayed()
        assertEquals(emptyList<Uri>(), restored)
        composeRule.onNodeWithTag("find_warn_confirm").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { restored.isNotEmpty() }
        assertEquals("content://test/b.zip", restored.single().toString())
    }

    @Test
    fun cancellingTheConfirmation_restoresNothing_andKeepsTheTick() {
        val viewModel = FindBackupViewModel({ listOf(backup("a.zip", "Roy Stores", 2_000)) }, service, kotlinx.coroutines.Dispatchers.Unconfined)
        composeRule.setContent {
            AuxoTheme { FindBackupDialog(viewModel, onSetUpNewShop = {}, onChooseAnother = {}) }
        }
        viewModel.onFolderChosen(Uri.parse("content://tree"))
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.found != null }
        composeRule.onNodeWithTag("find_backup_a.zip").performClick()
        composeRule.onNodeWithTag("find_restore").performClick()
        composeRule.onNodeWithTag("find_confirm_cancel").performClick()
        composeRule.onNodeWithTag("find_backup_a.zip").assertIsSelected()
        assertEquals(emptyList<Uri>(), restored)
    }

    @Test
    fun goingBackFromTheLastWarning_restoresNothing() {
        val viewModel = FindBackupViewModel({ listOf(backup("a.zip", "Roy Stores", 2_000)) }, service, kotlinx.coroutines.Dispatchers.Unconfined)
        composeRule.setContent {
            AuxoTheme { FindBackupDialog(viewModel, onSetUpNewShop = {}, onChooseAnother = {}) }
        }
        viewModel.onFolderChosen(Uri.parse("content://tree"))
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.found != null }
        composeRule.onNodeWithTag("find_backup_a.zip").performClick()
        composeRule.onNodeWithTag("find_restore").performClick()
        composeRule.onNodeWithTag("find_confirm").performClick()
        composeRule.onNodeWithTag("find_warn_back").performClick()
        composeRule.onNodeWithTag("find_confirm_step").assertIsDisplayed()
        assertEquals(emptyList<Uri>(), restored)
    }
}
