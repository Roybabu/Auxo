package com.auxo.pos.ui.screens.stock

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.export.ExportFile
import com.auxo.pos.core.export.FileExport
import com.auxo.pos.domain.imports.ImportColumn
import com.auxo.pos.domain.imports.ImportIssue
import com.auxo.pos.domain.imports.ImportItem
import com.auxo.pos.domain.imports.ImportParseError
import com.auxo.pos.domain.imports.ImportStatus
import com.auxo.pos.domain.imports.ProductImportTemplate
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.components.StatusChip
import com.auxo.pos.ui.screens.inventory.labelRes
import com.auxo.pos.ui.screens.inventory.messageRes
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ImportReadError
import com.auxo.pos.viewmodel.ProductImportUiState
import com.auxo.pos.viewmodel.ProductImportViewModel
import kotlinx.coroutines.launch

/** Callbacks of the import screen, so its content can be shown in tests without a ViewModel. */
interface ProductImportActions {
    fun onDownloadTemplate() = Unit
    fun onChooseFile() = Unit
    fun onMappingChange(column: ImportColumn, index: Int?) = Unit
    fun onDefaultCategoryChange(category: ProductCategory?) = Unit
    fun onImport() = Unit
    fun onConfirmImport() = Unit
    fun onDismissConfirm() = Unit
    fun onStartOver() = Unit
    fun onDone() = Unit
}

/** Number of problems listed under one product before "and N more". */
private const val ISSUES_SHOWN = 3

@Composable
fun ProductImportScreen(
    onBack: () -> Unit,
    viewModel: ProductImportViewModel = viewModel(factory = ProductImportViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val templateFailed = stringResource(R.string.import_template_failed)
    val templateTitle = stringResource(R.string.import_template)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.onFilePicked(uri.toString(), displayName(context, uri))
    }

    val actions = remember(viewModel) {
        object : ProductImportActions {
            override fun onDownloadTemplate() {
                scope.launch {
                    val shared = FileExport.share(
                        context,
                        ExportFile(ProductImportTemplate.FILE_NAME, ProductImportTemplate.csv()),
                        templateTitle,
                    )
                    if (!shared) snackbarHostState.showSnackbar(templateFailed)
                }
            }
            override fun onChooseFile() = picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
            override fun onMappingChange(column: ImportColumn, index: Int?) = viewModel.onMappingChange(column, index)
            override fun onDefaultCategoryChange(category: ProductCategory?) = viewModel.onDefaultCategoryChange(category)
            override fun onImport() = viewModel.onImport()
            override fun onConfirmImport() = viewModel.onConfirmImport()
            override fun onDismissConfirm() = viewModel.onDismissConfirm()
            override fun onStartOver() = viewModel.onStartOver()
            override fun onDone() = onBack()
        }
    }

    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.import_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        ProductImportContent(state, actions, Modifier.padding(padding))
    }
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

@Composable
fun ProductImportContent(state: ProductImportUiState, actions: ProductImportActions, modifier: Modifier = Modifier) {
    when {
        state.outcome != null -> ResultStage(state, actions, modifier)
        state.table == null -> PickStage(state, actions, modifier)
        else -> PreviewStage(state, actions, modifier)
    }

    if (state.confirmOpen) {
        val count = state.preview?.ready?.size ?: 0
        AlertDialog(
            onDismissRequest = actions::onDismissConfirm,
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(pluralStringResource(R.plurals.import_confirm_message, count, count)) },
            confirmButton = {
                TextButton(onClick = actions::onConfirmImport, modifier = Modifier.testTag("import_confirm")) {
                    Text(stringResource(R.string.import_confirm))
                }
            },
            dismissButton = { TextButton(onClick = actions::onDismissConfirm) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PickStage(state: ProductImportUiState, actions: ProductImportActions, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
            .testTag("import_pick"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AuxoCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.import_intro_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.import_intro_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(onClick = actions::onDownloadTemplate, modifier = Modifier.fillMaxWidth().testTag("import_template")) {
            Text(stringResource(R.string.import_template))
        }
        Button(
            onClick = actions::onChooseFile,
            enabled = !state.isReading,
            modifier = Modifier.fillMaxWidth().testTag("import_choose"),
        ) { Text(stringResource(R.string.import_choose)) }

        if (state.isReading) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.testTag("import_reading")) {
                CircularProgressIndicator(modifier = Modifier.padding(end = Spacing.xs))
                Text(stringResource(R.string.import_reading), style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.readError?.let { error ->
            Text(
                stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("import_read_error"),
            )
        }
    }
}

private fun ImportReadError.messageRes(): Int = when (this) {
    ImportReadError.UNREADABLE -> R.string.import_error_unreadable
    ImportReadError.TOO_LARGE -> R.string.import_error_too_large
    ImportReadError.EMPTY -> R.string.import_error_empty
    ImportReadError.TOO_MANY_ROWS -> R.string.import_error_too_many_rows
    ImportReadError.MALFORMED -> R.string.import_error_malformed
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewStage(state: ProductImportUiState, actions: ProductImportActions, modifier: Modifier) {
    val table = checkNotNull(state.table)
    val preview = state.preview
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag("import_preview"),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "file") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        state.fileName ?: stringResource(R.string.import_file_unnamed),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions::onStartOver, modifier = Modifier.testTag("import_start_over")) {
                        Text(stringResource(R.string.import_other_file))
                    }
                }
            }
            item(key = "columns") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SectionHeader(stringResource(R.string.import_columns_title))
                    Text(
                        stringResource(R.string.import_columns_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ImportColumn.entries, key = { "map_${it.key}" }) { column ->
                ColumnPicker(column, table.header, state.mapping.indexOf(column), actions)
            }
            item(key = "default_category") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.import_default_category), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = state.defaultCategory == null,
                            onClick = { actions.onDefaultCategoryChange(null) },
                            label = { Text(stringResource(R.string.import_default_none)) },
                            modifier = Modifier.testTag("import_default_none"),
                        )
                        // Restaurant items have no stock count and are added one at a time, not imported.
                        ProductCategory.entries.filter { it != ProductCategory.RESTAURANT }.forEach { category ->
                            FilterChip(
                                selected = state.defaultCategory == category,
                                onClick = { actions.onDefaultCategoryChange(category) },
                                label = { Text(stringResource(category.labelRes())) },
                                modifier = Modifier.testTag("import_default_${category.name}"),
                            )
                        }
                    }
                }
            }

            if (!state.mapping.isComplete) {
                item(key = "need_name") {
                    Text(
                        stringResource(R.string.import_need_name),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("import_need_name"),
                    )
                }
            } else if (preview != null) {
                item(key = "summary") {
                    SectionHeader(stringResource(R.string.import_preview_title), modifier = Modifier.padding(top = Spacing.sm))
                    Text(
                        stringResource(R.string.import_summary, preview.ready.size, preview.invalid.size, preview.existing.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("import_summary"),
                    )
                }
                // Products that need attention come first.
                val ordered = preview.invalid + preview.ready + preview.existing
                items(ordered, key = { "item_${it.rowNumbers.first()}" }) { item -> ImportItemCard(item) }
            }
        }
        if (state.mapping.isComplete && preview != null) {
            Button(
                onClick = actions::onImport,
                enabled = state.canImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                    .testTag("import_button"),
            ) {
                Text(pluralStringResource(R.plurals.import_action, preview.ready.size, preview.ready.size))
            }
        }
    }
}

