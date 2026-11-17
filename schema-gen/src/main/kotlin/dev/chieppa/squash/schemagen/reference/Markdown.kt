package dev.chieppa.squash.schemagen.reference

import java.util.Locale

/** Decimal places a reference number is written to before trailing zeros are cut. */
private const val PLACES = 3

/** A Markdown table of [rows] under [header]; pipes in cells are escaped. */
internal fun table(header: List<String>, rows: List<List<String>>): String = buildString {
    fun line(cells: List<String>) = appendLine("| " + cells.joinToString(" | ") { it.replace("|", "\\|") } + " |")
    line(header)
    appendLine("|" + header.joinToString("|") { " --- " } + "|")
    rows.forEach(::line)
}

/** [value] as a short, locale-free number: at most three decimals, no trailing zeros. */
internal fun num(value: Float): String {
    val text = String.format(Locale.ROOT, "%.${PLACES}f", value).trimEnd('0').trimEnd('.')
    return if (text == "-0") "0" else text
}

/** [values] as a bracketed list of numbers. */
internal fun nums(vararg values: Float): String = values.joinToString(", ", "[", "]") { num(it) }

/** Inline code. */
internal fun code(text: String) = "`$text`"
