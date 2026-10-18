package com.auxo.pos.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.CategoryRepository
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ShopCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Remembers that the owner closed the FSSAI hint on Home. */
interface FssaiHintStore {
    val dismissed: Flow<Boolean>
    fun dismiss()
}

class SharedPrefsFssaiHintStore(context: Context) : FssaiHintStore {
    private val prefs = context.applicationContext.getSharedPreferences("fssai_hint", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean(KEY, false))
    override val dismissed: Flow<Boolean> get() = state
    override fun dismiss() {
        prefs.edit().putBoolean(KEY, true).apply()
        state.value = true
    }

    private companion object {
        const val KEY = "dismissed"
    }
}

/**
 * Whether Home should remind the owner to add an FSSAI number: a food, drink or restaurant category is on, the
 * number is empty and the reminder has not been closed. It never blocks billing.
 */
class FssaiHintViewModel(
    settings: SettingsRepository,
    categories: CategoryRepository,
    private val store: FssaiHintStore,
) : ViewModel() {

    val visible: StateFlow<Boolean> = combine(
        settings.observeSettings(),
        categories.observeCategories(),
        store.dismissed,
    ) { shop, list, dismissed -> !dismissed && shop.fssai.isBlank() && list.any(::needsFssai) }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismiss() = store.dismiss()

    companion object {
        private val FOOD = setOf(
            ProductCategory.PACKAGED_FOOD, ProductCategory.BEVERAGE, ProductCategory.RESTAURANT,
        )

        fun needsFssai(category: ShopCategory): Boolean = category.enabled && category.profile in FOOD

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                FssaiHintViewModel(container.settingsRepository, container.categoryRepository, container.fssaiHintStore)
            }
        }
    }
}
