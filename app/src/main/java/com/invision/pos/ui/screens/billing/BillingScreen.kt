package com.invision.pos.ui.screens.billing

import com.invision.pos.domain.billing.BusinessTime
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.CustomerIssue
import com.invision.pos.domain.billing.PaymentError
import com.invision.pos.domain.billing.IndianStates
import com.invision.pos.domain.billing.Upi
import com.invision.pos.domain.model.CustomerAccount
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.ui.components.ActionIcon
import com.invision.pos.ui.components.ConfirmDialog
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.QrCode
import com.invision.pos.ui.components.ScanOutcome
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.components.rememberBarcodeScanner
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.BillingEvent
import com.invision.pos.viewmodel.BillingStage
import com.invision.pos.viewmodel.BillingUiState
import com.invision.pos.viewmodel.BillingViewModel
import com.invision.pos.viewmodel.CustomerSaveError
import com.invision.pos.viewmodel.DiscountInput
import com.invision.pos.viewmodel.ProductSearchResult
import java.time.LocalDate

/** Callbacks from the billing UI, so the content can be tested without a ViewModel. */
interface BillingActions {
    fun onSearchTextChange(text: String) = Unit
    fun onSearchSubmitted() = Unit
    fun onAddResult(result: ProductSearchResult) = Unit
    fun onAddMenuItem(item: MenuItem) = Unit
    fun onMenuSectionChange(section: String?) = Unit
    fun onAddCustomItem(name: String, price: String, quantity: String, gstBasisPoints: Int): Boolean = false
    fun onIncrement(lineKey: Long) = Unit
    fun onDecrement(lineKey: Long) = Unit
    fun onRemove(lineKey: Long) = Unit
    fun onToggleLineDiscount(lineKey: Long) = Unit
    fun onLineDiscountChange(lineKey: Long, text: String, isPercent: Boolean) = Unit
    fun onBillDiscountChange(text: String, isPercent: Boolean) = Unit
    fun onToggleCustomer() = Unit
    fun onCustomerChange(customer: CustomerDetails) = Unit
    fun onCustomerQueryChange(query: String) = Unit
    fun onLinkCustomer(account: CustomerAccount) = Unit
    fun onUnlinkCustomer() = Unit
    fun onSaveCustomer() = Unit
    fun onPlaceOfSupplyChange(stateCode: String?) = Unit
    fun onCharge() = Unit
    fun onOpenSettings() = Unit
    fun onPaymentMethodChange(key: Int, method: PaymentMethod) = Unit
    fun onPaymentAmountChange(key: Int, amount: String) = Unit
    fun onTenderedChange(key: Int, tendered: String) = Unit
    fun onReferenceChange(key: Int, reference: String) = Unit
    fun onAddSplit() = Unit
    fun onRemovePayment(key: Int) = Unit
    fun onCompleteSale() = Unit
}

