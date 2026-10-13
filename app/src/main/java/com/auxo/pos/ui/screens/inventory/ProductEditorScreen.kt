package com.auxo.pos.ui.screens.inventory

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.auxo.pos.ui.components.ProductPhoto
import com.auxo.pos.ui.visibleCategories
import java.io.File
import java.io.IOException
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.auxo.pos.R
import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.domain.validation.FieldError
import com.auxo.pos.domain.validation.FormField
import com.auxo.pos.domain.validation.GarmentVariants
import com.auxo.pos.domain.validation.GstChoice
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.formatRatePercent
import com.auxo.pos.domain.validation.ProductForm
import com.auxo.pos.domain.validation.ProductFormErrors
import com.auxo.pos.domain.validation.VariantDefaults
import com.auxo.pos.domain.validation.VariantField
import com.auxo.pos.domain.validation.VariantForm
import com.auxo.pos.ui.LocalSuppliersEnabled
import com.auxo.pos.ui.components.DatePickerSheet
import com.auxo.pos.ui.components.DropdownField
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.ScanOutcome
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.components.rememberBarcodeScanner
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ProductEditorEvent
import com.auxo.pos.viewmodel.ProductEditorUiState
import com.auxo.pos.viewmodel.ProductEditorViewModel

/** Callbacks from the editor UI, so the content can be previewed and tested without a ViewModel. */
interface ProductEditorActions {
    fun onCategoryChange(category: ProductCategory)
    fun onNameChange(value: String)
    fun onBrandChange(value: String)
    fun onDescriptionChange(value: String)
    fun onMaterialChange(value: String)
    fun onDietTypeChange(dietType: DietType?)
    fun onSubcategoryChange(value: String) = Unit
    fun onAvailableChange(available: Boolean) = Unit
    fun onTracksStockChange(tracks: Boolean) = Unit
    fun onGstChoiceChange(choice: GstChoice) = Unit
    fun onCustomGstRateChange(value: String) = Unit
    fun onHsnChange(value: String) = Unit
    fun onStockUnitChange(unit: StockUnit) = Unit
    fun onPreferredSupplierChange(supplierId: Long?) = Unit
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
    fun onTakePhoto() = Unit
    fun onChoosePhoto() = Unit
    fun onRemovePhoto() = Unit
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
                ProductEditorEvent.PhotoFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.editor_photo_failed))
            }
        }
    }

    val context = LocalContext.current
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val target = captureUri
        captureUri = null
        if (taken && target != null) viewModel.onPhotoPicked(Uri.parse(target))
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
            override fun onSubcategoryChange(value: String) = viewModel.onSubcategoryChange(value)
            override fun onAvailableChange(available: Boolean) = viewModel.onAvailableChange(available)
            override fun onTracksStockChange(tracks: Boolean) = viewModel.onTracksStockChange(tracks)
            override fun onGstChoiceChange(choice: GstChoice) = viewModel.onGstChoiceChange(choice)
            override fun onCustomGstRateChange(value: String) = viewModel.onCustomGstRateChange(value)
            override fun onHsnChange(value: String) = viewModel.onHsnChange(value)
            override fun onStockUnitChange(unit: StockUnit) = viewModel.onStockUnitChange(unit)
            override fun onPreferredSupplierChange(supplierId: Long?) = viewModel.onPreferredSupplierChange(supplierId)
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
            override fun onChoosePhoto() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            override fun onRemovePhoto() = viewModel.onPhotoRemoved()
            override fun onTakePhoto() {
                try {
                    // The camera app writes the picture into this file through a one-time permission to its address.
                    val file = File(File(context.cacheDir, "capture").apply { mkdirs() }, "photo.jpg").apply { createNewFile() }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    captureUri = uri.toString()
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    captureUri = null
                    viewModel.onPhotoUnavailable()
                } catch (e: IOException) {
                    captureUri = null
                    viewModel.onPhotoUnavailable()
                } catch (e: IllegalArgumentException) {
                    captureUri = null
                    viewModel.onPhotoUnavailable()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AuxoTopAppBar(
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
        DatePickerSheet(
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
    val suppliersEnabled = LocalSuppliersEnabled.current
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
                        visibleCategories().forEach { category ->
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

        item(key = "photo") { PhotoSection(form, uiState.photoBusy, actions) }
        item(key = "details") { DetailsSection(form, errors, actions) }

        if (form.category.canSkipStock) {
            item(key = "restaurant") { RestaurantSection(form, errors, actions) }
        }

        if (form.category.isSold) {
            item(key = "tax") { TaxSection(form, errors, actions) }
        }

        // Nothing to show here without suppliers unless the item can be counted in more than one unit.
        if (form.tracksStock && (suppliersEnabled || form.category.allowedStockUnits.size > 1)) {
            item(key = "purchasing") { PurchasingSection(form, uiState.suppliers, actions) }
        }

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
                unit = form.stockUnit,
                tracksStock = form.tracksStock,
                errors = errors.forVariant(row.key),
                canRemove = !form.category.usesSizeAndColour && form.variants.size > 1,
                actions = actions,
            )
        }

        if (form.category.usesNetQuantity) {
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

@Composable
private fun PhotoSection(form: ProductForm, busy: Boolean, actions: ProductEditorActions) {
    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("editor_photo")) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                ProductPhoto(
                    photoFile = form.photoFile,
                    contentDescription = form.name.ifBlank { null },
                    modifier = Modifier.fillMaxSize(),
                    maxSidePx = 400,
                ) {
                    Icon(
                        painter = painterResource(form.category.iconRes()),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(28.dp).testTag("editor_photo_busy"))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.editor_photo), style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = actions::onTakePhoto, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("editor_photo_take")) {
                    Text(stringResource(R.string.editor_photo_take))
                }
                OutlinedButton(onClick = actions::onChoosePhoto, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("editor_photo_choose")) {
                    Text(stringResource(R.string.editor_photo_choose))
                }
                if (form.photoFile != null) {
                    TextButton(onClick = actions::onRemovePhoto, enabled = !busy, modifier = Modifier.testTag("editor_photo_remove")) {
                        Text(stringResource(R.string.editor_photo_remove))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSection(form: ProductForm, errors: ProductFormErrors, actions: ProductEditorActions) {
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.editor_details))
            AuxoTextField(
                value = form.name,
                onValueChange = actions::onNameChange,
                label = stringResource(R.string.editor_name),
                error = errors.fields[FormField.NAME]?.let { stringResource(it.messageRes()) },
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_name"),
            )
            AuxoTextField(
                value = form.brand,
                onValueChange = actions::onBrandChange,
                label = stringResource(R.string.editor_brand),
                error = errors.fields[FormField.BRAND]?.let { stringResource(it.messageRes()) },
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.category == ProductCategory.GARMENT) {
                AuxoTextField(
                    value = form.material,
                    onValueChange = actions::onMaterialChange,
                    label = stringResource(R.string.editor_material),
                    error = errors.fields[FormField.MATERIAL]?.let { stringResource(it.messageRes()) },
                    capitalization = KeyboardCapitalization.Sentences,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (form.category.usesDietType) {
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
            AuxoTextField(
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

/** Menu section, whether the item can be billed, and whether it keeps a stock count. Restaurant items only. */
@Composable
private fun RestaurantSection(form: ProductForm, errors: ProductFormErrors, actions: ProductEditorActions) {
    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("editor_restaurant")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.editor_restaurant))
            AuxoTextField(
                value = form.subcategory,
                onValueChange = actions::onSubcategoryChange,
                label = stringResource(R.string.editor_subcategory),
                error = errors.fields[FormField.SUBCATEGORY]?.let { stringResource(it.messageRes()) },
                supportingText = stringResource(R.string.editor_subcategory_hint),
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_subcategory"),
            )
            SwitchRow(
                title = stringResource(R.string.editor_available),
                body = stringResource(R.string.editor_available_hint),
                checked = form.available,
                onCheckedChange = actions::onAvailableChange,
                modifier = Modifier.testTag("editor_available"),
            )
            SwitchRow(
                title = stringResource(R.string.editor_track_stock),
                body = stringResource(R.string.editor_track_stock_hint),
                checked = form.tracksStock,
                onCheckedChange = actions::onTracksStockChange,
                modifier = Modifier.testTag("editor_track_stock"),
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** How an ingredient is counted, and the supplier reorders go to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurchasingSection(form: ProductForm, suppliers: List<Supplier>, actions: ProductEditorActions) {
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.editor_purchasing))
            if (form.category.allowedStockUnits.size > 1) {
                Text(stringResource(R.string.editor_stock_unit), style = MaterialTheme.typography.labelLarge)
                if (form.isNew) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        form.category.allowedStockUnits.forEach { unit ->
                            FilterChip(
                                selected = form.stockUnit == unit,
                                onClick = { actions.onStockUnitChange(unit) },
                                label = { Text(stringResource(unit.labelRes())) },
                            )
                        }
                    }
                } else {
                    Text(stringResource(form.stockUnit.labelRes()), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = stringResource(R.string.editor_stock_unit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (LocalSuppliersEnabled.current) {
                val none = stringResource(R.string.editor_supplier_none)
                val options = listOf<Supplier?>(null) + suppliers
                DropdownField(
                    label = stringResource(R.string.editor_supplier),
                    value = suppliers.firstOrNull { it.id == form.preferredSupplierId }?.name ?: none,
                    options = options,
                    optionText = { it?.name ?: none },
                    onSelect = { actions.onPreferredSupplierChange(it?.id) },
                    supportingText = stringResource(R.string.editor_supplier_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaxSection(form: ProductForm, errors: ProductFormErrors, actions: ProductEditorActions) {
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.editor_tax))
            Text(
                text = stringResource(R.string.editor_gst_rate),
                style = MaterialTheme.typography.labelLarge,
            )
            val choices = buildList {
                add(GstChoice.NotSet)
                if (form.category == ProductCategory.GARMENT) add(GstChoice.Apparel)
                GstRate.STANDARD_BASIS_POINTS.forEach { add(GstChoice.Standard(it)) }
                add(GstChoice.Custom)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                choices.forEach { choice ->
                    FilterChip(
                        selected = form.gst == choice,
                        onClick = { actions.onGstChoiceChange(choice) },
                        label = {
                            Text(
                                when (choice) {
                                    GstChoice.NotSet -> stringResource(R.string.gst_not_set)
                                    GstChoice.Apparel -> stringResource(R.string.gst_apparel)
                                    GstChoice.Custom -> stringResource(R.string.gst_custom)
                                    is GstChoice.Standard -> formatRatePercent(choice.basisPoints) + "%"
                                },
                            )
                        },
                    )
                }
            }
            if (form.gst == GstChoice.Apparel) {
                Text(
                    text = stringResource(R.string.gst_apparel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (form.gst == GstChoice.NotSet) {
                Text(
                    text = stringResource(R.string.gst_not_set_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (form.gst == GstChoice.Custom) {
                AuxoTextField(
                    value = form.customGstRate,
                    onValueChange = actions::onCustomGstRateChange,
                    label = stringResource(R.string.gst_custom_rate),
                    error = errors.fields[FormField.GST_RATE]?.let { stringResource(it.messageRes()) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                errors.fields[FormField.GST_RATE]?.let {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            AuxoTextField(
                value = form.hsnCode,
                onValueChange = actions::onHsnChange,
                label = stringResource(R.string.editor_hsn),
                error = errors.fields[FormField.HSN]?.let { stringResource(it.messageRes()) },
                supportingText = stringResource(R.string.editor_hsn_hint),
                keyboardType = KeyboardType.Number,
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
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
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
                AuxoTextField(
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
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
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
                AuxoTextField(
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
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.editor_defaults))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AuxoTextField(
                    value = defaults.sellingPrice,
                    onValueChange = { actions.onDefaultsChange(defaults.copy(sellingPrice = sanitizeDecimal(it))) },
                    label = stringResource(R.string.field_selling_price),
                    prefix = currency,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                AuxoTextField(
                    value = defaults.mrp,
                    onValueChange = { actions.onDefaultsChange(defaults.copy(mrp = sanitizeDecimal(it))) },
                    label = stringResource(R.string.field_mrp),
                    prefix = currency,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AuxoTextField(
                    value = defaults.costPrice,
                    onValueChange = { actions.onDefaultsChange(defaults.copy(costPrice = sanitizeDecimal(it))) },
                    label = stringResource(R.string.field_cost_price),
                    prefix = currency,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                AuxoTextField(
                    value = defaults.openingStock,
                    onValueChange = { actions.onDefaultsChange(defaults.copy(openingStock = sanitizeCount(it))) },
                    label = stringResource(R.string.field_opening_stock),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            AuxoTextField(
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
    unit: StockUnit,
    tracksStock: Boolean,
    errors: Map<VariantField, FieldError>,
    canRemove: Boolean,
    actions: ProductEditorActions,
) {
    val currency = stringResource(R.string.currency_prefix)
    val key = row.key
    @Composable
    fun errorFor(field: VariantField): String? = errors[field]?.let { stringResource(it.messageRes()) }

    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = when {
                    category.usesSizeAndColour -> listOf(row.size, row.colour).filter { it.isNotBlank() }.joinToString(" · ")
                    category.usesNetQuantity -> stringResource(R.string.editor_pack, index + 1)
                    category == ProductCategory.RESTAURANT -> stringResource(R.string.editor_price_and_cost)
                    else -> stringResource(R.string.editor_stock_and_cost)
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
                AuxoTextField(
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

            if (category.isSold) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AuxoTextField(
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
                    if (category != ProductCategory.RESTAURANT) {
                        AuxoTextField(
                            value = row.mrp,
                            onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(mrp = sanitizeDecimal(value)) } },
                            label = stringResource(R.string.field_mrp),
                            prefix = currency,
                            error = errorFor(VariantField.MRP),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            // Quantities are typed in pieces, kg or L; ingredients may use up to 3 decimals.
            val quantityKeyboard = if (unit == StockUnit.UNIT) KeyboardType.Number else KeyboardType.Decimal
            fun sanitizeQuantity(text: String): String = if (unit == StockUnit.UNIT) sanitizeCount(text) else sanitizeDecimal(text, 3)
            @Composable
            fun quantityLabel(res: Int): String =
                if (unit == StockUnit.UNIT) stringResource(res) else stringResource(R.string.field_with_unit, stringResource(res), unit.entrySymbol)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AuxoTextField(
                    value = row.costPrice,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(costPrice = sanitizeDecimal(value)) } },
                    label = if (category.isSold) {
                        stringResource(R.string.field_cost_price)
                    } else {
                        stringResource(R.string.field_cost_per, unit.entrySymbol)
                    },
                    prefix = currency,
                    error = errorFor(VariantField.COST_PRICE),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("variant_cost_$index"),
                )
                if (tracksStock) {
                    AuxoTextField(
                        value = row.lowStockThreshold,
                        onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(lowStockThreshold = sanitizeQuantity(value)) } },
                        label = quantityLabel(R.string.field_low_stock),
                        error = errorFor(VariantField.LOW_STOCK),
                        keyboardType = quantityKeyboard,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (tracksStock) {
                AuxoTextField(
                    value = row.reorderQuantity,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(reorderQuantity = sanitizeQuantity(value)) } },
                    label = quantityLabel(R.string.field_reorder_quantity),
                    error = errorFor(VariantField.REORDER),
                    supportingText = stringResource(R.string.field_reorder_quantity_hint),
                    keyboardType = quantityKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!tracksStock) {
                // Nothing to count: no stock figure to enter.
            } else if (row.isSaved) {
                Text(
                    text = stringResource(R.string.editor_stock_locked, unit.format(row.currentStock ?: 0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AuxoTextField(
                    value = row.openingStock,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(openingStock = sanitizeQuantity(value)) } },
                    label = quantityLabel(R.string.field_opening_stock),
                    error = errorFor(VariantField.OPENING_STOCK),
                    keyboardType = quantityKeyboard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("variant_opening_$index"),
                )
            }

            // An item sold without a stock count needs no barcode or code; a barcode is made when it is saved.
            if (tracksStock) {
                AuxoTextField(
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
                AuxoTextField(
                    value = row.sku,
                    onValueChange = { value -> actions.onUpdateVariant(key) { it.copy(sku = value.trim().take(32)) } },
                    label = stringResource(R.string.field_sku),
                    error = errorFor(VariantField.SKU),
                    capitalization = KeyboardCapitalization.Characters,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (category.tracksExpiry && row.isSaved) {
                Text(
                    text = stringResource(if (LocalSuppliersEnabled.current) R.string.editor_batches_hint else R.string.editor_batches_hint_plain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (category.tracksExpiry) {
                AuxoTextField(
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
