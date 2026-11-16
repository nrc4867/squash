package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sets several parameters [part] holds none of yet as one step, where [SetParameters] would send them; a null value leaves its name
 * unset. Fails when the part already holds any of the names, so a new thing named by them, as a hand-made socket, never lands on one in use.
 */
@Serializable
@SerialName("addParameters")
data class AddParameters(
    val part: PartId,
    val values: Map<String, Float?>,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val held = document.writtenData(instance, thisSide).params.keys.filter { it in values }
        require(held.isEmpty()) { "Part ${part.value} already holds ${held.joinToString()}" }
        return SetParameters(part, values, thisSide).apply(document)
    }
}
