package com.invision.pos.ui.screens.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.model.DietType
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.validation.FieldError
import com.invision.pos.domain.validation.FormField
import com.invision.pos.domain.validation.GarmentVariants
import com.invision.pos.domain.validation.ProductForm
import com.invision.pos.domain.validation.ProductFormErrors
import com.invision.pos.domain.validation.VariantDefaults
import com.invision.pos.domain.validation.VariantField
import com.invision.pos.domain.validation.VariantForm
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.ScanOutcome
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.components.rememberBarcodeScanner
import com.invision.pos.ui.format.DateFormats
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.ProductEditorEvent
import com.invision.pos.viewmodel.ProductEditorUiState
import com.invision.pos.viewmodel.ProductEditorViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Callbacks from the editor UI, so the content can be previewed and tested without a ViewModel. */
interface ProductEditorActions {
    fun onCategoryChange(category: ProductCategory)
    fun onNameChange(value: String)
    fun onBrandChange(value: String)
    fun onDescriptionChange(value: String)
    fun onMaterialChange(value: String)
    fun onDietTypeChange(dietType: DietType?)
    fun onToggleSize(size: String)
    fun onAddColour(colour: String)
    fun onRemoveColour(colour: String)
    fun onDefaultsChange(defaults: VariantDefaults)
    fun onApplyDefaults()
    fun onAddPackSize()
    fun onRemoveVariant(key: String)
    fun onUpdateVariant(key: String, transform: (VariantForm) -> VariantForm)
    fun onGenerateBarcode(key: String)
    fun onScanBarcode(key: String)
    fun onPickExpiry(key: String)
    fun onSave()
}