@Composable
fun BillingScreen(
    onSaleCompleted: (invoiceId: Long, changeMinor: Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddProduct: (barcode: String) -> Unit,
    viewModel: BillingViewModel = viewModel(factory = BillingViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val formatter = LocalMoneyFormatter.current
    val currentOnCompleted by rememberUpdatedState(onSaleCompleted)
    val currentOnSettings by rememberUpdatedState(onOpenSettings)
    val currentOnAddProduct by rememberUpdatedState(onAddProduct)
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BillingEvent.Completed -> currentOnCompleted(event.invoiceId, event.change.minorUnits)
                is BillingEvent.OutOfStock -> snackbarHostState.showSnackbar(resources.getString(R.string.billing_out_of_stock_message, event.name))
                is BillingEvent.StockLimit -> snackbarHostState.showSnackbar(resources.getString(R.string.billing_stock_limit, event.name, event.available))
                is BillingEvent.BarcodeNotFound -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.billing_barcode_not_found, event.barcode),
                        actionLabel = resources.getString(R.string.inventory_add_product),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) currentOnAddProduct(event.barcode)
                }
                BillingEvent.ScannerUnavailable -> snackbarHostState.showSnackbar(resources.getString(R.string.scan_unavailable))
                BillingEvent.StockChanged -> snackbarHostState.showSnackbar(resources.getString(R.string.billing_stock_changed))
                is BillingEvent.IngredientsShort -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.billing_ingredients_short, event.names.joinToString(", ")),
                    duration = SnackbarDuration.Long,
                )
                BillingEvent.CreditLimitExceeded -> snackbarHostState.showSnackbar(resources.getString(R.string.payment_error_credit_limit))
                BillingEvent.CustomerMissing -> snackbarHostState.showSnackbar(resources.getString(R.string.billing_customer_missing))
                is BillingEvent.CustomerSaved -> snackbarHostState.showSnackbar(resources.getString(R.string.billing_customer_saved, event.name))
                BillingEvent.SetupRequired -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.billing_setup_body),
                        actionLabel = resources.getString(R.string.billing_setup_action),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) currentOnSettings()
                }
                BillingEvent.FixCart -> snackbarHostState.showSnackbar(resources.getString(R.string.billing_fix_cart))
                BillingEvent.SaveFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    val scan = rememberBarcodeScanner { outcome ->
        when (outcome) {
            is ScanOutcome.Scanned -> viewModel.onBarcodeScanned(outcome.value)
            ScanOutcome.Unavailable -> viewModel.onScannerUnavailable()
            ScanOutcome.Cancelled -> Unit
        }
    }

    BackHandler(enabled = uiState.stage == BillingStage.PAYMENT) { viewModel.backToCart() }

    val actions = remember(viewModel) {
        object : BillingActions {
            override fun onSearchTextChange(text: String) = viewModel.onSearchTextChange(text)
            override fun onSearchSubmitted() = viewModel.onSearchSubmitted()
            override fun onAddResult(result: ProductSearchResult) = viewModel.addResult(result)
            override fun onAddMenuItem(item: MenuItem) = viewModel.addMenuItem(item)
            override fun onMenuSectionChange(section: String?) = viewModel.onMenuSectionChange(section)
            override fun onAddCustomItem(name: String, price: String, quantity: String, gstBasisPoints: Int) =
                viewModel.addCustomItem(name, price, quantity, gstBasisPoints)
            override fun onIncrement(lineKey: Long) = viewModel.increment(lineKey)
            override fun onDecrement(lineKey: Long) = viewModel.decrement(lineKey)
            override fun onRemove(lineKey: Long) = viewModel.removeLine(lineKey)
            override fun onToggleLineDiscount(lineKey: Long) = viewModel.toggleLineDiscountEditor(lineKey)
            override fun onLineDiscountChange(lineKey: Long, text: String, isPercent: Boolean) =
                viewModel.onLineDiscountChange(lineKey, text, isPercent)
            override fun onBillDiscountChange(text: String, isPercent: Boolean) = viewModel.onBillDiscountChange(text, isPercent)
            override fun onToggleCustomer() = viewModel.toggleCustomer()
            override fun onCustomerChange(customer: CustomerDetails) = viewModel.onCustomerChange(customer)
            override fun onCustomerQueryChange(query: String) = viewModel.onCustomerQueryChange(query)
            override fun onLinkCustomer(account: CustomerAccount) = viewModel.linkCustomer(account)
            override fun onUnlinkCustomer() = viewModel.unlinkCustomer()
            override fun onSaveCustomer() = viewModel.saveTypedCustomer()
            override fun onPlaceOfSupplyChange(stateCode: String?) = viewModel.onPlaceOfSupplyChange(stateCode)
            override fun onCharge() = viewModel.proceedToPayment()
            override fun onOpenSettings() = currentOnSettings()
            override fun onPaymentMethodChange(key: Int, method: PaymentMethod) = viewModel.onPaymentMethodChange(key, method)
            override fun onPaymentAmountChange(key: Int, amount: String) = viewModel.onPaymentAmountChange(key, amount)
            override fun onTenderedChange(key: Int, tendered: String) = viewModel.onTenderedChange(key, tendered)
            override fun onReferenceChange(key: Int, reference: String) = viewModel.onReferenceChange(key, reference)
            override fun onAddSplit() = viewModel.addSplitPayment()
            override fun onRemovePayment(key: Int) = viewModel.removePayment(key)
            override fun onCompleteSale() = viewModel.completeSale()
        }
    }

    val inPayment = uiState.stage == BillingStage.PAYMENT
    Scaffold(
        topBar = {
            InvisionTopAppBar(
                title = stringResource(if (inPayment) R.string.payment_title else R.string.billing_title),
                onBack = if (inPayment) viewModel::backToCart else null,
                actions = {
                    if (!inPayment) {
                        ActionIcon(R.drawable.ic_scan, stringResource(R.string.action_scan), scan)
                        if (!uiState.cart.isEmpty) {
                            ActionIcon(R.drawable.ic_delete, stringResource(R.string.billing_clear), { confirmClear = true })
                        }
                        ActionIcon(R.drawable.ic_history, stringResource(R.string.billing_history), onOpenHistory)
                        ActionIcon(R.drawable.ic_settings, stringResource(R.string.billing_settings), onOpenSettings)
                    }
                },
            )
        },
        bottomBar = {
            if (!uiState.cart.isEmpty) {
                ChargeBar(
                    total = formatter.format(uiState.totals.grandTotal),
                    inPayment = inPayment,
                    canComplete = uiState.paymentRemaining.isZero && uiState.paymentErrors.isEmpty(),
                    isSaving = uiState.isSaving,
                    onCharge = actions::onCharge,
                    onComplete = actions::onCompleteSale,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            uiState.loadFailed -> ErrorState(message = stringResource(R.string.error_generic), onRetry = viewModel::retry, modifier = modifier)
            uiState.settings == null || !uiState.productsLoaded -> LoadingState(modifier)
            inPayment -> PaymentContent(uiState, actions, modifier)
            else -> CartContent(uiState, actions, modifier)
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.billing_clear_title),
            message = stringResource(R.string.billing_clear_body),
            confirmLabel = stringResource(R.string.billing_clear),
            onConfirm = {
                confirmClear = false
                viewModel.clearCart()
            },
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun ChargeBar(
    total: String,
    inPayment: Boolean,
    canComplete: Boolean,
    isSaving: Boolean,
    onCharge: () -> Unit,
    onComplete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.billing_total), style = MaterialTheme.typography.labelMedium)
                Text(total, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (inPayment) {
                Button(
                    onClick = onComplete,
                    enabled = canComplete && !isSaving,
                    modifier = Modifier.testTag("payment_complete"),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.payment_complete))
                    }
                }
            } else {
                Button(onClick = onCharge, modifier = Modifier.testTag("billing_charge")) {
                    Text(stringResource(R.string.billing_charge, total))
                }
            }
        }
    }
}

