package com.invision.pos.ui.screens.more

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.ShopUiState
import com.invision.pos.viewmodel.ShopViewModel

/** The parts of the app this shop uses. */
@Composable
fun ShopSettingsScreen(onBack: () -> Unit, viewModel: ShopViewModel = viewModel(factory = ShopViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShopSettingsContent(state, onBack, viewModel::onRestaurantMenuChange, viewModel::onSuppliersChange)
}

@Composable
fun ShopSettingsContent(
    state: ShopUiState,
    onBack: () -> Unit,
    onRestaurantMenuChange: (Boolean) -> Unit,
    onSuppliersChange: (Boolean) -> Unit = {},
) {
    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.shop_settings_title), onBack = onBack) },
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
            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.shop_restaurant_menu), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.shop_restaurant_menu_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.restaurantMenu,
                        onCheckedChange = onRestaurantMenuChange,
                        modifier = Modifier.testTag("restaurant_menu_switch"),
                    )
                }
            }
            InvisionCard(modifier = Modifier.fillMaxWidth()) {
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
