package com.invision.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.local.toDomain
import com.invision.pos.data.local.toEntity
import com.invision.pos.data.repository.RoomSettingsRepository
import com.invision.pos.domain.model.Language
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillLanguageStorageTest {

    private lateinit var database: InvisionDatabase
    private lateinit var settings: RoomSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InvisionDatabase::class.java).allowMainThreadQueries().build()
        settings = RoomSettingsRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun aShopThatNeverChoseOne_printsInEnglish() = runTest {
        assertEquals(Language.ENGLISH, settings.getSettings().receipt.language)
    }

    @Test
    fun theChosenBillLanguage_isSaved_andReadBack() = runTest {
        for (language in Language.entries) {
            settings.saveSettings(TestData.settings.let { it.copy(receipt = it.receipt.copy(language = language)) })
            assertEquals(language, settings.getSettings().receipt.language)
        }
    }

    @Test
    fun savingOtherSettings_keepsTheBillLanguage() = runTest {
        settings.saveSettings(TestData.settings.let { it.copy(receipt = it.receipt.copy(language = Language.BENGALI)) })
        val saved = settings.getSettings()
        settings.saveSettings(saved.copy(storeName = "Roy Mart 2"))
        assertEquals(Language.BENGALI, settings.getSettings().receipt.language)
    }

    @Test
    fun aMissingOrUnknownValue_meansEnglish() {
        val entity = TestData.settings.toEntity()
        assertEquals(Language.ENGLISH, entity.copy(receiptLanguage = null).toDomain().receipt.language)
        assertEquals(Language.ENGLISH, entity.copy(receiptLanguage = "xx").toDomain().receipt.language)
        assertEquals(Language.HINDI, entity.copy(receiptLanguage = "hi").toDomain().receipt.language)
        assertNull(null)
    }
}
