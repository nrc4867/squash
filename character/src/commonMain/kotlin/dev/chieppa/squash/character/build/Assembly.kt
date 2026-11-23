package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Loop
import dev.chieppa.squash.character.copies.PartCopies
import dev.chieppa.squash.character.copies.copiesOf
import dev.chieppa.squash.character.hair.LockPlacing
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.Outward
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.ShellInside
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.covers
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/** How many points round a ring its middle is read from. */
private const val MIDDLE_SAMPLES = 16

/** How far either side of a place, in u, the surface is read for which way the part runs there. */
private const val TANGENT_STEP = 0.01f

/** How far back along a part, in u, which way it runs at a place is read from. */
private const val ALONG_STEP = 0.08f

/** A ring whose points lie within this share of the ring [ALONG_STEP] in from it of their middle is a pole: a closed end. */
private const val POLE_SHARE = 0.05f

/** How near its parent's axis, in body units, a socket's surface point lies for a root by reach to sit on the axis: a pole. */
private const val AXIS_ROOT = 1e-5f

/** A place on a part's surface and which way it faces out. */
class SurfacePlace(val point: Vec3, val out: Vec3)

/**
 * The pole at [u] of a surface whose points [pointAt] gives by u and v, as [PlacedPart.poleAt] reads it: where the axis through the
 * middles of the rings [ALONG_STEP] and twice that in from it leaves the surface, facing out along it; null where the ring is no pole.
 */
internal fun poleOf(u: Float, pointAt: (Float, Float) -> Vec3?): SurfacePlace? {
    fun ring(at: Float) = (0 until MIDDLE_SAMPLES).mapNotNull { pointAt(at, it.toFloat() / MIDDLE_SAMPLES) }
    fun middle(points: List<Vec3>) = points.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / points.size)
    val inward = if (u < 0.5f) 1f else -1f
    val end = ring(u).ifEmpty { return null }
    val pole = middle(end)
    val nearRing = ring((u + inward * ALONG_STEP).coerceIn(0f, 1f)).ifEmpty { return null }
    val farRing = ring((u + inward * 2f * ALONG_STEP).coerceIn(0f, 1f)).ifEmpty { return null }
    val nearMiddle = middle(nearRing)
    val nearRadius = nearRing.maxOf { (it - nearMiddle).length }
    if (nearRadius == 0f || end.any { (it - pole).length > nearRadius * POLE_SHARE }) return null
    val out = (nearMiddle - middle(farRing)).takeIf { it.length > 0f }?.normalized() ?: return null
    return SurfacePlace(nearMiddle + out * ((pole - nearMiddle) dot out), out)
}

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
    /** For a garment's branch, the body part it was built over; null for a part of the body. */
    val covering: Covering? = null,
    /** For a garment's loose end, where it hangs from; null for anything else. */
    val hanging: Hanging? = null,
    /** What hides faces of it under a garment, so a pose can decide again; null when nothing does. */
    val hiding: Hiding? = null,
    /** The garment that owns it, a part of its own on a socket, or null for a part of the body and for a garment's branch. */
    val owner: GarmentId? = null,
    /** The blend of the join at its socket the document holds, or null for its default. */
    val blend: Float? = null,
    /** The copies of it its socket shows in its place, each an instance of this one build; null for a part shown once. */
    val copies: PartCopies? = null,
    /** For a membrane, the parts it is spanned between and where along them each of its vertices rides; null for anything else. */
    val spanning: Spanning? = null,
    /** For a lock group or its band, the group, the head carrying it and each lock's chain; null for anything else. */
    val locks: LockPlacing? = null,
    /** The places of its mouth's lip line, see [AssemblyPart.lipLine]. */
    val lipLine: List<Anchor> = emptyList(),
) {
    val planned: Boolean get() = id == null
    val mirrored: Boolean get() = surface.mirrored

    /** A point on this part's surface at [u] and [v] on [patch], or on its first run reaching [u] without one, in body space. */
    fun pointOn(u: Float, v: Float, patch: String? = null): Vec3? = patchAt(u, patch)?.let { origin + surface.shown(it.positionAt(u, v)) }

    /** The middle of this part's ring at [u] on [patch], or on its first run reaching [u] without one, in body space. */
    fun middleAt(u: Float, patch: String? = null): Vec3? {
        val around = (0 until MIDDLE_SAMPLES).mapNotNull { pointOn(u, it.toFloat() / MIDDLE_SAMPLES, patch) }
        return if (around.isEmpty()) null else around.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / around.size)
    }

    /** Which way this part runs as it reaches [u], from the middle of its ring [ALONG_STEP] before, in body space. */
    fun alongAt(u: Float, patch: String? = null): Vec3? {
        val here = middleAt(u, patch) ?: return null
        val before = middleAt((u - ALONG_STEP).coerceAtLeast(0f), patch) ?: return null
        return (here - before).takeIf { it.length > 0f }?.normalized()
    }

    /**
     * Where a socket at [u] sits when the ring there is a pole, a closed end: on the run's axis where it leaves the surface, the middle
     * of the end however the outline's furthest point leans, facing out along the axis; null where the ring at [u] is not a pole.
     */
    fun poleAt(u: Float, patch: String? = null): SurfacePlace? = poleOf(u) { at, v -> pointOn(at, v, patch) }

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

    /** The inside test of its swept surface as it stands, in body space, or null when it has none; read once per part. */
    internal val inside: ShellInside? by lazy { bodyPatch()?.let { patch -> ShellInside(patch) { origin + surface.shown(it) } } }

    /** Every vertex of this part's mesh where it shows, in body space: once at its origin, or where each of its copies shows it. */
    fun shownPoints(): List<Vec3> {
        val mesh = copies?.rule?.surface?.mesh ?: surface.mesh
        val offsets = List(mesh.vertexCount) { mesh.position(it) }
        return copies?.places?.flatMap { place -> offsets.map(place::placed) } ?: offsets.map { origin + it }
    }

    /** Where this part reaches in body space, its copies' reach where it shows as copies, or null when it built nothing. */
    val box: BodyBox? get() = if (copies != null) copies.box else surface.box?.moved(origin)

    private fun patchAt(u: Float, patch: String?): Patch? =
        if (patch == null) surface.patches.firstOrNull { it.covers(u) } else surface.patches.firstOrNull { it.name == patch }

    /** This part showing [surface], turned by [turn], where it sits, with what [hiding] says hides faces of it. */
    fun showing(surface: PartSurface, turn: Mat3, hiding: Hiding? = this.hiding): PlacedPart =
        PlacedPart(id, template, side, surface, origin, socket, anchor, placement, lengthFactor, socketSpec, turn, role, covering, hanging, hiding, owner, blend, copies, spanning, locks, lipLine)

    /** This part with [copies] in place of the ones it shows. */
    fun withCopies(copies: PartCopies?): PlacedPart =
        PlacedPart(id, template, side, surface, origin, socket, anchor, placement, lengthFactor, socketSpec, turn, role, covering, hanging, hiding, owner, blend, copies, spanning, locks, lipLine)
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
 * [defaults] a template's build with nothing drawn on either side, and [lengths] what the legs among them were built at. A part in
 * [skeletons] has its sockets read from that surface, its bare build, rather than from what it shows. Roots sit at the origin. See HLD:
 * data-model, Placing parts.
 */
