package com.auxo.pos.core.locale

import com.auxo.pos.domain.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every string the app has in English must exist in every other language, with the same placeholders, and the
 * plurals must cover the quantities that language needs. These read the resource files themselves, so a string
 * added to English and forgotten elsewhere fails here instead of showing English (or crashing) on a phone.
 */
class StringResourcesCompleteTest {

    private class Resources(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>, val rawNames: List<String>)

    private val placeholder = Regex("%(?:\\d+\\$)?[sdf]|%%")

    private fun file(language: Language): File {
        val folder = if (language == Language.DEFAULT) "values" else "values-${language.tag}"
        // Gradle runs unit tests from the module folder; fall back to the repository root for IDE runs.
        return listOf(File("src/main/res/$folder/strings.xml"), File("app/src/main/res/$folder/strings.xml")).first { it.exists() }
    }

    private fun load(language: Language): Resources {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(language))
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val names = mutableListOf<String>()
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            names += name
            when (node.tagName) {
                "string" -> strings[name] = node.textContent
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    plurals[name] = (0 until items.length).associate { (items.item(it) as Element).getAttribute("quantity") to items.item(it).textContent }
                }
            }
        }
        return Resources(strings, plurals, names)
    }

    private fun holders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    /** Every language except the English the strings are written in. A new Language must come with a full file. */
    private val translated get() = Language.entries.filter { it != Language.DEFAULT }

    @Test
    fun noLanguageDefinesTheSameNameTwice() {
        for (language in Language.entries) {
            val names = load(language).rawNames
            val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys
            assertTrue("${language.tag} repeats $duplicates", duplicates.isEmpty())
        }
    }

    @Test
    fun everyLanguageHasEveryEnglishString() {
        val english = load(Language.DEFAULT)
        for (language in translated) {
            val other = load(language)
            val missing = english.strings.keys - other.strings.keys
            val extra = other.strings.keys - english.strings.keys
            assertTrue("${language.tag} is missing ${missing.take(10)} (${missing.size})", missing.isEmpty())
            assertTrue("${language.tag} has strings English lacks: ${extra.take(10)}", extra.isEmpty())
        }
    }

    @Test
    fun everyLanguageHasEveryEnglishPlural_withTheQuantitiesItNeeds() {
        val english = load(Language.DEFAULT)
        for (language in translated) {
            val other = load(language)
            assertEquals("${language.tag} plurals", english.plurals.keys, other.plurals.keys)
            for ((name, items) in other.plurals) {
                // Hindi and Bengali use "one" (0 and 1) and "other".
                assertEquals("${language.tag} $name", setOf("one", "other"), items.keys)
                items.values.forEach { assertTrue("${language.tag} $name has an empty item", it.isNotBlank()) }
            }
        }
    }

    @Test
    fun placeholdersMatchEnglish() {
        val english = load(Language.DEFAULT)
        for (language in translated) {
            val other = load(language)
            for ((name, value) in english.strings) {
                assertEquals("${language.tag} $name", holders(value), holders(other.strings.getValue(name)))
            }
            for ((name, items) in english.plurals) {
                // A plural may drop the number from its "one" form, but never add a placeholder English lacks.
                val allowed = items.values.flatMap { holders(it) }.toSet()
                other.plurals.getValue(name).forEach { (quantity, text) ->
                    assertTrue("${language.tag} $name/$quantity", holders(text).all { it in allowed })
                }
            }
        }
    }

    @Test
    fun noTranslationIsBlank_orHasAnUnescapedApostrophe() {
        for (language in translated) {
            val raw = file(language).readText()
            assertTrue("${language.tag} has an unescaped apostrophe", Regex("(?<!\\\\)'(?![^<]*-->)").findAll(raw.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")).none())
            load(language).strings.forEach { (name, value) -> assertTrue("${language.tag} $name is blank", value.isNotBlank()) }
        }
    }

    @Test
    fun aTranslationIsNotJustTheEnglishCopied() {
        val english = load(Language.DEFAULT).strings
        for (language in translated) {
            val other = load(language).strings
            val same = english.filter { (name, value) -> other[name] == value && value.any { it.isLetter() } }
            // Names such as Auxo and abbreviations such as UPI or CGST stay as they are.
            assertTrue("${language.tag} left ${same.size} strings in English: ${same.keys.take(15)}", same.size <= english.size / 10)
        }
    }

    @Test
    fun everyLanguageHasAResourceFolder() {
        Language.entries.forEach { assertTrue("no strings for ${it.tag}", file(it).exists()) }
    }
}
