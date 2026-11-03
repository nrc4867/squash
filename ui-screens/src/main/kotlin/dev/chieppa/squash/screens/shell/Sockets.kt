package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.Ulid

/** An empty socket on a placed part: where a part can go next, and which loaded templates it takes. */
data class OpenSocket(val parent: PartId, val name: String, val takes: List<TemplateId>, val paired: Boolean) {
    val ref: SocketRef get() = SocketRef(parent, name)
}

/** The part a mirrored pair is reached through: its left side, or any part not in a pair. */
val PartInstance.isLeader: Boolean get() = pair == null || side != Side.RIGHT

/** The other side of this part's mirrored pair. */
fun SquashDocument.twin(part: PartInstance): PartInstance? =
    part.pair?.let { pair -> character.parts.firstOrNull { it.pair == pair && it.id != part.id } }

/** Every socket with nothing on it, on the document's parts, offering only templates that are loaded. */
fun openSockets(document: SquashDocument, plan: BodyPlan, templates: TemplateFolder): List<OpenSocket> {
    val parts = document.character.parts
    val filled = parts.mapNotNull { it.socket }.toSet()
    return parts.filter { it.isLeader }.flatMap { part ->
        val template = templates.template(part.template) ?: return@flatMap emptyList()
        plan.socketsOn(template).mapNotNull { spec ->
            val takes = spec.takes.filter { templates.template(it) != null }
            when {
                takes.isEmpty() || SocketRef(part.id, spec.name) in filled -> null
                else -> OpenSocket(part.id, spec.name, takes, spec.pair != null || part.pair != null)
            }
        }
    }
}

/** The first open socket that takes [template], preferring [current] when it does. */
fun socketFor(template: TemplateId, sockets: List<OpenSocket>, current: OpenSocket?): OpenSocket? =
    current?.takeIf { template in it.takes && it in sockets } ?: sockets.firstOrNull { template in it.takes }

/**
 * The instances [template] makes on [socket]. A part on a paired parent pairs up across the parent's two sides; a
 * paired socket on a single parent makes both sides there; anything else is one part. The left side comes first and
 * is the one selected. See HLD: data-model, Mirroring is shared data plus overrides.
 */
fun placeOnSocket(
    socket: OpenSocket,
    template: PartTemplate,
    document: SquashDocument,
    id: () -> String = Ulid::next,
): List<PartInstance> {
    val parent = requireNotNull(document.character.part(socket.parent)) { "No part ${socket.parent.value}" }
    fun instance(on: PartId, pair: PairId?, side: Side?) =
        PartInstance(PartId(id()), template.ref, SocketRef(on, socket.name), pair = pair, side = side)
    if (!socket.paired) return listOf(instance(parent.id, null, null))
    val pair = PairId(id())
    val twin = document.twin(parent)
    return listOf(
        instance(parent.id, pair, Side.LEFT),
        instance(twin?.id ?: parent.id, pair, Side.RIGHT),
    )
}
