package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.stretch.ui.TreeNode

/** A row of the Parts tree: a placed part, a mirrored pair counting once, or a socket waiting for one. */
sealed interface PartRow {
    val key: String
    val label: String

    /** The name its test tag ends with: a socket's, or the root's template id. */
    val tagName: String

    data class Placed(val part: PartInstance, override val label: String, val drawn: Boolean) : PartRow {
        override val key get() = part.id.value
        override val tagName get() = part.socket?.socket ?: part.template.id.value
    }

    /** One side of a mirrored pair, in its own row while the sides can differ; [differs] says it holds overrides. */
    data class SideOf(val part: PartInstance, val side: Side, override val label: String, val differs: Boolean) : PartRow {
        override val key get() = "side/${part.id.value}"
        override val tagName get() = "${part.socket?.socket}.${side.name.lowercase()}"
    }

    data class Open(val socket: OpenSocket, override val label: String) : PartRow {
        override val key get() = "${socket.parent.value}/${socket.name}"
        override val tagName get() = socket.name
    }
}

/**
 * The Parts tree: each placed part under the part it sits on, and each open socket under the part that has it. A
 * pair reads as one row, as the boards draw it, until its
 * sides can differ: with [mirror] off or once a side has overrides, each side has a row of its own under it, named by [sideLabel].
 */
fun partTree(
    document: SquashDocument,
    plan: BodyPlan,
    templates: TemplateFolder,
    mirror: Boolean = true,
    sideLabel: (Side) -> String = { it.name },
): List<TreeNode<PartRow>> {
    val parts = document.character.parts
    val open = openSockets(document, plan, templates).groupBy { it.parent }
    fun node(part: PartInstance): TreeNode<PartRow> {
        val placed = parts.filter { it.isLeader && it.socket?.parent == part.id }.map(::node)
        val waiting = open[part.id].orEmpty().map { TreeNode<PartRow>(PartRow.Open(it, displayName(it.name))) }
        return TreeNode(PartRow.Placed(part, partName(part, templates), document.isDrawn(part)), document.sideRows(part, mirror, sideLabel) + placed + waiting)
    }
    return parts.filter { it.socket == null }.map(::node)
}

/** A part's name: its socket's, or its template's at the root. */
fun partName(part: PartInstance, templates: TemplateFolder): String =
    part.socket?.let { displayName(it.socket) } ?: templates.template(part.template)?.name ?: part.template.id.value

private fun SquashDocument.sideRows(part: PartInstance, mirror: Boolean, label: (Side) -> String): List<TreeNode<PartRow>> {
    val sides = listOfNotNull(part, twin(part)).filter { it.pair != null && it.side != null }
    val differs = sides.associateWith { character.overridesOf(it) != PartData() }
    if (sides.size < 2 || (mirror && differs.values.none { it })) return emptyList()
    return sides.sortedBy { it.side }.map { side ->
        val named = checkNotNull(side.side)
        TreeNode<PartRow>(PartRow.SideOf(side, named, label(named), differs.getValue(side)))
    }
}

/** How many of the plan's parts have something drawn: [drawn] of [total], a mirrored pair counting once and a broken one as two. */
data class PartProgress(val drawn: Int, val total: Int)

fun partProgress(document: SquashDocument, plan: BodyPlan, templates: TemplateFolder): PartProgress {
    val parts = document.character.parts
    val broken = parts.count { it.pair == null && it.side == Side.RIGHT }
    return PartProgress(parts.count { it.isLeader && document.isDrawn(it) }, plan.partCount(templates) + broken)
}

/** Whether [part], or the other side of its pair, has an outline. */
private fun SquashDocument.isDrawn(part: PartInstance): Boolean =
    listOfNotNull(part, twin(part)).any { character.resolved(it).outlines.isNotEmpty() }
