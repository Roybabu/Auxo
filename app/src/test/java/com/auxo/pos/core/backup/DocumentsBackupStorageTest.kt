package com.auxo.pos.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Android 8 (the oldest supported) has no Documents write access, so backups go to the app's own folder. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class DocumentsBackupStorageTest {

    @Test
    fun onAndroid8_theBackupIsSavedInTheAppFolder_withTheContentsWritten() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = DocumentsBackupStorage(context).save("Auxo-backup-test.zip") { it.write("zip-bytes".toByteArray()) }

        assertEquals("Auxo-backup-test.zip", saved.fileName)
        assertTrue(saved.folder.endsWith("files/Auxo"))
        val file = File(File(context.getExternalFilesDir(null), "Auxo"), "Auxo-backup-test.zip")
        assertEquals("zip-bytes", file.readText())
    }
}
