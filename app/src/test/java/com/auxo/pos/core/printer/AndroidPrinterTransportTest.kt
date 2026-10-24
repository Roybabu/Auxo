// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.printer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** The sending logic of the Bluetooth transport, run on a fake connection (no Bluetooth is involved). */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidPrinterTransportTest {

    private class FakeLink(
        private val onConnect: () -> Unit = {},
        private val out: OutputStream = ByteArrayOutputStream(),
    ) : PrinterLink {
        var closed = 0
        override fun connect() {
            onConnect()
        }

        override fun output(): OutputStream = out

        override fun close() {
            closed++
        }
    }

    private class FailingOutput(private val error: () -> Exception) : OutputStream() {
        override fun write(b: Int) {
            throw error()
        }
    }

    private suspend fun send(link: PrinterLink, bytes: ByteArray = ByteArray(10)) =
        AndroidPrinterTransport.transmit({ link }, bytes, pauseMillis = 0, closeDelayMillis = 0)

    @Test
    fun aGoodConnection_getsEveryByteInOrder_andIsClosed() = runTest {
        val sink = ByteArrayOutputStream()
        val link = FakeLink(out = sink)
        val bytes = ByteArray(2_500) { (it % 251).toByte() }
        assertEquals(PrintResult.PRINTED, send(link, bytes))
        assertTrue(bytes.contentEquals(sink.toByteArray()))
        assertEquals(1, link.closed)
    }

    @Test
    fun dataIsWrittenInPiecesOfAtMostOneKilobyte() = runTest {
        val sizes = mutableListOf<Int>()
        val out = object : OutputStream() {
            override fun write(b: Int) {
                sizes += 1
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                sizes += len
            }
        }
        send(FakeLink(out = out), ByteArray(2_500))
        assertEquals(listOf(1024, 1024, 452), sizes)
    }

    @Test
    fun aConnectionThatFails_isClosedAndReportedAsUnreachable() = runTest {
        // The socket made before connect() must be closed too, or the printer stays busy.
        val link = FakeLink(onConnect = { throw IOException("read failed, socket might closed or timeout") })
        assertEquals(PrintResult.UNREACHABLE, send(link))
        assertEquals(1, link.closed)
    }

    @Test
    fun aSocketThatCannotBeCreated_isUnreachable() = runTest {
        val result = AndroidPrinterTransport.transmit({ throw IOException("no socket") }, ByteArray(1), 0, 0)
        assertEquals(PrintResult.UNREACHABLE, result)
    }

    @Test
    fun anUnexpectedErrorWhileConnecting_isUnreachable_notACrash() = runTest {
        val link = FakeLink(onConnect = { throw IllegalStateException("binder died") })
        assertEquals(PrintResult.UNREACHABLE, send(link))
        assertEquals(1, link.closed)
    }

    @Test
    fun aConnectionThatDropsWhileSending_isWriteFailed_andClosed() = runTest {
        val link = FakeLink(out = FailingOutput { IOException("Broken pipe") })
        assertEquals(PrintResult.WRITE_FAILED, send(link))
        assertEquals(1, link.closed)
    }

    @Test
    fun anUnexpectedErrorWhileSending_isWriteFailed_notACrash() = runTest {
        val link = FakeLink(out = FailingOutput { IllegalArgumentException("bad") })
        assertEquals(PrintResult.WRITE_FAILED, send(link))
        assertEquals(1, link.closed)
    }

    @Test
    fun missingPermission_isPassedUp_soTheCallerCanAskForIt() = runTest {
        val link = FakeLink(onConnect = { throw SecurityException("BLUETOOTH_CONNECT") })
        try {
            send(link)
            fail("expected SecurityException")
        } catch (_: SecurityException) {
            assertEquals(1, link.closed)
        }
    }

    @Test
    fun aCancelledPrint_closesTheConnection_andIsNotTurnedIntoAResult() = runTest {
        val link = FakeLink(onConnect = { throw CancellationException("cancelled") })
        try {
            send(link)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            assertEquals(1, link.closed)
        }
    }

    @Test
    fun aCloseThatFails_doesNotChangeTheResult() = runTest {
        val link = object : PrinterLink {
            override fun connect() {}

            override fun output(): OutputStream = ByteArrayOutputStream()

            override fun close() {
                throw IOException("already closed")
            }
        }
        assertEquals(PrintResult.PRINTED, send(link))
    }
}
