package com.invision.pos.core.imports

import android.content.Context
import android.net.Uri
import com.invision.pos.domain.imports.FileReadResult
import com.invision.pos.domain.imports.ImportFile
import com.invision.pos.domain.imports.ImportFileReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads a file chosen with the system file picker, never more than [ImportFile.MAX_BYTES] of it. */
class ContentResolverImportReader(context: Context) : ImportFileReader {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String): FileReadResult = withContext(Dispatchers.IO) {
        try {
            val bytes = resolver.openInputStream(Uri.parse(uri))?.use { input -> readAtMost(input, ImportFile.MAX_BYTES + 1) }
                ?: return@withContext FileReadResult.Unreadable
            if (bytes.size > ImportFile.MAX_BYTES) FileReadResult.TooLarge else FileReadResult.Text(ImportFile.decode(bytes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileReadResult.Unreadable
        }
    }

    /** Reads up to [limit] bytes (InputStream.readNBytes needs Android 13). */
    private fun readAtMost(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - total))
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
