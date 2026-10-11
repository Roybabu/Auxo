package com.invision.pos.ui.screens.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.core.label.LabelContent
import com.invision.pos.core.label.LabelContentFactory
import com.invision.pos.core.label.LabelPrinter
import com.invision.pos.core.label.LabelRenderer
import com.invision.pos.core.label.LabelTemplate
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.QuantityStepper
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.LabelPrintUiState
import com.invision.pos.viewmodel.LabelPrintViewModel
import kotlin.math.roundToInt

@Composable
fun LabelPrintScreen(
    onBack: () -> Unit,
    viewModel: LabelPrintViewModel = viewModel(factory = LabelPrintViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val formatter = LocalMoneyFormatter.current
    val jobName = stringResource(R.string.labels_job_name, uiState.product?.name.orEmpty())

    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.labels_title), onBack = onBack) },
        bottomBar = {
            val product = uiState.product
            if (product != null) {
                PrintBar(
                    uiState = uiState,
                    onPrint = {
                        val labels = LabelContentFactory.build(product, uiState.copies, uiState.showMrp, formatter::format)
                        LabelPrinter.print(context, uiState.template, labels, jobName)
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            uiState.isLoading -> LoadingState(contentModifier)
            uiState.loadFailed || uiState.product == null -> ErrorState(
                message = stringResource(R.string.detail_not_found),
                onRetry = viewModel::retry,
                modifier = contentModifier,
            )
            else -> LabelPrintContent(
                uiState = uiState,
                onTemplateChange = viewModel::setTemplate,
                onShowMrpChange = viewModel::setShowMrp,
                onCopiesChange = viewModel::setCopies,
                onMatchStock = viewModel::matchStock,
                modifier = contentModifier,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelPrintContent(
    uiState: LabelPrintUiState,
    onTemplateChange: (LabelTemplate) -> Unit,
    onShowMrpChange: (Boolean) -> Unit,
    onCopiesChange: (Long, Int) -> Unit,
    onMatchStock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val product = uiState.product ?: return
    val formatter = LocalMoneyFormatter.current
    // Every size or colour that will print gets its own preview, so nothing on the sheet is a surprise.
    val printing = product.variants.filter { (uiState.copies[it.id] ?: 0) > 0 }
    val skipped = product.variants.filter { (uiState.copies[it.id] ?: 0) <= 0 }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "template") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(stringResource(R.string.labels_template))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    LabelTemplate.ALL.forEach { template ->
                        FilterChip(
                            selected = uiState.template == template,
                            onClick = { onTemplateChange(template) },
                            label = { Text(stringResource(templateLabel(template))) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.labels_show_mrp),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = uiState.showMrp, onCheckedChange = onShowMrpChange)
                }
            }
        }

        item(key = "preview_header") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionHeader(stringResource(R.string.labels_preview))
                Text(
                    text = if (printing.isEmpty()) {
                        stringResource(R.string.labels_preview_none)
                    } else {
                        stringResource(R.string.labels_preview_note, printing.size, product.variants.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(printing, key = { "preview_${it.id}" }) { variant ->
            val content = LabelContentFactory.forVariant(product, variant, uiState.showMrp, formatter::format)
            val name = variant.attributesLabel.ifEmpty { stringResource(R.string.detail_variant_standard) }
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.testTag("label_preview_${variant.id}"),
            ) {
                Text(
                    text = stringResource(R.string.labels_preview_caption, name, variant.barcode, uiState.copies[variant.id] ?: 0),
                    style = MaterialTheme.typography.labelLarge,
                )
                LabelPreview(content = content, template = uiState.template, description = "$name, ${variant.barcode}")
            }
        }
        if (skipped.isNotEmpty() && printing.isNotEmpty()) {
            item(key = "preview_skipped") {
                Text(
                    text = stringResource(
                        R.string.labels_preview_skipped,
                        skipped.joinToString { it.attributesLabel.ifEmpty { it.barcode } },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "copies_header") {
            SectionHeader(
                title = stringResource(R.string.labels_copies),
                action = { TextButton(onClick = onMatchStock) { Text(stringResource(R.string.labels_match_stock)) } },
            )
        }
        item(key = "copies") {
            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    product.variants.forEachIndexed { index, variant ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = variant.attributesLabel.ifEmpty { stringResource(R.string.detail_variant_standard) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${variant.barcode} · ${formatter.format(variant.sellingPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            QuantityStepper(
                                value = uiState.copies[variant.id] ?: 0,
                                onValueChange = { onCopiesChange(variant.id, it) },
                                max = LabelPrintViewModel.MAX_COPIES,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Renders the label with the same code used for printing. */
@Composable
private fun LabelPreview(content: LabelContent, template: LabelTemplate, description: String) {
    val ratio = template.labelWidthMm / template.labelHeightMm
    val bitmap = remember(content, template) {
        val width = 640
        LabelRenderer.renderBitmap(content, width, (width / ratio).roundToInt()).asImageBitmap()
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .aspectRatio(ratio),
    ) {
        Image(bitmap = bitmap, contentDescription = description)
    }
}

@Composable
private fun PrintBar(uiState: LabelPrintUiState, onPrint: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (uiState.totalLabels == 0) {
                    stringResource(R.string.labels_none)
                } else {
                    pluralStringResource(R.plurals.labels_count, uiState.totalLabels, uiState.totalLabels) + " · " +
                        pluralStringResource(R.plurals.labels_pages, uiState.pageCount, uiState.pageCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onPrint, enabled = uiState.totalLabels > 0) {
                Icon(painterResource(R.drawable.ic_print), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_print), modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

private fun templateLabel(template: LabelTemplate): Int = when (template) {
    LabelTemplate.ROLL_50X25 -> R.string.template_roll_50x25
    else -> R.string.template_a4_24
}