// Cart

@Composable
fun CartContent(state: BillingUiState, actions: BillingActions, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    var customItemOpen by rememberSaveable { mutableStateOf(false) }
    if (customItemOpen) {
        CustomItemDialog(
            showGst = state.settings?.gstRegistered ?: true,
            onAdd = actions::onAddCustomItem,
            onDismiss = { customItemOpen = false },
        )
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("billing_cart"),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.needsSetup) {
            item(key = "setup") { SetupBanner(actions::onOpenSettings) }
        }
        item(key = "search") {
            InvisionTextField(
                value = state.searchText,
                onValueChange = actions::onSearchTextChange,
                label = stringResource(R.string.billing_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("billing_search")
                    .onPreviewKeyEvent { event ->
                        // USB and Bluetooth barcode scanners type the code and then press Enter.
                        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                            if (event.type == KeyEventType.KeyDown) actions.onSearchSubmitted()
                            true
                        } else {
                            false
                        }
                    },
                imeAction = ImeAction.Search,
                keyboardActions = KeyboardActions(onSearch = { actions.onSearchSubmitted() }),
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = if (state.searchText.isNotEmpty()) {
                    {
                        IconButton(onClick = { actions.onSearchTextChange("") }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                } else {
                    null
                },
            )
        }

        item(key = "custom_item") {
            OutlinedButton(onClick = { customItemOpen = true }, modifier = Modifier.fillMaxWidth().testTag("billing_custom_item")) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.custom_item_button), modifier = Modifier.padding(start = Spacing.sm))
            }
        }

        if (state.searchText.isNotBlank()) {
            if (state.menuResults.isNotEmpty()) {
                item(key = "menu_results") {
                    InvisionCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            state.menuResults.forEachIndexed { index, dish ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                MenuResultRow(dish, onClick = { actions.onAddMenuItem(dish) })
                            }
                        }
                    }
                }
            }
            if (state.searchResults.isEmpty() && state.menuResults.isEmpty()) {
                item(key = "no_results") {
                    Text(
                        text = stringResource(R.string.billing_no_results, state.searchText.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.sm),
                    )
                }
            } else if (state.searchResults.isNotEmpty()) {
                item(key = "results") {
                    InvisionCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            state.searchResults.forEachIndexed { index, result ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                SearchResultRow(result, onClick = { actions.onAddResult(result) })
                            }
                        }
                    }
                }
            }
        }

        if (state.searchText.isBlank() && state.menu.isNotEmpty()) {
            item(key = "quick_menu") { QuickMenuCard(state, actions) }
        }

        if (state.cart.isEmpty && state.searchText.isBlank() && state.bestSellers.isNotEmpty()) {
            item(key = "best_sellers") { BestSellersCard(state.bestSellers, actions) }
        }

        if (state.cart.isEmpty) {
            if (state.searchText.isBlank() && state.menu.isEmpty() && state.bestSellers.isEmpty()) {
                item(key = "empty") {
                    val noProducts = state.productCount == 0 && state.menu.isEmpty()
                    EmptyState(
                        iconRes = if (noProducts) R.drawable.ic_nav_inventory else R.drawable.ic_nav_billing,
                        title = stringResource(if (noProducts) R.string.billing_no_products_title else R.string.billing_empty_title),
                        message = stringResource(if (noProducts) R.string.billing_no_products_body else R.string.billing_empty_body),
                        modifier = Modifier.height(320.dp),
                    )
                }
            }
        } else {
            item(key = "items_header") {
                SectionHeader(title = stringResource(R.string.billing_items) + " (${state.cart.itemCount})")
            }
            items(state.cart.lines, key = { "line_${it.variantId}" }) { line ->
                // Dishes are keyed by minus their menu item id, so the key never clashes with a variant.
                val amounts = state.totals.lines.firstOrNull { it.variantId == line.variantId }
                CartLineCard(
                    name = line.productName,
                    variant = line.variantLabel,
                    unitPrice = formatter.format(line.unitPrice),
                    quantity = line.quantity,
                    lineTotal = formatter.format(amounts?.let { it.gross - it.lineDiscount } ?: (line.unitPrice * line.quantity)),
                    hasDiscount = amounts?.lineDiscount?.isZero == false,
                    issue = state.check.lineIssues[line.variantId]?.let { lineIssueText(it) },
                    discountOpen = state.editingDiscountFor == line.variantId,
                    discountInput = state.lineDiscounts[line.variantId] ?: DiscountInput(),
                    onIncrement = { actions.onIncrement(line.variantId) },
                    onDecrement = { actions.onDecrement(line.variantId) },
                    onRemove = { actions.onRemove(line.variantId) },
                    onToggleDiscount = { actions.onToggleLineDiscount(line.variantId) },
                    onDiscountChange = { text, percent -> actions.onLineDiscountChange(line.variantId, text, percent) },
                )
            }
            item(key = "bill_discount") {
                DiscountEditor(
                    label = stringResource(R.string.billing_bill_discount),
                    input = state.billDiscount,
                    error = when {
                        state.billDiscount.invalid -> stringResource(R.string.billing_discount_invalid)
                        state.check.billDiscountTooLarge -> stringResource(R.string.issue_bill_discount_too_large)
                        else -> null
                    },
                    onChange = actions::onBillDiscountChange,
                    testTag = "bill_discount",
                )
            }
            item(key = "customer") { CustomerCard(state, actions) }
            item(key = "totals") { TotalsCard(state) }
            if (state.check.totalNotPositive) {
                item(key = "total_issue") {
                    Text(
                        stringResource(R.string.issue_total_not_positive),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupBanner(onOpenSettings: () -> Unit) {
    val tone = InvisionThemeExtras.statusColors.warning
    Surface(shape = MaterialTheme.shapes.large, color = tone.container, contentColor = tone.content, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.billing_setup_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.billing_setup_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.billing_setup_action)) }
        }
    }
}

