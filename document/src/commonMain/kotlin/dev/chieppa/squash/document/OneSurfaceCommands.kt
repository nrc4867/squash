package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Builds the character's body as one surface when [on], and as each part's own mesh when not. */
@Serializable
@SerialName("setOneSurface")
data class SetOneSurface(val on: Boolean) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        Applied(document.copy(character = document.character.copy(oneSurface = on)), SetOneSurface(document.character.oneSurface))
}

/**
 * Sets the [blend] of the join at [part]'s socket, and at the other side's of its pair, which shares it: the radius of the smooth union
 * where the part meets its parent on a character built as one surface. Null takes the part's default.
 */
@Serializable
@SerialName("setSocketBlend")
data class SetSocketBlend(val part: PartId, val blend: Float?) : DocumentCommand {
    init {
        require(blend == null || blend >= 0f) { "A blend is never below 0, not $blend" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        requireNotNull(instance.socket) { "Part ${part.value} sits on no socket" }
        val sides = document.character.parts.filter { it.id == part || (instance.pair != null && it.pair == instance.pair) }.map { it.id }.toSet()
        val parts = document.character.parts.map { if (it.id in sides) it.copy(blend = blend) else it }
        return Applied(document.copy(character = document.character.copy(parts = parts)), SetSocketBlend(part, instance.blend))
    }
}

/** Sets the painted eye [id] to [eye], adding it after the others when new; null removes it. */
@Serializable
@SerialName("setPaintedEye")
data class SetPaintedEye(val id: String, val eye: PaintedEye?) : DocumentCommand {
    init {
        require(eye == null || eye.id == id) { "Eye ${eye?.id} given for $id" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val eyes = document.character.paintedEyes
        val old = eyes.firstOrNull { it.id == id }
        eye?.let { document.requirePart(it.anchor.part) }
        val now = when {
            eye == null -> eyes.filterNot { it.id == id }
            old == null -> eyes + eye
            else -> eyes.map { if (it.id == id) eye else it }
        }
        return Applied(document.copy(character = document.character.copy(paintedEyes = now)), SetPaintedEye(id, old))
    }
}
