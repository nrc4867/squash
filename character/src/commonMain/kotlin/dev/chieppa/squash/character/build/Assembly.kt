package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.Outward
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.covers
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt

/** What placing a part reads of it from the document. */
data class AssemblyPart(
    val id: PartId,
    val template: TemplateRef,
    val socket: SocketRef?,
    val pair: PairId?,
    val side: Side?,
    val placement: Map<String, Float> = emptyMap(),
    val role: Role? = null,
)

/** A part on the character: [origin] is where its socket plane's centre sits, in body space. */
class PlacedPart(
    /** The part in the document, or null for one a socket of the plan would take that nothing has been placed on. */
    val id: PartId?,
    val template: PartTemplate,
    val side: Side?,
    val surface: PartSurface,
    val origin: Vec3,
    /** The socket it sits on, and where that socket lies on its parent's surface. */
    val socket: SocketRef? = null,
    val anchor: Vec3? = null,
    /** What the Move tool stored on this part, which its children's sockets read. */
    val placement: Map<String, Float> = emptyMap(),
    /** What its length along its axis was built at, see [LegLengths]. */
    val lengthFactor: Float = 1f,
    /** The socket it sits on as placed on its parent, moved where the Move tool moved it, or null for a root. */
    val socketSpec: SocketSpec? = null,
    /** How a pose has turned it, by the bone at its root; [Mat3.IDENTITY] at rest. */
    val turn: Mat3 = Mat3.IDENTITY,
    /** What it is to the body: its own role, else its template's. */
    val role: Role? = null,
) {
    val planned: Boolean get() = id == null
    val mirrored: Boolean get() = surface.mirrored

    /** A point on this part's surface at [u] and [v] on its first run reaching [u], in body space. */
    fun pointOn(u: Float, v: Float): Vec3? = runAt(u)?.let { origin + surface.shown(it.positionAt(u, v)) }

    /** The outward normal there, in body space. */
    fun normalAt(u: Float, v: Float): Vec3? = runAt(u)?.let { surface.shown(it.surfaceAt(u, v).normal) }

    /** Where this part reaches in body space, or null when it built nothing. */
    val box: BodyBox? get() = surface.box?.moved(origin)

    private fun runAt(u: Float): Patch? = surface.patches.firstOrNull { it.covers(u) }

    /** This part showing [surface], turned by [turn], where it sits. */
    fun showing(surface: PartSurface, turn: Mat3): PlacedPart =
        PlacedPart(id, template, side, surface, origin, socket, anchor, placement, lengthFactor, socketSpec, turn, role)
}

/** An empty socket on a placed part, in body space. */
data class PlacedSocket(val ref: SocketRef, val position: Vec3)

/**
 * The whole character: every placed part on its socket, what the empty sockets of the plan would take, the empty sockets of the
 * placed parts, and the height of the ground under the lowest vertex. As assembled it stands in its rest pose; [posedBy] shows it in a pose.
 */
class AssembledCharacter(val parts: List<PlacedPart>, val sockets: List<PlacedSocket>) {
    fun part(id: PartId): PlacedPart? = parts.firstOrNull { it.id == id }

    /** Where everything reaches, or null when nothing has built. */
    val box: BodyBox? = parts.mapNotNull { it.box }.reduceOrNull(BodyBox::union)

    val floor: Float = box?.low?.y ?: 0f
}

/**
 * Places [parts] on their sockets, and what the empty sockets of [plan] would take. [surfaces] holds each part's build,
 * [defaults] a template's build with nothing drawn on either side, and [lengths] what the legs among them were built at. Roots sit at the origin. See HLD: data-model,
 * Placing parts.
 */
fun assemble(
    parts: List<AssemblyPart>,
    surfaces: Map<PartId, PartSurface>,
    plan: BodyPlan?,
    templates: TemplateLibrary,
    defaults: (TemplateRef, Boolean) -> PartSurface?,
    lengths: LegLengths = LegLengths(),
): AssembledCharacter = Assembler(parts, surfaces, plan, templates, defaults, lengths).assemble()

