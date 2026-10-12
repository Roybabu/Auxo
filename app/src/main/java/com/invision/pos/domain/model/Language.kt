package com.invision.pos.domain.model

import java.util.Locale

/**
 * A language the app and its bills can be shown in. [tag] is the language code that is saved and that names the
 * resource folder (`values-hi`, `values-bn`); never change one. Names are written in their own language so
 * someone can find theirs whatever language the app is showing.
 */
enum class Language(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    BENGALI("bn", "বাংলা"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        val DEFAULT = ENGLISH

        /** The language saved under [tag], or null for anything unknown. */
        fun fromTag(tag: String?): Language? = entries.firstOrNull { it.tag == tag }

        /** The supported language that matches [device]'s language, or English. */
        fun closestTo(device: Locale): Language = entries.firstOrNull { it.tag == device.language } ?: DEFAULT
    }
}
