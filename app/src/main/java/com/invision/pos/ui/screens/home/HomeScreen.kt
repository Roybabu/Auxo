package com.invision.pos.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.ui.components.SummaryCard
import com.invision.pos.ui.navigation.TopLevelDestination
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.HomeUiState
import com.invision.pos.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState = uiState, onNavigate = onNavigate, modifier = modifier)
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> {
            val loadingLabel = stringResource(R.string.loading)
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = loadingLabel },
                )
            }
        }

        is HomeUiState.Ready -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.home_greeting),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = uiState.storeName,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.home_today),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCard(
                            label = stringResource(R.string.home_sales_today),
                            value = uiState.salesToday,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryCard(
                            label = stringResource(R.string.home_bills_today),
                            value = uiState.billsToday.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.home_quick_actions),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    QuickAction(
                        label = stringResource(R.string.home_action_new_bill),
                        destination = TopLevelDestination.BILLING,
                        onNavigate = onNavigate,
                    )
                    QuickAction(
                        label = stringResource(R.string.home_action_add_product),
                        destination = TopLevelDestination.INVENTORY,
                        onNavigate = onNavigate,
                    )
                    QuickAction(
                        label = stringResource(R.string.home_action_add_customer),
                        destination = TopLevelDestination.CUSTOMERS,
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    destination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    FilledTonalButton(
        onClick = { onNavigate(destination) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(
            painter = painterResource(destination.iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun HomeContentPreview() {
    InvisionTheme {
        HomeContent(
            uiState = HomeUiState.Ready(
                storeName = "My Store",
                salesToday = "₹12,450.00",
                billsToday = 38,
            ),
            onNavigate = {},
        )
    }
}
