package com.auxo.pos.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.components.AuxoCard
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
    ShopSettingsContent(state, onBack, viewModel::onSuppliersChange, onOpenStoreDetails, onOpenCategories)
}

@Composable
fun ShopSettingsContent(
    state: ShopUiState,
    onBack: () -> Unit,
    onSuppliersChange: (Boolean) -> Unit = {},
    onOpenStoreDetails: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
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
                    Icon(painterResource(R.drawable.ic_store), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
        }
    }
}
