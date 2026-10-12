package com.auxo.pos.ui.navigation

import android.net.Uri
import com.auxo.pos.domain.inventory.StockFilter
import com.auxo.pos.viewmodel.InventoryViewModel
import com.auxo.pos.viewmodel.ProductDetailViewModel
import com.auxo.pos.viewmodel.ProductEditorViewModel

/** Routes inside the Inventory tab. The tab's graph route is [TopLevelDestination.INVENTORY]. */
object InventoryRoutes {
    private const val PREFIX = "inventory"

    const val ARG_STOCK_FILTER = InventoryViewModel.ARG_STOCK_FILTER
    const val ARG_PRODUCT_ID = ProductDetailViewModel.ARG_PRODUCT_ID
    const val ARG_BARCODE = ProductEditorViewModel.ARG_BARCODE

    const val LIST = "$PREFIX/list?$ARG_STOCK_FILTER={$ARG_STOCK_FILTER}"
    const val PRODUCT = "$PREFIX/product/{$ARG_PRODUCT_ID}"
    const val EDITOR = "$PREFIX/editor?$ARG_PRODUCT_ID={$ARG_PRODUCT_ID}&$ARG_BARCODE={$ARG_BARCODE}"
    const val LABELS = "$PREFIX/labels/{$ARG_PRODUCT_ID}"

    fun list(stockFilter: StockFilter): String = "$PREFIX/list?$ARG_STOCK_FILTER=${stockFilter.name}"

    fun product(productId: Long): String = "$PREFIX/product/$productId"

    fun newProduct(barcode: String? = null): String =
        if (barcode.isNullOrBlank()) "$PREFIX/editor" else "$PREFIX/editor?$ARG_BARCODE=${Uri.encode(barcode)}"

    fun editProduct(productId: Long): String = "$PREFIX/editor?$ARG_PRODUCT_ID=$productId"

    fun labels(productId: Long): String = "$PREFIX/labels/$productId"
}
