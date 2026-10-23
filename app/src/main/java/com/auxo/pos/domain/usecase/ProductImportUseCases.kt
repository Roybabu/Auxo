package com.auxo.pos.domain.usecase

import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.imports.ImportItem
import com.auxo.pos.domain.imports.ImportStatus
import kotlinx.coroutines.CancellationException

/** What an import did. Names are listed so the person can see which products did not go in. */
data class ImportOutcome(
    val imported: Int,
    /** Products whose barcode or SKU was taken by another product in the meantime. */
    val conflicts: List<String> = emptyList(),
    /** Products that could not be saved (for example the person is not allowed to add products). */
    val failed: List<String> = emptyList(),
) {
    val notImported: Int get() = conflicts.size + failed.size
}

/**
 * Saves the products of a checked import, one by one, through the same path as the product editor (so barcodes are
 * generated, permissions checked and the activity log written). A product that fails does not stop the others.
 */
class ImportProductsUseCase(private val saveProduct: SaveProductUseCase) {
    suspend operator fun invoke(items: List<ImportItem>): ImportOutcome {
        var imported = 0
        val conflicts = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (item in items) {
            val product = item.product?.takeIf { item.status == ImportStatus.READY } ?: continue
            val result = try {
                saveProduct(product)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.auxo.pos.core.debuglog.DebugLog.error("ProductImportUseCases", "Failed and showed an error", e)
                null
            }
            when (result) {
                is SaveProductResult.Saved -> imported++
                is SaveProductResult.Conflict -> conflicts += product.name
                null -> failed += product.name
            }
        }
        com.auxo.pos.core.debuglog.DebugLog.event("Import", "Products imported: $imported saved, ${conflicts.size} in conflict, ${failed.size} failed")
        return ImportOutcome(imported, conflicts, failed)
    }
}