@Composable
private fun SearchResultRow(result: ProductSearchResult, onClick: () -> Unit) {
    val formatter = LocalMoneyFormatter.current
    val sellable = result.variant.sellableQuantity(LocalDate.now(BusinessTime.ZONE))
    val outOfStock = sellable <= 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !outOfStock, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.product.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(result.variant.attributesLabel.ifEmpty { null }, result.variant.barcode).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatter.format(result.variant.sellingPrice), style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (outOfStock) {
                    stringResource(R.string.billing_out_of_stock)
                } else {
                    stringResource(R.string.billing_in_stock, sellable)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (outOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MenuResultRow(item: MenuItem, onClick: () -> Unit) {
    val formatter = LocalMoneyFormatter.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.available, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.billing_menu_dish, item.section),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatter.format(item.price), style = MaterialTheme.typography.titleSmall)
            if (!item.available) {
                Text(stringResource(R.string.menu_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Restaurant menu by section, for adding dishes with one tap. */
@OptIn(ExperimentalLayoutApi::class)
/** The items that sold the most in the last 30 days, ready to tap and add to the bill. */
@Composable
private fun BestSellersCard(results: List<ProductSearchResult>, actions: BillingActions) {
    InvisionCard(modifier = Modifier.fillMaxWidth().testTag("best_sellers")) {
        Column {
            Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.sm)) {
                Text(stringResource(R.string.billing_best_sellers_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.billing_best_sellers_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            results.forEach { result ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SearchResultRow(result, onClick = { actions.onAddResult(result) })
            }
        }
    }
}

@Composable
private fun QuickMenuCard(state: BillingUiState, actions: BillingActions) {
    val formatter = LocalMoneyFormatter.current
    val sections = state.menuSections
    val section = state.menuSection?.takeIf { it in sections } ?: sections.firstOrNull() ?: return
    InvisionCard(modifier = Modifier.fillMaxWidth().testTag("quick_menu")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.billing_menu_title), style = MaterialTheme.typography.titleSmall)
            if (sections.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    sections.forEach { name ->
                        FilterChip(selected = name == section, onClick = { actions.onMenuSectionChange(name) }, label = { Text(name) })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.menu.filter { it.section == section }.forEach { dish ->
                    OutlinedButton(onClick = { actions.onAddMenuItem(dish) }, modifier = Modifier.testTag("dish_${dish.id}")) {
                        Text("${dish.name} · ${formatter.format(dish.price)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun CartLineCard(
    name: String,
    variant: String,
    unitPrice: String,
    quantity: Int,
    lineTotal: String,
    hasDiscount: Boolean,
    issue: String?,
    discountOpen: Boolean,
    discountInput: DiscountInput,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    onToggleDiscount: () -> Unit,
    onDiscountChange: (String, Boolean) -> Unit,
) {
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (variant.isNotEmpty()) {
                        Text(variant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$quantity × $unitPrice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = Spacing.sm)) {
                    Text(lineTotal, style = MaterialTheme.typography.titleSmall)
                    if (hasDiscount) {
                        Text(
                            stringResource(R.string.billing_line_discount),
                            style = MaterialTheme.typography.labelSmall,
                            color = InvisionThemeExtras.statusColors.success.accent,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrement, enabled = quantity > 1) {
                    Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.action_decrease))
                }
                Text(quantity.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                IconButton(onClick = onIncrement) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_increase))
                }
                TextButton(onClick = onToggleDiscount) { Text(stringResource(R.string.billing_line_discount)) }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onRemove) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.billing_remove_item))
                    }
                }
            }
            if (discountOpen) {
                DiscountEditor(
                    label = stringResource(R.string.billing_discount_value),
                    input = discountInput,
                    error = if (discountInput.invalid) stringResource(R.string.billing_discount_invalid) else null,
                    onChange = onDiscountChange,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
            }
            if (issue != null) {
                Text(
                    text = issue,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun DiscountEditor(
    label: String,
    input: DiscountInput,
    error: String?,
    onChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "",
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        InvisionTextField(
            value = input.text,
            onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(10), input.isPercent) },
            label = label,
            error = error,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier
                .weight(1f)
                .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        )
        FilterChip(
            selected = !input.isPercent,
            onClick = { onChange(input.text, false) },
            label = { Text(stringResource(R.string.billing_discount_amount)) },
        )
        FilterChip(
            selected = input.isPercent,
            onClick = { onChange(input.text, true) },
            label = { Text(stringResource(R.string.billing_discount_percent)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerCard(state: BillingUiState, actions: BillingActions) {
    val formatter = LocalMoneyFormatter.current
    val customer = state.cart.customer
    val account = state.account
    val issues = if (state.showErrors || customer.gstin.length >= 15 || customer.phone.filter { it.isDigit() }.length >= 10) {
        state.check.customerIssues
    } else {
        emptySet()
    }
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions::onToggleCustomer),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md)) {
                    Text(
                        text = customer.name.trim().ifEmpty { stringResource(R.string.billing_walk_in) },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val subtitle = when {
                        account != null -> stringResource(R.string.billing_account_balance, formatter.format(account.balance))
                        !state.customerExpanded -> stringResource(R.string.billing_add_customer)
                        else -> null
                    }
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    painterResource(if (state.customerExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
                    contentDescription = stringResource(R.string.billing_customer),
                )
            }
            if (state.customerExpanded) {
                if (account != null) {
                    account.customer.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    account.customer.creditLimit?.let {
                        Text(
                            stringResource(R.string.billing_account_limit, formatter.format(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = actions::onUnlinkCustomer) { Text(stringResource(R.string.billing_customer_change)) }
                } else {
                    InvisionTextField(
                        value = state.customerQuery,
                        onValueChange = { actions.onCustomerQueryChange(it.take(40)) },
                        label = stringResource(R.string.billing_customer_search),
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_search"),
                    )
                    state.customerResults.forEach { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { actions.onLinkCustomer(result) }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.customer.name, style = MaterialTheme.typography.bodyLarge)
                                result.customer.phone?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (!result.balance.isZero) {
                                Text(formatter.format(result.balance), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    InvisionTextField(
                        value = customer.name,
                        onValueChange = { actions.onCustomerChange(customer.copy(name = it.take(70))) },
                        label = stringResource(R.string.billing_customer_name),
                        error = issues.firstOrNull { it == CustomerIssue.NAME_REQUIRED_WITH_GSTIN || it == CustomerIssue.NAME_TOO_LONG }
                            ?.let { stringResource(it.messageRes()) },
                        capitalization = KeyboardCapitalization.Words,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_name"),
                    )
                    InvisionTextField(
                        value = customer.phone,
                        onValueChange = { actions.onCustomerChange(customer.copy(phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16))) },
                        label = stringResource(R.string.billing_customer_phone),
                        error = issues.firstOrNull { it == CustomerIssue.INVALID_PHONE }?.let { stringResource(it.messageRes()) },
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_phone"),
                    )
                    InvisionTextField(
                        value = customer.gstin,
                        onValueChange = { actions.onCustomerChange(customer.copy(gstin = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(15))) },
                        label = stringResource(R.string.billing_customer_gstin),
                        error = issues.firstOrNull { it == CustomerIssue.INVALID_GSTIN }?.let { stringResource(it.messageRes()) },
                        capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_gstin"),
                    )
                    if (customer.name.isNotBlank()) {
                        TextButton(onClick = actions::onSaveCustomer, modifier = Modifier.testTag("customer_save")) {
                            Text(stringResource(R.string.billing_customer_save))
                        }
                    }
                    state.customerSaveError?.let {
                        Text(stringResource(it.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                val settings = state.settings
                if (settings != null && settings.gstRegistered && settings.stateCode.isNotEmpty()) {
                    StatePicker(
                        label = stringResource(R.string.billing_place_of_supply),
                        selectedCode = state.cart.placeOfSupplyStateCode ?: settings.stateCode,
                        onSelect = actions::onPlaceOfSupplyChange,
                    )
                    if (state.cart.placeOfSupplyStateCode != null) {
                        Text(
                            stringResource(R.string.billing_interstate_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.StringRes
private fun CustomerSaveError.messageRes(): Int = when (this) {
    CustomerSaveError.NAME_REQUIRED -> R.string.billing_customer_save_name
    CustomerSaveError.INVALID_PHONE -> R.string.customer_issue_phone
    CustomerSaveError.INVALID_DETAILS -> R.string.billing_customer_save_invalid
    CustomerSaveError.PHONE_IN_USE -> R.string.customer_error_phone_in_use
    CustomerSaveError.FAILED -> R.string.error_generic
}

/** Drop-down list of Indian states and union territories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatePicker(label: String, selectedCode: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = if (selectedCode.isEmpty()) "" else IndianStates.label(selectedCode),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.settings_state_pick)) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IndianStates.ALL.forEach { state ->
                DropdownMenuItem(
                    text = { Text("${state.name} (${state.code})") },
                    onClick = {
                        onSelect(state.code)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(state: BillingUiState) {
    val formatter = LocalMoneyFormatter.current
    val totals = state.totals
    val settings = state.settings
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TotalRow(stringResource(R.string.billing_subtotal), formatter.format(totals.subtotal))
            if (!totals.discountTotal.isZero) {
                TotalRow(stringResource(R.string.billing_discount_total), "−" + formatter.format(totals.discountTotal))
            }
            if (settings?.gstRegistered == true) {
                TotalRow(stringResource(R.string.billing_taxable), formatter.format(totals.taxableTotal))
                if (!totals.igstTotal.isZero || state.cart.placeOfSupplyStateCode != null) {
                    TotalRow(stringResource(R.string.billing_igst), formatter.format(totals.igstTotal))
                } else {
                    TotalRow(stringResource(R.string.billing_cgst), formatter.format(totals.cgstTotal))
                    TotalRow(stringResource(R.string.billing_sgst), formatter.format(totals.sgstTotal))
                }
            }
            if (!totals.roundOff.isZero) {
                val sign = if (totals.roundOff > Money.ZERO) "+" else "−"
                val absolute = if (totals.roundOff > Money.ZERO) totals.roundOff else Money(-totals.roundOff.minorUnits)
                TotalRow(stringResource(R.string.billing_round_off), sign + formatter.format(absolute))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TotalRow(stringResource(R.string.billing_total), formatter.format(totals.grandTotal), emphasised = true)
            if (totals.savings > Money.ZERO) {
                Text(
                    stringResource(R.string.billing_savings, formatter.format(totals.savings)),
                    style = MaterialTheme.typography.bodySmall,
                    color = InvisionThemeExtras.statusColors.success.accent,
                )
            }
            val note = when {
                settings?.gstRegistered == false -> R.string.billing_not_registered
                settings?.pricesIncludeTax == true -> R.string.billing_prices_include_gst
                else -> null
            }
            if (note != null) {
                Text(
                    text = stringResource(note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, emphasised: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = if (emphasised) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = if (emphasised) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}

// Payment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentContent(state: BillingUiState, actions: BillingActions, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    val settings = state.settings
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("payment"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "due") {
            Column {
                Text(stringResource(R.string.payment_due), style = MaterialTheme.typography.labelLarge)
                Text(
                    formatter.format(state.totals.grandTotal),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(state.payments, key = { "payment_${it.key}" }) { draft ->
            val error = if (state.paymentShowErrors || draft.amount.isNotEmpty()) state.paymentErrors[draft.key] else null
            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.weight(1f)) {
                            state.paymentMethods.forEach { method ->
                                FilterChip(
                                    selected = draft.method == method,
                                    onClick = { actions.onPaymentMethodChange(draft.key, method) },
                                    label = { Text(stringResource(method.labelRes())) },
                                    modifier = Modifier.testTag("method_${method.name}_${draft.key}"),
                                )
                            }
                        }
                        if (state.payments.size > 1) {
                            IconButton(onClick = { actions.onRemovePayment(draft.key) }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.payment_remove))
                            }
                        }
                    }
                    InvisionTextField(
                        value = draft.amount,
                        onValueChange = { actions.onPaymentAmountChange(draft.key, it.filter { c -> c.isDigit() || c == '.' }.take(12)) },
                        label = stringResource(R.string.payment_amount),
                        prefix = stringResource(R.string.currency_prefix),
                        error = error?.takeIf { it.name.startsWith("AMOUNT") }?.let { stringResource(it.messageRes()) },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_amount_${draft.key}"),
                    )
                    when (draft.method) {
                        PaymentMethod.CASH -> CashFields(draft.amount, draft.tendered, error, onTendered = { actions.onTenderedChange(draft.key, it) })
                        PaymentMethod.UPI -> {
                            val amount = Money.parseOrNull(draft.amount)
                            if (settings != null && settings.upiId.isNotBlank() && amount != null && !amount.isZero) {
                                val uri = Upi.paymentUri(
                                    vpa = settings.upiId,
                                    payeeName = settings.upiPayeeName.ifBlank { settings.storeName },
                                    amount = amount,
                                    note = settings.storeName,
                                    reference = "",
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    QrCode(
                                        content = uri,
                                        description = stringResource(R.string.payment_upi_qr, formatter.format(amount)),
                                        modifier = Modifier.size(200.dp),
                                    )
                                    Text(stringResource(R.string.payment_upi_qr, formatter.format(amount)), style = MaterialTheme.typography.bodySmall)
                                    Text(settings.upiId, style = MaterialTheme.typography.labelMedium)
                                }
                            } else if (settings?.upiId.isNullOrBlank()) {
                                Text(
                                    stringResource(R.string.payment_upi_not_set),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            InvisionTextField(
                                value = draft.reference,
                                onValueChange = { actions.onReferenceChange(draft.key, it.filter { c -> c.isLetterOrDigit() }.take(35)) },
                                label = stringResource(R.string.payment_upi_reference),
                                error = error?.takeIf { it.name.startsWith("REFERENCE") }?.let { stringResource(it.messageRes()) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PaymentMethod.CARD -> InvisionTextField(
                            value = draft.reference,
                            onValueChange = { actions.onReferenceChange(draft.key, it.filter { c -> c.isDigit() }.take(4)) },
                            label = stringResource(R.string.payment_card_last4),
                            error = error?.takeIf { it.name.startsWith("REFERENCE") }?.let { stringResource(it.messageRes()) },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PaymentMethod.CREDIT -> {
                            val account = state.account
                            val amount = Money.parseOrNull(draft.amount) ?: Money.ZERO
                            if (account != null) {
                                Text(
                                    stringResource(R.string.payment_credit_note, account.customer.name, formatter.format(account.balance + amount)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (error == PaymentError.CREDIT_NEEDS_CUSTOMER || error == PaymentError.CREDIT_OVER_LIMIT) {
                                Text(stringResource(error.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        item(key = "split") {
            OutlinedButton(onClick = actions::onAddSplit, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.payment_add_split), modifier = Modifier.padding(start = Spacing.sm))
            }
        }
        item(key = "status") {
            val remaining = state.paymentRemaining
            val status = InvisionThemeExtras.statusColors
            val (text, tone) = when {
                remaining > Money.ZERO -> stringResource(R.string.payment_remaining, formatter.format(remaining)) to status.warning
                remaining < Money.ZERO -> stringResource(R.string.payment_overpaid, formatter.format(Money(-remaining.minorUnits))) to status.danger
                else -> stringResource(R.string.payment_balanced) to status.success
            }
            Surface(shape = MaterialTheme.shapes.medium, color = tone.container, contentColor = tone.content, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("payment_status"))
                    if (state.change > Money.ZERO) {
                        Text(stringResource(R.string.payment_change, formatter.format(state.change)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashFields(amountText: String, tendered: String, error: PaymentError?, onTendered: (String) -> Unit) {
    val formatter = LocalMoneyFormatter.current
    val amount = Money.parseOrNull(amountText)
    InvisionTextField(
        value = tendered,
        onValueChange = { onTendered(it.filter { c -> c.isDigit() || c == '.' }.take(12)) },
        label = stringResource(R.string.payment_tendered),
        prefix = stringResource(R.string.currency_prefix),
        error = error?.takeIf { it.name.startsWith("TENDERED") }?.let { stringResource(it.messageRes()) },
        keyboardType = KeyboardType.Decimal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_tendered"),
    )
    if (amount != null && !amount.isZero) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(selected = false, onClick = { onTendered(amount.toDecimalString()) }, label = { Text(stringResource(R.string.payment_exact)) })
            CashSuggestions.forAmount(amount).forEach { note ->
                FilterChip(selected = false, onClick = { onTendered(note.toDecimalString()) }, label = { Text(formatter.format(note)) })
            }
        }
    }
}

/** Likely notes a customer hands over for a cash amount, e.g. ₹347 -> ₹350, ₹400, ₹500. */
object CashSuggestions {
    private val steps = listOf(10_00L, 50_00L, 100_00L, 500_00L, 2_000_00L)

    fun forAmount(amount: Money): List<Money> =
        steps.map { step -> Money(((amount.minorUnits + step - 1) / step) * step) }
            .filter { it > amount }
            .distinct()
            .take(3)
}
