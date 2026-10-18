package com.auxo.pos.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.LocalCapabilities
import com.auxo.pos.ui.LocalStoreLogo
import com.auxo.pos.ui.navigation.TopLevelDestination
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ShopViewModel

/**
 * Home for an Employee: the shop's name and the three things an employee does. It has no sales figures, stock
 * values, charts or alerts, and it reads none of that data.
 */
@Composable
fun EmployeeHomeScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onOpenInvoices: () -> Unit,
    modifier: Modifier = Modifier,
    shopViewModel: ShopViewModel = viewModel(factory = ShopViewModel.Factory),
) {
    val shop by shopViewModel.uiState.collectAsStateWithLifecycle()
    EmployeeHomeContent(
        shopName = shop.shopName ?: stringResource(R.string.shop_name_fallback),
        onNewBill = { onNavigate(TopLevelDestination.BILLING) },
        onOpenInvoices = onOpenInvoices,
        onMore = { onNavigate(TopLevelDestination.MORE) },
        modifier = modifier,
    )
}

@Composable
fun EmployeeHomeContent(
    shopName: String,
    onNewBill: () -> Unit,
    onOpenInvoices: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val userName = LocalCapabilities.current.userName
    Box(modifier = modifier.fillMaxSize().background(AuxoThemeExtras.home.header).testTag("employee_home")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo = LocalStoreLogo.current
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (logo != null) Color.White else AuxoThemeExtras.home.avatar),
                    contentAlignment = Alignment.Center,
                ) {
                    if (logo != null) {
                        Image(
                            bitmap = logo.asImageBitmap(),
                            contentDescription = stringResource(R.string.store_logo_description),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_home),
                            contentDescription = null,
                            tint = AuxoThemeExtras.home.avatarIcon,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shopName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.employee_home_signed_in, userName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("employee_home_user"),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                QuickAction(stringResource(R.string.home_action_new_bill), R.drawable.ic_nav_billing, onNewBill, "home_action_new_bill")
                QuickAction(stringResource(R.string.employee_home_invoices), R.drawable.ic_invoice, onOpenInvoices, "home_action_invoices")
                QuickAction(stringResource(R.string.nav_more), R.drawable.ic_apps_outline, onMore, "home_action_more")
            }
        }
    }
}
