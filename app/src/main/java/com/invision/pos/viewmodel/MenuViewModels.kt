package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.data.repository.MenuRepository
import com.invision.pos.domain.inventory.PickableItem
import com.invision.pos.domain.menu.MenuError
import com.invision.pos.domain.menu.MenuField
import com.invision.pos.domain.menu.MenuItemForm
import com.invision.pos.domain.menu.MenuValidation
import com.invision.pos.domain.menu.MenuValidator
import com.invision.pos.domain.menu.RecipeLineForm
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.formatRatePercent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Products that can go into a recipe: anything except garments. */
internal fun isRecipeIngredient(product: Product): Boolean = product.category != ProductCategory.GARMENT

// Menu list

/** A dish with how many servings its ingredients allow right now. */
data class MenuDish(
    val item: MenuItem,
    /** Null when the dish doesn't take ingredients out of stock. */
    val servingsLeft: Int?,
) {
    /** Ingredient stock has run out for this dish. */
    val ingredientsShort: Boolean get() = servingsLeft == 0
}

data class MenuSection(val name: String, val dishes: List<MenuDish>)

data class MenuListUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val query: String = "",
    val sections: List<MenuSection> = emptyList(),
    val dishCount: Int = 0,
    val unavailableCount: Int = 0,
    /** Dishes that deduct ingredients but can't be made from current stock. */
    val shortCount: Int = 0,
)

