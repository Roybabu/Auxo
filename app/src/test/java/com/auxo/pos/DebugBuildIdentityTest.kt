package com.auxo.pos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.backup.DocumentsBackupStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The debug build must be able to sit on a phone beside the final Auxo: its own app ID and name, no update option and
 * its own backup folder. These tests run on the debug build, so they pin down what the debug build is; the final
 * build keeps the plain defaults (checked by hand and by the release build in CI).
 */
@RunWith(AndroidJUnit4::class)
class DebugBuildIdentityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun hasItsOwnAppId_soItInstallsBesideTheFinalBuild() {
        assertEquals("com.auxo.pos.debug", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun hasNoUpdateOption() {
        assertFalse(BuildConfig.UPDATES_ENABLED)
    }

    @Test
    fun keepsItsBackupsInItsOwnFolder() {
        assertEquals("Auxo Debug", BuildConfig.BACKUP_FOLDER)
        assertEquals("Auxo Debug", DocumentsBackupStorage.FOLDER)
    }

    @Test
    fun isNamedAuxoDebugUnderItsIcon_butStillAuxoInsideTheApp() {
        assertEquals("Auxo Debug", context.getString(R.string.launcher_label))
        // The logo's description and the sign-in page use this one, so it stays the plain name.
        assertEquals("Auxo", context.getString(R.string.app_name))
    }
}
