package com.invision.pos.ui

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.backup.BackupProblem
import com.invision.pos.core.backup.SavedBackup
import com.invision.pos.ui.screens.more.BackupContent
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.BackupUiState
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
            InvisionTheme {
                BackupContent(
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
        composeRule.onNodeWithText("Invision folder in Documents", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No backup made on this phone yet").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_now").performClick()
        composeRule.onNodeWithTag("backup_choose").performClick()
        assertEquals(listOf("backup", "choose"), log)
    }

    @Test
    fun afterABackup_showsTheFile_andCanShareIt() {
        val saved = SavedBackup("Invision-backup-2026-10-12_0106.zip", "Documents/Invision", Uri.parse("content://x/y"))
        show(BackupUiState(saved = saved, lastBackupMillis = 1_700_000_000_000))
        composeRule.onNodeWithTag("backup_saved").assertIsDisplayed()
        composeRule.onNodeWithText("Invision-backup-2026-10-12_0106.zip", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("backup_share").performClick()
        assertEquals(listOf("share Invision-backup-2026-10-12_0106.zip"), log)
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
        composeRule.onNodeWithText("That file is not an Invision backup.").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(listOf("dismiss"), log)
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
}
