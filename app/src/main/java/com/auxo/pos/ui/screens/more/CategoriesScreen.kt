package com.auxo.pos.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.domain.category.CategoryRules
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ShopCategory
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.screens.inventory.labelRes
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.CategoriesUiState
import com.auxo.pos.viewmodel.CategoriesViewModel
import com.auxo.pos.viewmodel.CategoryProblem

/** What the admin can do to the category list. */
interface CategoryActions {
    fun onEnabledChange(id: Long, enabled: Boolean) = Unit
    fun onAdd(name: String, profile: ProductCategory) = Unit
    fun onRename(id: Long, name: String) = Unit
    fun onDelete(id: Long) = Unit
    fun onAddSubcategory(categoryId: Long, name: String) = Unit
    fun onRenameSubcategory(id: Long, name: String) = Unit
    fun onDeleteSubcategory(id: Long) = Unit
    fun onDismissProblem() = Unit
}

/** The name to show for a category: the shop's own, or the translated built-in name. */
@Composable
fun ShopCategory.label(): String = name ?: stringResource(profile.labelRes())

/** Shop Settings → Product Categories. */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        object : CategoryActions {
            override fun onEnabledChange(id: Long, enabled: Boolean) = viewModel.onEnabledChange(id, enabled)
            override fun onAdd(name: String, profile: ProductCategory) = viewModel.onAdd(name, profile)
            override fun onRename(id: Long, name: String) = viewModel.onRename(id, name)
            override fun onDelete(id: Long) = viewModel.onDelete(id)
            override fun onAddSubcategory(categoryId: Long, name: String) = viewModel.onAddSubcategory(categoryId, name)
            override fun onRenameSubcategory(id: Long, name: String) = viewModel.onRenameSubcategory(id, name)
            override fun onDeleteSubcategory(id: Long) = viewModel.onDeleteSubcategory(id)
            override fun onDismissProblem() = viewModel.dismissProblem()
        }
    }
    CategoriesContent(state, onBack, actions)
}

private sealed interface Editing {
    data object Adding : Editing
    data class Renaming(val category: ShopCategory) : Editing
    data class AddingSub(val category: ShopCategory) : Editing
    data class RenamingSub(val id: Long, val name: String) : Editing
    data class Deleting(val category: ShopCategory) : Editing
}

@Composable
fun CategoriesContent(state: CategoriesUiState, onBack: () -> Unit, actions: CategoryActions) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.categories_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .testTag("categories_list"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.categories_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.problem?.let { ProblemCard(it, actions::onDismissProblem) }
            state.categories.forEach { category ->
                CategoryCard(
                    category = category,
                    actions = actions,
                    onRename = { editing = Editing.Renaming(category) },
                    onAddSub = { editing = Editing.AddingSub(category) },
                    onRenameSub = { id, name -> editing = Editing.RenamingSub(id, name) },
                    onDelete = { editing = Editing.Deleting(category) },
                )
            }
            TextButton(onClick = { editing = Editing.Adding }, modifier = Modifier.testTag("category_add")) {
                Text(stringResource(R.string.categories_add))
            }
        }
    }

    when (val e = editing) {
        null -> Unit
        Editing.Adding -> AddCategoryDialog(
            onDismiss = { editing = null },
            onAdd = { name, profile -> actions.onAdd(name, profile); editing = null },
        )
        is Editing.Renaming -> NameDialog(
            title = stringResource(R.string.categories_rename),
            initial = e.category.name.orEmpty(),
            hint = if (e.category.builtIn) stringResource(R.string.categories_rename_hint) else null,
            tag = "category_name",
            onDismiss = { editing = null },
            onSave = { actions.onRename(e.category.id, it); editing = null },
        )
        is Editing.AddingSub -> NameDialog(
            title = stringResource(R.string.categories_add_sub),
            initial = "",
            hint = null,
            tag = "subcategory_name",
            onDismiss = { editing = null },
            onSave = { actions.onAddSubcategory(e.category.id, it); editing = null },
        )
        is Editing.RenamingSub -> NameDialog(
            title = stringResource(R.string.categories_rename),
            initial = e.name,
            hint = null,
            tag = "subcategory_name",
            onDismiss = { editing = null },
            onSave = { actions.onRenameSubcategory(e.id, it); editing = null },
        )
        is Editing.Deleting -> AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.categories_delete_title, e.category.label())) },
            text = { Text(stringResource(R.string.categories_delete_body)) },
            confirmButton = {
                TextButton(onClick = { actions.onDelete(e.category.id); editing = null }, modifier = Modifier.testTag("category_delete_confirm")) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ProblemCard(problem: CategoryProblem, onDismiss: () -> Unit) {
    val message = when (problem) {
        CategoryProblem.BLANK -> R.string.categories_error_blank
        CategoryProblem.TOO_LONG -> R.string.categories_error_long
        CategoryProblem.TAKEN -> R.string.categories_error_taken
        CategoryProblem.LAST_ONE -> R.string.categories_error_last
        CategoryProblem.IN_USE -> R.string.categories_error_in_use
        CategoryProblem.BUILT_IN -> R.string.categories_error_built_in
        CategoryProblem.NOT_FOUND, CategoryProblem.FAILED -> R.string.categories_error_failed
    }
    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("categories_problem")) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(
    category: ShopCategory,
    actions: CategoryActions,
    onRename: () -> Unit,
    onAddSub: () -> Unit,
    onRenameSub: (Long, String) -> Unit,
    onDelete: () -> Unit,
) {
    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("category_${category.id}")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.label(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.categories_works_like, stringResource(category.profile.labelRes())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = category.enabled,
                    onCheckedChange = { actions.onEnabledChange(category.id, it) },
                    modifier = Modifier.testTag("category_switch_${category.id}"),
                )
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                category.subcategories.forEach { sub ->
                    FilterChip(
                        selected = false,
                        onClick = { onRenameSub(sub.id, sub.name) },
                        label = { Text(sub.name) },
                        modifier = Modifier.testTag("subcategory_${sub.id}"),
                    )
                }
                AssistChip(onClick = onAddSub, label = { Text(stringResource(R.string.categories_add_sub)) }, modifier = Modifier.testTag("subcategory_add_${category.id}"))
            }
            Row {
                TextButton(onClick = onRename, modifier = Modifier.testTag("category_rename_${category.id}")) { Text(stringResource(R.string.categories_rename)) }
                if (!category.builtIn) {
                    TextButton(onClick = onDelete, modifier = Modifier.testTag("category_delete_${category.id}")) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, hint: String?, tag: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AuxoTextField(
                value = text, onValueChange = { text = it }, label = stringResource(R.string.categories_name),
                supportingText = hint, modifier = Modifier.fillMaxWidth().testTag(tag),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, modifier = Modifier.testTag("${tag}_save")) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Name plus how the new category behaves (it follows one of the built-in kinds). */
@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onAdd: (String, ProductCategory) -> Unit) {
    var text by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(ProductCategory.GARMENT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AuxoTextField(value = text, onValueChange = { text = it }, label = stringResource(R.string.categories_name), modifier = Modifier.fillMaxWidth().testTag("category_name"))
                Text(stringResource(R.string.categories_behaves_like), style = MaterialTheme.typography.labelLarge)
                CategoryRules.builtInOrder.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == profile, onClick = { profile = option }, role = Role.RadioButton)
                            .testTag("profile_${option.name}"),
                    ) {
                        RadioButton(selected = option == profile, onClick = null)
                        Text(stringResource(option.labelRes()), modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
                Text(
                    stringResource(R.string.categories_behaves_like_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text, profile) }, modifier = Modifier.testTag("category_name_save")) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
