package com.auxo.pos.domain.billing


/**
 * The words used to spell out an amount in one language, with the Indian places (crore, lakh, thousand, hundred).
 * Hindi and Bengali count 1 to 99 with words of their own that cannot be built from tens and ones, so each has a
 * full table. [below100] is indexed by the number itself.
 */
internal class NumberWords(
    private val below100: List<String>,
    private val hundred: String,
    private val thousand: String,
    private val lakh: String,
    private val crore: String,
    /** How a whole number of rupees, the paise and the closing word are put together. */
    private val sentence: (rupees: String, paise: String?) -> String,
) {
    init {
        require(below100.size == 100)
    }

    fun belowHundred(n: Int): String = below100[n]

    fun rupees(rupees: Long, paise: Int): String =
        sentence(if (rupees == 0L) below100[0] else words(rupees), if (paise > 0) below100[paise] else null)

    /** Words for a whole number using crore, lakh, thousand and hundred. */
    fun words(number: Long): String {
        require(number >= 0)
        if (number == 0L) return below100[0]
        val parts = mutableListOf<String>()
        var n = number
        val croreCount = n / 1_00_00_000
        n %= 1_00_00_000
        if (croreCount > 0) parts += words(croreCount) + " " + crore
        val lakhCount = (n / 1_00_000).toInt()
        n %= 1_00_000
        if (lakhCount > 0) parts += below100[lakhCount] + " " + lakh
        val thousandCount = (n / 1_000).toInt()
        n %= 1_000
        if (thousandCount > 0) parts += below100[thousandCount] + " " + thousand
        val hundredCount = (n / 100).toInt()
        n %= 100
        if (hundredCount > 0) parts += below100[hundredCount] + " " + hundred
        if (n > 0) parts += below100[n.toInt()]
        return parts.joinToString(" ")
    }

    companion object {
        val HINDI = NumberWords(
            below100 = listOf(
        "शून्य",
        "एक",
        "दो",
        "तीन",
        "चार",
        "पाँच",
        "छह",
        "सात",
        "आठ",
        "नौ",
        "दस",
        "ग्यारह",
        "बारह",
        "तेरह",
        "चौदह",
        "पंद्रह",
        "सोलह",
        "सत्रह",
        "अठारह",
        "उन्नीस",
        "बीस",
        "इक्कीस",
        "बाईस",
        "तेईस",
        "चौबीस",
        "पच्चीस",
        "छब्बीस",
        "सत्ताईस",
        "अट्ठाईस",
        "उनतीस",
        "तीस",
        "इकतीस",
        "बत्तीस",
        "तैंतीस",
        "चौंतीस",
        "पैंतीस",
        "छत्तीस",
        "सैंतीस",
        "अड़तीस",
        "उनतालीस",
        "चालीस",
        "इकतालीस",
        "बयालीस",
        "तैंतालीस",
        "चौवालीस",
        "पैंतालीस",
        "छियालीस",
        "सैंतालीस",
        "अड़तालीस",
        "उनचास",
        "पचास",
        "इक्यावन",
        "बावन",
        "तिरपन",
        "चौवन",
        "पचपन",
        "छप्पन",
        "सत्तावन",
        "अट्ठावन",
        "उनसठ",
        "साठ",
        "इकसठ",
        "बासठ",
        "तिरसठ",
        "चौंसठ",
        "पैंसठ",
        "छियासठ",
        "सड़सठ",
        "अड़सठ",
        "उनहत्तर",
        "सत्तर",
        "इकहत्तर",
        "बहत्तर",
        "तिहत्तर",
        "चौहत्तर",
        "पचहत्तर",
        "छिहत्तर",
        "सतहत्तर",
        "अठहत्तर",
        "उन्यासी",
        "अस्सी",
        "इक्यासी",
        "बयासी",
        "तिरासी",
        "चौरासी",
        "पचासी",
        "छियासी",
        "सत्तासी",
        "अट्ठासी",
        "नवासी",
        "नब्बे",
        "इक्यानवे",
        "बानवे",
        "तिरानवे",
        "चौरानवे",
        "पंचानवे",
        "छियानवे",
        "सत्तानवे",
        "अट्ठानवे",
        "निन्यानवे"
            ),
            hundred = "सौ", thousand = "हज़ार", lakh = "लाख", crore = "करोड़",
            sentence = { rupees, paise -> rupees + " रुपये" + (paise?.let { " और $it पैसे" } ?: "") + " मात्र" },
        )

        val BENGALI = NumberWords(
            below100 = listOf(
        "শূন্য",
        "এক",
        "দুই",
        "তিন",
        "চার",
        "পাঁচ",
        "ছয়",
        "সাত",
        "আট",
        "নয়",
        "দশ",
        "এগারো",
        "বারো",
        "তেরো",
        "চৌদ্দ",
        "পনেরো",
        "ষোলো",
        "সতেরো",
        "আঠারো",
        "ঊনিশ",
        "কুড়ি",
        "একুশ",
        "বাইশ",
        "তেইশ",
        "চব্বিশ",
        "পঁচিশ",
        "ছাব্বিশ",
        "সাতাশ",
        "আটাশ",
        "ঊনত্রিশ",
        "ত্রিশ",
        "একত্রিশ",
        "বত্রিশ",
        "তেত্রিশ",
        "চৌত্রিশ",
        "পঁয়ত্রিশ",
        "ছত্রিশ",
        "সাঁইত্রিশ",
        "আটত্রিশ",
        "ঊনচল্লিশ",
        "চল্লিশ",
        "একচল্লিশ",
        "বিয়াল্লিশ",
        "তেতাল্লিশ",
        "চুয়াল্লিশ",
        "পঁয়তাল্লিশ",
        "ছেচল্লিশ",
        "সাতচল্লিশ",
        "আটচল্লিশ",
        "ঊনপঞ্চাশ",
        "পঞ্চাশ",
        "একান্ন",
        "বাহান্ন",
        "তিপ্পান্ন",
        "চুয়ান্ন",
        "পঞ্চান্ন",
        "ছাপ্পান্ন",
        "সাতান্ন",
        "আটান্ন",
        "ঊনষাট",
        "ষাট",
        "একষট্টি",
        "বাষট্টি",
        "তেষট্টি",
        "চৌষট্টি",
        "পঁয়ষট্টি",
        "ছেষট্টি",
        "সাতষট্টি",
        "আটষট্টি",
        "ঊনসত্তর",
        "সত্তর",
        "একাত্তর",
        "বাহাত্তর",
        "তিয়াত্তর",
        "চুয়াত্তর",
        "পঁচাত্তর",
        "ছিয়াত্তর",
        "সাতাত্তর",
        "আটাত্তর",
        "ঊনআশি",
        "আশি",
        "একাশি",
        "বিরাশি",
        "তিরাশি",
        "চুরাশি",
        "পঁচাশি",
        "ছিয়াশি",
        "সাতাশি",
        "অষ্টাশি",
        "ঊননব্বই",
        "নব্বই",
        "একানব্বই",
        "বিরানব্বই",
        "তিরানব্বই",
        "চুরানব্বই",
        "পঁচানব্বই",
        "ছিয়ানব্বই",
        "সাতানব্বই",
        "আটানব্বই",
        "নিরানব্বই"
            ),
            hundred = "শত", thousand = "হাজার", lakh = "লক্ষ", crore = "কোটি",
            sentence = { rupees, paise -> rupees + " টাকা" + (paise?.let { " এবং $it পয়সা" } ?: "") + " মাত্র" },
        )
    }
}
