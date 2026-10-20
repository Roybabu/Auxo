package com.auxo.pos.core.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import com.auxo.pos.core.shop.LogoSizing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

/** Rules for product photo files: where they live, what they are called and how big they get. */
object PhotoFiles {
    /** Folder inside the app's private storage. */
    const val DIRECTORY = "product_photos"

    /** The longest side a stored photo may have. Plenty for a product card, small enough for hundreds of products. */
    const val MAX_SIDE_PX = 800
    const val JPEG_QUALITY = 82

    /** Largest photo file taken from a backup, so a damaged or hostile file cannot fill the phone. */
    const val MAX_FILE_BYTES = 4L * 1024 * 1024

    /** Most photos taken from one backup. */
    const val MAX_FILES = 20_000

    private val NAME = Regex("[0-9a-f]{32}\\.jpg")

    /** Names are always 32 hex digits and ".jpg", so a name can never point outside the photo folder. */
    fun isValidName(name: String): Boolean = NAME.matches(name)

    fun newName(): String = UUID.randomUUID().toString().replace("-", "") + ".jpg"

    /** Zip entry of a photo inside a backup. */
    fun backupEntry(name: String): String = "$BACKUP_PREFIX$name"

    /** The photo name in a backup entry, or null when the entry is not a well-formed photo entry. */
    fun nameFromBackupEntry(entryName: String): String? =
        entryName.takeIf { it.startsWith(BACKUP_PREFIX) }?.removePrefix(BACKUP_PREFIX)?.takeIf(::isValidName)

    const val BACKUP_PREFIX = "photos/"
}

/** Turns the EXIF "orientation" a camera writes into the rotation needed to show the picture upright. */
object PhotoOrientation {
    fun degrees(exifOrientation: Int): Int = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

sealed interface PhotoSaveResult {
    data class Saved(val fileName: String) : PhotoSaveResult
    data object Unreadable : PhotoSaveResult
}

/** Product photos, kept as small JPEG files in the app's private storage (not in the database). */
interface ProductPhotoStore {
    /**
     * Reads the picture at [source] (camera or gallery), turns it upright, shrinks it, stores it under a new name
     * and returns the name. The source is never changed.
     */
    suspend fun save(source: Uri): PhotoSaveResult

    /** Removes a photo file (a quick file operation). A name that is not a photo name, or a file already gone, is ignored. */
    fun delete(fileName: String)

    /** A small copy of the photo for lists and the billing screen, or null if the file is missing or damaged. */
    suspend fun thumbnail(fileName: String, maxSidePx: Int): Bitmap?

    /** The thumbnail if it is already in memory, so a list does not flash a placeholder while scrolling. */
    fun cachedThumbnail(fileName: String, maxSidePx: Int): Bitmap?

    /** Every stored photo file, for backups. */
    fun files(): List<File>

    /**
     * Restore: replaces every stored photo with [photos]. Files with names that are not photo names are skipped.
     * Blocking file work, so call it off the main thread (restore already runs on the IO dispatcher).
     */
    fun replaceAll(photos: List<File>)
}

class FileProductPhotoStore(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ProductPhotoStore {

    private val directory: File get() = File(context.filesDir, PhotoFiles.DIRECTORY).also { it.mkdirs() }

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    override suspend fun save(source: Uri): PhotoSaveResult = withContext(io) {
        val bitmap = decode(source) ?: return@withContext PhotoSaveResult.Unreadable
        val name = PhotoFiles.newName()
        val target = File(directory, name)
        val temp = File(directory, "$name.tmp")
        try {
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, PhotoFiles.JPEG_QUALITY, it)) }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            PhotoSaveResult.Saved(name)
        } catch (e: IOException) {
            temp.delete()
            PhotoSaveResult.Unreadable
        } catch (e: IllegalStateException) {
            temp.delete()
            PhotoSaveResult.Unreadable
        }
    }

    override fun delete(fileName: String) {
        if (!PhotoFiles.isValidName(fileName)) return
        File(directory, fileName).delete()
        cache.evictAll()
    }

    override suspend fun thumbnail(fileName: String, maxSidePx: Int): Bitmap? {
        cachedThumbnail(fileName, maxSidePx)?.let { return it }
        if (!PhotoFiles.isValidName(fileName)) return null
        return withContext(io) {
            val file = File(directory, fileName)
            if (!file.isFile) return@withContext null
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                val options = BitmapFactory.Options().apply { inSampleSize = LogoSizing.sampleSize(bounds.outWidth, bounds.outHeight, maxSidePx) }
                BitmapFactory.decodeFile(file.path, options)?.also { cache.put(cacheKey(fileName, maxSidePx), it) }
            } catch (e: OutOfMemoryError) {
                null
            }
        }
    }

    override fun cachedThumbnail(fileName: String, maxSidePx: Int): Bitmap? = cache.get(cacheKey(fileName, maxSidePx))

    override fun files(): List<File> =
        directory.listFiles { file -> file.isFile && PhotoFiles.isValidName(file.name) }?.sortedBy { it.name }.orEmpty()

    override fun replaceAll(photos: List<File>) {
        directory.listFiles()?.forEach { it.delete() }
        for (photo in photos) {
            if (!PhotoFiles.isValidName(photo.name)) continue
            try {
                photo.copyTo(File(directory, photo.name), overwrite = true)
            } catch (e: IOException) {
                // A photo that cannot be copied is left out; the product keeps working without it.
            }
        }
        cache.evictAll()
    }

    private fun cacheKey(fileName: String, maxSidePx: Int) = "$fileName@$maxSidePx"

    /** The picture at [source], upright, no larger than [PhotoFiles.MAX_SIDE_PX], on white if it had see-through parts. */
    private fun decode(source: Uri): Bitmap? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = LogoSizing.sampleSize(bounds.outWidth, bounds.outHeight, PhotoFiles.MAX_SIDE_PX)
            }
            val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            decoded?.let { finish(it, rotation = orientationOf(source)) }
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

    private fun orientationOf(source: Uri): Int = try {
        context.contentResolver.openInputStream(source)?.use { stream ->
            PhotoOrientation.degrees(ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        } ?: 0
    } catch (e: IOException) {
        0
    } catch (e: RuntimeException) {
        0
    }

    private fun finish(decoded: Bitmap, rotation: Int): Bitmap {
        var bitmap = decoded
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        val (width, height) = LogoSizing.fit(bitmap.width, bitmap.height, PhotoFiles.MAX_SIDE_PX)
        if (width != bitmap.width || height != bitmap.height) bitmap = bitmap.scale(width, height)
        if (bitmap.hasAlpha()) {
            // JPEG has no transparency; without this, see-through parts of a PNG would turn black.
            val flat = createBitmap(bitmap.width, bitmap.height)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            bitmap = flat
        }
        return bitmap
    }

    private companion object {
        /** About 8 MB of decoded thumbnails kept in memory. */
        const val CACHE_BYTES = 8 * 1024 * 1024
    }
}
