// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auxo.pos.R
import com.auxo.pos.core.backup.BackupProblem
import com.auxo.pos.core.backup.FoundBackup
import com.auxo.pos.core.backup.RestartApp
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoIconStyle
import com.auxo.pos.ui.screens.more.UnlockForm
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.FindBackupUiState
import com.auxo.pos.viewmodel.FindBackupViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MintSurface = Color(0xFFF1FAF5)
private val MintTint = Color(0xFFE3F4EA)
private val MintBorder = Color(0xFFD3E9DC)
private val NoticeBlue = Color(0xFFE6F0FA)
private val NoticeAmber = Color(0xFFFCF1D6)

/**
 * A bottom sheet over the sign-in page, shown when the folder the person chose holds Auxo backups. It has three
 * steps in one place: tick a backup in the list, confirm what will be restored, then wait while it is restored.
 * Choosing another folder and setting up a new shop stay available from the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindBackupDialog(
    viewModel: FindBackupViewModel,
    onSetUpNewShop: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.restored) { if (state.restored) RestartApp.restart(context) }

    // While data is being replaced the sheet cannot be swiped away or dismissed.
    val busy by rememberUpdatedState(state.restoring || state.restored)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden || !busy },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) viewModel.onChooseAnother() },
        sheetState = sheetState,
        containerColor = MintSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy),
        modifier = Modifier.testTag("find_backup"),
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.xl).padding(bottom = Spacing.lg)) {
            val confirming = state.selected
            when {
                state.restored -> ProgressStep(done = true)
                state.restoring -> ProgressStep(done = false)
                confirming != null -> ConfirmStep(confirming, viewModel)
                else -> ListStep(state, viewModel, onSetUpNewShop, onChooseAnother)
            }
        }
    }

    // A locked backup asks for its passphrase on top of the sheet.
    if (state.unlocking && state.selected != null && !state.restoring) {
        Dialog(onDismissRequest = viewModel::onSelectionCancelled) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                UnlockForm(onUnlock = viewModel::onPassphrase, onCancel = viewModel::onSelectionCancelled)
            }
        }
    }
}

/** Step 1: the backups as cards, with Restore shop acting only on the ticked one. */
@Composable
private fun ListStep(state: FindBackupUiState, viewModel: FindBackupViewModel, onSetUpNewShop: () -> Unit, onChooseAnother: () -> Unit) {
    val found = state.found.orEmpty()
    val groups = remember(state.found) { groupByShop(found) }
    val newest = remember(state.found) { found.maxByOrNull { it.takenAtMillis } }
    val noShopNames = found.none { it.manifest?.shopName?.isNotBlank() == true }
    val showHeadings = groups.count { it.title.isNotBlank() } > 1
    Column(modifier = Modifier.fillMaxWidth().testTag("find_list").weightedScroll()) {
        SheetHeader(R.drawable.ic_cloud_download, stringResource(R.string.find_sheet_title), stringResource(R.string.find_sheet_body), big = true)
        if (noShopNames) {
            Notice(
                icon = R.drawable.ic_info,
                tint = NoticeBlue,
                title = stringResource(R.string.find_banner_title),
                body = stringResource(R.string.find_banner_body),
                modifier = Modifier.padding(top = Spacing.lg).testTag("find_banner"),
            )
        }
        state.problem?.let {
            Text(
                stringResource(problemText(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.md).testTag("find_problem"),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.find_available), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AuthInk, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.find_found, found.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            groups.forEach { group ->
                if (showHeadings) {
                    Text(group.title.ifBlank { stringResource(R.string.find_unknown_shop) }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                group.backups.forEach { backup ->
                    BackupCard(
                        backup = backup,
                        latest = found.size > 1 && backup === newest,
                        selected = state.chosen?.uri == backup.uri,
                        onClick = { viewModel.onChoose(backup) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        PrimaryAction(
            label = stringResource(R.string.find_restore_shop),
            icon = R.drawable.ic_history,
            enabled = state.chosen != null,
            onClick = viewModel::onRestoreRequested,
            modifier = Modifier.testTag("find_restore"),
        )
        OutlinedButton(
            onClick = onChooseAnother,
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, MintBorder),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).height(52.dp).testTag("find_another"),
        ) {
            AuxoIcon(R.drawable.ic_folder, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.find_another), color = AuthEmerald, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onSetUpNewShop, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("find_new_shop")) {
            Text(stringResource(R.string.find_new), color = AuthEmerald, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Step 2: what is about to be restored, and what that replaces. */
@Composable
private fun ConfirmStep(backup: FoundBackup, viewModel: FindBackupViewModel) {
    var askingAgain by rememberSaveable { mutableStateOf(false) }
    if (askingAgain) {
        // A last warning before anything is replaced: what is on this phone now is not kept.
        AlertDialog(
            onDismissRequest = { askingAgain = false },
            icon = { AuxoIcon(R.drawable.ic_warning, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat) },
            title = { Text(stringResource(R.string.find_w_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.find_w_body)) },
            // The dialog puts confirmButton on the right and dismissButton on the left, so the two are swapped here:
            // "Replace and restore" sits on the left and "Go back" on the right.
            confirmButton = {
                TextButton(onClick = { askingAgain = false }, modifier = Modifier.testTag("find_warn_back")) { Text(stringResource(R.string.find_w_back)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { askingAgain = false; viewModel.onConfirmed() },
                    modifier = Modifier.testTag("find_warn_confirm"),
                ) { Text(stringResource(R.string.find_w_action), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            modifier = Modifier.testTag("find_warning_dialog"),
        )
    }
    Column(modifier = Modifier.fillMaxWidth().weightedScroll().testTag("find_confirm_step")) {
        TextButton(onClick = viewModel::onSelectionCancelled, modifier = Modifier.testTag("find_confirm_back")) {
            AuxoIcon(R.drawable.ic_arrow_back, contentDescription = stringResource(R.string.action_cancel), size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
        }
        SheetHeader(R.drawable.ic_invoice, stringResource(R.string.find_c_title), stringResource(R.string.find_c_body), badge = R.drawable.ic_history)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MintBorder),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                SummaryRow(R.drawable.ic_calendar, stringResource(R.string.find_row_date), formatDate(backup.takenAtMillis))
                SummaryRow(R.drawable.ic_invoice, stringResource(R.string.find_row_file), backup.fileName)
                SummaryRow(R.drawable.ic_backup, stringResource(R.string.find_row_size), fileSize(backup))
                SummaryRow(
                    R.drawable.ic_store,
                    stringResource(R.string.find_row_shop),
                    backup.manifest?.shopName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.find_not_available),
                )
            }
        }
        Notice(
            icon = R.drawable.ic_warning,
            tint = NoticeAmber,
            title = null,
            body = stringResource(R.string.find_c_warning),
            modifier = Modifier.padding(top = Spacing.md).testTag("find_warning"),
        )
        Spacer(Modifier.height(Spacing.lg))
        PrimaryAction(
            label = stringResource(R.string.find_restore_shop),
            icon = R.drawable.ic_history,
            enabled = true,
            onClick = { askingAgain = true },
            modifier = Modifier.testTag("find_confirm"),
        )
        OutlinedButton(
            onClick = viewModel::onSelectionCancelled,
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, MintBorder),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).height(52.dp).testTag("find_confirm_cancel"),
        ) { Text(stringResource(R.string.action_cancel), color = AuthInk, fontWeight = FontWeight.SemiBold) }
    }
}

/**
 * Step 3: restoring. The restore reports no stages or percentage, so none are shown: only that work is going on,
 * and once it has worked, that the app is about to restart.
 */
@Composable
private fun ProgressStep(done: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg).testTag("find_working"), horizontalAlignment = Alignment.CenterHorizontally) {
        RestoreArt()
        Text(
            stringResource(if (done) R.string.find_done_title else R.string.find_restoring_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = AuthInk,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            stringResource(if (done) R.string.find_done_body else R.string.find_restoring_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        LinearProgressIndicator(
            color = AuthEmerald,
            trackColor = MintBorder,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
        )
    }
}

/**
 * The picture above the progress text: a cloud with a download arrow, with the three things a backup holds (the shop's
 * data, its documents and its store) circling it on a dashed track, and a few leaves for Auxo's "grow". It is drawn
 * from vectors and shapes, so it scales with the screen and carries no claim about how far the restore has got.
 */
@Composable
private fun RestoreArt() {
    val track = AuthEmerald.copy(alpha = 0.35f)
    val leaf = AuthEmerald.copy(alpha = 0.55f)
    Box(modifier = Modifier.size(width = 280.dp, height = 176.dp).clearAndSetSemantics {}) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            // The dashed track the three icons sit on.
            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            drawArc(
                color = track,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - 104.dp.toPx(), 84.dp.toPx() - 70.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(208.dp.toPx(), 140.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx(), pathEffect = dash),
            )
            // Leaves at the lower left and right.
            fun leafAt(cx: Float, cy: Float, length: Float, degrees: Float) {
                rotate(degrees, androidx.compose.ui.geometry.Offset(cx, cy)) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy - length / 2)
                        quadraticTo(cx + length * 0.38f, cy, cx, cy + length / 2)
                        quadraticTo(cx - length * 0.38f, cy, cx, cy - length / 2)
                        close()
                    }
                    drawPath(path, leaf)
                }
            }
            leafAt(size.width * 0.14f, size.height * 0.80f, 40.dp.toPx(), -60f)
            leafAt(size.width * 0.10f, size.height * 0.52f, 22.dp.toPx(), -20f)
            leafAt(size.width * 0.86f, size.height * 0.78f, 40.dp.toPx(), 60f)
            leafAt(size.width * 0.91f, size.height * 0.46f, 22.dp.toPx(), 25f)
        }
        ArtBubble(R.drawable.ic_database, 60.dp, Modifier.align(Alignment.TopCenter))
        ArtBubble(R.drawable.ic_invoice, 50.dp, Modifier.align(Alignment.CenterStart).padding(start = 36.dp, bottom = 20.dp))
        ArtBubble(R.drawable.ic_store, 50.dp, Modifier.align(Alignment.CenterEnd).padding(end = 36.dp, bottom = 20.dp))
        AuxoIcon(
            R.drawable.ic_cloud_download,
            contentDescription = null,
            style = AuxoIconStyle.Flat,
            modifier = Modifier.size(112.dp).align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ArtBubble(icon: Int, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(MintTint).padding(1.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        AuxoIcon(icon, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
    }
}

@Composable
private fun SheetHeader(icon: Int, title: String, body: String, badge: Int? = null, big: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(if (big) 96.dp else 72.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(if (big) 88.dp else 64.dp).clip(CircleShape).background(MintTint), contentAlignment = Alignment.Center) {
                AuxoIcon(icon, contentDescription = null, size = if (big) AuxoIconSize.Hero else AuxoIconSize.Medium, style = AuxoIconStyle.Flat)
            }
            // A small round badge on the lower right of the circle, such as a clock on a document.
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MintSurface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(AuthEmerald),
                    contentAlignment = Alignment.Center,
                ) {
                    AuxoIcon(badge, contentDescription = null, size = AuxoIconSize.Mini, style = AuxoIconStyle.Flat, accent = Color.White)
                }
            }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = AuthInk, modifier = Modifier.padding(top = Spacing.md))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Notice(icon: Int, tint: Color, title: String?, body: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = tint, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AuxoIcon(icon, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
            Column {
                title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AuthInk) }
                Text(body, style = MaterialTheme.typography.bodyMedium, color = AuthInk)
            }
        }
    }
}

@Composable
private fun SummaryRow(icon: Int, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        AuxoIcon(icon, contentDescription = null, size = AuxoIconSize.Mini, style = AuxoIconStyle.Flat)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.md).weight(0.9f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AuthInk, modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun PrimaryAction(label: String, icon: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AuthEmerald, contentColor = Color.White),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        AuxoIcon(icon, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat, accent = Color.White)
        Spacer(Modifier.width(Spacing.sm))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

/** One backup: its date, file name, size and kind, with a tick when it is the chosen one. */
@Composable
private fun BackupCard(backup: FoundBackup, latest: Boolean, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MintTint else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AuthEmerald else MintBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag("find_backup_${backup.fileName}"),
    ) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (selected) Color.White else MintTint), contentAlignment = Alignment.Center) {
                AuxoIcon(R.drawable.ic_invoice, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (latest) {
                    Surface(shape = RoundedCornerShape(6.dp), color = AuthEmerald) {
                        Text(
                            stringResource(R.string.find_badge_latest),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp).testTag("find_latest"),
                        )
                    }
                }
                Text(formatDate(backup.takenAtMillis), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AuthInk)
                Text(backup.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Text(fileSize(backup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val manifest = backup.manifest
                val products = manifest?.products
                val customers = manifest?.customers
                val invoices = manifest?.invoices
                if (products != null && customers != null && invoices != null) {
                    Text(stringResource(R.string.find_counts, products, customers, invoices), style = MaterialTheme.typography.bodySmall, color = AuthInk)
                }
                if (backup.locked) {
                    Text(stringResource(R.string.find_locked_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SelectionMark(selected)
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    if (selected) {
        Box(modifier = Modifier.size(26.dp).clip(CircleShape).background(AuthEmerald), contentAlignment = Alignment.Center) {
            AuxoIcon(R.drawable.ic_check, contentDescription = null, size = AuxoIconSize.Mini, style = AuxoIconStyle.Flat, accent = Color.White)
        }
    } else {
        Surface(shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.5.dp, MintBorder), modifier = Modifier.size(26.dp)) {}
    }
}

/** Scrolls its content when the sheet is shorter than it; the buttons are part of the content so they stay reachable. */
@Composable
private fun Modifier.weightedScroll(): Modifier = this.verticalScroll(rememberScrollState())

internal class ShopGroup(val key: String, val title: String, val backups: List<FoundBackup>)

/** Groups [found] by shop name (locked ones together, since their name is hidden), shops with the newest backup first. */
internal fun groupByShop(found: List<FoundBackup>): List<ShopGroup> =
    found.groupBy { backup ->
        when {
            backup.locked -> LOCKED_KEY
            else -> backup.manifest?.shopName?.trim()?.lowercase().orEmpty()
        }
    }.map { (key, backups) ->
        val sorted = backups.sortedByDescending { it.takenAtMillis }
        ShopGroup(
            key = key.ifEmpty { "unnamed" },
            title = if (key == LOCKED_KEY) "" else sorted.first().manifest?.shopName?.trim().orEmpty(),
            backups = sorted,
        )
    }.sortedByDescending { it.backups.first().takenAtMillis }

private const val LOCKED_KEY = "\u0000locked"

/** "24 Oct 2026 · 8:58 PM", in the phone's language and time zone. */
private fun formatDate(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy '·' h:mm a", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun fileSize(backup: FoundBackup): String {
    val size = android.text.format.Formatter.formatShortFileSize(LocalContext.current, backup.sizeBytes)
    val kind = stringResource(if (backup.locked) R.string.find_kind_locked else R.string.find_kind_zip)
    return "$size · $kind"
}

private fun problemText(problem: BackupProblem): Int = when (problem) {
    BackupProblem.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
    BackupProblem.WRONG_APP -> R.string.backup_error_wrong_app
    BackupProblem.NEWER_BACKUP -> R.string.backup_error_newer
    BackupProblem.CORRUPT -> R.string.backup_error_corrupt
    BackupProblem.UNREADABLE -> R.string.backup_error_unreadable
    BackupProblem.PASSPHRASE_NEEDED, BackupProblem.WRONG_PASSPHRASE -> R.string.backup_error_wrong_passphrase
    else -> R.string.backup_error_restore
}
