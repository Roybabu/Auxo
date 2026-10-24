// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.domain.imports.ColumnMapping
import com.auxo.pos.domain.imports.CsvParseResult
import com.auxo.pos.domain.imports.CsvParser
import com.auxo.pos.domain.imports.CsvTable
import com.auxo.pos.domain.imports.FileReadResult
import com.auxo.pos.domain.imports.ImportColumn
import com.auxo.pos.domain.imports.ImportFileReader
import com.auxo.pos.domain.imports.ImportOptions
import com.auxo.pos.domain.imports.ImportPreview
import com.auxo.pos.domain.imports.ProductImport
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.usecase.ImportOutcome
import com.auxo.pos.domain.usecase.ImportProductsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImportReadError { UNREADABLE, TOO_LARGE, EMPTY, TOO_MANY_ROWS, MALFORMED }

data class ProductImportUiState(
    val isReading: Boolean = false,
    val fileName: String? = null,
    val readError: ImportReadError? = null,
    val table: CsvTable? = null,
    val mapping: ColumnMapping = ColumnMapping(),
    val defaultCategory: ProductCategory? = null,
    val preview: ImportPreview? = null,
    val confirmOpen: Boolean = false,
    val isImporting: Boolean = false,
    /** Set once products have been saved; the screen then shows the result instead of the preview. */
    val outcome: ImportOutcome? = null,
) {
    val canImport: Boolean get() = preview?.canImport == true && !isImporting
}

/**
 * Product import from a CSV file: pick the file, check how its columns line up, look at the preview, then confirm.
 * Nothing is saved until the person confirms, and only products with no problems are saved.
 */
class ProductImportViewModel(
    private val repository: InventoryRepository,
    private val importProducts: ImportProductsUseCase,
    private val reader: ImportFileReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductImportUiState())
    val uiState: StateFlow<ProductImportUiState> = _uiState.asStateFlow()

    private var existing: List<Product> = emptyList()

    fun onFilePicked(uri: String, displayName: String? = null) {
        if (_uiState.value.isReading || _uiState.value.isImporting) return
        _uiState.value = ProductImportUiState(isReading = true, fileName = displayName)
        viewModelScope.launch {
            val failure = load(uri)
            _uiState.update { it.copy(isReading = false, readError = failure) }
        }
    }

    private suspend fun load(uri: String): ImportReadError? {
        val text = when (val read = reader.read(uri)) {
            is FileReadResult.Text -> read.text
            FileReadResult.TooLarge -> return ImportReadError.TOO_LARGE
            FileReadResult.Unreadable -> return ImportReadError.UNREADABLE
        }
        val table = when (val parsed = CsvParser.parse(text)) {
            is CsvParseResult.Parsed -> parsed.table
            CsvParseResult.Empty -> return ImportReadError.EMPTY
            is CsvParseResult.TooManyRows -> return ImportReadError.TOO_MANY_ROWS
            CsvParseResult.Malformed -> return ImportReadError.MALFORMED
        }
        existing = loadExisting() ?: return ImportReadError.UNREADABLE
        val mapping = ColumnMapping.auto(table.header)
        _uiState.update { it.copy(table = table, mapping = mapping, preview = previewOf(table, mapping, it.defaultCategory)) }
        return null
    }

    private suspend fun loadExisting(): List<Product>? = try {
        repository.observeProducts().first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        com.auxo.pos.core.debuglog.DebugLog.error("ProductImportViewModel", "Failed and showed an error", e)
        null
    }

    fun onMappingChange(column: ImportColumn, index: Int?) {
        _uiState.update { state ->
            val table = state.table ?: return@update state
            val base = state.mapping
            // A file column feeds one field only: choosing it here frees it elsewhere.
            val freed = if (index == null) base else base.indexes.filter { it.value == index && it.key != column }.keys.fold(base) { m, c -> m.with(c, null) }
            val mapping = freed.with(column, index)
            state.copy(mapping = mapping, preview = previewOf(table, mapping, state.defaultCategory))
        }
    }

    fun onDefaultCategoryChange(category: ProductCategory?) {
        _uiState.update { state ->
            val table = state.table ?: return@update state
            state.copy(defaultCategory = category, preview = previewOf(table, state.mapping, category))
        }
    }

    fun onImport() {
        if (_uiState.value.canImport) _uiState.update { it.copy(confirmOpen = true) }
    }

    fun onDismissConfirm() = _uiState.update { it.copy(confirmOpen = false) }

    fun onConfirmImport() {
        val state = _uiState.value
        val table = state.table ?: return
        if (state.isImporting || state.outcome != null) return
        _uiState.update { it.copy(confirmOpen = false, isImporting = true) }
        viewModelScope.launch {
            // Products may have been added since the file was read, so check against the current list again.
            existing = loadExisting() ?: existing
            val fresh = previewOf(table, state.mapping, state.defaultCategory)
            val outcome = importProducts(fresh.ready)
            _uiState.update { it.copy(isImporting = false, outcome = outcome, preview = fresh) }
        }
    }

    fun onStartOver() {
        if (_uiState.value.isImporting) return
        _uiState.value = ProductImportUiState()
    }

    private fun previewOf(table: CsvTable, mapping: ColumnMapping, defaultCategory: ProductCategory?): ImportPreview =
        if (mapping.isComplete) {
            ProductImport.preview(table, mapping, ImportOptions(defaultCategory), existing)
        } else {
            ImportPreview(emptyList(), table.rows.size)
        }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ProductImportViewModel(
                    repository = container.inventoryRepository,
                    importProducts = container.importProducts,
                    reader = container.importFileReader,
                )
            }
        }
    }
}
