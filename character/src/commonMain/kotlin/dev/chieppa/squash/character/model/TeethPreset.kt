package dev.chieppa.squash.character.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.Jaw
import dev.chieppa.squash.document.JawName
import dev.chieppa.squash.document.ToothType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A teeth preset (`templates/teeth/<id>.teeth.json`): the tooth types it uses and its two jaws, each with its level and rows of slots
 * from the middle outward. A mouth starts from one and keeps only what it changes. See HLD: look, Teeth.
 */
@Serializable
@Doc("A teeth preset: tooth types and two jaws of rows of slots, from the middle outward, mirrored unless a slot says otherwise.")
data class TeethPreset(
    @SerialName("\$schema")
    val schemaPath: String? = null,
    @Doc("The grammar version of the file.") @Range(min = 1.0, max = 1.0)
    val format: Int = 1,
    @Doc("The name a mouth style's `teeth` and a group's `teeth.preset` use.")
    val id: String,
    val name: String = id,
    @Doc("The tooth types its slots hold, by name.")
    val types: Map<String, ToothType> = emptyMap(),
    @Doc("The upper jaw, riding the skull.")
    val upper: Jaw = Jaw(),
    @Doc("The lower jaw, riding the jaw bone.")
    val lower: Jaw = Jaw(),
) {
    fun jaw(name: JawName): Jaw = when (name) {
        JawName.UPPER -> upper
        JawName.LOWER -> lower
    }

    companion object {
        /** The preset a mouth takes when neither its group nor its style names one that is loaded: no teeth. */
        val NONE = TeethPreset(id = "none")
    }
}