@Composable
fun ProductEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: ProductEditorViewModel = viewModel(factory = ProductEditorViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val currentOnSaved by rememberUpdatedState(onSaved)
    var scanTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var expiryTarget by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProductEditorEvent.Saved -> currentOnSaved(event.productId)
                is ProductEditorEvent.InvalidForm -> snackbarHostState.showSnackbar(
                    resources.getQuantityString(R.plurals.editor_fix_errors, event.errorCount, event.errorCount),
                )
                ProductEditorEvent.SaveFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.error_generic))
            }
        }
    }

    val scan = rememberBarcodeScanner { outcome ->
        val key = scanTarget
        scanTarget = null
        when (outcome) {
            is ScanOutcome.Scanned -> if (key != null) viewModel.onBarcodeScanned(key, outcome.value)
            ScanOutcome.Unavailable -> Unit
            ScanOutcome.Cancelled -> Unit
        }
    }

    val actions = remember(viewModel) {
        object : ProductEditorActions {
            override fun onCategoryChange(category: ProductCategory) = viewModel.onCategoryChange(category)
            override fun onNameChange(value: String) = viewModel.onNameChange(value)
            override fun onBrandChange(value: String) = viewModel.onBrandChange(value)
            override fun onDescriptionChange(value: String) = viewModel.onDescriptionChange(value)
            override fun onMaterialChange(value: String) = viewModel.onMaterialChange(value)
            override fun onDietTypeChange(dietType: DietType?) = viewModel.onDietTypeChange(dietType)
            override fun onToggleSize(size: String) = viewModel.toggleSize(size)
            override fun onAddColour(colour: String) = viewModel.addColour(colour)
            override fun onRemoveColour(colour: String) = viewModel.removeColour(colour)
            override fun onDefaultsChange(defaults: VariantDefaults) = viewModel.onDefaultsChange(defaults)
            override fun onApplyDefaults() = viewModel.applyDefaultsToAll()
            override fun onAddPackSize() = viewModel.addPackSize()
            override fun onRemoveVariant(key: String) = viewModel.removeVariant(key)
            override fun onUpdateVariant(key: String, transform: (VariantForm) -> VariantForm) =
                viewModel.updateVariant(key, transform)
            override fun onGenerateBarcode(key: String) = viewModel.generateBarcode(key)
            override fun onScanBarcode(key: String) {
                scanTarget = key
                scan()
            }
            override fun onPickExpiry(key: String) {
                expiryTarget = key
            }
            override fun onSave() = viewModel.save()
        }
    }

    Scaffold(
        topBar = {
            InvisionTopAppBar(
                title = stringResource(if (uiState.form.isNew) R.string.editor_title_new else R.string.editor_title_edit),
                onBack = onBack,
                actions = {
                    TextButton(onClick = actions::onSave, enabled = !uiState.isSaving && !uiState.isLoading) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            uiState.isLoading -> LoadingState(contentModifier)
            uiState.loadFailed -> ErrorState(
                message = stringResource(R.string.error_generic),
                onRetry = viewModel::retry,
                modifier = contentModifier,
            )
            else -> ProductEditorContent(uiState = uiState, actions = actions, modifier = contentModifier)
        }
    }

    val dateKey = expiryTarget
    if (dateKey != null) {
        val current = uiState.form.variants.firstOrNull { it.key == dateKey }?.expiryDate
        ExpiryDatePickerDialog(
            initial = current,
            onConfirm = { date ->
                viewModel.onExpiryChange(dateKey, date)
                expiryTarget = null
            },
            onDismiss = { expiryTarget = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductEditorContent(
    uiState: ProductEditorUiState,
    actions: ProductEditorActions,
    modifier: Modifier = Modifier,
) {
    val form = uiState.form
    val errors = uiState.visibleErrors
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("product_editor"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (form.isNew) {
            item(key = "category") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(stringResource(R.string.editor_category))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ProductCategory.entries.forEach { category ->
                            FilterChip(
                                selected = form.category == category,
                                onClick = { actions.onCategoryChange(category) },
                                label = { Text(stringResource(category.labelRes())) },
                                leadingIcon = {
                                    Icon(painterResource(category.iconRes()), contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "details") { DetailsSection(form, errors, actions) }

        if (form.category.usesSizeAndColour) {
            item(key = "sizes") { SizesSection(form, errors, actions) }
            item(key = "colours") { ColoursSection(form, errors, actions) }
            item(key = "defaults") { DefaultsSection(form.defaults, actions) }
        }

        item(key = "variants_header") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionHeader(stringResource(R.string.editor_variants))
                errors.fields[FormField.VARIANTS]?.let { error ->
                    Text(
                        text = stringResource(
                            if (error == FieldError.REQUIRED) R.string.error_variants_required else error.messageRes(),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        items(form.variants, key = { it.key }) { row ->
            VariantEditorCard(
                row = row,
                index = form.variants.indexOf(row),
                category = form.category,
                errors = errors.forVariant(row.key),
                canRemove = !form.category.usesSizeAndColour && form.variants.size > 1,
                actions = actions,
            )
        }

        if (!form.category.usesSizeAndColour) {
            item(key = "add_pack") {
                OutlinedButton(onClick = actions::onAddPackSize, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.editor_add_pack), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }

        item(key = "save") {
            Button(
                onClick = actions::onSave,
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("editor_save"),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSection(form: ProductForm, errors: ProductFormErrors, actions: ProductEditorActions) {
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.editor_details))
            InvisionTextField(
                value = form.name,
                onValueChange = actions::onNameChange,
                label = stringResource(R.string.editor_name),
                error = errors.fields[FormField.NAME]?.let { stringResource(it.messageRes()) },
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_name"),
            )
            InvisionTextField(
                value = form.brand,
                onValueChange = actions::onBrandChange,
                label = stringResource(R.string.editor_brand),
                error = errors.fields[FormField.BRAND]?.let { stringResource(it.messageRes()) },
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.category == ProductCategory.GARMENT) {
                InvisionTextField(
                    value = form.material,
                    onValueChange = actions::onMaterialChange,
                    label = stringResource(R.string.editor_material),
                    error = errors.fields[FormField.MATERIAL]?.let { stringResource(it.messageRes()) },
                    capitalization = KeyboardCapitalization.Sentences,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (form.category == ProductCategory.PACKAGED_FOOD) {
                Text(stringResource(R.string.editor_diet), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(null, DietType.VEG, DietType.NON_VEG).forEach { diet ->
                        FilterChip(
                            selected = form.dietType == diet,
                            onClick = { actions.onDietTypeChange(diet) },
                            label = { Text(stringResource(diet.labelRes())) },
                        )
                    }
                }
            }
            InvisionTextField(
                value = form.description,
                onValueChange = actions::onDescriptionChange,
                label = stringResource(R.string.editor_description),
                error = errors.fields[FormField.DESCRIPTION]?.let { stringResource(it.messageRes()) },
                capitalization = KeyboardCapitalization.Sentences,
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizesSection(form: ProductForm, errors: ProductFormErrors, actions: ProductEditorActions) {
    var customSize by rememberSaveable { mutableStateOf("") }
    val customSizes = form.sizes.filterNot { size -> GarmentVariants.PRESET_SIZES.any { it.equals(size, ignoreCase = true) } }
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(stringResource(R.string.editor_sizes))
            Text(
                text = stringResource(R.string.editor_garment_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                (GarmentVariants.PRESET_SIZES + customSizes).forEach { size ->
                    FilterChip(
                        selected = form.sizes.any { it.equals(size, ignoreCase = true) },
                        onClick = { actions.onToggleSize(size) },
                        label = { Text(size) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InvisionTextField(
                    value = customSize,
                    onValueChange = { customSize = it.take(12) },
                    label = stringResource(R.string.editor_custom_size),
                    capitalization = KeyboardCapitalization.Characters,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        if (customSize.isNotBlank()) {
                            if (form.sizes.none { it.equals(customSize.trim(), ignoreCase = true) }) {
                                actions.onToggleSize(customSize.trim())
                            }
                            customSize = ""
                        }
                    },
                ) { Text(stringResource(R.string.action_add)) }
            }
            if (errors.fields[FormField.SIZES] != null) {
                Text(
                    text = stringResource(R.string.error_sizes_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColoursSection(form: ProductForm, errors: ProductFormErrors, actions: ProductEditorActions) {
    var colour by rememberSaveable { mutableStateOf("") }
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(stringResource(R.string.editor_colours))
            if (form.colours.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    form.colours.forEach { name ->
                        FilterChip(
                            selected = true,
                            onClick = { actions.onRemoveColour(name) },
                            label = { Text(name) },
                            trailingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.action_remove),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InvisionTextField(
                    value = colour,
                    onValueChange = { colour = it.take(30) },
                    label = stringResource(R.string.editor_colour_hint),
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_colour"),
                )
                OutlinedButton(
                    onClick = {
                        actions.onAddColour(colour)
                        colour = ""
                    },
                    modifier = Modifier.testTag("editor_add_colour"),
                ) { Text(stringResource(R.string.action_add)) }
            }
            if (errors.fields[FormField.COLOURS] != null) {
                Text(
                    text = stringResource(R.string.error_colours_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DefaultsSection(defaults: VariantDefaults, actions: ProductEditorActions) {
    val currency = stringResource(R.string.currency_prefix)
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.editor_defaults))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InvisionTextField(
                    value = defaults.sellingPrice,
                    onValueChange = { actions.onDefaultsChange(defaults.copy(sellingPrice = sanitizeDecimal(it))) },
                    label = stringResource(R.string.field_selling_price),
                    prefix = currency,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                InvisionTextField(
                    value = defaults.mrp,
                    onValueChange = { actions.onDefaultsChange(defaults.copy(mrp = sanitizeDecimal(it))) },
                    label = stringResource(R.string.field_mrp),
                    prefix = currency,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InvisionTextField(
                    value = defaults.costPrice,
                    onValueChange = { actions.onDefaultsChange(defaults.copy(costPrice = sanitizeDecimal(it))) },
                    label = stringResource(R.string.field_cost_price),
                    prefix = currency,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                InvisionTextField(
                    value = defaults.openingStock,
                    onValueChange = { actions.onDefaultsChange(defaults.copy(openingStock = sanitizeCount(it))) },
                    label = stringResource(R.string.field_opening_stock),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            InvisionTextField(
                value = defaults.lowStockThreshold,
                onValueChange = { actions.onDefaultsChange(defaults.copy(lowStockThreshold = sanitizeCount(it))) },
                label = stringResource(R.string.field_low_stock),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = actions::onApplyDefaults, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.editor_apply_defaults))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantEditorCard(
    row: VariantForm,
    index: Int,
    category: ProductCategory,
    errors: Map<VariantField, FieldError>,
    canRemove: Boolean,
    actions: ProductEditorActions,
) {
    val currency = stringResource(R.string.currency_prefix)
    val key = row.key
    @Composable
    fun errorFor(field: VariantField): String? = errors[field]?.let { stringResource(it.messageRes()) }

    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = if (category.usesSizeAndColour) {
                    listOf(row.size, row.colour).filter { it.isNotBlank() }.joinToString(" · ")
                } else {
                    stringResource(R.string.editor_pack, index + 1)
                }
                Text(text = title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = { actions.onRemoveVariant(key) }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.editor_remove_variant))
                    }
                }
            }
            errorFor(VariantField.SIZE)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (category.usesNetQuantity) {
                InvisionTextField(
                    value = row.netQuantity,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(netQuantity = sanitizeDecimal(value, 3)) } },
                    label = stringResource(R.string.field_net_quantity),
                    error = errorFor(VariantField.NET_QUANTITY) ?: errorFor(VariantField.UNIT),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    category.allowedUnits.forEach { unit ->
                        FilterChip(
                            selected = row.unit == unit,
                            onClick = { actions.onUpdateVariant(key) { it.copy(unit = unit) } },
                            label = { Text(unit.symbol) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InvisionTextField(
                    value = row.sellingPrice,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(sellingPrice = sanitizeDecimal(value)) } },
                    label = stringResource(R.string.field_selling_price),
                    prefix = currency,
                    error = errorFor(VariantField.SELLING_PRICE),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("variant_price_$index"),
                )
                InvisionTextField(
                    value = row.mrp,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(mrp = sanitizeDecimal(value)) } },
                    label = stringResource(R.string.field_mrp),
                    prefix = currency,
                    error = errorFor(VariantField.MRP),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InvisionTextField(
                    value = row.costPrice,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(costPrice = sanitizeDecimal(value)) } },
                    label = stringResource(R.string.field_cost_price),
                    prefix = currency,
                    error = errorFor(VariantField.COST_PRICE),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                InvisionTextField(
                    value = row.lowStockThreshold,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(lowStockThreshold = sanitizeCount(value)) } },
                    label = stringResource(R.string.field_low_stock),
                    error = errorFor(VariantField.LOW_STOCK),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }

            if (row.isSaved) {
                Text(
                    text = stringResource(R.string.editor_stock_locked, row.currentStock ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                InvisionTextField(
                    value = row.openingStock,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(openingStock = sanitizeCount(value)) } },
                    label = stringResource(R.string.field_opening_stock),
                    error = errorFor(VariantField.OPENING_STOCK),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            InvisionTextField(
                value = row.barcode,
                onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(barcode = value.trim().take(32)) } },
                label = stringResource(R.string.field_barcode),
                placeholder = stringResource(R.string.field_barcode_hint),
                error = errorFor(VariantField.BARCODE),
                supportingText = if (row.barcode.isBlank()) stringResource(R.string.field_barcode_hint) else null,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { actions.onScanBarcode(key) }) {
                            Icon(painterResource(R.drawable.ic_scan), contentDescription = stringResource(R.string.action_scan))
                        }
                        IconButton(onClick = { actions.onGenerateBarcode(key) }) {
                            Icon(
                                painterResource(R.drawable.ic_autorenew),
                                contentDescription = stringResource(R.string.action_generate_barcode),
                            )
                        }
                    }
                },
            )
            InvisionTextField(
                value = row.sku,
                onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(sku = value.trim().take(32)) } },
                label = stringResource(R.string.field_sku),
                error = errorFor(VariantField.SKU),
                capitalization = KeyboardCapitalization.Characters,
                modifier = Modifier.fillMaxWidth(),
            )

            if (category.tracksExpiry) {
                InvisionTextField(
                    value = row.batchNumber,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(batchNumber = value.take(32)) } },
                    label = stringResource(R.string.field_batch),
                    error = errorFor(VariantField.BATCH),
                    capitalization = KeyboardCapitalization.Characters,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.field_expiry), style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = row.expiryDate?.let(DateFormats::date) ?: stringResource(R.string.field_expiry_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (row.expiryDate != null) {
                        TextButton(onClick = { actions.onUpdateVariant(key) { it.copy(expiryDate = null) } }) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                    OutlinedButton(onClick = { actions.onPickExpiry(key) }) {
                        Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.action_pick_date), modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDatePickerDialog(
    initial: LocalDate?,
    onConfirm: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val date = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    onConfirm(date)
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/** Keeps digits and one decimal point, with at most [decimals] digits after it. */
internal fun sanitizeDecimal(text: String, decimals: Int = 2): String {
    val filtered = text.filter { it.isDigit() || it == '.' }
    val point = filtered.indexOf('.')
    val result = if (point < 0) {
        filtered
    } else {
        filtered.substring(0, point + 1) + filtered.substring(point + 1).replace(".", "").take(decimals)
    }
    return result.take(12)
}

internal fun sanitizeCount(text: String): String = text.filter { it.isDigit() }.take(7)
