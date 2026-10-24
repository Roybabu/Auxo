// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.domain.jewellery.JewelleryMetal
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.screens.inventory.labelRes
import com.auxo.pos.ui.screens.inventory.sanitizeDecimal
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ShopUiState
import com.auxo.pos.viewmodel.ShopViewModel

/** The parts of the app this shop uses. */
@Composable
fun ShopSettingsScreen(
    onBack: () -> Unit,
    onOpenStoreDetails: () -> Unit,
    onOpenCategories: () -> Unit = {},
    viewModel: ShopViewModel = viewModel(factory = ShopViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShopSettingsContent(state, onBack, viewModel::onSuppliersChange, onOpenStoreDetails, onOpenCategories, viewModel::saveMetalRates, viewModel::onTableCountChange)
}

@Composable
fun ShopSettingsContent(
    state: ShopUiState,
    onBack: () -> Unit,
    onSuppliersChange: (Boolean) -> Unit = {},
    onOpenStoreDetails: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onSaveMetalRates: (Map<JewelleryMetal, String>, Boolean) -> Unit = { _, _ -> },
    onTableCountChange: (String) -> Unit = {},
) {
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.shop_settings_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.shopName != null) {
                Text(state.shopName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("shop_name"))
            }
            AuxoCard(onClick = onOpenStoreDetails, modifier = Modifier.fillMaxWidth().testTag("shop_store_details")) {
                Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    AuxoIcon(R.drawable.ic_store, contentDescription = null, size = AuxoIconSize.Medium)
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.lg)) {
                        Text(stringResource(R.string.store_details_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.store_details_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AuxoCard(onClick = onOpenCategories, modifier = Modifier.fillMaxWidth().testTag("shop_categories")) {
                Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.categories_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.shop_categories_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AuxoCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.shop_suppliers), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.shop_suppliers_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.suppliers,
                        onCheckedChange = onSuppliersChange,
                        modifier = Modifier.testTag("suppliers_switch"),
                    )
                }
            }
            TablesCard(state.tableCount, onTableCountChange)
            if (com.auxo.pos.ui.visibleCategories().contains(com.auxo.pos.domain.model.ProductCategory.JEWELLERY)) {
                MetalRatesCard(state, onSaveMetalRates)
            }
        }
    }
}

/**
 * Today's rate per gram for each metal. Jewellery is priced from these: weight times the rate, plus the making
 * charge, plus GST. The prices on the tags change only when the owner asks for it.
 */
@Composable
private fun MetalRatesCard(state: ShopUiState, onSave: (Map<JewelleryMetal, String>, Boolean) -> Unit) {
    val texts = remember(state.metalRates) {
        mutableStateMapOf<JewelleryMetal, String>().apply {
            JewelleryMetal.entries.forEach { metal ->
                put(metal, state.metalRates.rateFor(metal)?.toDecimalString().orEmpty())
            }
        }
    }
    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("metal_rates_card")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.metal_rates_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.metal_rates_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            JewelleryMetal.entries.forEach { metal ->
                AuxoTextField(
                    value = texts[metal].orEmpty(),
                    onValueChange = { texts[metal] = sanitizeDecimal(it) },
                    label = stringResource(R.string.field_with_unit, stringResource(metal.labelRes()), stringResource(R.string.per_unit, "g")),
                    prefix = stringResource(R.string.currency_prefix),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth().testTag("metal_rate_${metal.name}"),
                )
            }
            state.repricedCount?.let {
                Text(
                    text = androidx.compose.ui.res.pluralStringResource(R.plurals.metal_rates_repriced, it, it),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("metal_rates_result"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { onSave(texts.toMap(), false) }, modifier = Modifier.weight(1f).testTag("metal_rates_save")) {
                    Text(stringResource(R.string.metal_rates_save))
                }
                Button(onClick = { onSave(texts.toMap(), true) }, modifier = Modifier.weight(1f).testTag("metal_rates_update")) {
                    Text(stringResource(R.string.metal_rates_update))
                }
            }
        }
    }
}

/** How many tables the restaurant has. Parking a restaurant bill asks for one of them. */
@Composable
private fun TablesCard(tableCount: Int, onChange: (String) -> Unit) {
    var text by rememberSaveable(tableCount) { androidx.compose.runtime.mutableStateOf(tableCount.toString()) }
    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("tables_card")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.shop_tables_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.shop_tables_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AuxoTextField(
                value = text,
                onValueChange = {
                    text = it.filter(Char::isDigit).take(2)
                    onChange(text)
                },
                label = stringResource(R.string.shop_tables_field),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth().testTag("tables_field"),
            )
        }
    }
}
