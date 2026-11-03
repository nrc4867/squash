package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import dev.chieppa.stretch.core.Command
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every change to a [SquashDocument]. Each command and its undo is serializable, so anything can send them. */
@Serializable
sealed interface DocumentCommand : Command<SquashDocument>

/** Adds [part], or restores it to [index] when undoing a removal. */
@Serializable
@SerialName("addPart")
data class AddPart(val part: PartInstance, val index: Int? = null) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val parts = document.character.parts
        require(parts.none { it.id == part.id }) { "Part ${part.id.value} already exists" }
        val at = index ?: parts.size
        val next = parts.take(at) + part + parts.drop(at)
        return Applied(document.withParts(next), RemovePart(part.id))
    }
}

@Serializable
@SerialName("removePart")
data class RemovePart(val part: PartId) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val parts = document.character.parts
        val at = parts.indexOfFirst { it.id == part }
        require(at >= 0) { "No part ${part.value}" }
        return Applied(document.withParts(parts - parts[at]), AddPart(parts[at], at))
    }
}

/** Adds [parts] as one step, as a mirrored pair is placed, or restores them to [indices] when undoing a removal. */
@Serializable
@SerialName("addParts")
data class AddParts(val parts: List<PartInstance>, val indices: List<Int>? = null) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val next = document.character.parts.toMutableList()
        parts.forEachIndexed { i, part ->
            require(next.none { it.id == part.id }) { "Part ${part.id.value} already exists" }
            next.add(indices?.get(i) ?: next.size, part)
        }
        return Applied(document.withParts(next), RemoveParts(parts.map { it.id }))
    }
}

@Serializable
@SerialName("removeParts")
data class RemoveParts(val parts: List<PartId>) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val current = document.character.parts
        val indices = parts.map { id -> current.indexOfFirst { it.id == id }.also { require(it >= 0) { "No part ${id.value}" } } }
        val removed = indices.map { current[it] }
        val order = indices.indices.sortedBy { indices[it] }
        return Applied(
            document.withParts(current - removed.toSet()),
            AddParts(order.map { removed[it] }, order.map { indices[it] }),
        )
    }
}

/** Sets the outline drawn in [view]; a null [curve] clears it. */
@Serializable
@SerialName("setOutline")
data class SetOutline(val part: PartId, val view: View, val curve: OutlineCurve?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val outlines = if (curve == null) instance.outlines - view else instance.outlines + (view to curve)
        return Applied(document.withPart(instance.copy(outlines = outlines)), SetOutline(part, view, instance.outlines[view]))
    }
}

/** Sets a parameter's value; a null [value] returns it to the template's default. */
@Serializable
@SerialName("setParameter")
data class SetParameter(val part: PartId, val name: String, val value: Float?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val params = if (value == null) instance.params - name else instance.params + (name to value)
        return Applied(document.withPart(instance.copy(params = params)), SetParameter(part, name, instance.params[name]))
    }
}

private fun SquashDocument.requirePart(id: PartId): PartInstance =
    requireNotNull(character.part(id)) { "No part ${id.value}" }

private fun SquashDocument.withParts(parts: List<PartInstance>): SquashDocument =
    copy(character = character.copy(parts = parts))

private fun SquashDocument.withPart(part: PartInstance): SquashDocument =
    withParts(character.parts.map { if (it.id == part.id) part else it })
