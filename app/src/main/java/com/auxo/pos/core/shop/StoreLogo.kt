package com.auxo.pos.core.shop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Size rules for the store logo. All of them keep the picture's aspect ratio. */
object LogoSizing {
    /** The longest side a stored logo may have. Plenty for the screen and for a thermal roll. */
    const val MAX_SIDE_PX = 600

    /** The size to store a [width] x [height] picture at: shrunk to fit [maxSide], never enlarged. */
    fun fit(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toFloat() / longest
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /** The power-of-two sample size that keeps the decoded picture at least [maxSide] on its longest side. */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxSide) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /** The largest size with the picture's ratio that fits inside [boxWidth] x [boxHeight]. */
    fun contain(width: Int, height: Int, boxWidth: Float, boxHeight: Float): Pair<Float, Float> {
        if (width <= 0 || height <= 0 || boxWidth <= 0f || boxHeight <= 0f) return 0f to 0f
        val scale = minOf(boxWidth / width, boxHeight / height)
        return width * scale to height * scale
    }
}

enum class LogoSaveResult { SAVED, UNREADABLE }

/** The shop's logo, kept as a small picture file in the app's own storage (not in the database). */
interface StoreLogoRepository {
    /** The saved logo, or null when none was added or its file is missing or damaged. */
    val logo: StateFlow<Bitmap?>

    /** Reads the picture at [source], shrinks it, stores it and makes it the logo. */
    suspend fun save(source: Uri): LogoSaveResult

    /** Takes a picture that is already in memory (used by tests and restore). */
    suspend fun save(bitmap: Bitmap): LogoSaveResult

    suspend fun remove()
}

class FileStoreLogoRepository(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : StoreLogoRepository {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    private val state = MutableStateFlow(readFile())
    override val logo: StateFlow<Bitmap?> = state.asStateFlow()

    override suspend fun save(source: Uri): LogoSaveResult = withContext(io) {
        val bitmap = decode(source) ?: return@withContext LogoSaveResult.UNREADABLE
        store(bitmap)
    }

    override suspend fun save(bitmap: Bitmap): LogoSaveResult = withContext(io) {
        val (width, height) = LogoSizing.fit(bitmap.width, bitmap.height)
        val sized = if (width == bitmap.width && height == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        store(sized)
    }

    override suspend fun remove() {
        withContext(io) {
            file.delete()
            state.value = null
        }
    }

    private fun store(bitmap: Bitmap): LogoSaveResult {
        val target = file
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        return try {
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            state.value = bitmap
            LogoSaveResult.SAVED
        } catch (e: IOException) {
            temp.delete()
            LogoSaveResult.UNREADABLE
        } catch (e: IllegalStateException) {
            temp.delete()
            LogoSaveResult.UNREADABLE
        }
    }

    private fun decode(source: Uri): Bitmap? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply { inSampleSize = LogoSizing.sampleSize(bounds.outWidth, bounds.outHeight) }
            val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            decoded?.let {
                val (width, height) = LogoSizing.fit(it.width, it.height)
                if (width == it.width && height == it.height) it else Bitmap.createScaledBitmap(it, width, height, true)
            }
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** The stored logo, or null when the file is absent or cannot be read as a picture. */
    private fun readFile(): Bitmap? {
        val stored = file
        if (!stored.isFile) return null
        return try {
            BitmapFactory.decodeFile(stored.path)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private companion object {
        const val FILE_NAME = "store_logo.png"
    }
}
