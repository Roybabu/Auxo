package com.invision.pos.core.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

class PhotoFilesTest {

    @Test
    fun aPhotoName_is32HexDigitsAndJpg() {
        assertTrue(PhotoFiles.isValidName("0123456789abcdef0123456789abcdef.jpg"))
        assertFalse(PhotoFiles.isValidName("0123456789ABCDEF0123456789ABCDEF.jpg"))
        assertFalse(PhotoFiles.isValidName("0123456789abcdef.jpg"))
        assertFalse(PhotoFiles.isValidName("0123456789abcdef0123456789abcdef.png"))
        assertFalse(PhotoFiles.isValidName("../0123456789abcdef0123456789abcdef.jpg"))
        assertFalse(PhotoFiles.isValidName("sub/0123456789abcdef0123456789abcdef.jpg"))
        assertFalse(PhotoFiles.isValidName(""))
    }

    @Test
    fun newNames_areValidAndDifferent() {
        val names = (1..50).map { PhotoFiles.newName() }
        assertTrue(names.all(PhotoFiles::isValidName))
        assertEquals(50, names.toSet().size)
    }

    @Test
    fun aBackupEntry_isReadBackOnlyWhenWellFormed() {
        val name = "0123456789abcdef0123456789abcdef.jpg"
        assertEquals("photos/$name", PhotoFiles.backupEntry(name))
        assertEquals(name, PhotoFiles.nameFromBackupEntry("photos/$name"))
        assertNull(PhotoFiles.nameFromBackupEntry("photos/../$name"))
        assertNull(PhotoFiles.nameFromBackupEntry("photos/sub/$name"))
        assertNull(PhotoFiles.nameFromBackupEntry("photos/evil.jpg"))
        assertNull(PhotoFiles.nameFromBackupEntry("other/$name"))
        assertNull(PhotoFiles.nameFromBackupEntry(name))
    }

    @Test
    fun cameraOrientation_becomesARotation() {
        assertEquals(0, PhotoOrientation.degrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(90, PhotoOrientation.degrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, PhotoOrientation.degrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, PhotoOrientation.degrees(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(0, PhotoOrientation.degrees(ExifInterface.ORIENTATION_UNDEFINED))
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileProductPhotoStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val folder = TemporaryFolder()

    private val store = FileProductPhotoStore(context, Dispatchers.Unconfined)
    private val photoDir get() = File(context.filesDir, PhotoFiles.DIRECTORY)
    private var sources = 0

    @Before
    fun clean() {
        photoDir.deleteRecursively()
    }

    private fun bitmap(width: Int, height: Int, color: Int = Color.RED): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    private fun source(bitmap: Bitmap, jpeg: Boolean = false): File {
        val file = folder.newFile("source-${sources++}.${if (jpeg) "jpg" else "png"}")
        file.outputStream().use { bitmap.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 95, it) }
        return file
    }

    private fun saved(result: PhotoSaveResult): File {
        assertTrue(result.toString(), result is PhotoSaveResult.Saved)
        return File(photoDir, (result as PhotoSaveResult.Saved).fileName)
    }

    private fun sizeOf(file: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    @Test
    fun aLargePicture_isShrunkToTheLimit_keepingItsShape_andStoredAsJpeg() = runTest {
        val file = saved(store.save(Uri.fromFile(source(bitmap(2000, 1000)))))

        assertTrue(PhotoFiles.isValidName(file.name))
        assertTrue(file.isFile)
        assertEquals(PhotoFiles.MAX_SIDE_PX to PhotoFiles.MAX_SIDE_PX / 2, sizeOf(file))
        val header = file.readBytes().take(2).map { it.toInt() and 0xFF }
        assertEquals(listOf(0xFF, 0xD8), header)
    }

    @Test
    fun aSmallPicture_isNotEnlarged() = runTest {
        val file = saved(store.save(Uri.fromFile(source(bitmap(300, 200)))))
        assertEquals(300 to 200, sizeOf(file))
    }

    @Test
    fun theOriginalPicture_isNeverChanged() = runTest {
        val original = source(bitmap(1600, 1200))
        val before = original.readBytes()
        store.save(Uri.fromFile(original))
        assertTrue(before.contentEquals(original.readBytes()))
    }

    @Test
    fun seeThroughParts_becomeWhite_notBlack() = runTest {
        val file = saved(store.save(Uri.fromFile(source(bitmap(100, 100, Color.TRANSPARENT)))))
        val pixel = BitmapFactory.decodeFile(file.path).getPixel(10, 10)
        assertTrue(Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240)
    }

    @Test
    fun aCameraPictureTurnedBy90Degrees_isStoredUpright() = runTest {
        val sideways = source(bitmap(400, 200), jpeg = true)
        ExifInterface(sideways.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val file = saved(store.save(Uri.fromFile(sideways)))
        assertEquals(200 to 400, sizeOf(file))
    }

    @Test
    fun somethingThatIsNotAPicture_isRefused_andLeavesNoFile() = runTest {
        val text = folder.newFile("notes.png").apply { writeText("this is not a picture") }
        assertEquals(PhotoSaveResult.Unreadable, store.save(Uri.fromFile(text)))
        assertEquals(PhotoSaveResult.Unreadable, store.save(Uri.fromFile(File(folder.root, "missing.png"))))
        assertTrue(store.files().isEmpty())
        assertTrue(photoDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aThumbnail_isNoLargerThanAsked_andIsKeptInMemoryAfterwards() = runTest {
        val name = saved(store.save(Uri.fromFile(source(bitmap(2000, 1000))))).name
        assertNull(store.cachedThumbnail(name, 200))

        val thumbnail = store.thumbnail(name, 200)!!
        assertTrue(maxOf(thumbnail.width, thumbnail.height) <= 200)
        assertTrue(thumbnail.width > thumbnail.height)
        assertNotNull(store.cachedThumbnail(name, 200))
    }

    @Test
    fun aThumbnailOfAMissingOrInvalidFile_isNull() = runTest {
        assertNull(store.thumbnail("0123456789abcdef0123456789abcdef.jpg", 100))
        assertNull(store.thumbnail("../../etc/passwd", 100))
    }

    @Test
    fun deleting_removesTheFile_butOnlyPhotoFiles() = runTest {
        val file = saved(store.save(Uri.fromFile(source(bitmap(100, 100)))))
        val outside = File(context.filesDir, "keep.txt").apply { writeText("keep me") }

        store.delete("../keep.txt")
        assertTrue(outside.exists())

        store.delete(file.name)
        assertFalse(file.exists())
        // Deleting again, or something that never existed, is fine.
        store.delete(file.name)
        outside.delete()
    }

    @Test
    fun files_listsOnlyPhotoFiles() = runTest {
        val a = saved(store.save(Uri.fromFile(source(bitmap(100, 100)))))
        photoDir.resolve("note.txt").writeText("x")
        photoDir.resolve("${PhotoFiles.newName()}.tmp").writeText("x")
        assertEquals(listOf(a.name), store.files().map { it.name })
    }

    @Test
    fun replacingAll_swapsThePhotos_andSkipsBadNames() = runTest {
        val old = saved(store.save(Uri.fromFile(source(bitmap(100, 100)))))
        val incoming = folder.newFolder("incoming")
        val keep = File(incoming, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val bad = File(incoming, "not-a-photo.jpg").apply { writeBytes(byteArrayOf(9)) }

        store.replaceAll(listOf(keep, bad))

        assertFalse(old.exists())
        assertEquals(listOf(keep.name), store.files().map { it.name })
        assertEquals(listOf<Byte>(1, 2, 3), store.files().single().readBytes().toList())
    }
}
