// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.imports

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

sealed interface FileReadResult {
    data class Text(val text: String) : FileReadResult
    data object TooLarge : FileReadResult
    data object Unreadable : FileReadResult
}

/** Reads the file the person picked. The Android version uses the content resolver; tests use a fake. */
fun interface ImportFileReader {
    suspend fun read(uri: String): FileReadResult
}

object ImportFile {
    /** A product list of 1000 rows is far below this; the limit stops someone opening a huge file by mistake. */
    const val MAX_BYTES = 1_000_000

    /**
     * Excel's "CSV (Comma delimited)" is Windows-1252, while "CSV UTF-8" and most other tools write UTF-8. The bytes
     * are read as UTF-8 when they are valid UTF-8, and as Windows-1252 otherwise.
     */
    fun decode(bytes: ByteArray): String {
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
    }
}
