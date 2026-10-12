package com.auxo.pos.core.receipt

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import java.text.BreakIterator

/** The scripts a receipt can contain that need a font of their own. Latin also covers digits and punctuation. */
enum class Script { LATIN, DEVANAGARI, BENGALI }

/** A stretch of text that is drawn with one script's font. */
data class ScriptRun(val text: String, val script: Script)

/**
 * Splits text into runs of one script each, so every run can be drawn with a font that has its letters. Spaces,
 * digits, punctuation, the rupee sign and the zero-width joiners that shape conjuncts belong to no script: they
 * stay with the letters before them (or after them at the start of a line), so a conjunct is never cut and a
 * price next to a Hindi word is drawn in the same font as that word. Text with no letters at all is Latin.
 */
object ScriptRuns {

    fun scriptOf(codePoint: Int): Script? = when {
        codePoint in 0x0900..0x097F && codePoint != 0x0964 && codePoint != 0x0965 -> Script.DEVANAGARI
        codePoint in 0xA8E0..0xA8FF -> Script.DEVANAGARI
        codePoint in 0x0980..0x09FF -> Script.BENGALI
        Character.isLetter(codePoint) && codePoint < 0x0250 -> Script.LATIN
        // Other scripts are left to the phone's own fallback fonts, so they stay with the text around them.
        else -> null
    }

    fun split(text: String): List<ScriptRun> {
        if (text.isEmpty()) return emptyList()
        // First pass: the script of each code point, or null for neutral ones.
        val points = text.codePoints().toArray()
        val scripts = arrayOfNulls<Script>(points.size)
        points.forEachIndexed { i, cp -> scripts[i] = scriptOf(cp) }
        // Neutral characters take the script before them, or the first script after them.
        var previous: Script? = null
        for (i in scripts.indices) {
            if (scripts[i] != null) previous = scripts[i] else scripts[i] = previous
        }
        val firstStrong = scripts.firstOrNull { it != null } ?: Script.LATIN
        for (i in scripts.indices) {
            if (scripts[i] == null) scripts[i] = firstStrong else break
        }
        val runs = mutableListOf<ScriptRun>()
        val current = StringBuilder()
        var currentScript = scripts[0]!!
        for (i in points.indices) {
            val script = scripts[i]!!
            if (script != currentScript) {
                runs += ScriptRun(current.toString(), currentScript)
                current.clear()
                currentScript = script
            }
            current.appendCodePoint(points[i])
        }
        runs += ScriptRun(current.toString(), currentScript)
        return runs
    }
}

/**
 * The fonts receipts are drawn with. Hindi and Bengali come from bundled Noto Sans fonts, so they print the same
 * on every phone and in every shared PDF instead of depending on what the phone happens to have. Latin text keeps
 * the system fonts (monospace on thermal rolls).
 */
class ReceiptFonts(private val bundled: Map<Pair<Script, Boolean>, Typeface>) {

    fun typeface(script: Script, bold: Boolean, mono: Boolean): Typeface =
        bundled[script to bold] ?: when {
            mono -> Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }

    /** True if [script] is drawn with a font of this app rather than whatever the phone falls back to. */
    fun hasBundled(script: Script): Boolean = bundled.containsKey(script to false) && bundled.containsKey(script to true)

    companion object {
        const val DEVANAGARI_REGULAR = "fonts/NotoSansDevanagari-Regular.ttf"
        const val DEVANAGARI_BOLD = "fonts/NotoSansDevanagari-Bold.ttf"
        const val BENGALI_REGULAR = "fonts/NotoSansBengali-Regular.ttf"
        const val BENGALI_BOLD = "fonts/NotoSansBengali-Bold.ttf"

        /** Every font file the app bundles. */
        val assetPaths = listOf(DEVANAGARI_REGULAR, DEVANAGARI_BOLD, BENGALI_REGULAR, BENGALI_BOLD)

        /** Only system fonts; what the phone has is used for Hindi and Bengali. */
        val system = ReceiptFonts(emptyMap())

        @Volatile
        private var loaded: ReceiptFonts? = null

        /** The bundled fonts, loaded once. A font that fails to load is skipped, and that script falls back to the system. */
        fun from(context: Context): ReceiptFonts =
            loaded ?: synchronized(this) { loaded ?: load(context.applicationContext.assets).also { loaded = it } }

        internal fun load(assets: AssetManager): ReceiptFonts {
            val map = mutableMapOf<Pair<Script, Boolean>, Typeface>()
            fun add(script: Script, bold: Boolean, path: String) {
                runCatching { Typeface.createFromAsset(assets, path) }.getOrNull()?.let { map[script to bold] = it }
            }
            add(Script.DEVANAGARI, false, DEVANAGARI_REGULAR)
            add(Script.DEVANAGARI, true, DEVANAGARI_BOLD)
            add(Script.BENGALI, false, BENGALI_REGULAR)
            add(Script.BENGALI, true, BENGALI_BOLD)
            return ReceiptFonts(map)
        }
    }
}

/**
 * A text style that draws and measures each script run with the right font. It stands in for a Paint wherever
 * receipts are drawn, because one Paint has one typeface. [mono] is for thermal rolls, where Latin text is monospaced.
 */
class ScriptPaint(
    private val fonts: ReceiptFonts,
    private val bold: Boolean = false,
    private val mono: Boolean = false,
    size: Float = 10f,
) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.BLACK
        textSize = size
    }

    var textSize: Float
        get() = paint.textSize
        set(value) {
            paint.textSize = value
        }

    fun measureText(text: String): Float {
        var width = 0f
        for (run in ScriptRuns.split(text)) {
            paint.typeface = fonts.typeface(run.script, bold, mono)
            width += paint.measureText(run.text)
        }
        return width
    }

    /** Draws [text] with its baseline at [y], starting at [x]. */
    fun draw(canvas: Canvas, text: String, x: Float, y: Float) {
        var at = x
        for (run in ScriptRuns.split(text)) {
            paint.typeface = fonts.typeface(run.script, bold, mono)
            canvas.drawText(run.text, at, y, paint)
            at += paint.measureText(run.text)
        }
    }

    /** [text] cut at a letter boundary, ending in an ellipsis, so that it is at most [width] wide. */
    fun fit(text: String, width: Float): String {
        if (measureText(text) <= width) return text
        val ellipsis = "…"
        val boundaries = BreakIterator.getCharacterInstance().apply { setText(text) }
        var end = text.length
        while (end > 0) {
            end = boundaries.preceding(end)
            if (end <= 0) return ""
            val candidate = text.substring(0, end).trimEnd() + ellipsis
            if (measureText(candidate) <= width) return candidate
        }
        return ""
    }
}
