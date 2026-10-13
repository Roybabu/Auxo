package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.CategoryRepository
import com.auxo.pos.data.repository.CategoryResult
import com.auxo.pos.domain.category.NameCheck
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ShopCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the last change to the category list was not made. */
enum class CategoryProblem { BLANK, TOO_LONG, TAKEN, LAST_ONE, IN_USE, BUILT_IN, NOT_FOUND, FAILED }

data class CategoriesUiState(
    val categories: List<ShopCategory> = emptyList(),
    val loaded: Boolean = false,
    val problem: CategoryProblem? = null,
)

/** Shop Settings → Product Categories. */
class CategoriesViewModel(private val repository: CategoryRepository) : ViewModel() {

    private val problem = MutableStateFlow<CategoryProblem?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.observeCategories().catch { emit(emptyList()) },
        problem,
    ) { categories, problem -> CategoriesUiState(categories, loaded = true, problem = problem) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun dismissProblem() = problem.update { null }

    private fun run(block: suspend () -> CategoryResult) {
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: com.auxo.pos.core.auth.PermissionDeniedException) {
                throw e
            } catch (e: Exception) {
                null
            }
            problem.value = when (result) {
                null -> CategoryProblem.FAILED
                is CategoryResult.Done -> null
                is CategoryResult.BadName -> when (result.check) {
                    NameCheck.Blank -> CategoryProblem.BLANK
                    NameCheck.TooLong -> CategoryProblem.TOO_LONG
                    NameCheck.Taken -> CategoryProblem.TAKEN
                    is NameCheck.Valid -> null
                }
                CategoryResult.LastOne -> CategoryProblem.LAST_ONE
                CategoryResult.InUse -> CategoryProblem.IN_USE
                CategoryResult.BuiltIn -> CategoryProblem.BUILT_IN
                CategoryResult.NotFound -> CategoryProblem.NOT_FOUND
            }
        }
    }

    fun onEnabledChange(id: Long, enabled: Boolean) = run { repository.setEnabled(id, enabled) }

    fun onAdd(name: String, profile: ProductCategory) = run { repository.addCategory(name, profile) }

    fun onRename(id: Long, name: String) = run { repository.renameCategory(id, name) }

    fun onDelete(id: Long) = run { repository.deleteCategory(id) }

    fun onAddSubcategory(categoryId: Long, name: String) = run { repository.addSubcategory(categoryId, name) }

    fun onRenameSubcategory(id: Long, name: String) = run { repository.renameSubcategory(id, name) }

    fun onDeleteSubcategory(id: Long) = run { repository.deleteSubcategory(id) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { CategoriesViewModel(appContainer().categoryRepository) }
        }
    }
}
