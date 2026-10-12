package com.auxo.pos.ui.screens.more

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.printer.DeviceList
import com.auxo.pos.core.printer.PairedDevice
import com.auxo.pos.core.printer.PrintResult
import com.auxo.pos.domain.model.ReceiptPaper
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.PrinterSettingsUiState
import com.auxo.pos.viewmodel.PrinterSettingsViewModel

/** The words for how a print ended; also used on the invoice screen. */
@StringRes
fun PrintResult.messageRes(): Int = when (this) {
    PrintResult.PRINTED -> R.string.printer_result_printed
    PrintResult.NO_PRINTER -> R.string.printer_result_no_printer
    PrintResult.BLUETOOTH_UNAVAILABLE -> R.string.printer_result_unavailable
    PrintResult.BLUETOOTH_OFF -> R.string.printer_result_off
    PrintResult.PERMISSION_NEEDED -> R.string.printer_result_permission
    PrintResult.NOT_PAIRED -> R.string.printer_result_not_paired
    PrintResult.UNREACHABLE -> R.string.printer_result_unreachable
    PrintResult.WRITE_FAILED -> R.string.printer_result_write_failed
    PrintResult.UNSUPPORTED_PAPER -> R.string.printer_result_unsupported
    PrintResult.RENDER_FAILED -> R.string.printer_result_render_failed
}

@StringRes
private fun ReceiptPaper.labelRes(): Int = when (this) {
    ReceiptPaper.THERMAL_58 -> R.string.paper_58
    ReceiptPaper.THERMAL_76 -> R.string.paper_76
    ReceiptPaper.THERMAL_80 -> R.string.paper_80
    ReceiptPaper.A5 -> R.string.paper_a5
    ReceiptPaper.A4 -> R.string.paper_a4
}

@Composable
fun PrinterSettingsScreen(onBack: () -> Unit, viewModel: PrinterSettingsViewModel = viewModel(factory = PrinterSettingsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Pairing and switching Bluetooth on happen in the phone's settings; look again when the person comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    PrinterSettingsContent(
        state = state,
        onBack = onBack,
        onChoose = viewModel::choose,
        onPaper = viewModel::setPaper,
        onCut = viewModel::setCut,
        onForget = viewModel::forget,
        onTest = viewModel::testPrint,
        onAllowBluetooth = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
        onOpenBluetoothSettings = {
            runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrinterSettingsContent(
    state: PrinterSettingsUiState,
    onBack: () -> Unit,
    onChoose: (PairedDevice) -> Unit,
    onPaper: (ReceiptPaper) -> Unit,
    onCut: (Boolean) -> Unit,
    onForget: () -> Unit,
    onTest: () -> Unit,
    onAllowBluetooth: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.printer_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val printer = state.printer
            AuxoCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (printer == null) {
                        Text(stringResource(R.string.printer_none), modifier = Modifier.testTag("printer_none"))
                    } else {
                        Text(stringResource(R.string.printer_current, printer.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("printer_current"))
                        Text(stringResource(R.string.printer_paper_heading), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            state.papers.forEach { paper ->
                                FilterChip(
                                    selected = printer.paper == paper,
                                    onClick = { onPaper(paper) },
                                    label = { Text(stringResource(paper.labelRes())) },
                                    modifier = Modifier.testTag("printer_paper_${paper.name}"),
                                )
                            }
                        }
                        Text(stringResource(R.string.printer_paper_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.printer_cut))
                                Text(stringResource(R.string.printer_cut_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = printer.cutPaper, onCheckedChange = onCut, modifier = Modifier.testTag("printer_cut"))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = onTest, enabled = !state.testing, modifier = Modifier.weight(1f).testTag("printer_test")) {
                                Text(stringResource(if (state.testing) R.string.printer_testing else R.string.printer_test))
                            }
                            OutlinedButton(onClick = onForget, modifier = Modifier.weight(1f).testTag("printer_forget")) {
                                Text(stringResource(R.string.printer_forget))
                            }
                        }
                        state.testResult?.let {
                            Text(
                                stringResource(if (it == PrintResult.PRINTED) R.string.printer_test_sent else it.messageRes()),
                                color = if (it == PrintResult.PRINTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("printer_test_result"),
                            )
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.printer_devices_heading))
            when (val devices = state.devices) {
                DeviceList.Unavailable -> Text(stringResource(R.string.printer_result_unavailable), modifier = Modifier.testTag("printer_devices_unavailable"))
                DeviceList.Off -> Text(stringResource(R.string.printer_result_off), modifier = Modifier.testTag("printer_devices_off"))
                DeviceList.NeedsPermission -> {
                    Text(stringResource(R.string.printer_permission_body), modifier = Modifier.testTag("printer_devices_permission"))
                    Button(onClick = onAllowBluetooth, modifier = Modifier.testTag("printer_allow")) { Text(stringResource(R.string.printer_allow)) }
                }
                is DeviceList.Ready -> {
                    if (devices.devices.isEmpty()) {
                        Text(stringResource(R.string.printer_devices_empty), modifier = Modifier.testTag("printer_devices_empty"))
                    }
                    devices.devices.forEach { device ->
                        val chosen = printer?.address == device.address
                        AuxoCard(modifier = Modifier.fillMaxWidth().testTag("printer_device_${device.address}")) {
                            Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, style = MaterialTheme.typography.titleSmall)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (chosen) {
                                    Text(stringResource(R.string.printer_chosen), color = MaterialTheme.colorScheme.primary)
                                } else {
                                    TextButton(onClick = { onChoose(device) }, modifier = Modifier.testTag("printer_choose_${device.address}")) {
                                        Text(stringResource(R.string.printer_choose))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth().testTag("printer_open_settings")) {
                Text(stringResource(R.string.printer_open_settings))
            }
        }
    }
}
