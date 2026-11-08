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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/** How far either side of a place, in u, the surface is read for which way the part runs there. */
private const val TANGENT_STEP = 0.01f

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

    /** A point on this part's surface at [u] and [v] on [patch], or on its first run reaching [u] without one, in body space. */
    fun pointOn(u: Float, v: Float, patch: String? = null): Vec3? = patchAt(u, patch)?.let { origin + surface.shown(it.positionAt(u, v)) }

    /** The outward normal there, in body space. */
    fun normalAt(u: Float, v: Float, patch: String? = null): Vec3? = patchAt(u, patch)?.let { surface.shown(it.surfaceAt(u, v).normal) }

    /** The normal there turned [tilt] degrees toward the part's end, which way a socket there with that tilt faces. */
    fun outwardAt(u: Float, v: Float, tilt: Float, patch: String? = null): Vec3? {
        val normal = normalAt(u, v, patch) ?: return null
        if (tilt == 0f) return normal
        val ahead = pointOn((u + TANGENT_STEP).coerceAtMost(1f), v, patch) ?: return normal
        val behind = pointOn((u - TANGENT_STEP).coerceAtLeast(0f), v, patch) ?: return normal
        val along = (ahead - behind).let { it - normal * (it dot normal) }
        if (along.length == 0f) return normal
        val angle = tilt * DEGREES_TO_RADIANS
        return normal * cos(angle) + along.normalized() * sin(angle)
    }

    /** Where this part reaches in body space, or null when it built nothing. */
    val box: BodyBox? get() = surface.box?.moved(origin)

    private fun patchAt(u: Float, patch: String?): Patch? =
        if (patch == null) surface.patches.firstOrNull { it.covers(u) } else surface.patches.firstOrNull { it.name == patch }

    /** This part showing [surface], turned by [turn], where it sits. */
    fun showing(surface: PartSurface, turn: Mat3): PlacedPart =
        PlacedPart(id, template, side, surface, origin, socket, anchor, placement, lengthFactor, socketSpec, turn, role)
}

/** An empty socket on a placed part, in body space, as a part on [side] would sit on it, which lies on part [on], its parent's twin on a paired parent's right. */
data class PlacedSocket(val ref: SocketRef, val position: Vec3, val side: Side? = null, val on: PartId = ref.parent)

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
        val template = templates[part.template].asBuilt(part.placement, onSocket = part.socket != null)
        val surface = surfaces.getValue(part.id)
        val socket = part.socket
        val spec = if (socket != null && parent != null) {
            socketsOf(parent).firstOrNull { it.name == socket.socket }?.placedBy(parent.placement, part.side)
        } else {
            null
        }
        val origin = if (spec != null && parent != null) originOn(parent, spec, template, part.side) else Vec3.ZERO
        val anchor = if (spec != null && parent != null) parent.pointOn(spec.u, socketV(parent, spec, part.side), spec.patch) else null
        return PlacedPart(part.id, template, part.side, surface, origin, socket, anchor, part.placement, lengths.ofPart(part.id), spec, role = part.role ?: template.role)
    }

    private fun socketsOf(part: PlacedPart): List<SocketSpec> = socketsOn(plan, part.template, part.placement, part.surface.patches.map { it.name })

    /**
     * Where the socket plane of a [template] part on [side] sits on [parent]: the socket's point, sunk by the template's depth
     * times the socket's, then carried back by the template's mount. A right side reads the socket's mirrored v, unless its
     * parent is itself mirrored, which already reflects whatever sits on it.
     */
    private fun originOn(parent: PlacedPart, spec: SocketSpec, template: PartTemplate, side: Side?): Vec3 {
        val mirroredHere = side == Side.RIGHT
        val v = socketV(parent, spec, side)
        val point = parent.pointOn(spec.u, v, spec.patch) ?: return parent.origin
        val rest = PartBuilder.axes(template).along
        val along = if (mirroredHere) rest.mirrored else rest
        val into = when (spec.outward) {
            Outward.AXIS -> along
            Outward.NORMAL -> parent.outwardAt(spec.u, v, spec.tilt, spec.patch) ?: along
        }
        val mount = templates.mountOffset(template.ref)
        return point - into * (templates.socketSink(template.ref) * spec.sink) - (if (mirroredHere) mount.mirrored else mount)
    }

    private fun socketV(parent: PlacedPart, spec: SocketSpec, side: Side?): Float =
        if (side == Side.RIGHT && !parent.mirrored) 1f - spec.v else spec.v

    /**
     * What the empty sockets under [here] would take, each a planned part on its template, and those sockets' places when
     * [here] is in the document, as [leader]. A paired socket makes both sides, and a paired parent's right side, its
     * [twin], carries the right one. [seen] keeps a plan that loops from going round for ever.
     */
    private fun planUnder(here: PlacedPart, twin: PlacedPart?, leader: PartId?, seen: Set<String>) {
        for (unmoved in socketsOf(here)) {
            val spec = unmoved.placedBy(here.placement, null)
            if (leader != null && SocketRef(leader, spec.name) in filled) continue
            if (spec.takes.isEmpty()) {
                if (leader != null) sockets += takingAnything(here, twin, leader, unmoved)
                continue
            }
            val template = spec.takes.firstNotNullOfOrNull { templates.latest(it) } ?: continue
            if (leader != null) here.pointOn(spec.u, spec.v, spec.patch)?.let { sockets += PlacedSocket(SocketRef(leader, spec.name), it) }
            if (template.id.value in seen) continue
            val paired = spec.pair != null || twin != null
            val left = plannedPart(template, if (paired) Side.LEFT else null, here, unmoved.placedBy(here.placement, Side.LEFT)) ?: continue
            val right = if (paired) plannedPart(template, Side.RIGHT, twin ?: here, unmoved.placedBy((twin ?: here).placement, Side.RIGHT)) else null
            planned += listOfNotNull(left, right)
            planUnder(left, right, null, seen + template.id.value)
        }
    }

    /**
     * The dots of a socket that takes any template, which shows no planned part: one, or both sides where it is paired or its
     * parent is, the right side read on the [twin] or mirrored on [here].
     */
    private fun takingAnything(here: PlacedPart, twin: PlacedPart?, leader: PartId, unmoved: SocketSpec): List<PlacedSocket> {
        val ref = SocketRef(leader, unmoved.name)
        if (unmoved.pair == null && twin == null) {
            val spec = unmoved.placedBy(here.placement, null)
            return listOfNotNull(here.pointOn(spec.u, spec.v, spec.patch)?.let { PlacedSocket(ref, it) })
        }
        return listOf(Side.LEFT to here, Side.RIGHT to (twin ?: here)).mapNotNull { (side, parent) ->
            val spec = unmoved.placedBy(parent.placement, side)
            parent.pointOn(spec.u, socketV(parent, spec, side), spec.patch)?.let { PlacedSocket(ref, it, side, parent.id ?: leader) }
        }
    }

    private fun plannedPart(template: PartTemplate, side: Side?, parent: PlacedPart, spec: SocketSpec): PlacedPart? {
        val surface = defaults(template.ref, side == Side.RIGHT) ?: return null
        return PlacedPart(null, template, side, surface, originOn(parent, spec, template, side), lengthFactor = lengths.ofPlanned(template.ref), role = template.role)
    }
}
