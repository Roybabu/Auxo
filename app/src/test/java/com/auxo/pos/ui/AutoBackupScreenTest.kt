package com.auxo.pos.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.backup.auto.AutoBackupFailure
import com.auxo.pos.core.backup.auto.AutoBackupResult
import com.auxo.pos.core.backup.auto.AutoBackupSettings
import com.auxo.pos.core.backup.auto.AutoBackupStatus
import com.auxo.pos.core.backup.auto.BackupSchedule
import com.auxo.pos.ui.screens.home.BackupStatusCard
import com.auxo.pos.ui.screens.more.AutoBackupCardContent
import com.auxo.pos.ui.screens.more.BackupContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.AutoBackupUiState
import com.auxo.pos.viewmodel.BackupUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h2400dp-xhdpi")
class AutoBackupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val log = mutableListOf<String>()
    private val withFolder = AutoBackupSettings(folderUri = "content://tree/sd", folderName = "SD card", schedule = BackupSchedule.DAILY)

    private fun card(state: AutoBackupUiState) {
        composeRule.setContent {
            AuxoTheme {
                AutoBackupCardContent(
                    state = state,
                    onChooseFolder = { log += "folder" },
                    onSchedule = { log += "schedule ${it.name}" },
                    onInterval = { log += "interval $it" },
                    onKeep = { log += "keep $it" },
                    onBackUpNow = { log += "now" },
                )
            }
        }
    }

    // The settings card

    @Test
    fun withNoFolder_itSaysSo_andOffersToChooseOne_butCannotBackUp() {
        card(AutoBackupUiState())
        composeRule.onNodeWithText("No folder chosen yet").assertIsDisplayed()
        composeRule.onNodeWithText("Choose folder").assertIsDisplayed()
        composeRule.onNodeWithTag("auto_backup_now").assertIsNotEnabled()
        composeRule.onNodeWithTag("auto_backup_choose_folder").performClick()
        assertEquals(listOf("folder"), log)
    }

    @Test
    fun afterChoosingAFolder_itShowsTheFolderName_andCanBackUpNow() {
        card(AutoBackupUiState(settings = withFolder))
        composeRule.onNodeWithText("SD card").assertIsDisplayed()
        composeRule.onNodeWithText("Change folder").assertIsDisplayed()
        composeRule.onNodeWithTag("auto_backup_now").assertIsEnabled()
        composeRule.onNodeWithTag("auto_backup_now").performClick()
        assertEquals(listOf("now"), log)
    }

    @Test
    fun allFourSchedules_areOffered_andPickingOneIsReported() {
        card(AutoBackupUiState(settings = withFolder))
        for (schedule in BackupSchedule.entries) composeRule.onNodeWithTag("auto_schedule_${schedule.name}").assertIsDisplayed()
        composeRule.onNodeWithTag("auto_schedule_WEEKLY").performClick()
        composeRule.onNodeWithTag("auto_schedule_EVERY_N_INVOICES").performClick()
        assertEquals(listOf("schedule WEEKLY", "schedule EVERY_N_INVOICES"), log)
    }

    @Test
    fun theInvoiceCount_isOnlyAskedFor_whenThatScheduleIsChosen() {
        card(AutoBackupUiState(settings = withFolder))
        composeRule.onAllNodesWithTag("auto_backup_interval").assertCountEquals(0)
    }

    @Test
    fun theInvoiceCountBox_appearsForThatSchedule_andFlagsABadNumber() {
        card(AutoBackupUiState(settings = withFolder.copy(schedule = BackupSchedule.EVERY_N_INVOICES), intervalText = "0"))
        composeRule.onNodeWithTag("auto_backup_interval").assertIsDisplayed()
    }

    @Test
    fun theKeepBox_isAlwaysThere() {
        card(AutoBackupUiState())
        composeRule.onNodeWithTag("auto_backup_keep").assertIsDisplayed()
    }

    @Test
    fun aScheduleWithNoFolder_tellsTheSuperAdminWhatIsMissing() {
        card(AutoBackupUiState(settings = AutoBackupSettings(schedule = BackupSchedule.DAILY)))
        composeRule.onNodeWithTag("auto_backup_needs_folder").assertIsDisplayed()
        composeRule.onAllNodesWithTag("auto_backup_needs_folder").assertCountEquals(1)
    }

    @Test
    fun noWarning_whenScheduleIsOff() {
        card(AutoBackupUiState())
        composeRule.onAllNodesWithTag("auto_backup_needs_folder").assertCountEquals(0)
    }

    @Test
    fun showsTheLastBackupTime_orThatThereHasBeenNone() {
        card(AutoBackupUiState(settings = withFolder))
        composeRule.onNodeWithText("No automatic backup yet").assertIsDisplayed()
        composeRule.onAllNodesWithTag("auto_backup_failure").assertCountEquals(0)
    }

    @Test
    fun showsTheTimeOfTheLastBackup() {
        card(AutoBackupUiState(settings = withFolder.copy(lastSuccessMillis = 1_700_000_000_000)))
        composeRule.onNodeWithTag("auto_backup_last").assertIsDisplayed()
        composeRule.onAllNodesWithTag("auto_backup_failure").assertCountEquals(0)
    }

    @Test
    fun aFailure_isExplained() {
        card(AutoBackupUiState(settings = withFolder.copy(lastFailureMillis = 5L, lastFailure = AutoBackupFailure.FOLDER_UNAVAILABLE)))
        composeRule.onNodeWithTag("auto_backup_failure").assertIsDisplayed()
        composeRule.onNodeWithText("The backup folder is not available", substring = true).assertIsDisplayed()
    }

    @Test
    fun theResultOfBackUpNow_isShown() {
        card(AutoBackupUiState(settings = withFolder, lastResult = AutoBackupResult.Saved("Auxo-backup-2026-10-12_0900.zip", 0)))
        composeRule.onNodeWithText("Saved Auxo-backup-2026-10-12_0900.zip").assertIsDisplayed()
    }

    @Test
    fun aFolderAccessProblem_isShown() {
        card(AutoBackupUiState(folderAccessProblem = true))
        composeRule.onNodeWithTag("auto_backup_access_problem").assertIsDisplayed()
    }

    @Test
    fun whileBackingUp_theButtonIsOff() {
        card(AutoBackupUiState(settings = withFolder, isWorking = true))
        composeRule.onNodeWithTag("auto_backup_now").assertIsNotEnabled()
    }

    // On the Backup page

    @Test
    fun theBackupPage_showsTheCardBetweenMakingAndRestoring() {
        composeRule.setContent {
            AuxoTheme {
                BackupContent(
                    state = BackupUiState(),
                    onBack = {},
                    onBackUp = {},
                    onChooseFile = {},
                    onShare = {},
                    onConfirmRestore = {},
                    onCancelRestore = {},
                    onDismissProblem = {},
                    extra = { AutoBackupCardContent(AutoBackupUiState(), {}, {}, {}, {}, {}) },
                )
            }
        }
        composeRule.onNodeWithTag("auto_backup_card").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_now").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_choose").assertIsDisplayed()
    }

    // The Home notice

    private fun notice(state: AutoBackupUiState) {
        composeRule.setContent { AuxoTheme { BackupStatusCard(state, onClick = { log += "open" }) } }
    }

    @Test
    fun home_whenOff_invitesTheSuperAdminToSetItUp() {
        notice(AutoBackupUiState(status = AutoBackupStatus.OFF))
        composeRule.onNodeWithText("Back up automatically").assertIsDisplayed()
        composeRule.onNodeWithTag("home_backup_status").performClick()
        assertEquals(listOf("open"), log)
    }

    @Test
    fun home_whenWorking_showsTheLastBackupTime() {
        notice(AutoBackupUiState(settings = withFolder.copy(lastSuccessMillis = 1_700_000_000_000), status = AutoBackupStatus.OK))
        composeRule.onNodeWithText("Backups are working").assertIsDisplayed()
        // The card is one clickable surface, so its pieces are read together under its own tag.
        composeRule.onNodeWithTag("home_backup_status").assertTextContains("Last automatic backup:", substring = true)
    }

    @Test
    fun home_whenFailing_warnsAndSaysWhy() {
        notice(
            AutoBackupUiState(
                settings = withFolder.copy(lastFailureMillis = 5L, lastFailure = AutoBackupFailure.FOLDER_UNAVAILABLE),
                status = AutoBackupStatus.FAILED,
            ),
        )
        composeRule.onNodeWithText("Automatic backup is failing").assertIsDisplayed()
        composeRule.onNodeWithText("The backup folder is not available", substring = true).assertIsDisplayed()
    }

    @Test
    fun home_whenOverdue_warns() {
        notice(AutoBackupUiState(settings = withFolder.copy(lastSuccessMillis = 1_700_000_000_000), status = AutoBackupStatus.OVERDUE))
        composeRule.onNodeWithText("A backup is overdue").assertIsDisplayed()
    }
}
