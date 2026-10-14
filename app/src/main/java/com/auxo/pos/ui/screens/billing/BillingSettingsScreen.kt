package com.auxo.pos.ui.screens.billing

import com.auxo.pos.domain.billing.BusinessTime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.auxo.pos.ui.can
import com.auxo.pos.domain.billing.DiscountPolicy
import com.auxo.pos.domain.billing.Fssai
import com.auxo.pos.domain.billing.DiscountLimits
import com.auxo.pos.core.auth.Permission
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.domain.billing.InvoiceNumbers
import com.auxo.pos.domain.billing.SettingsField
import com.auxo.pos.domain.model.ExpiryWarning
import com.auxo.pos.domain.model.Language
import com.auxo.pos.domain.model.ReceiptOptions
import com.auxo.pos.domain.model.ReceiptPaper
import com.auxo.pos.domain.model.StoreSettings
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.BillingSettingsUiState
import com.auxo.pos.viewmodel.BillingSettingsViewModel
import com.auxo.pos.viewmodel.SettingsEvent
import java.time.LocalDate

@Composable
fun BillingSettingsScreen(
    onBack: () -> Unit,
    viewModel: BillingSettingsViewModel = viewModel(factory = BillingSettingsViewModel.Factory),
    /** Extra content shown above the billing settings (Store details adds the logo here). */
    topSection: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.Saved -> resources.getString(R.string.settings_saved)
                is SettingsEvent.Invalid -> resources.getQuantityString(R.plurals.settings_fix_errors, event.count, event.count)
                SettingsEvent.SaveFailed -> resources.getString(R.string.error_generic)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            AuxoTopAppBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !uiState.isSaving && !uiState.isLoading) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            uiState.isLoading -> LoadingState(modifier)
            uiState.loadFailed -> ErrorState(stringResource(R.string.error_generic), viewModel::retry, modifier)
            else -> BillingSettingsContent(
                state = uiState,
                onChange = viewModel::update,
                onGstinChange = viewModel::onGstinChange,
                onSave = viewModel::save,
                modifier = modifier,
                topSection = topSection,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillingSettingsContent(
    state: BillingSettingsUiState,
    onChange: ((StoreSettings) -> StoreSettings) -> Unit,
    onGstinChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    topSection: (@Composable () -> Unit)? = null,
) {
    val settings = state.settings
    val errors = state.visibleErrors
    @Composable
    fun errorOf(field: SettingsField): String? = errors[field]?.let { stringResource(it.messageRes()) }
    val canSetDiscountLimit = can(Permission.UNLIMITED_DISCOUNT)
    fun receipt(transform: (ReceiptOptions) -> ReceiptOptions) = onChange { it.copy(receipt = transform(it.receipt)) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (topSection != null) {
            item(key = "top") { topSection() }
        }

        item(key = "store") {
            SettingsCard(stringResource(R.string.settings_store)) {
                AuxoTextField(
                    value = settings.storeName,
                    onValueChange = { v -> onChange { it.copy(storeName = v.take(70)) } },
                    label = stringResource(R.string.settings_store_name),
                    error = errorOf(SettingsField.STORE_NAME),
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_store_name"),
                )
                AuxoTextField(
                    value = settings.address,
                    onValueChange = { v -> onChange { it.copy(address = v.take(220)) } },
                    label = stringResource(R.string.settings_address),
                    error = errorOf(SettingsField.ADDRESS),
                    capitalization = KeyboardCapitalization.Words,
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                AuxoTextField(
                    value = settings.phone,
                    onValueChange = { v -> onChange { it.copy(phone = v.filter { c -> c.isDigit() || c in "+ -" }.take(20)) } },
                    label = stringResource(R.string.settings_phone),
                    error = errorOf(SettingsField.PHONE),
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "gst") {
            SettingsCard(stringResource(R.string.settings_gst)) {
                SwitchRow(
                    label = stringResource(R.string.settings_gst_registered),
                    checked = settings.gstRegistered,
                    onCheckedChange = { v -> onChange { it.copy(gstRegistered = v) } },
                    hint = if (!settings.gstRegistered) stringResource(R.string.settings_gst_registered_off) else null,
                    testTag = "settings_gst_registered",
                )
                if (settings.gstRegistered) {
                    AuxoTextField(
                        value = settings.gstin,
                        onValueChange = onGstinChange,
                        label = stringResource(R.string.settings_gstin),
                        error = errorOf(SettingsField.GSTIN),
                        capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_gstin"),
                    )
                }
                AuxoTextField(
                    value = settings.fssai,
                    onValueChange = { v -> onChange { it.copy(fssai = Fssai.normalize(v).take(Fssai.LENGTH)) } },
                    label = stringResource(R.string.settings_fssai),
                    error = errorOf(SettingsField.FSSAI),
                    supportingText = stringResource(R.string.settings_fssai_hint),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_fssai"),
                )
                StatePicker(
                    label = stringResource(R.string.settings_state),
                    selectedCode = settings.stateCode,
                    onSelect = { code -> onChange { it.copy(stateCode = code) } },
                    error = errorOf(SettingsField.STATE),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (settings.gstRegistered) {
                    SwitchRow(
                        label = stringResource(R.string.settings_prices_include_tax),
                        checked = settings.pricesIncludeTax,
                        onCheckedChange = { v -> onChange { it.copy(pricesIncludeTax = v) } },
                        hint = stringResource(R.string.settings_prices_include_tax_hint),
                    )
                }
                SwitchRow(
                    label = stringResource(R.string.settings_round_off),
                    checked = settings.roundOffTotal,
                    onCheckedChange = { v -> onChange { it.copy(roundOffTotal = v) } },
                )
            }
        }

        // Only the Super Admin sees (and may change) what Employees are allowed to discount.
        if (canSetDiscountLimit) {
            item(key = "discount_limit") {
                SettingsCard(stringResource(R.string.settings_employee_discount_limit)) {
                    Text(
                        stringResource(R.string.settings_employee_discount_limit_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        DiscountPolicy.LIMIT_CHOICES.forEach { choice ->
                            FilterChip(
                                selected = settings.employeeDiscountLimitBp == choice,
                                onClick = { onChange { it.copy(employeeDiscountLimitBp = choice) } },
                                label = { Text(if (choice == 0) stringResource(R.string.settings_discount_limit_none) else DiscountLimits.label(choice)) },
                                modifier = Modifier.testTag("discount_limit_$choice"),
                            )
                        }
                    }
                }
            }
        }

        item(key = "expiry_warning") {
            SettingsCard(stringResource(R.string.settings_expiry_warning)) {
                Text(
                    stringResource(R.string.settings_expiry_warning_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ExpiryWarning.CHOICES.forEach { days ->
                        FilterChip(
                            selected = settings.expiryWarningDays == days,
                            onClick = { onChange { it.copy(expiryWarningDays = days) } },
                            label = { Text(pluralStringResource(R.plurals.settings_expiry_days, days, days)) },
                            modifier = Modifier.testTag("expiry_days_$days"),
                        )
                    }
                }
            }
        }

        item(key = "numbering") {
            SettingsCard(stringResource(R.string.settings_numbering)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AuxoTextField(
                        value = settings.invoicePrefix,
                        onValueChange = { v -> onChange { it.copy(invoicePrefix = v.uppercase().filter { c -> c.isLetterOrDigit() }.take(4)) } },
                        label = stringResource(R.string.settings_invoice_prefix),
                        error = errorOf(SettingsField.INVOICE_PREFIX),
                        capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier.weight(1f),
                    )
                    AuxoTextField(
                        value = settings.creditNotePrefix,
                        onValueChange = { v -> onChange { it.copy(creditNotePrefix = v.uppercase().filter { c -> c.isLetterOrDigit() }.take(4)) } },
                        label = stringResource(R.string.settings_credit_note_prefix),
                        error = errorOf(SettingsField.CREDIT_NOTE_PREFIX),
                        capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier.weight(1f),
                    )
                }
                val example = InvoiceNumbers.format(
                    settings.invoicePrefix.ifEmpty { "INV" },
                    InvoiceNumbers.financialYearStart(LocalDate.now(BusinessTime.ZONE)),
                    1,
                )
                Text(
                    stringResource(R.string.settings_numbering_hint, example),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "upi") {
            SettingsCard(stringResource(R.string.settings_upi)) {
                AuxoTextField(
                    value = settings.upiId,
                    onValueChange = { v -> onChange { it.copy(upiId = v.trim().take(80)) } },
                    label = stringResource(R.string.settings_upi_id),
                    error = errorOf(SettingsField.UPI_ID),
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.fillMaxWidth(),
                )
                AuxoTextField(
                    value = settings.upiPayeeName,
                    onValueChange = { v -> onChange { it.copy(upiPayeeName = v.take(60)) } },
                    label = stringResource(R.string.settings_upi_payee),
                    error = errorOf(SettingsField.UPI_PAYEE),
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "receipt") {
            SettingsCard(stringResource(R.string.settings_receipt)) {
                Text(stringResource(R.string.settings_receipt_paper), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ReceiptPaper.entries.forEach { paper ->
                        FilterChip(
                            selected = settings.receipt.paper == paper,
                            onClick = { receipt { it.copy(paper = paper) } },
                            label = { Text(stringResource(paper.longLabelRes())) },
                        )
                    }
                }
                Text(stringResource(R.string.settings_bill_language), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Language.entries.forEach { language ->
                        FilterChip(
                            selected = settings.receipt.language == language,
                            onClick = { receipt { it.copy(language = language) } },
                            label = { Text(language.nativeName) },
                            modifier = Modifier.testTag("settings_bill_language_${language.tag}"),
                        )
                    }
                }
                Text(
                    stringResource(R.string.settings_bill_language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AuxoTextField(
                    value = settings.receipt.footer,
                    onValueChange = { v -> receipt { it.copy(footer = v.take(140)) } },
                    label = stringResource(R.string.settings_footer),
                    error = errorOf(SettingsField.FOOTER),
                    capitalization = KeyboardCapitalization.Sentences,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(stringResource(R.string.settings_show_customer), settings.receipt.showCustomer, { v -> receipt { it.copy(showCustomer = v) } })
                SwitchRow(stringResource(R.string.settings_show_hsn), settings.receipt.showHsn, { v -> receipt { it.copy(showHsn = v) } })
                SwitchRow(stringResource(R.string.settings_show_tax_breakup), settings.receipt.showTaxBreakup, { v -> receipt { it.copy(showTaxBreakup = v) } })
                SwitchRow(stringResource(R.string.settings_show_savings), settings.receipt.showSavings, { v -> receipt { it.copy(showSavings = v) } })
                SwitchRow(stringResource(R.string.settings_show_barcode), settings.receipt.showInvoiceBarcode, { v -> receipt { it.copy(showInvoiceBarcode = v) } })
                SwitchRow(stringResource(R.string.settings_show_upi_qr), settings.receipt.showUpiQr, { v -> receipt { it.copy(showUpiQr = v) } })
            }
        }

        item(key = "save") {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("settings_save"),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(title)
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
    testTag: String = "",
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier,
        )
    }
}
