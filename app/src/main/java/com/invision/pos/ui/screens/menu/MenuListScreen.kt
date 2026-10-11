package com.invision.pos.ui.screens.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.model.DietType
import com.invision.pos.domain.model.formatRatePercent
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.components.StatTile
import com.invision.pos.ui.components.StatusChip
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.screens.inventory.labelRes
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.MenuDish
import com.invision.pos.viewmodel.MenuListEvent
import com.invision.pos.viewmodel.MenuListUiState
import com.invision.pos.viewmodel.MenuListViewModel

@Composable
fun MenuListScreen(
    onBack: () -> Unit,
    onOpenDish: (Long) -> Unit,
    onAddDish: () -> Unit,
    viewModel: MenuListViewModel = viewModel(factory = MenuListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MenuListEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.menu_title), onBack = onBack) },
        floatingActionButton = {
            if (uiState.dishCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onAddDish,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.menu_add)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        MenuListContent(
            state = uiState,
            onQueryChange = viewModel::onQueryChange,
            onAvailableChange = viewModel::setAvailable,
            onRetry = viewModel::retry,
            onOpenDish = onOpenDish,
            onAddDish = onAddDish,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun MenuListContent(
    state: MenuListUiState,
    onQueryChange: (String) -> Unit,
    onAvailableChange: (Long, Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenDish: (Long) -> Unit,
    onAddDish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = InvisionThemeExtras.statusColors
    when {
        state.isLoading -> LoadingState(modifier)
        state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry, modifier)
        state.dishCount == 0 -> EmptyState(
            iconRes = R.drawable.ic_menu_book,
            title = stringResource(R.string.menu_empty_title),
            message = stringResource(R.string.menu_empty_body),
            actionLabel = stringResource(R.string.menu_add),
            onAction = onAddDish,
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("menu_list"),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "tiles") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(stringResource(R.string.menu_dishes), state.dishCount.toString(), Modifier.weight(1f), iconRes = R.drawable.ic_menu_book)
                    StatTile(stringResource(R.string.menu_unavailable_count), state.unavailableCount.toString(), Modifier.weight(1f))
                    StatTile(
                        stringResource(R.string.menu_short_count),
                        state.shortCount.toString(),
                        Modifier.weight(1f),
                        tone = if (state.shortCount > 0) status.danger else null,
                    )
                }
            }
            item(key = "search") {
                InvisionTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = stringResource(R.string.menu_search),
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.sections.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.menu_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.lg),
                    )
                }
            }
            state.sections.forEach { section ->
                item(key = "section-${section.name}") {
                    SectionHeader(section.name, modifier = Modifier.padding(top = Spacing.sm))
                }
                items(section.dishes, key = { "dish-${it.item.id}" }) { dish ->
                    DishRow(dish, onClick = { onOpenDish(dish.item.id) }, onAvailableChange = { onAvailableChange(dish.item.id, it) })
                }
            }
        }
    }
}

@Composable
private fun DishRow(dish: MenuDish, onClick: () -> Unit, onAvailableChange: (Boolean) -> Unit) {
    val formatter = LocalMoneyFormatter.current
    val status = InvisionThemeExtras.statusColors
    val item = dish.item
    val availableLabel = stringResource(R.string.menu_available_for, item.name)
    InvisionCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    item.dietType?.let { diet ->
                        StatusChip(stringResource(diet.labelRes()), if (diet == DietType.VEG) status.success else status.danger)
                    }
                }
                Text(
                    text = listOfNotNull(
                        formatter.format(item.price),
                        stringResource(R.string.menu_gst, formatRatePercent(item.gstRateBasisPoints)),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    !item.available -> Text(stringResource(R.string.menu_unavailable), style = MaterialTheme.typography.bodySmall, color = status.neutral.content)
                    dish.ingredientsShort -> Text(stringResource(R.string.menu_ingredients_short), style = MaterialTheme.typography.bodySmall, color = status.danger.accent)
                    dish.servingsLeft != null -> Text(
                        pluralStringResource(R.plurals.menu_servings_left, dish.servingsLeft.coerceAtMost(9_999), dish.servingsLeft.coerceAtMost(9_999)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Switch(
                checked = item.available,
                onCheckedChange = onAvailableChange,
                modifier = Modifier
                    .semantics { contentDescription = availableLabel }
                    .testTag("dish_available_${item.id}"),
            )
        }
    }
}
