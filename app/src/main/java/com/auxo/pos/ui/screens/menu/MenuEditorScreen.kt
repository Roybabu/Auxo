package com.auxo.pos.ui.screens.menu

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.domain.menu.MenuError
import com.auxo.pos.domain.menu.MenuField
import com.auxo.pos.domain.menu.MenuItemForm
import com.auxo.pos.domain.model.DietType
import com.auxo.pos.ui.components.ConfirmDialog
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.screens.inventory.labelRes
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.MenuEditorEvent
import com.auxo.pos.viewmodel.MenuEditorUiState
import com.auxo.pos.viewmodel.MenuEditorViewModel

@StringRes
fun MenuError.messageRes(): Int = when (this) {
    MenuError.REQUIRED -> R.string.error_required
    MenuError.TOO_LONG -> R.string.error_too_long
    MenuError.INVALID_PRICE -> R.string.error_invalid_price
    MenuError.INVALID_GST_RATE -> R.string.error_invalid_gst_rate
    MenuError.INVALID_SAC -> R.string.menu_error_sac
    MenuError.RECIPE_NEEDED -> R.string.menu_error_recipe_needed
    MenuError.INVALID_QUANTITY -> R.string.adjust_error_invalid
    MenuError.DUPLICATE_INGREDIENT -> R.string.menu_error_duplicate_ingredient
}

/** Callbacks of the dish editor, so its content can be shown in tests without a ViewModel. */
interface MenuEditorActions {
    fun onFormChange(form: MenuItemForm) = Unit
    fun onDelete() = Unit
}

@Composable
fun MenuEditorScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: MenuEditorViewModel = viewModel(factory = MenuEditorViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MenuEditorEvent.Saved, MenuEditorEvent.Deleted -> onDone()
                is MenuEditorEvent.Invalid -> snackbarHostState.showSnackbar(resources.getString(R.string.form_fix_errors))
                is MenuEditorEvent.AlreadyAdded -> snackbarHostState.showSnackbar(resources.getString(R.string.menu_error_duplicate_ingredient))
                MenuEditorEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    val actions = remember(viewModel) {
        object : MenuEditorActions {
            override fun onFormChange(form: MenuItemForm) = viewModel.onFormChange(form)
            override fun onDelete() {
                confirmDelete = true
            }
        }
    }

    Scaffold(
        topBar = {
            AuxoTopAppBar(
                title = stringResource(if (uiState.isNew) R.string.menu_editor_new else R.string.menu_editor_edit),
                onBack = onBack,
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !uiState.isSaving && !uiState.isLoading && !uiState.loadFailed) {
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
            else -> MenuEditorContent(uiState, actions, modifier)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.menu_delete_title, uiState.form.name),
            message = stringResource(R.string.menu_delete_body),
            confirmLabel = stringResource(R.string.menu_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenuEditorContent(
    state: MenuEditorUiState,
    actions: MenuEditorActions,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val errors = state.visibleErrors

    @Composable
    fun errorOf(field: MenuField): String? = errors[field]?.let { stringResource(it.messageRes()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("menu_editor"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "dish") {
            AuxoCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AuxoTextField(
                        value = form.name,
                        onValueChange = { actions.onFormChange(form.copy(name = it.take(70))) },
                        label = stringResource(R.string.menu_field_name),
                        error = errorOf(MenuField.NAME),
                        capitalization = KeyboardCapitalization.Words,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("menu_name"),
                    )
                    AuxoTextField(
                        value = form.section,
                        onValueChange = { actions.onFormChange(form.copy(section = it.take(40))) },
                        label = stringResource(R.string.menu_field_section),
                        error = errorOf(MenuField.SECTION),
                        supportingText = stringResource(R.string.menu_field_section_hint),
                        capitalization = KeyboardCapitalization.Words,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("menu_section"),
                    )
                    val suggestions = state.sections.filter { it != form.section.trim() }
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            suggestions.forEach { section ->
                                FilterChip(selected = false, onClick = { actions.onFormChange(form.copy(section = section)) }, label = { Text(section) })
                            }
                        }
                    }
                    AuxoTextField(
                        value = form.description,
                        onValueChange = { actions.onFormChange(form.copy(description = it.take(220))) },
                        label = stringResource(R.string.menu_field_description),
                        error = errorOf(MenuField.DESCRIPTION),
                        capitalization = KeyboardCapitalization.Sentences,
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        AuxoTextField(
                            value = form.price,
                            onValueChange = { actions.onFormChange(form.copy(price = it.filter { c -> c.isDigit() || c == '.' }.take(12))) },
                            label = stringResource(R.string.menu_field_price),
                            error = errorOf(MenuField.PRICE),
                            prefix = stringResource(R.string.currency_prefix),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("menu_price"),
                        )
                        AuxoTextField(
                            value = form.gstRate,
                            onValueChange = { actions.onFormChange(form.copy(gstRate = it.filter { c -> c.isDigit() || c == '.' }.take(6))) },
                            label = stringResource(R.string.menu_field_gst),
                            error = errorOf(MenuField.GST_RATE),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AuxoTextField(
                        value = form.sacCode,
                        onValueChange = { actions.onFormChange(form.copy(sacCode = it.filter { c -> c.isDigit() }.take(8))) },
                        label = stringResource(R.string.menu_field_sac),
                        error = errorOf(MenuField.SAC),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.menu_field_diet), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(null, DietType.VEG, DietType.NON_VEG).forEach { diet ->
                            FilterChip(
                                selected = form.dietType == diet,
                                onClick = { actions.onFormChange(form.copy(dietType = diet)) },
                                label = { Text(stringResource(diet.labelRes())) },
                            )
                        }
                    }
                    SwitchRow(
                        label = stringResource(R.string.menu_field_available),
                        checked = form.available,
                        onCheckedChange = { actions.onFormChange(form.copy(available = it)) },
                    )
                }
            }
        }
        if (!state.isNew) {
            item(key = "delete") {
                OutlinedButton(onClick = actions::onDelete, enabled = !state.isSaving, modifier = Modifier.testTag("menu_delete")) {
                    Text(stringResource(R.string.menu_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
    tag: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}