@Composable
private fun ColumnPicker(column: ImportColumn, header: List<String>, selected: Int?, actions: ProductImportActions) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            column.key + if (column.required) " *" else "",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Column {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("map_${column.key}")) {
                Text(selected?.let { columnName(header, it) } ?: stringResource(R.string.import_column_none), maxLines = 1)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_column_none)) },
                    onClick = {
                        open = false
                        actions.onMappingChange(column, null)
                    },
                    modifier = Modifier.testTag("map_${column.key}_none"),
                )
                header.indices.forEach { index ->
                    DropdownMenuItem(
                        text = { Text(columnName(header, index)) },
                        onClick = {
                            open = false
                            actions.onMappingChange(column, index)
                        },
                        modifier = Modifier.testTag("map_${column.key}_$index"),
                    )
                }
            }
        }
    }
}

@Composable
private fun columnName(header: List<String>, index: Int): String =
    header.getOrNull(index)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.import_column_unnamed, index + 1)

@Composable
private fun ImportItemCard(item: ImportItem) {
    val status = AuxoThemeExtras.statusColors
    AuxoCard(Modifier.fillMaxWidth().testTag("import_item_${item.rowNumbers.first()}")) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    item.name.ifEmpty { stringResource(R.string.import_row_unnamed, item.rowNumbers.first()) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                when (item.status) {
                    ImportStatus.READY -> StatusChip(stringResource(R.string.import_status_ready), status.success)
                    ImportStatus.INVALID -> StatusChip(stringResource(R.string.import_status_invalid), status.danger)
                    ImportStatus.EXISTS -> StatusChip(stringResource(R.string.import_status_exists), status.neutral)
                }
            }
            val category = item.category?.let { stringResource(it.labelRes()) }
            val variants = pluralStringResource(R.plurals.import_item_variants, item.variantCount, item.variantCount)
            Text(
                listOfNotNull(category, variants).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.issues.take(ISSUES_SHOWN).forEach { issue ->
                Text(
                    stringResource(
                        R.string.import_issue_row,
                        issue.rowNumber,
                        issue.column?.key.orEmpty(),
                        stringResource(issue.messageRes()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val more = item.issues.size - ISSUES_SHOWN
            if (more > 0) {
                Text(pluralStringResource(R.plurals.import_issue_more, more, more), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun ImportIssue.messageRes(): Int = when {
    fieldError != null -> fieldError.messageRes()
    else -> when (parseError) {
        ImportParseError.UNKNOWN_CATEGORY -> R.string.import_parse_unknown_category
        ImportParseError.UNKNOWN_DIET -> R.string.import_parse_unknown_diet
        ImportParseError.UNKNOWN_UNIT -> R.string.import_parse_unknown_unit
        ImportParseError.UNKNOWN_STOCK_UNIT -> R.string.import_parse_unknown_stock_unit
        ImportParseError.INVALID_DATE -> R.string.import_parse_invalid_date
        null -> R.string.error_generic
    }
}

@Composable
private fun ResultStage(state: ProductImportUiState, actions: ProductImportActions, modifier: Modifier) {
    val outcome = checkNotNull(state.outcome)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
            .testTag("import_result"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.import_done_title), style = MaterialTheme.typography.titleLarge)
        Text(
            pluralStringResource(R.plurals.import_done_added, outcome.imported, outcome.imported),
            modifier = Modifier.testTag("import_result_added"),
        )
        if (outcome.notImported > 0) {
            Text(
                stringResource(R.string.import_done_not_added, outcome.notImported, (outcome.conflicts + outcome.failed).joinToString(", ")),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("import_result_failed"),
            )
        }
        Button(onClick = actions::onDone, modifier = Modifier.fillMaxWidth().testTag("import_done")) {
            Text(stringResource(R.string.import_done_close))
        }
        OutlinedButton(onClick = actions::onStartOver, modifier = Modifier.fillMaxWidth().testTag("import_another")) {
            Text(stringResource(R.string.import_done_another))
        }
    }
}
