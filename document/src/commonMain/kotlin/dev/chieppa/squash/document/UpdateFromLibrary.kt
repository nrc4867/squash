package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Moves [part], and the other side of its pair, to [version] of the template it pins, as one step; its undo moves them back. Its
 * outlines, parameters, overrides, surface work, role and socket stay as they are, and so do the pose, the seams and the parts on
 * it, so whatever the other version builds that they name carries over, and what it does not build stays stored for a version
 * that does. See HLD: data-model, Update from library.
 */
@Serializable
@SerialName("updateFromLibrary")
data class UpdateFromLibrary(val part: PartId, val version: Int) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val pinned = document.requirePart(part).template
        require(version != pinned.version) { "Part ${part.value} already pins ${pinned.id.value} version $version" }
        return Applied(document.withTemplate(part, pinned.copy(version = version)), UpdateFromLibrary(part, pinned.version))
    }
}

/** This document with [part], and the other side of its pair, pinning [template]; nothing else of them changes. */
internal fun SquashDocument.withTemplate(part: PartId, template: TemplateRef): SquashDocument {
    val pair = requirePart(part).pair
    val parts = character.parts.map { if (it.id == part || (pair != null && it.pair == pair)) it.copy(template = template) else it }
    return copy(character = character.copy(parts = parts))
}