sealed interface MenuListEvent {
    data object Failed : MenuListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class MenuListViewModel(
    private val menu: MenuRepository,
    private val inventory: InventoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val reload = MutableStateFlow(0)
    private val eventChannel = Channel<MenuListEvent>(Channel.BUFFERED)
    val events: Flow<MenuListEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<MenuListUiState> = reload
        .flatMapLatest {
            combine(menu.observeMenu(), inventory.observeProducts(), query) { items, products, text ->
                val dishes = items.map { MenuDish(it, servingsLeft(it, products, LocalDate.now(clock))) }
                val shown = filterDishes(dishes, text)
                MenuListUiState(
                    isLoading = false,
                    query = text,
                    sections = shown.groupBy { it.item.section }.map { (name, list) -> MenuSection(name, list) },
                    dishCount = items.size,
                    unavailableCount = items.count { !it.available },
                    shortCount = dishes.count { it.ingredientsShort },
                )
            }.catch { emit(MenuListUiState(isLoading = false, loadFailed = true)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MenuListUiState())

    fun onQueryChange(text: String) = query.update { text.take(40) }

    fun retry() = reload.update { it + 1 }

    fun setAvailable(menuItemId: Long, available: Boolean) {
        viewModelScope.launch {
            if (runCatching { menu.setAvailable(menuItemId, available) }.isFailure) eventChannel.send(MenuListEvent.Failed)
        }
    }

    companion object {
        /**
         * Whole servings that unexpired ingredient stock allows, for dishes that deduct
         * ingredients; null otherwise. A missing ingredient means none.
         */
        fun servingsLeft(item: MenuItem, products: List<Product>, today: LocalDate): Int? {
            if (!item.deductIngredients || item.recipe.isEmpty()) return null
            val stock = products.flatMap { it.variants }.associate { it.id to it.sellableQuantity(today) }
            return item.recipe.minOf { line ->
                val available = stock[line.variantId] ?: 0
                if (line.quantity <= 0) Int.MAX_VALUE else available / line.quantity
            }
        }

        fun filterDishes(dishes: List<MenuDish>, query: String): List<MenuDish> {
            val text = query.trim()
            if (text.isEmpty()) return dishes
            return dishes.filter {
                it.item.name.contains(text, ignoreCase = true) ||
                    it.item.section.contains(text, ignoreCase = true) ||
                    it.item.description?.contains(text, ignoreCase = true) == true
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                MenuListViewModel(container.menuRepository, container.inventoryRepository, container.clock)
            }
        }
    }
}

// Dish editor

data class MenuEditorUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isNew: Boolean = true,
    val form: MenuItemForm = MenuItemForm(),
    val errors: Map<MenuField, MenuError> = emptyMap(),
    val recipeErrors: Map<String, MenuError> = emptyMap(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    /** Sections already on the menu, to pick from. */
    val sections: List<String> = emptyList(),
    val picker: ItemPickerState = ItemPickerState(),
) {
    val visibleErrors: Map<MenuField, MenuError> get() = if (showErrors) errors else emptyMap()
    val visibleRecipeErrors: Map<String, MenuError> get() = if (showErrors) recipeErrors else emptyMap()
}

sealed interface MenuEditorEvent {
    data class Saved(val menuItemId: Long) : MenuEditorEvent
    data object Deleted : MenuEditorEvent
    data class Invalid(val count: Int) : MenuEditorEvent
    data class AlreadyAdded(val label: String) : MenuEditorEvent
    data object Failed : MenuEditorEvent
}

class MenuEditorViewModel(
    private val menuItemId: Long,
    private val menu: MenuRepository,
    private val inventory: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuEditorUiState(isNew = menuItemId == 0L))
    val uiState: StateFlow<MenuEditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<MenuEditorEvent>(Channel.BUFFERED)
    val events: Flow<MenuEditorEvent> = eventChannel.receiveAsFlow()

    private var existing: MenuItem? = null
    private var products: List<Product> = emptyList()
    private var nextKey = 0
    private var jobs: List<Job> = emptyList()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        jobs.forEach { it.cancel() }
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        val loadJob = viewModelScope.launch {
            val loaded = runCatching {
                val catalogue = inventory.observeProducts().first()
                val dishes = menu.observeMenu().first()
                val item = if (menuItemId != 0L) menu.getItem(menuItemId) else null
                Triple(catalogue, dishes, item)
            }.getOrNull()
            if (loaded == null || (menuItemId != 0L && loaded.third == null)) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            val (catalogue, dishes, item) = loaded
            products = catalogue
            existing = item
            val sections = dishes.map { it.section }.distinct()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    sections = sections,
                    form = if (item == null) state.form else formFor(item, catalogue),
                )
            }
            revalidate()
            // Keep the ingredient picker current.
            inventory.observeProducts().catch { }.collect { list ->
                products = list
                _uiState.update { it.copy(picker = it.picker.copy(results = pickItems(list, it.picker.query, ::isRecipeIngredient))) }
            }
        }
        jobs = listOf(loadJob)
    }

    private fun formFor(item: MenuItem, catalogue: List<Product>): MenuItemForm {
        return MenuItemForm(
            name = item.name,
            section = item.section,
            description = item.description.orEmpty(),
            price = item.price.toDecimalString(),
            gstRate = formatRatePercent(item.gstRateBasisPoints),
            sacCode = item.sacCode.orEmpty(),
            dietType = item.dietType,
            available = item.available,
            // Recipes are no longer part of a dish; saving a dish clears any old one.
            deductIngredients = false,
            recipe = emptyList(),
        )
    }

    private fun newKey(): String = "ingredient-${nextKey++}"

    fun onFormChange(form: MenuItemForm) {
        _uiState.update { it.copy(form = form) }
        revalidate()
    }

    fun onIngredientQueryChange(query: String) = _uiState.update {
        it.copy(picker = ItemPickerState(query.take(40), pickItems(products, query, ::isRecipeIngredient)))
    }

    fun addIngredient(item: PickableItem) {
        val form = _uiState.value.form
        if (form.recipe.any { it.variantId == item.variant.id }) {
            eventChannel.trySend(MenuEditorEvent.AlreadyAdded(item.label))
            _uiState.update { it.copy(picker = ItemPickerState()) }
            return
        }
        val line = RecipeLineForm(newKey(), item.variant.id, item.label, item.product.stockUnit)
        _uiState.update { it.copy(form = form.copy(recipe = form.recipe + line), picker = ItemPickerState()) }
        revalidate()
    }

    fun onIngredientQuantityChange(key: String, text: String) {
        val clean = text.filter { it.isDigit() || it == '.' }.take(10)
        _uiState.update { state ->
            state.copy(form = state.form.copy(recipe = state.form.recipe.map { if (it.key == key) it.copy(quantity = clean) else it }))
        }
        revalidate()
    }

    fun removeIngredient(key: String) {
        _uiState.update { state -> state.copy(form = state.form.copy(recipe = state.form.recipe.filterNot { it.key == key })) }
        revalidate()
    }

    private fun revalidate() = _uiState.update { state ->
        when (val result = MenuValidator.validate(state.form, existing)) {
            is MenuValidation.Invalid -> state.copy(errors = result.errors, recipeErrors = result.recipeErrors)
            is MenuValidation.Valid -> state.copy(errors = emptyMap(), recipeErrors = emptyMap())
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.loadFailed) return
        val item = when (val result = MenuValidator.validate(state.form, existing)) {
            is MenuValidation.Invalid -> {
                _uiState.update { it.copy(errors = result.errors, recipeErrors = result.recipeErrors, showErrors = true) }
                eventChannel.trySend(MenuEditorEvent.Invalid(result.errors.size + result.recipeErrors.size))
                return
            }
            is MenuValidation.Valid -> result.item
        }
        _uiState.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            val id = runCatching { menu.saveItem(item) }.getOrNull()
            _uiState.update { it.copy(isSaving = false) }
            if (id == null) {
                eventChannel.send(MenuEditorEvent.Failed)
            } else {
                existing = item.copy(id = id)
                eventChannel.send(MenuEditorEvent.Saved(id))
            }
        }
    }

    /** Removes the dish. Past bills keep their own copy of it. */
    fun delete() {
        val item = existing ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val ok = runCatching { menu.deleteItem(item.id) }.isSuccess
            _uiState.update { it.copy(isSaving = false) }
            eventChannel.send(if (ok) MenuEditorEvent.Deleted else MenuEditorEvent.Failed)
        }
    }

    companion object {
        const val ARG_MENU_ITEM_ID = "menuItemId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                MenuEditorViewModel(
                    menuItemId = createSavedStateHandle().get<Long>(ARG_MENU_ITEM_ID) ?: 0L,
                    menu = container.menuRepository,
                    inventory = container.inventoryRepository,
                )
            }
        }
    }
}
