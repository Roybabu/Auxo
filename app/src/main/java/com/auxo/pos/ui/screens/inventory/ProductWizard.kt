// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.domain.model.formatRatePercent
import com.auxo.pos.domain.validation.FormField
import com.auxo.pos.domain.validation.GstChoice
import com.auxo.pos.domain.validation.ProductForm
import com.auxo.pos.domain.validation.ProductFormErrors
import com.auxo.pos.domain.validation.VariantForm
import com.auxo.pos.ui.LocalSuppliersEnabled
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.ProductPhoto
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.screens.more.label
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ProductEditorUiState

/** The three steps of adding a product on a phone. */
enum class WizardStep { DETAILS, PRICING, REVIEW }

/** Which step each kind of error belongs to, so Next can say "fix this first" for the step being left. */
object WizardValidation {
    private val detailsFields = setOf(
        FormField.NAME, FormField.BRAND, FormField.DESCRIPTION, FormField.MATERIAL,
        FormField.SUBCATEGORY, FormField.WARRANTY, FormField.METAL, FormField.MAKING_CHARGE,
    )

    fun stepHasErrors(step: WizardStep, errors: ProductFormErrors): Boolean = when (step) {
        WizardStep.DETAILS -> errors.fields.keys.any { it in detailsFields }
        WizardStep.PRICING -> errors.fields.keys.any { it !in detailsFields } || errors.variants.values.any { it.isNotEmpty() }
        WizardStep.REVIEW -> !errors.isEmpty
    }

    /** The first step that still has something to fix, or null when the form is ready to save. */
    fun firstStepWithErrors(errors: ProductFormErrors): WizardStep? =
        listOf(WizardStep.DETAILS, WizardStep.PRICING).firstOrNull { stepHasErrors(it, errors) }
}

/** Wide screens show everything side by side. */
const val WIDE_EDITOR_DP = 840

/**
 * Add product: three guided steps on a phone, two columns on a tablet. It edits the same form as the
 * single-page editor, so nothing typed is lost moving between steps.
 */
@Composable
fun ProductWizard(
    uiState: ProductEditorUiState,
    actions: ProductEditorActions,
    modifier: Modifier = Modifier,
) {
    if (LocalConfiguration.current.screenWidthDp >= WIDE_EDITOR_DP) {
        WideProductEditor(uiState, actions, modifier)
    } else {
        PhoneProductWizard(uiState, actions, modifier)
    }
}

@Composable
private fun PhoneProductWizard(uiState: ProductEditorUiState, actions: ProductEditorActions, modifier: Modifier) {
    val suppliersEnabled = LocalSuppliersEnabled.current
    var stepIndex by rememberSaveable { mutableStateOf(0) }
    val step = WizardStep.entries[stepIndex]
    val errors = uiState.visibleErrors

    Column(modifier = modifier.fillMaxSize().testTag("product_wizard")) {
        StepIndicator(current = stepIndex, onJump = { target -> if (target < stepIndex) stepIndex = target })
        Box(modifier = Modifier.weight(1f)) {
            when (step) {
                WizardStep.DETAILS -> StepList(Modifier) { detailsItems(uiState, actions) }
                WizardStep.PRICING -> StepList(Modifier) { pricingItems(uiState, actions, suppliersEnabled) }
                WizardStep.REVIEW -> StepList(Modifier) {
                    reviewItems(uiState) { target -> stepIndex = target.ordinal }
                }
            }
        }
        BottomBar(
            showBack = stepIndex > 0,
            onBack = { stepIndex -= 1 },
            primary = {
                if (step == WizardStep.REVIEW) {
                    SaveButton(uiState.isSaving, actions, Modifier.weight(1f))
                } else {
                    Button(
                        onClick = {
                            if (WizardValidation.stepHasErrors(step, uiState.errors)) {
                                actions.onRevealErrors()
                            } else {
                                stepIndex += 1
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp).testTag("wizard_next"),
                    ) { Text(stringResource(R.string.wizard_next)) }
                }
            },
            hint = if (WizardValidation.stepHasErrors(step, errors) && step != WizardStep.REVIEW) {
                stringResource(R.string.wizard_fix_to_continue)
            } else {
                null
            },
        )
    }
}

@Composable
private fun WideProductEditor(uiState: ProductEditorUiState, actions: ProductEditorActions, modifier: Modifier) {
    val suppliersEnabled = LocalSuppliersEnabled.current
    Column(modifier = modifier.fillMaxSize().testTag("product_wizard_wide")) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StepList(Modifier.weight(1f)) { detailsItems(uiState, actions) }
            StepList(Modifier.weight(1f)) { pricingItems(uiState, actions, suppliersEnabled) }
        }
        BottomBar(
            showBack = false,
            onBack = {},
            primary = { SaveButton(uiState.isSaving, actions, Modifier.weight(1f)) },
            hint = null,
        )
    }
}

