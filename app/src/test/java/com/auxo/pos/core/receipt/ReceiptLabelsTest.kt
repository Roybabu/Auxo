// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.receipt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Modifier

@RunWith(AndroidJUnit4::class)
class ReceiptLabelsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val placeholder = Regex("%\\d\\$[sd]")

    private fun values(labels: ReceiptLabels): Map<String, String> =
        ReceiptLabels::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to it.get(labels) as String }

    @Test
    fun theEnglishResources_matchTheBuiltInEnglishWording() {
        assertEquals(ReceiptLabels(), ReceiptLabels.from(context, Language.ENGLISH))
    }

    @Test
    fun everyWordingField_hasAResource_andNothingIsLeftOut() {
        // language and thankYou are the only fields that are not read from resources.
        assertEquals(values(ReceiptLabels()).size - 1, ReceiptLabels.resourceIds.size)
        assertEquals(ReceiptLabels.resourceIds.size, ReceiptLabels.resourceIds.toSet().size)
    }

    @Test
    fun everyLanguage_hasEveryReceiptWord_withTheSamePlaceholders() {
        val english = values(ReceiptLabels.from(context, Language.ENGLISH))
        for (language in Language.entries) {
            val labels = ReceiptLabels.from(context, language)
            assertEquals(language, labels.language)
            for ((name, value) in values(labels)) {
                if (name == "thankYou") continue
                assertTrue("$name is empty in ${language.tag}", value.isNotBlank())
                assertEquals(
                    "$name has different placeholders in ${language.tag}",
                    placeholder.findAll(english.getValue(name)).map { it.value }.sorted().toList(),
                    placeholder.findAll(value).map { it.value }.sorted().toList(),
                )
            }
        }
    }

    @Test
    fun hindiAndBengali_areNotJustEnglishRepeated() {
        val english = values(ReceiptLabels.from(context, Language.ENGLISH))
        for (language in listOf(Language.HINDI, Language.BENGALI)) {
            val translated = values(ReceiptLabels.from(context, language))
            val same = english.filter { (name, value) -> name != "thankYou" && translated[name] == value }.keys
            // Only abbreviations and format templates may stay the same.
            val allowed = setOf("gstin", "hsn", "gstRate", "igst", "cgst", "sgst", "methodUpi", "hsnHeading", "gstHeading", "qtyTimesPrice")
            assertEquals("Untranslated in ${language.tag}: ${same - allowed}", emptySet<String>(), same - allowed)
            assertNotEquals(english.getValue("taxInvoice"), translated.getValue("taxInvoice"))
        }
    }

    @Test
    fun theBillLanguage_isIndependentOfTheLanguageOfTheScreens() {
        val inHindi = com.auxo.pos.core.locale.LocaleContext.wrap(context, Language.HINDI)
        assertEquals(ReceiptLabels.from(context, Language.BENGALI), ReceiptLabels.from(inHindi, Language.BENGALI))
        assertFalse(ReceiptLabels.from(inHindi, Language.ENGLISH).taxInvoice == ReceiptLabels.from(inHindi, Language.HINDI).taxInvoice)
    }
}
