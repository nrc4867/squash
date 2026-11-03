package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.stretch.ui.TreeNode

/** A row of the Parts tree: a placed part, a mirrored pair counting once, or a socket waiting for one. */
sealed interface PartRow {
    val key: String
    val label: String

    /** The name its test tag ends with: a socket's, or the root's template id. */
    val tagName: String

    data class Placed(val part: PartInstance, override val label: String) : PartRow {
        override val key get() = part.id.value
        override val tagName get() = part.socket?.socket ?: part.template.id.value
        val drawn get() = part.outlines.isNotEmpty()
    }

    data class Open(val socket: OpenSocket, override val label: String) : PartRow {
        override val key get() = "${socket.parent.value}/${socket.name}"
        override val tagName get() = socket.name
    }
}

/**
 * The Parts tree: each placed part under the part it sits on, and each open socket under the part that has it. A
 * part is named for its socket, or for its template at the root.
 */
fun partTree(document: SquashDocument, plan: BodyPlan, templates: TemplateFolder): List<TreeNode<PartRow>> {
    val parts = document.character.parts
    val open = openSockets(document, plan, templates).groupBy { it.parent }
    fun node(part: PartInstance): TreeNode<PartRow> {
        val name = part.socket?.let { displayName(it.socket) } ?: templates.template(part.template)?.name ?: part.template.id.value
        val placed = parts.filter { it.isLeader && it.socket?.parent == part.id }.map(::node)
        val waiting = open[part.id].orEmpty().map { TreeNode<PartRow>(PartRow.Open(it, displayName(it.name))) }
        return TreeNode(PartRow.Placed(part, name), placed + waiting)
    }
    return parts.filter { it.socket == null }.map(::node)
}

/** How many of the plan's parts have something drawn: [drawn] of [total], a mirrored pair counting once. */
data class PartProgress(val drawn: Int, val total: Int)

fun partProgress(document: SquashDocument, plan: BodyPlan, templates: TemplateFolder): PartProgress =
    PartProgress(document.character.parts.count { it.isLeader && it.outlines.isNotEmpty() }, plan.partCount(templates))
