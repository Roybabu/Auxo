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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
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
                var socket: BluetoothSocket? = null
                try {
                    socket = try {
                        device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                    } catch (_: IOException) {
                        socket?.closeQuietly()
                        return@withContext PrintResult.UNREACHABLE
                    }
                    try {
                        val out = socket.outputStream
                        var offset = 0
                        while (offset < bytes.size) {
                            val count = minOf(CHUNK, bytes.size - offset)
                            out.write(bytes, offset, count)
                            offset += count
                            // Small printers have little memory; give them time to print what they have.
                            delay(CHUNK_PAUSE_MILLIS)
                        }
                        out.flush()
                        // Closing at once can drop the last lines.
                        delay(CLOSE_DELAY_MILLIS)
                        PrintResult.PRINTED
                    } catch (_: IOException) {
                        PrintResult.WRITE_FAILED
                    }
                } finally {
                    socket?.closeQuietly()
                }
            } catch (_: SecurityException) {
                PrintResult.PERMISSION_NEEDED
            }
        }
    }

    private fun BluetoothSocket.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CHUNK = 1024
        const val CHUNK_PAUSE_MILLIS = 40L
        const val CLOSE_DELAY_MILLIS = 500L
    }
}
