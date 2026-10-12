package com.invision.pos.testutil

import android.graphics.Bitmap
import android.net.Uri
import com.invision.pos.core.photos.PhotoSaveResult
import com.invision.pos.core.photos.ProductPhotoStore
import java.io.File

/** Keeps photo names in memory so tests can see which photo files exist, without any picture work. */
class FakeProductPhotoStore : ProductPhotoStore {
    val stored = linkedSetOf<String>()

    /** The next [save] fails once, as for a file that is not a picture. */
    var failNext = false

    private var counter = 0

    override suspend fun save(source: Uri): PhotoSaveResult {
        if (failNext) {
            failNext = false
            return PhotoSaveResult.Unreadable
        }
        val name = "%032x.jpg".format(++counter)
        stored += name
        return PhotoSaveResult.Saved(name)
    }

    override fun delete(fileName: String) {
        stored -= fileName
    }

    override suspend fun thumbnail(fileName: String, maxSidePx: Int): Bitmap? = null

    override fun cachedThumbnail(fileName: String, maxSidePx: Int): Bitmap? = null

    override fun files(): List<File> = emptyList()

    override fun replaceAll(photos: List<File>) = Unit
}