@Composable
private fun StepList(modifier: Modifier = Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("product_editor"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        content = content,
    )
}

// Step 1: category, photo, name and the category's own details.
private fun androidx.compose.foundation.lazy.LazyListScope.detailsItems(uiState: ProductEditorUiState, actions: ProductEditorActions) {
    val form = uiState.form
    val errors = uiState.visibleErrors
    item(key = "category") { CategoryPicker(form, actions) }
    item(key = "photo") { PhotoSection(form, uiState.photoBusy, actions) }
    item(key = "details") { DetailsSection(form, errors, actions) }
    if (form.category == com.auxo.pos.domain.model.ProductCategory.RESTAURANT) {
        item(key = "subcategory") { SubcategorySection(form, errors, actions) }
    }
    if (form.category.canSkipStock) {
        item(key = "restaurant") { RestaurantSection(form, errors, actions) }
    }
}

// Step 2: sizes and colours, the variants they make, prices, stock and tax.
private fun androidx.compose.foundation.lazy.LazyListScope.pricingItems(
    uiState: ProductEditorUiState,
    actions: ProductEditorActions,
    suppliersEnabled: Boolean,
) {
    val form = uiState.form
    val errors = uiState.visibleErrors
    item(key = "tax") { TaxSection(form, errors, actions) }
    if (form.category.usesSizeAndColour) {
        item(key = "sizes") { SizesSection(form, errors, actions) }
        item(key = "colours") { ColoursSection(form, errors, uiState.photoBusy, actions) }
        item(key = "defaults") { DefaultsSection(form.defaults, actions) }
    }
    item(key = "variants_header") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.editor_variants))
            errors.fields[FormField.VARIANTS]?.let { error ->
                Text(
                    text = stringResource(if (error == com.auxo.pos.domain.validation.FieldError.REQUIRED) R.string.error_variants_required else error.messageRes()),
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
                Text(stringResource(R.string.editor_add_pack))
            }
        }
    }
    if (form.tracksStock && (suppliersEnabled || form.category.allowedStockUnits.size > 1)) {
        item(key = "purchasing") { PurchasingSection(form, uiState.suppliers, actions) }
    }
}

// Step 3: what will be saved, with a way back to each part.
private fun androidx.compose.foundation.lazy.LazyListScope.reviewItems(uiState: ProductEditorUiState, onEdit: (WizardStep) -> Unit) {
    val form = uiState.form
    val errors = uiState.errors
    item(key = "review_status") { ReviewStatus(errors, onEdit) }
    item(key = "review_product") { ReviewProduct(form) { onEdit(WizardStep.DETAILS) } }
    item(key = "review_variants_header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.wizard_review_variants, form.variants.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onEdit(WizardStep.PRICING) }, modifier = Modifier.testTag("wizard_edit_pricing")) {
                Text(stringResource(R.string.action_edit))
            }
        }
    }
    items(form.variants, key = { "rv_" + it.key }) { row -> ReviewVariantRow(form, row) }
    item(key = "review_tax") { ReviewTax(form) { onEdit(WizardStep.PRICING) } }
}

@Composable
private fun ReviewStatus(errors: ProductFormErrors, onEdit: (WizardStep) -> Unit) {
    val problemStep = WizardValidation.firstStepWithErrors(errors)
    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("wizard_review_status")) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (problemStep == null) {
                Text(stringResource(R.string.wizard_review_ready), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = pluralStringResource(R.plurals.editor_fix_errors, errors.errorCount, errors.errorCount),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onEdit(problemStep) }, modifier = Modifier.testTag("wizard_fix")) {
                    Text(stringResource(R.string.wizard_review_fix))
                }
            }
        }
    }
}

