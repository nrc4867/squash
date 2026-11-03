package dev.chieppa.squash.character.load

private val pathSegment = Regex("""\.([^.\[]+)|\[(\d+)]""")

/** Finds the line of a JSON path such as `$.blocks[2].u` in a template file, comments and all. */
internal class JsonLines(private val text: String) {
    private var i = 0

    /** The 1-based line of [path], or of the deepest part of it that exists. */
    fun lineOf(path: String): Int {
        i = 0
        val segments = pathSegment.findAll(path).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
        skipSpace()
        descend(segments)
        return lineAt(i)
    }

    fun lineAt(offset: Int): Int = text.take(offset).count { it == '\n' } + 1

    private fun descend(segments: List<String>) {
        if (segments.isEmpty()) return
        val start = i
        val found = when (text.getOrNull(i)) {
            '{' -> findKey(segments.first())
            '[' -> findIndex(segments.first().toIntOrNull() ?: return)
            else -> false
        }
        if (found) descend(segments.drop(1)) else i = start
    }

    private fun findKey(key: String): Boolean {
        i++
        while (true) {
            skipSpace()
            if (text.getOrNull(i) != '"') return false
            val keyStart = i
            skipString()
            val name = text.substring(keyStart + 1, i - 1)
            skipSpace()
            i++
            skipSpace()
            if (name == key) return true
            skipValue()
            if (!skipComma()) return false
        }
    }

    private fun findIndex(index: Int): Boolean {
        i++
        var position = 0
        while (true) {
            skipSpace()
            if (text.getOrNull(i) == ']') return false
            if (position == index) return true
            skipValue()
            position++
            if (!skipComma()) return false
        }
    }

    private fun skipComma(): Boolean {
        skipSpace()
        if (text.getOrNull(i) != ',') return false
        i++
        return true
    }

    private fun skipValue() {
        when (text.getOrNull(i)) {
            '"' -> skipString()
            '{', '[' -> skipNested()
            else -> while (i < text.length && text[i] !in ",}] \t\r\n/") i++
        }
    }

    private fun skipNested() {
        var depth = 0
        do {
            skipSpace()
            when (text.getOrNull(i)) {
                '"' -> skipString()
                '{', '[' -> { depth++; i++ }
                '}', ']' -> { depth--; i++ }
                null -> return
                else -> i++
            }
        } while (depth > 0)
    }

    private fun skipString() {
        i++
        while (i < text.length && text[i] != '"') i += if (text[i] == '\\') 2 else 1
        i++
    }

    private fun skipSpace() {
        while (i < text.length) {
            when {
                text[i].isWhitespace() -> i++
                text.startsWith("//", i) -> while (i < text.length && text[i] != '\n') i++
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2)
                    i = if (end < 0) text.length else end + 2
                }
                else -> return
            }
        }
    }
}
