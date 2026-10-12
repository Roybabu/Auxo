package com.invision.pos.ui.screens.billing

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invision.pos.core.printer.PrintResult
import com.invision.pos.core.printer.hasBluetoothPermission
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.ui.LocalReceiptPrinting
import com.invision.pos.ui.screens.more.messageRes
import kotlinx.coroutines.launch

/**
 * What the Bluetooth print button does, or null when there is no button: no printing service (previews, tests) or
 * no printer chosen in the printer settings. Asks for the Bluetooth permission when it is needed and then prints;
 * says in a message why when it could not.
 */
@Composable
fun rememberBluetoothPrint(snackbar: SnackbarHostState, logo: Bitmap?): ((Invoice, ReceiptOptions) -> Unit)? {
    val printing = LocalReceiptPrinting.current ?: return null
    val printer by printing.printer.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<Invoice, ReceiptOptions>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run(invoice: Invoice, options: ReceiptOptions) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = printing.printInvoice(invoice, options, logo)
                if (result != PrintResult.PRINTED) snackbar.showSnackbar(resources.getString(result.messageRes()))
            } finally {
                busy = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val retry = pending
        pending = null
        if (granted && retry != null) {
            run(retry.first, retry.second)
        } else if (!granted) {
            scope.launch { snackbar.showSnackbar(resources.getString(PrintResult.PERMISSION_NEEDED.messageRes())) }
        }
    }

    if (printer == null) return null
    return { invoice, options ->
        if (!context.hasBluetoothPermission()) {
            pending = invoice to options
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            run(invoice, options)
        }
    }
}