fun assemble(
    parts: List<AssemblyPart>,
    surfaces: Map<PartId, PartSurface>,
    plan: BodyPlan?,
    templates: TemplateLibrary,
    defaults: (TemplateRef, Boolean) -> PartSurface?,
    lengths: LegLengths = LegLengths(),
    skeletons: Map<PartId, PartSurface> = emptyMap(),
): AssembledCharacter = Assembler(parts, surfaces, plan, templates, defaults, lengths, skeletons).assemble()

private class Assembler(
    private val parts: List<AssemblyPart>,
    private val surfaces: Map<PartId, PartSurface>,
    private val plan: BodyPlan?,
    private val templates: TemplateLibrary,
    private val defaults: (TemplateRef, Boolean) -> PartSurface?,
    private val lengths: LegLengths,
    private val skeletons: Map<PartId, PartSurface>,
) {
    private val placed = LinkedHashMap<PartId, PlacedPart>()
    private val planned = mutableListOf<PlacedPart>()
    private val sockets = mutableListOf<PlacedSocket>()
    private val filled = parts.mapNotNull { it.socket }.toSet()
    private val byParent = parts.filter { it.socket != null }.groupBy { it.socket?.parent }
    private val wornSockets = parts.filter { it.wornSockets.isNotEmpty() }.associate { it.id to it.placement + it.wornSockets }

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
        val owned = part.owner != null
        val origin = if (spec != null && parent != null) originOn(parent, spec, template, part.side, owned) else Vec3.ZERO
        val anchor = if (spec != null && parent != null) socketPoint(parent, spec, socketV(parent, spec, part.side), part.side, owned) else null
        val copies = if (spec?.copies != null && parent != null) {
            copiesOf(
                spec.copies, skeletonOf(parent, owned), surface, PartBuilder.axes(template), part.side == Side.RIGHT, part.side == Side.RIGHT && !parent.mirrored,
                templates.socketSink(template.ref), template.blocks.any { it is Loop }, part.shades,
            )
        } else {
            null
        }
        return PlacedPart(part.id, template, part.side, surface, origin, socket, anchor, part.placement, lengths.ofPart(part.id), spec, role = part.role ?: template.role, owner = part.owner, blend = part.blend, copies = copies, lipLine = part.lipLine)
    }

    /**
     * [part] as its sockets read it: showing its skeleton while a garment reshapes it, else itself. A part a garment owns, with [owned],
     * reads it as it shows, since it belongs to the shape the garment gives.
     */
    private fun skeletonOf(part: PlacedPart, owned: Boolean = false): PlacedPart =
        if (owned) part else part.id?.let(skeletons::get)?.let { part.showing(it, part.turn) } ?: part

    /**
     * Where socket [spec] lies on [parent] at [v], for a part on [side]. On a part a garment reshapes it keeps the place it has inside
     * the bare part: the point on the skeleton, carried from the middle of the skeleton's ring there to the middle of the shown part's
     * ring where the garment puts that socket, so a garment that only reshapes leaves it where it was and one that draws the part
     * longer carries it along with the rest of the part. For a part a garment owns, with [owned], it lies on [parent] as it shows.
     */
    private fun socketPoint(parent: PlacedPart, spec: SocketSpec, v: Float, side: Side?, owned: Boolean = false): Vec3? {
        val frame = skeletonOf(parent, owned)
        val point = frame.poleAt(spec.u, spec.patch)?.point ?: frame.pointOn(spec.u, v, spec.patch) ?: return null
        if (frame === parent) return point
        val worn = parent.id?.let(wornSockets::get)?.let { held -> socketsOf(parent).firstOrNull { it.name == spec.name }?.placedBy(held, side) } ?: spec
        val from = frame.middleAt(spec.u, spec.patch) ?: return point
        val to = parent.middleAt(worn.u, worn.patch) ?: return point
        return point + (to - from)
    }

    private fun socketsOf(part: PlacedPart): List<SocketSpec> = socketsOn(plan, part.template, part.placement, part.surface.patches.map { it.name })

    /**
     * Where the socket plane of a [template] part on [side] sits on [parent]: the socket's point, stood off the surface along its normal
     * by the socket's outset, sunk by the template's depth times the socket's, then carried back by the template's mount. A right side reads the socket's mirrored v, unless its
     * parent is itself mirrored, which already reflects whatever sits on it.
     */
    private fun originOn(parent: PlacedPart, spec: SocketSpec, template: PartTemplate, side: Side?, owned: Boolean = false): Vec3 {
        val mirroredHere = side == Side.RIGHT
        val v = socketV(parent, spec, side)
        val frame = skeletonOf(parent, owned)
        val point = socketPoint(parent, spec, v, side, owned) ?: return parent.origin
        val rest = PartBuilder.axes(template).along
        val along = if (mirroredHere) rest.mirrored else rest
        val into = when (spec.outward) {
            Outward.AXIS -> along
            Outward.NORMAL -> frame.outwardAt(spec.u, v, spec.tilt, spec.patch) ?: along
            Outward.ALONG -> frame.alongAt(spec.u, spec.patch) ?: along
        }
        val mount = templates.mountOffset(template.ref)
        spec.reach?.let { reach -> rootAt(frame, spec, v, reach)?.let { return it - (if (mirroredHere) mount.mirrored else mount) } }
        val standing = if (spec.outset == 0f) point else point + (frame.normalAt(spec.u, v, spec.patch) ?: into) * spec.outset
        return standing - into * (templates.socketSink(template.ref) * spec.sink) - (if (mirroredHere) mount.mirrored else mount)
    }

    /**
     * Where the root of a part on socket [spec] sits when the socket gives its [reach]: that far from [frame]'s axis at the socket's u,
     * toward the socket's [v], so the part stays where it is however wide its parent is drawn there. The axis point is the middle of the
     * ring at u carried onto the line along the part from its origin.
     */
    private fun rootAt(frame: PlacedPart, spec: SocketSpec, v: Float, reach: Float): Vec3? {
        val middle = frame.middleAt(spec.u, spec.patch) ?: return null
        val rest = PartBuilder.axes(frame.template).along
        val along = (if (frame.mirrored) rest.mirrored else rest).normalized()
        val axis = frame.origin + along * ((middle - frame.origin) dot along)
        val toward = frame.pointOn(spec.u, v, spec.patch)?.let { it - axis }?.let { it - along * (it dot along) }
        if (toward == null || toward.length < AXIS_ROOT) return axis
        return axis + toward.normalized() * reach
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
            if (leader != null) socketPoint(here, spec, spec.v, null)?.let { sockets += PlacedSocket(SocketRef(leader, spec.name), it) }
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
            return listOfNotNull(socketPoint(here, spec, spec.v, null)?.let { PlacedSocket(ref, it) })
        }
        return listOf(Side.LEFT to here, Side.RIGHT to (twin ?: here)).mapNotNull { (side, parent) ->
            val spec = unmoved.placedBy(parent.placement, side)
            socketPoint(parent, spec, socketV(parent, spec, side), side)?.let { PlacedSocket(ref, it, side, parent.id ?: leader) }
        }
    }

    private fun plannedPart(template: PartTemplate, side: Side?, parent: PlacedPart, spec: SocketSpec): PlacedPart? {
        val surface = defaults(template.ref, side == Side.RIGHT) ?: return null
        val placed = template.placedBy(plannedData(plan?.spreadFor(template.id) ?: 0f).params)
        return PlacedPart(null, placed, side, surface, originOn(parent, spec, placed, side), lengthFactor = lengths.ofPlanned(template.ref), role = template.role)
    }
}
