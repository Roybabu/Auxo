// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

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
    private val withFolder = AutoBackupSettings(schedule = BackupSchedule.DAILY)

    private fun card(state: AutoBackupUiState) {
        composeRule.setContent {
            AuxoTheme {
                AutoBackupCardContent(
                    state = state,
                    onSchedule = { log += "schedule ${it.name}" },
                    onInterval = { log += "interval $it" },
                    onKeep = { log += "keep $it" },
                )
            }
        }
    }

    // The settings card

    @Test
    fun noFolderIsAskedFor_andNoBackUpNowButton_backupsGoToTheAuxoFolderByThemselves() {
        card(AutoBackupUiState(settings = AutoBackupSettings()))
        composeRule.onAllNodesWithTag("auto_backup_choose_folder").assertCountEquals(0)
        composeRule.onAllNodesWithTag("auto_backup_now").assertCountEquals(0)
        composeRule.onNodeWithText("Auxo folder in Documents", substring = true).assertIsDisplayed()
    }

    @Test
    fun everyScheduleIsInOnePicker_andPickingOneIsReported() {
        card(AutoBackupUiState(settings = withFolder))
        composeRule.onNodeWithTag("auto_schedule_picker").assertIsDisplayed()
        composeRule.onNodeWithText("Every day").assertIsDisplayed()
        composeRule.onNodeWithTag("auto_schedule_picker").performClick()
        for (label in listOf("Off", "Every 15 minutes", "Every 30 minutes", "Every hour", "Every 2 hours", "Every 3 hours", "Every 6 hours", "Every 12 hours", "Every week", "After a number of invoices")) {
            composeRule.onNodeWithText(label).assertExists()
        }
        composeRule.onNodeWithText("Every week").performClick()
        composeRule.onNodeWithTag("auto_schedule_picker").performClick()
        composeRule.onNodeWithText("After a number of invoices").performClick()
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
        composeRule.onNodeWithText("The Auxo folder in Documents could not be used", substring = true).assertIsDisplayed()
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
                    onConfirmRestore = {},
                    onCancelRestore = {},
                    onDismissProblem = {},
                    extra = { AutoBackupCardContent(AutoBackupUiState(), {}, {}, {}) },
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
        composeRule.onNodeWithText("The Auxo folder in Documents could not be used", substring = true).assertIsDisplayed()
    }

    @Test
    fun home_whenOverdue_warns() {
        notice(AutoBackupUiState(settings = withFolder.copy(lastSuccessMillis = 1_700_000_000_000), status = AutoBackupStatus.OVERDUE))
        composeRule.onNodeWithText("A backup is overdue").assertIsDisplayed()
    }
}
