package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sets how [part], a part a garment owns, moves with the body, and the other side's of its pair, which shares it: [bind] which bones,
 * null for the rule that picks one by where it sits, and [stiff], 0 to 1, how rigid a part bound to several bones keeps each stretch,
 * null for 0. See HLD: data-model, A garment can own parts.
 */
@Serializable
@SerialName("setPartBind")
data class SetPartBind(val part: PartId, val bind: PartBind?, val stiff: Float?) : DocumentCommand {
    init {
        require(stiff == null || stiff in STIFF_RANGE) { "A stiffness is 0 to 1, not $stiff" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        requireNotNull(instance.garment) { "Part ${part.value} is owned by no garment" }
        val sides = document.character.parts.filter { it.id == part || (instance.pair != null && it.pair == instance.pair) }.map { it.id }.toSet()
        val parts = document.character.parts.map { if (it.id in sides) it.copy(bind = bind, stiff = stiff) else it }
        return Applied(document.copy(character = document.character.copy(parts = parts)), SetPartBind(part, instance.bind, instance.stiff))
    }
}
