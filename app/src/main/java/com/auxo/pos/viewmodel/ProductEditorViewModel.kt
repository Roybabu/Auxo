package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.net.Uri
import com.auxo.pos.core.photos.PhotoSaveResult
import com.auxo.pos.core.photos.ProductPhotoStore
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.PurchasingRepository
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.barcode.InternalBarcodeGenerator
import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.domain.validation.FieldError
import com.auxo.pos.domain.validation.GarmentVariants
import com.auxo.pos.domain.validation.GstChoice
import com.auxo.pos.domain.validation.ProductForm
import com.auxo.pos.domain.validation.ProductFormErrors
import com.auxo.pos.domain.validation.ProductFormValidator
import com.auxo.pos.domain.validation.ProductValidation
import com.auxo.pos.domain.validation.VariantDefaults
import com.auxo.pos.domain.validation.VariantField
import com.auxo.pos.domain.validation.VariantForm
import com.auxo.pos.domain.usecase.SaveProductUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProductEditorUiState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val form: ProductForm = ProductForm(),
    /** Errors are calculated on every change but only shown after the first save attempt. */
    val errors: ProductFormErrors = ProductFormErrors(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    /** Active suppliers to choose a preferred one from. */
    val suppliers: List<Supplier> = emptyList(),
    /** A photo is being read and resized. */
    val photoBusy: Boolean = false,
) {
    val visibleErrors: ProductFormErrors get() = if (showErrors) errors else ProductFormErrors()
}

sealed interface ProductEditorEvent {
    data class Saved(val productId: Long) : ProductEditorEvent
    data class InvalidForm(val errorCount: Int) : ProductEditorEvent
    data object SaveFailed : ProductEditorEvent

    /** The chosen picture could not be used, or there is no camera app. */
    data object PhotoFailed : ProductEditorEvent
}