private class Assembler(
    private val parts: List<AssemblyPart>,
    private val surfaces: Map<PartId, PartSurface>,
    private val plan: BodyPlan?,
    private val templates: TemplateLibrary,
    private val defaults: (TemplateRef, Boolean) -> PartSurface?,
    private val lengths: LegLengths,
) {
    private val placed = LinkedHashMap<PartId, PlacedPart>()
    private val planned = mutableListOf<PlacedPart>()
    private val sockets = mutableListOf<PlacedSocket>()
    private val filled = parts.mapNotNull { it.socket }.toSet()
    private val byParent = parts.filter { it.socket != null }.groupBy { it.socket?.parent }

    fun assemble(): AssembledCharacter {
        val known = parts.map { it.id }.toSet()
        parts.filter { it.socket == null || it.socket.parent !in known }.forEach { place(it, null) }
        // A part reached from no root, such as one on a loop of parents, still shows at the origin.
        parts.forEach { place(it, null) }
        for (part in parts) {
            if (part.pair != null && part.side == Side.RIGHT) continue
            val here = placed.getValue(part.id)
            val twin = part.pair?.let { pair -> parts.firstOrNull { it.pair == pair && it.id != part.id } }?.let { placed[it.id] }
            planUnder(here, twin, part.id, setOf(here.template.id.value))
        }
        return AssembledCharacter(placed.values + planned, sockets)
    }

    private fun place(part: AssemblyPart, parent: PlacedPart?) {
        if (part.id in placed) return
        val here = placedPart(part, parent)
        placed[part.id] = here
        byParent[part.id].orEmpty().forEach { place(it, here) }
    }

    private fun placedPart(part: AssemblyPart, parent: PlacedPart?): PlacedPart {
        val template = templates[part.template].placedBy(part.placement)
        val surface = surfaces.getValue(part.id)
        val socket = part.socket
        val spec = if (socket != null && parent != null) {
            socketsOf(parent.template).firstOrNull { it.name == socket.socket }?.placedBy(parent.placement, part.side)
        } else {
            null
        }
        val origin = if (spec != null && parent != null) originOn(parent, spec, template, part.side) else Vec3.ZERO
        val anchor = if (spec != null && parent != null) parent.pointOn(spec.u, socketV(parent, spec, part.side)) else null
        return PlacedPart(part.id, template, part.side, surface, origin, socket, anchor, part.placement, lengths.ofPart(part.id), spec, role = part.role ?: template.role)
    }

    private fun socketsOf(template: PartTemplate): List<SocketSpec> =
        plan?.socketsOn(template) ?: template.blocks.flatMap { it.sockets() }

    /**
     * Where the socket plane of a [template] part on [side] sits on [parent]. A right side reads the socket's mirrored v,
     * unless its parent is itself mirrored, which already reflects whatever sits on it.
     */
    private fun originOn(parent: PlacedPart, spec: SocketSpec, template: PartTemplate, side: Side?): Vec3 {
        val mirroredHere = side == Side.RIGHT
        val v = socketV(parent, spec, side)
        val point = parent.pointOn(spec.u, v) ?: return parent.origin
        val rest = PartBuilder.axes(template).along
        val along = if (mirroredHere) rest.mirrored else rest
        val into = when (spec.outward) {
            Outward.AXIS -> along
            Outward.NORMAL -> parent.normalAt(spec.u, v) ?: along
        }
        return point - into * templates.socketSink(template.ref)
    }

    private fun socketV(parent: PlacedPart, spec: SocketSpec, side: Side?): Float =
        if (side == Side.RIGHT && !parent.mirrored) 1f - spec.v else spec.v

    /**
     * What the empty sockets under [here] would take, each a planned part on its template, and those sockets' places when
     * [here] is in the document, as [leader]. A paired socket makes both sides, and a paired parent's right side, its
     * [twin], carries the right one. [seen] keeps a plan that loops from going round for ever.
     */
    private fun planUnder(here: PlacedPart, twin: PlacedPart?, leader: PartId?, seen: Set<String>) {
        for (unmoved in socketsOf(here.template)) {
            val spec = unmoved.placedBy(here.placement, null)
            if (leader != null && SocketRef(leader, spec.name) in filled) continue
            val template = spec.takes.firstNotNullOfOrNull { templates.latest(it) } ?: continue
            if (leader != null) here.pointOn(spec.u, spec.v)?.let { sockets += PlacedSocket(SocketRef(leader, spec.name), it) }
            if (template.id.value in seen) continue
            val paired = spec.pair != null || twin != null
            val left = plannedPart(template, if (paired) Side.LEFT else null, here, unmoved.placedBy(here.placement, Side.LEFT)) ?: continue
            val right = if (paired) plannedPart(template, Side.RIGHT, twin ?: here, unmoved.placedBy((twin ?: here).placement, Side.RIGHT)) else null
            planned += listOfNotNull(left, right)
            planUnder(left, right, null, seen + template.id.value)
        }
    }

    private fun plannedPart(template: PartTemplate, side: Side?, parent: PlacedPart, spec: SocketSpec): PlacedPart? {
        val surface = defaults(template.ref, side == Side.RIGHT) ?: return null
        return PlacedPart(null, template, side, surface, originOn(parent, spec, template, side), lengthFactor = lengths.ofPlanned(template.ref), role = template.role)
    }
}
