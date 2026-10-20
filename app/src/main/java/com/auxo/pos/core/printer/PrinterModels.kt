package com.auxo.pos.core.printer

import android.content.Context
import android.content.SharedPreferences
import com.auxo.pos.domain.model.ReceiptPaper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/** How a print to the Bluetooth receipt printer ended. Everything except [PRINTED] needs something from the person. */
enum class PrintResult {
    PRINTED,

    /** No printer has been chosen in the printer settings. */
    NO_PRINTER,

    /** This phone has no Bluetooth. */
    BLUETOOTH_UNAVAILABLE,

    /** Bluetooth is switched off. */
    BLUETOOTH_OFF,

    /** The app may not use Bluetooth yet (Android 12 and later ask first). */
    PERMISSION_NEEDED,

    /** The chosen printer is no longer paired with this phone. */
    NOT_PAIRED,

    /** The printer did not answer: switched off, out of range or busy with another phone. */
    UNREACHABLE,

    /** The connection dropped while printing. */
    WRITE_FAILED,

    /** The paper chosen for the printer is not a roll (should not happen from the settings screen). */
    UNSUPPORTED_PAPER,

    /** The receipt could not be turned into a picture for the printer (for example the phone ran out of memory). */
    RENDER_FAILED,
}

/** The printer the shop chose. [address] is its Bluetooth address; [paper] decides how wide the receipt prints. */
data class SavedPrinter(
    val address: String,
    val name: String,
    val paper: ReceiptPaper = ReceiptPaper.THERMAL_58,
    /** Send a cut command after each receipt (only for printers with a cutter). */
    val cutPaper: Boolean = false,
)

/** The chosen printer belongs to the phone, not to the shop, so it is not part of backups. */
interface PrinterPreferences {
    val printer: StateFlow<SavedPrinter?>

    fun save(printer: SavedPrinter)

    fun forget()
}

class SharedPrefsPrinterPreferences(private val prefs: SharedPreferences) : PrinterPreferences {

    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val state = MutableStateFlow(read())
    override val printer: StateFlow<SavedPrinter?> = state.asStateFlow()

    override fun save(printer: SavedPrinter) {
        prefs.edit {
            putString(KEY_ADDRESS, printer.address)
            putString(KEY_NAME, printer.name)
            putString(KEY_PAPER, printer.paper.name)
            putBoolean(KEY_CUT, printer.cutPaper)
        }
        state.value = printer
    }

    override fun forget() {
        prefs.edit { clear() }
        state.value = null
    }

    private fun read(): SavedPrinter? {
        val address = prefs.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return null
        val paper = prefs.getString(KEY_PAPER, null)
            ?.let { name -> ReceiptPaper.entries.firstOrNull { it.name == name } }
            ?.takeIf { PrinterDots.widthFor(it) != null }
            ?: ReceiptPaper.THERMAL_58
        return SavedPrinter(address, prefs.getString(KEY_NAME, null).orEmpty().ifBlank { address }, paper, prefs.getBoolean(KEY_CUT, false))
    }

    companion object {
        const val FILE = "bluetooth_printer"
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
        const val KEY_PAPER = "paper"
        const val KEY_CUT = "cut"
    }
}

/** A device already paired with this phone. */
data class PairedDevice(val address: String, val name: String, val looksLikePrinter: Boolean)

/** What the phone's Bluetooth lets the app list right now. */
sealed interface DeviceList {
    data object Unavailable : DeviceList
    data object Off : DeviceList
    data object NeedsPermission : DeviceList
    data class Ready(val devices: List<PairedDevice>) : DeviceList
}

/** Looks at the phone's Bluetooth. Only devices that are already paired are listed. */
fun interface BluetoothDevices {
    fun list(): DeviceList
}

/** Sends bytes to a paired Bluetooth printer and reports how it went. */
fun interface PrinterTransport {
    suspend fun send(address: String, bytes: ByteArray): PrintResult
}

/** Names that printers commonly advertise, so they can be listed ahead of headphones and watches. */
internal fun looksLikePrinter(name: String?): Boolean {
    val text = name?.lowercase() ?: return false
    return PRINTER_HINTS.any { it in text }
}

private val PRINTER_HINTS = listOf("print", "pos", "rpp", "mtp", "pt-", "bt-", "spp", "thermal", "xp-", "zj-", "tm-", "gp-", "rongta", "epson", "mpt", "hs-", "sunmi", "tsc")

fun List<PairedDevice>.printersFirst(): List<PairedDevice> =
    sortedWith(compareByDescending<PairedDevice> { it.looksLikePrinter }.thenBy { it.name.lowercase() })