class ProductEditorViewModel(
    private val productId: Long,
    private val initialBarcode: String?,
    private val repository: InventoryRepository,
    private val saveProduct: SaveProductUseCase,
    private val barcodeGenerator: InternalBarcodeGenerator,
    private val purchasing: PurchasingRepository? = null,
    private val photos: ProductPhotoStore? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductEditorUiState(isLoading = productId != 0L))
    val uiState: StateFlow<ProductEditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ProductEditorEvent>(Channel.BUFFERED)
    val events: Flow<ProductEditorEvent> = eventChannel.receiveAsFlow()

    private var originalVariants: List<ProductVariant> = emptyList()
    private var keyCounter = 0

    /** The photo the product had when it was opened. It is deleted only once a different photo is saved. */
    private var originalPhotoFile: String? = null

    /** A photo chosen in this editor that is not saved with the product yet; deleted if the editor is left unsaved. */
    private var pendingPhotoFile: String? = null
    private var savedProduct = false

    init {
        if (productId != 0L) {
            load()
        } else {
            val category = if (initialBarcode != null) ProductCategory.PACKAGED_FOOD else ProductCategory.GARMENT
            setForm(newForm(category))
        }
        purchasing?.let { repository ->
            viewModelScope.launch {
                repository.observeSuppliers()
                    .catch { emit(emptyList()) }
                    .collect { suppliers -> _uiState.update { it.copy(suppliers = suppliers.filter(Supplier::active)) } }
            }
        }
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val product = runCatching { repository.getProduct(productId) }.getOrNull()
            if (product == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            } else {
                originalVariants = product.variants
                originalPhotoFile = product.photoFile
                _uiState.update { it.copy(isLoading = false) }
                setForm(ProductForm.fromProduct(product))
            }
        }
    }

    private fun newKey(): String = "n${keyCounter++}"

    private fun newForm(category: ProductCategory, base: ProductForm = ProductForm()): ProductForm {
        val variants = if (category.usesSizeAndColour) {
            GarmentVariants.sync(base.sizes, base.colours, emptyList(), base.defaults, ::newKey)
        } else {
            listOf(VariantForm(key = newKey(), unit = category.defaultUnit, barcode = initialBarcode.orEmpty()))
        }
        val gst = when {
            base.gst is GstChoice.Apparel && category != ProductCategory.GARMENT -> GstChoice.NotSet
            base.gst is GstChoice.NotSet -> GstChoice.defaultFor(category)
            else -> base.gst
        }
        return base.copy(
            category = category,
            variants = variants,
            dietType = base.dietType.takeIf { category == ProductCategory.PACKAGED_FOOD },
            gst = if (category.isSold) gst else GstChoice.NotSet,
            stockUnit = base.stockUnit.takeIf { it in category.allowedStockUnits } ?: category.allowedStockUnits.first(),
        )
    }

    private fun setForm(form: ProductForm) {
        val errors = when (val result = ProductFormValidator.validate(form, originalVariants)) {
            is ProductValidation.Invalid -> result.errors
            is ProductValidation.Valid -> ProductFormErrors()
        }
        _uiState.update { it.copy(form = form, errors = errors) }
    }

    private fun editForm(transform: (ProductForm) -> ProductForm) = setForm(transform(_uiState.value.form))

    // Product details

    fun onCategoryChange(category: ProductCategory) {
        val form = _uiState.value.form
        if (!form.isNew || form.category == category) return
        setForm(newForm(category, form))
    }

    fun onNameChange(value: String) = editForm { it.copy(name = value.take(ProductFormValidator.NAME_MAX + 20)) }

    fun onBrandChange(value: String) = editForm { it.copy(brand = value.take(ProductFormValidator.BRAND_MAX + 20)) }

    fun onDescriptionChange(value: String) =
        editForm { it.copy(description = value.take(ProductFormValidator.DESCRIPTION_MAX + 50)) }

    fun onMaterialChange(value: String) = editForm { it.copy(material = value.take(ProductFormValidator.MATERIAL_MAX + 20)) }

    fun onDietTypeChange(dietType: DietType?) = editForm { it.copy(dietType = dietType) }

    fun onGstChoiceChange(choice: GstChoice) = editForm { it.copy(gst = choice) }

    fun onCustomGstRateChange(value: String) = editForm { it.copy(customGstRate = value.filter { c -> c.isDigit() || c == '.' }.take(6)) }

    fun onHsnChange(value: String) = editForm { it.copy(hsnCode = value.filter { c -> c.isDigit() }.take(8)) }

    /** Ingredients can be counted by weight, volume or piece; fixed once the product is saved. */
    fun onStockUnitChange(unit: StockUnit) = editForm { form ->
        if (!form.isNew || unit !in form.category.allowedStockUnits) form else form.copy(stockUnit = unit)
    }

    fun onPreferredSupplierChange(supplierId: Long?) = editForm { it.copy(preferredSupplierId = supplierId) }

    // Photo

    /** Reads, upright-turns, shrinks and stores the picture at [source], and makes it the product's photo. */
    fun onPhotoPicked(source: Uri) {
        val store = photos ?: return
        if (_uiState.value.photoBusy) return
        _uiState.update { it.copy(photoBusy = true) }
        viewModelScope.launch {
            when (val result = store.save(source)) {
                is PhotoSaveResult.Saved -> {
                    // A photo chosen earlier in this editor and never saved is replaced, so it must not pile up.
                    pendingPhotoFile?.let(store::delete)
                    pendingPhotoFile = result.fileName
                    editForm { it.copy(photoFile = result.fileName) }
                }
                PhotoSaveResult.Unreadable -> eventChannel.send(ProductEditorEvent.PhotoFailed)
            }
            _uiState.update { it.copy(photoBusy = false) }
        }
    }

    /** The camera could not be opened. */
    fun onPhotoUnavailable() {
        eventChannel.trySend(ProductEditorEvent.PhotoFailed)
    }

    /** Takes the photo off the product. The saved file is deleted only when the product is saved. */
    fun onPhotoRemoved() {
        pendingPhotoFile?.let { photos?.delete(it) }
        pendingPhotoFile = null
        editForm { it.copy(photoFile = null) }
    }

    override fun onCleared() {
        if (!savedProduct) pendingPhotoFile?.let { photos?.delete(it) }
    }

    // Garment sizes and colours

    fun toggleSize(size: String) = editForm { form ->
        val sizes = if (form.sizes.any { it.equals(size, ignoreCase = true) }) {
            form.sizes.filterNot { it.equals(size, ignoreCase = true) }
        } else {
            orderSizes(form.sizes + size)
        }
        resyncGarment(form.copy(sizes = sizes))
    }

    fun addColour(colour: String) {
        val value = colour.trim().take(ProductFormValidator.COLOUR_MAX)
        if (value.isEmpty()) return
        editForm { form ->
            if (form.colours.any { it.equals(value, ignoreCase = true) }) {
                form
            } else {
                resyncGarment(form.copy(colours = form.colours + value))
            }
        }
    }

    fun removeColour(colour: String) = editForm { form ->
        resyncGarment(form.copy(colours = form.colours.filterNot { it == colour }))
    }

    fun onDefaultsChange(defaults: VariantDefaults) = editForm { it.copy(defaults = defaults) }

    fun applyDefaultsToAll() = editForm { it.copy(variants = GarmentVariants.applyDefaults(it.variants, it.defaults)) }

    private fun resyncGarment(form: ProductForm): ProductForm {
        val variants = GarmentVariants.sync(form.sizes, form.colours, form.variants, form.defaults, ::newKey)
        val withBarcode = if (initialBarcode != null && variants.none { it.barcode == initialBarcode }) {
            variants.mapIndexed { index, row -> if (index == 0 && row.barcode.isBlank()) row.copy(barcode = initialBarcode) else row }
        } else {
            variants
        }
        return form.copy(variants = withBarcode)
    }

    /** Preset sizes keep their usual order; custom sizes follow in the order added. */
    private fun orderSizes(sizes: List<String>): List<String> {
        val presets = GarmentVariants.PRESET_SIZES
        val (known, custom) = sizes.distinctBy { it.lowercase() }.partition { size ->
            presets.any { it.equals(size, ignoreCase = true) }
        }
        return known.sortedBy { size -> presets.indexOfFirst { it.equals(size, ignoreCase = true) } } + custom
    }

    // Variants

    fun addPackSize() = editForm { form ->
        form.copy(variants = form.variants + VariantForm(key = newKey(), unit = form.category.defaultUnit))
    }

    fun removeVariant(key: String) = editForm { form -> form.copy(variants = form.variants.filterNot { it.key == key }) }

    fun updateVariant(key: String, transform: (VariantForm) -> VariantForm) = editForm { form ->
        form.copy(variants = form.variants.map { if (it.key == key) transform(it) else it })
    }

    fun generateBarcode(key: String) = updateVariant(key) { it.copy(barcode = barcodeGenerator.next()) }

    fun onBarcodeScanned(key: String, value: String) = updateVariant(key) { it.copy(barcode = value.trim()) }

    fun onExpiryChange(key: String, date: LocalDate?) = updateVariant(key) { it.copy(expiryDate = date) }

    // Saving

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        val product = when (val result = ProductFormValidator.validate(state.form, originalVariants)) {
            is ProductValidation.Invalid -> {
                _uiState.update { it.copy(errors = result.errors, showErrors = true) }
                eventChannel.trySend(ProductEditorEvent.InvalidForm(result.errors.errorCount))
                return
            }
            is ProductValidation.Valid -> result.product
        }

        _uiState.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            val result = runCatching { saveProduct(product) }.getOrNull()
            when (result) {
                is SaveProductResult.Saved -> {
                    savedProduct = true
                    // The photo that was replaced or removed is no longer wanted.
                    originalPhotoFile?.takeIf { it != product.photoFile }?.let { photos?.delete(it) }
                    originalPhotoFile = product.photoFile
                    pendingPhotoFile = null
                    _uiState.update { it.copy(isSaving = false) }
                    eventChannel.send(ProductEditorEvent.Saved(result.productId))
                }
                is SaveProductResult.Conflict -> {
                    val errors = conflictErrors(state.form, result)
                    _uiState.update { it.copy(isSaving = false, errors = errors, showErrors = true) }
                    eventChannel.send(ProductEditorEvent.InvalidForm(errors.errorCount))
                }
                null -> {
                    _uiState.update { it.copy(isSaving = false) }
                    eventChannel.send(ProductEditorEvent.SaveFailed)
                }
            }
        }
    }

    private fun conflictErrors(form: ProductForm, conflict: SaveProductResult.Conflict): ProductFormErrors {
        val variantErrors = form.variants.associate { row ->
            val errors = buildMap {
                if (row.barcode.trim() in conflict.barcodesInUse) put(VariantField.BARCODE, FieldError.BARCODE_IN_USE)
                if (row.sku.trim() in conflict.skusInUse) put(VariantField.SKU, FieldError.SKU_IN_USE)
            }
            row.key to errors
        }.filterValues { it.isNotEmpty() }
        return ProductFormErrors(variants = variantErrors)
    }

    companion object {
        const val ARG_PRODUCT_ID = "productId"
        const val ARG_BARCODE = "barcode"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val handle = createSavedStateHandle()
                ProductEditorViewModel(
                    productId = handle.get<Long>(ARG_PRODUCT_ID) ?: 0L,
                    initialBarcode = handle.get<String>(ARG_BARCODE)?.takeIf { it.isNotBlank() },
                    repository = container.inventoryRepository,
                    saveProduct = container.saveProduct,
                    barcodeGenerator = container.barcodeGenerator,
                    purchasing = container.purchasingRepository,
                    photos = container.productPhotos,
                )
            }
        }
    }
}
