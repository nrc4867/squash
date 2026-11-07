package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Cap
import dev.chieppa.squash.character.model.CapShape
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartInstance

/**
 * This template as a part with [params] builds it: turned and its joints moved by Move ([placedBy]), with a generic part's own
 * section, views, rings and segments ([shapedBy]), and, for a generic part on no socket, as Blank's root is, its open end closed
 * round, since it has no socket plane to sit on. Everything that reads a part's template reads this one.
 */
fun PartTemplate.asBuilt(params: Map<String, Float>, onSocket: Boolean): PartTemplate =
    placedBy(params).shapedBy(params).let { if (onSocket || !it.isGeneric) it else it.withEndsClosed() }

/** The template [part] of [character] builds with, from [unplaced], the template it pins. See [asBuilt]. */
fun Character.builtTemplate(part: PartInstance, unplaced: PartTemplate): PartTemplate =
    unplaced.asBuilt(resolvedShape(part).params, onSocket = part.socket != null)

private fun PartTemplate.withEndsClosed(): PartTemplate {
    val end = openEnd ?: return this
    return copy(openEnd = null, blocks = blocks + Cap(end = end, shape = CapShape.ROUND))
}
