// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.shop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.keepActive
import com.auxo.pos.viewmodel.StoreLogoViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

class LogoSizingTest {

    @Test
    fun fit_shrinksTheLongestSide_andKeepsTheRatio() {
        assertEquals(600 to 300, LogoSizing.fit(1200, 600))
        assertEquals(300 to 600, LogoSizing.fit(600, 1200))
        assertEquals(600 to 400, LogoSizing.fit(3000, 2000))
    }

    @Test
    fun fit_neverEnlargesASmallPicture() {
        assertEquals(120 to 60, LogoSizing.fit(120, 60))
        assertEquals(600 to 600, LogoSizing.fit(600, 600))
    }

    @Test
    fun sampleSize_keepsAtLeastTheTargetSize() {
        assertEquals(1, LogoSizing.sampleSize(500, 400))
        assertEquals(1, LogoSizing.sampleSize(1100, 800))
        assertEquals(2, LogoSizing.sampleSize(1200, 800))
        assertEquals(8, LogoSizing.sampleSize(4800, 3000))
    }

    @Test
    fun contain_fitsInsideTheBox_withoutStretching() {
        val (wide, wideHeight) = LogoSizing.contain(200, 100, 100f, 100f)
        assertEquals(100f, wide, 0.01f)
        assertEquals(50f, wideHeight, 0.01f)
        val (tall, tallHeight) = LogoSizing.contain(100, 200, 100f, 100f)
        assertEquals(50f, tall, 0.01f)
        assertEquals(100f, tallHeight, 0.01f)
        // A small picture is scaled to the box, keeping its ratio.
        val (small, smallHeight) = LogoSizing.contain(10, 5, 100f, 100f)
        assertEquals(2f, small / smallHeight, 0.001f)
    }

    @Test
    fun contain_isEmptyForAnInvalidBox() {
        assertEquals(0f to 0f, LogoSizing.contain(0, 10, 100f, 100f))
        assertEquals(0f to 0f, LogoSizing.contain(10, 10, 0f, 100f))
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreLogoRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stored get() = File(context.filesDir, "store_logo.png")

    private fun bitmap(width: Int, height: Int, color: Int = Color.RED) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun repository() = FileStoreLogoRepository(context, Dispatchers.Unconfined)

    @Before
    fun clean() {
        stored.delete()
        File(context.cacheDir, "pick.png").delete()
    }

    @Test
    fun startsEmpty() {
        assertNull(repository().logo.value)
    }

    @Test
    fun savedLogo_isShrunkWithItsRatio_andKeptAsAFile() = runTest {
        val repository = repository()
        assertEquals(LogoSaveResult.SAVED, repository.save(bitmap(1200, 600)))
        val logo = repository.logo.value
        assertNotNull(logo)
        assertEquals(600, logo!!.width)
        assertEquals(300, logo.height)
        assertTrue(stored.isFile)
    }

    @Test
    fun logo_survivesARestart() = runTest {
        repository().save(bitmap(200, 100))
        val reopened = repository().logo.value
        assertNotNull(reopened)
        assertEquals(200, reopened!!.width)
        assertEquals(100, reopened.height)
    }

    @Test
    fun replacing_swapsThePicture() = runTest {
        val repository = repository()
        repository.save(bitmap(200, 100))
        repository.save(bitmap(80, 80, Color.BLUE))
        assertEquals(80, repository.logo.value!!.width)
        assertEquals(80, repository().logo.value!!.width)
    }

    @Test
    fun removing_clearsTheLogoAndItsFile() = runTest {
        val repository = repository()
        repository.save(bitmap(200, 100))
        repository.remove()
        assertNull(repository.logo.value)
        assertFalse(stored.exists())
        assertNull(repository().logo.value)
    }

    @Test
    fun aDamagedFile_isTreatedAsNoLogo() {
        stored.writeText("this is not a picture")
        assertNull(repository().logo.value)
    }

    @Test
    fun aMissingFile_isTreatedAsNoLogo() = runTest {
        val repository = repository()
        repository.save(bitmap(50, 50))
        stored.delete()
        assertNull(repository().logo.value)
    }

    @Test
    fun aPictureChosenFromTheGallery_isReadShrunkAndSaved() = runTest {
        val source = File(context.cacheDir, "pick.png")
        source.outputStream().use { bitmap(1500, 500).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val repository = repository()
        assertEquals(LogoSaveResult.SAVED, repository.save(Uri.fromFile(source)))
        val logo = repository.logo.value!!
        assertEquals(3f, logo.width.toFloat() / logo.height, 0.05f)
        assertTrue(maxOf(logo.width, logo.height) <= LogoSizing.MAX_SIDE_PX)
    }

    @Test
    fun aFileThatIsNotAPicture_isRejected_andTheOldLogoStays() = runTest {
        val repository = repository()
        repository.save(bitmap(40, 40))
        val bad = File(context.cacheDir, "pick.png").apply { writeText("plain text") }
        assertEquals(LogoSaveResult.UNREADABLE, repository.save(Uri.fromFile(bad)))
        assertEquals(40, repository.logo.value!!.width)
    }

    @Test
    fun anInaccessibleUri_isRejected() = runTest {
        assertEquals(LogoSaveResult.UNREADABLE, repository().save(Uri.parse("content://auxo.missing/none")))
    }
}

private class FakeLogoRepository(private val result: LogoSaveResult = LogoSaveResult.SAVED) : StoreLogoRepository {
    private val state = MutableStateFlow<Bitmap?>(null)
    override val logo: StateFlow<Bitmap?> = state
    var saved = 0

    override suspend fun save(source: Uri): LogoSaveResult {
        saved++
        if (result == LogoSaveResult.SAVED) state.value = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        return result
    }

    override suspend fun save(bitmap: Bitmap): LogoSaveResult {
        state.value = bitmap
        return LogoSaveResult.SAVED
    }

    override suspend fun remove() {
        state.value = null
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreLogoViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val uri: Uri = Uri.parse("content://auxo.test/logo")

    @Test
    fun picking_uploadsTheLogo_andRemovingClearsIt() = runTest {
        val repository = FakeLogoRepository()
        val viewModel = StoreLogoViewModel(repository)
        keepActive(viewModel.uiState)
        assertNull(viewModel.uiState.value.logo)
        viewModel.onPicked(uri)
        assertNotNull(viewModel.uiState.value.logo)
        assertFalse(viewModel.uiState.value.unreadable)
        assertFalse(viewModel.uiState.value.isWorking)
        viewModel.onRemove()
        assertNull(viewModel.uiState.value.logo)
    }

    @Test
    fun backingOutOfThePicker_changesNothing() = runTest {
        val repository = FakeLogoRepository()
        val viewModel = StoreLogoViewModel(repository)
        viewModel.onPicked(null)
        assertEquals(0, repository.saved)
    }

    @Test
    fun anUnreadablePicture_isReported_andClearedByTheNextTry() = runTest {
        val repository = FakeLogoRepository(LogoSaveResult.UNREADABLE)
        val viewModel = StoreLogoViewModel(repository)
        keepActive(viewModel.uiState)
        viewModel.onPicked(uri)
        assertTrue(viewModel.uiState.value.unreadable)
        viewModel.onRemove()
        assertFalse(viewModel.uiState.value.unreadable)
    }
}
