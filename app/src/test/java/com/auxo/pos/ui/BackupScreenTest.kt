// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui

import android.net.Uri
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.SavedBackup
import com.auxo.pos.ui.screens.more.BackupContent
import com.auxo.pos.ui.screens.more.ChoosePassphraseForm
import com.auxo.pos.ui.screens.more.UnlockForm
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.BackupUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class BackupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val log = mutableListOf<String>()

    private fun show(state: BackupUiState) {
        composeRule.setContent {
            AuxoTheme {
                BackupContent(
                    onChoosePassphrase = { log += "choose passphrase" },
                    onPassphraseSet = { log += "set $it" },
                    onAskTurnOff = { log += "ask turn off" },
                    onTurnOffConfirmed = { log += "turn off" },
                    onPassphraseForRestore = { log += "unlock $it" },
                    state = state,
                    onBack = {},
                    onBackUp = { log += "backup" },
                    onChooseFile = { log += "choose" },
                    onShare = { log += "share ${it.fileName}" },
                    onConfirmRestore = { log += "confirm" },
                    onCancelRestore = { log += "cancel" },
                    onDismissProblem = { log += "dismiss" },
                )
            }
        }
    }

    @Test
    fun explainsWhatIsSaved_andWhereTheFileGoes() {
        show(BackupUiState())
        composeRule.onNodeWithText("Back up this phone").assertIsDisplayed()
        composeRule.onNodeWithText("Auxo folder in Documents", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No backup made on this phone yet").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_now").performClick()
        composeRule.onNodeWithTag("backup_choose").performClick()
        assertEquals(listOf("backup", "choose"), log)
    }

    @Test
    fun afterABackup_showsTheFile_andCanShareIt() {
        val saved = SavedBackup("Auxo-backup-2026-10-12_0106.zip", "Documents/Auxo", Uri.parse("content://x/y"))
        show(BackupUiState(saved = saved, lastBackupMillis = 1_700_000_000_000))
        composeRule.onNodeWithTag("backup_saved").assertIsDisplayed()
        composeRule.onNodeWithText("Auxo-backup-2026-10-12_0106.zip", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("backup_share").performClick()
        assertEquals(listOf("share Auxo-backup-2026-10-12_0106.zip"), log)
    }

    @Test
    fun choosingAFile_asksBeforeReplacingAnything() {
        show(BackupUiState(pendingRestore = Uri.parse("content://x/old.zip")))
        composeRule.onNodeWithText("Replace everything on this phone?").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_confirm").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("confirm", "cancel"), log)
    }

    @Test
    fun aBadFile_isExplained() {
        show(BackupUiState(problem = BackupProblem.NOT_A_BACKUP))
        composeRule.onNodeWithText("That file is not an Auxo backup.").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(listOf("dismiss"), log)
    }

    @Test
    fun aBackupOnlyInTheAppFolder_warnsItIsErasedOnUninstall() {
        val saved = SavedBackup("b.zip", "Android/data/com.auxo.pos/files/Auxo", Uri.parse("content://x/y"), appFolderOnly = true)
        show(BackupUiState(saved = saved))
        composeRule.onNodeWithTag("backup_app_only_warning").assertIsDisplayed()
    }

    @Test
    fun aBackupInDocuments_showsNoWarning() {
        val saved = SavedBackup("b.zip", "Documents/Auxo", Uri.parse("content://x/y"))
        show(BackupUiState(saved = saved))
        composeRule.onNodeWithTag("backup_app_only_warning").assertDoesNotExist()
    }

    @Test
    fun whileWorking_theButtonsAreOff() {
        show(BackupUiState(isWorking = true))
        composeRule.onNodeWithTag("backup_now").assertIsNotEnabled()
        composeRule.onNodeWithTag("backup_choose").assertIsNotEnabled()
    }

    @Test
    fun afterRestoring_showsAProgressMessage() {
        show(BackupUiState(restored = true))
        composeRule.onNodeWithTag("backup_restoring").assertIsDisplayed()
    }

    @Test
    fun lockingIsOffByDefault_andTheCardSaysSo() {
        show(BackupUiState())
        composeRule.onNodeWithText("Lock backups with a passphrase").assertIsDisplayed()
        composeRule.onNodeWithText("Backups are not locked.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("backup_protect_set").performClick()
        assertEquals(listOf("choose passphrase"), log)
    }

    @Test
    fun whenLocked_theCardOffersChangeAndTurnOff() {
        show(BackupUiState(isProtected = true))
        composeRule.onNodeWithText("New backups are locked.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Change passphrase").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_protect_off").performClick()
        assertEquals(listOf("ask turn off"), log)
    }

    // The boxes are tested without their dialog window: a text field inside a dialog window never lets Robolectric's
    // Compose clock go idle, whether or not anything is typed.
    private fun showForm(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { AuxoTheme { content() } }
    }

    @Test
    fun choosingAPassphrase_needsEightCharacters_andTwoMatchingEntries() {
        showForm { ChoosePassphraseForm(onSave = { log += "set $it" }, onCancel = {}) }
        composeRule.onNodeWithTag("backup_pass_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("backup_pass_first").performTextInput("short")
        composeRule.onNodeWithTag("backup_pass_second").performTextInput("short")
        composeRule.onNodeWithTag("backup_pass_save").assertIsNotEnabled()
        composeRule.onNodeWithText("Use at least 8 characters.").assertIsDisplayed()

        composeRule.onNodeWithTag("backup_pass_first").performTextInput("er passphrase")
        composeRule.onNodeWithTag("backup_pass_second").performTextInput("x")
        composeRule.onNodeWithTag("backup_pass_save").assertIsNotEnabled()
        composeRule.onNodeWithText("The two passphrases are not the same.").assertIsDisplayed()
    }

    @Test
    fun matchingEntries_turnLockingOn() {
        showForm { ChoosePassphraseForm(onSave = { log += "set $it" }, onCancel = {}) }
        composeRule.onNodeWithTag("backup_pass_first").performTextInput("a good passphrase")
        composeRule.onNodeWithTag("backup_pass_second").performTextInput("a good passphrase")
        composeRule.onNodeWithTag("backup_pass_save").performClick()
        assertEquals(listOf("set a good passphrase"), log)
    }

    @Test
    fun theChoosePassphraseBox_opensWithTheWarning() {
        showForm { ChoosePassphraseForm(onSave = {}, onCancel = {}) }
        composeRule.onNodeWithText("Choose a passphrase").assertIsDisplayed()
        composeRule.onNodeWithText("Auxo cannot recover it", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("backup_pass_save").assertIsNotEnabled()
    }

    @Test
    fun aLockedBackup_asksForItsPassphrase() {
        showForm { UnlockForm(onUnlock = {}, onCancel = {}) }
        composeRule.onNodeWithText("This backup is locked").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_unlock_action").assertIsNotEnabled()
    }

    @Test
    fun theUnlockBox_passesOnWhatWasTyped() {
        showForm { UnlockForm(onUnlock = { log += "unlock $it" }, onCancel = {}) }
        composeRule.onNodeWithTag("backup_unlock_action").assertIsNotEnabled()
        composeRule.onNodeWithTag("backup_unlock_field").performTextInput("correct horse")
        composeRule.onNodeWithTag("backup_unlock_action").performClick()
        assertEquals(listOf("unlock correct horse"), log)
    }

    @Test
    fun aWrongPassphrase_isExplained() {
        show(BackupUiState(problem = BackupProblem.WRONG_PASSPHRASE))
        composeRule.onNodeWithText("That passphrase did not open the backup", substring = true).assertIsDisplayed()
    }
}
