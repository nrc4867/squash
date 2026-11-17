package dev.chieppa.squash.recipe

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The widest a line of packed small values runs. */
private const val WIDTH = 120

/** The widest a small value, a point or a bone, is kept on one line. */
private const val SMALL = 90

private const val INDENT = "  "

/**
 * Prints JSON for a person to read: objects a key a line, but points, colours and other small values on one line, and lists of
 * them packed several to a line.
 */
internal object CompactJson {
    fun print(element: JsonElement): String = buildString { write(element, "") }

    private fun StringBuilder.write(element: JsonElement, indent: String) {
        val small = inline(element)
        when {
            small != null -> append(small)
            element is JsonArray -> writeArray(element, indent)
            element is JsonObject -> writeObject(element, indent)
        }
    }

    private fun StringBuilder.writeObject(element: JsonObject, indent: String) {
        val inner = indent + INDENT
        append("{\n")
        element.entries.forEachIndexed { i, (key, value) ->
            append(inner).append(JsonPrimitive(key)).append(": ")
            write(value, inner)
            if (i < element.size - 1) append(",")
            append("\n")
        }
        append(indent).append("}")
    }

    private fun StringBuilder.writeArray(element: JsonArray, indent: String) {
        val inner = indent + INDENT
        val items = element.map(::inline)
        append("[\n")
        if (items.all { it != null }) {
            var line = StringBuilder()
            items.forEachIndexed { i, item ->
                val piece = item + if (i < items.size - 1) "," else ""
                if (line.isNotEmpty() && inner.length + line.length + 1 + piece.length > WIDTH) {
                    append(inner).append(line).append("\n")
                    line = StringBuilder()
                }
                if (line.isNotEmpty()) line.append(" ")
                line.append(piece)
            }
            if (line.isNotEmpty()) append(inner).append(line).append("\n")
        } else {
            element.forEachIndexed { i, item ->
                append(inner)
                write(item, inner)
                if (i < element.size - 1) append(",")
                append("\n")
            }
        }
        append(indent).append("]")
    }

    /** [element] on one line when it is small enough, else null. */
    private fun inline(element: JsonElement): String? {
        val text = when (element) {
            is JsonPrimitive -> return element.toString()
            is JsonArray -> element.map { inline(it) ?: return null }.joinToString(", ", "[", "]")
            is JsonObject -> when {
                element.isEmpty() -> "{}"
                element.values.any { it !is JsonPrimitive } -> return null
                else -> element.entries.joinToString(", ", "{", "}") { (key, value) -> "${JsonPrimitive(key)}: $value" }
            }
        }
        return text.takeIf { it.length <= SMALL }
    }
}