@Composable
private fun ReviewProduct(form: ProductForm, onEdit: () -> Unit) {
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                ProductPhoto(photoFile = form.photoFile, contentDescription = form.name.ifBlank { null }, modifier = Modifier.fillMaxSize(), maxSidePx = 300) {
                    androidx.compose.material3.Icon(
                        androidx.compose.ui.res.painterResource(form.category.iconRes()),
                        contentDescription = null,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = form.name.ifBlank { stringResource(R.string.wizard_review_no_name) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("wizard_review_name"),
                )
                Text(categoryName(form), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (form.brand.isNotBlank()) Text("${stringResource(R.string.editor_brand)}: ${form.brand}", style = MaterialTheme.typography.bodySmall)
                if (form.material.isNotBlank()) Text("${stringResource(R.string.editor_material)}: ${form.material}", style = MaterialTheme.typography.bodySmall)
                if (form.description.isNotBlank()) Text(form.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onEdit, modifier = Modifier.testTag("wizard_edit_details")) { Text(stringResource(R.string.action_edit)) }
        }
    }
}

@Composable
private fun categoryName(form: ProductForm): String {
    val row = com.auxo.pos.ui.selectableCategories(form.categoryId).firstOrNull { it.id == form.categoryId }
    return row?.label() ?: stringResource(form.category.labelRes())
}

@Composable
private fun ReviewVariantRow(form: ProductForm, row: VariantForm) {
    val currency = stringResource(R.string.currency_prefix)
    val label = variantLabel(row, form.variants.indexOf(row))
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = if (row.sellingPrice.isBlank()) "—" else currency + row.sellingPrice,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (form.tracksStock) {
                Text(
                    text = row.openingStock.ifBlank { row.currentStock?.toString() ?: "0" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp).padding(start = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun variantLabel(row: VariantForm, index: Int): String {
    val parts = listOf(row.size, row.colour).filter { it.isNotBlank() }
    return if (parts.isNotEmpty()) parts.joinToString(" / ") else stringResource(R.string.editor_pack, index + 1)
}

@Composable
private fun ReviewTax(form: ProductForm, onEdit: () -> Unit) {
    val rate = when (val gst = form.gst) {
        GstChoice.NotSet -> stringResource(R.string.gst_not_set)
        GstChoice.Apparel -> stringResource(R.string.gst_apparel)
        GstChoice.Custom -> form.customGstRate.ifBlank { "—" } + "%"
        is GstChoice.Standard -> formatRatePercent(gst.basisPoints) + "%"
    }
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wizard_review_tax), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit, modifier = Modifier.testTag("wizard_edit_tax")) { Text(stringResource(R.string.action_edit)) }
            }
            ReviewLine(stringResource(R.string.editor_gst_rate), rate)
            ReviewLine(stringResource(R.string.editor_hsn), form.hsnCode.ifBlank { "—" })
        }
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StepIndicator(current: Int, onJump: (Int) -> Unit) {
    val names = listOf(R.string.wizard_step_details, R.string.wizard_step_pricing, R.string.wizard_step_review)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm).testTag("wizard_progress"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { index, nameRes ->
            val done = index < current
            val active = index == current
            val name = stringResource(nameRes)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .let { if (done) it.clickable { onJump(index) } else it }
                    .semantics { contentDescription = "${index + 1}. $name" }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (active || done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (done) "✓" else "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active || done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(showBack: Boolean, onBack: () -> Unit, primary: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit, hint: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = Spacing.xs))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                OutlinedButton(onClick = onBack, modifier = Modifier.height(52.dp).testTag("wizard_back")) {
                    Text(stringResource(R.string.action_back))
                }
            }
            primary()
        }
    }
}

@Composable
private fun SaveButton(isSaving: Boolean, actions: ProductEditorActions, modifier: Modifier) {
    Button(
        onClick = actions::onSave,
        enabled = !isSaving,
        modifier = modifier.height(52.dp).testTag("editor_save"),
    ) {
        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.wizard_save_product))
    }
}
