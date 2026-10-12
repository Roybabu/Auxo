package com.invision.pos.core.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/** True when the app may talk to Bluetooth devices. Before Android 12 the install-time permission is enough. */
fun Context.hasBluetoothPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private fun Context.adapter(): BluetoothAdapter? =
    (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

/** Lists paired devices. Pairing itself is done in the phone's Bluetooth settings, so no scan permission is needed. */
class AndroidBluetoothDevices(context: Context) : BluetoothDevices {
    private val context = context.applicationContext

    @SuppressLint("MissingPermission")
    override fun list(): DeviceList {
        val adapter = context.adapter() ?: return DeviceList.Unavailable
        if (!context.hasBluetoothPermission()) return DeviceList.NeedsPermission
        return try {
            if (!adapter.isEnabled) return DeviceList.Off
            DeviceList.Ready(
                adapter.bondedDevices.orEmpty()
                    .map { PairedDevice(it.address, it.name?.takeIf { n -> n.isNotBlank() } ?: it.address, looksLikePrinter(it.name)) }
                    .printersFirst(),
            )
        } catch (_: SecurityException) {
            DeviceList.NeedsPermission
        }
    }
}

/** Prints over a classic Bluetooth serial (SPP) connection, which is what receipt printers speak. One print at a time. */
class AndroidPrinterTransport(context: Context) : PrinterTransport {
    private val context = context.applicationContext
    private val lock = Mutex()

    @SuppressLint("MissingPermission")
    override suspend fun send(address: String, bytes: ByteArray): PrintResult = lock.withLock {
        withContext(Dispatchers.IO) {
            val adapter = context.adapter() ?: return@withContext PrintResult.BLUETOOTH_UNAVAILABLE
            if (!context.hasBluetoothPermission()) return@withContext PrintResult.PERMISSION_NEEDED
            try {
                if (!adapter.isEnabled) return@withContext PrintResult.BLUETOOTH_OFF
                if (!BluetoothAdapter.checkBluetoothAddress(address)) return@withContext PrintResult.NOT_PAIRED
                val device = adapter.bondedDevices.orEmpty().firstOrNull { it.address.equals(address, ignoreCase = true) }
                    ?: return@withContext PrintResult.NOT_PAIRED
                // A running discovery slows a connection down and often makes it fail.
                runCatching { adapter.cancelDiscovery() }
                transmit({ RfcommLink(device.createRfcommSocketToServiceRecord(SPP_UUID)) }, bytes)
            } catch (_: SecurityException) {
                PrintResult.PERMISSION_NEEDED
            }
        }
    }

    companion object {
        /**
         * Opens a connection with [open], sends [bytes] in small pieces and always closes it again. Every way this
         * can go wrong comes back as a [PrintResult]: the connection could not be made (also if it fails in an
         * unexpected way), or it broke while sending. Only a cancelled print is allowed to throw.
         */
        internal suspend fun transmit(
            open: () -> PrinterLink,
            bytes: ByteArray,
            pauseMillis: Long = CHUNK_PAUSE_MILLIS,
            closeDelayMillis: Long = CLOSE_DELAY_MILLIS,
        ): PrintResult {
            var link: PrinterLink? = null
            try {
                // The link is kept before it connects, so it is closed even when connecting fails.
                try {
                    link = open()
                    link.connect()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    return PrintResult.UNREACHABLE
                } catch (e: SecurityException) {
                    throw e
                } catch (_: RuntimeException) {
                    return PrintResult.UNREACHABLE
                }
                val connected = link ?: return PrintResult.UNREACHABLE
                return try {
                    val out = connected.output()
                    var offset = 0
                    while (offset < bytes.size) {
                        val count = minOf(CHUNK, bytes.size - offset)
                        out.write(bytes, offset, count)
                        offset += count
                        // Small printers have little memory; give them time to print what they have.
                        delay(pauseMillis)
                    }
                    out.flush()
                    // Closing at once can drop the last lines.
                    delay(closeDelayMillis)
                    PrintResult.PRINTED
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    PrintResult.WRITE_FAILED
                } catch (e: SecurityException) {
                    throw e
                } catch (_: RuntimeException) {
                    PrintResult.WRITE_FAILED
                }
            } finally {
                link?.closeQuietly()
            }
        }

        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CHUNK = 1024
        const val CHUNK_PAUSE_MILLIS = 40L
        const val CLOSE_DELAY_MILLIS = 500L
    }
}

/** One connection to a printer, so the sending logic can be tested without Bluetooth. */
internal interface PrinterLink {
    @Throws(IOException::class)
    fun connect()

    @Throws(IOException::class)
    fun output(): OutputStream

    @Throws(IOException::class)
    fun close()
}

private fun PrinterLink.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
    } catch (_: RuntimeException) {
    }
}

private class RfcommLink(private val socket: BluetoothSocket) : PrinterLink {
    @SuppressLint("MissingPermission")
    override fun connect() = socket.connect()

    override fun output(): OutputStream = socket.outputStream

    override fun close() = socket.close()
}
