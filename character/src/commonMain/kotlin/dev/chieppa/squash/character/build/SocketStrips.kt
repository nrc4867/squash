package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.SurfaceUv
import kotlin.math.floor
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.ShellInside
import dev.chieppa.squash.geom.posed
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck

/** The parts joined to their parents by strips: what sits on a body at a neck, a shoulder, a hip, a wrist or an ankle, and a head on a neck. */
private val JOINED = setOf(Role.NECK, Role.HEAD, Role.ARM, Role.LEG, Role.HAND, Role.FOOT)

/** [assembly] with each part of [JOINED]'s roles joined to its parent by a strip, see [withSocketStrips]. */
fun Graph.socketStripNode(assembly: Node<*, AssembledCharacter>): Node<AssembledCharacter, AssembledCharacter> =
    follow(assembly, stage = BuildStages.ASSEMBLY) { it.withSocketStrips(cancellationCheck()) }

/**
 * This character with a strip of quads where each part of [JOINED]'s roles meets its parent, in place of the overlap: the part that
 * runs into the other as a tube, the child from its start or else its parent from its end (the neck into a head first), or else a
 * neck, an arm or a leg seated on its parent's surface, is trimmed at its first ring well clear of the other, the other's faces inside
 * the strip's foot are left out, and a faired strip joins the ring to the foot as a patch of the tube's own surface. Every grid,
 * vertex and anchor stays; a join with no tube that way keeps its overlap, as do a head on anything but a neck and a part whose
 * template fillets its socket. See HLD: look, Where parts meet.
 */
fun AssembledCharacter.withSocketStrips(checkCancelled: () -> Unit = {}): AssembledCharacter {
    val shown = LinkedHashMap<PartId, PlacedPart>()
    parts.forEach { part -> part.id?.let { shown[it] = part } }
    var changed = false
    val planned = HashMap<PartId, Pair<Boolean, StripJoin>>()
    for (child in parts) {
        val id = child.id ?: continue
        if (child.role !in JOINED || child.covering != null || child.hanging != null || socketBlend(child) != null) continue
        val parentId = child.socket?.parent ?: continue
        val parent = shown[parentId] ?: continue
        if (child.role == Role.HEAD && parent.role != Role.NECK) continue
        val here = shown.getValue(id)
        checkCancelled()
        val twin = twinOf(child)?.let { planned[it] }
        val (tube, host, join) = if (twin != null) {
            val (childIsTube, left) = twin
            if (childIsTube) Triple(here, parent, left.mirroredFor(id, parent)) else Triple(parent, here, left.mirroredFor(id, here))
        } else {
            val asChild = { planStrip(here, true, parent, id)?.let { Triple(here, parent, it) } }
            val asParent = { planStrip(parent, (here.socketSpec?.u ?: 1f) < HALF, here, id)?.let { Triple(parent, here, it) } }
            val seated = { planStrip(here, true, parent, id, seated = true)?.let { Triple(here, parent, it) } }
            when (child.role) {
                Role.HEAD -> asParent() ?: asChild() ?: seated()
                Role.NECK -> asChild() ?: seated() ?: asParent()
                Role.ARM, Role.LEG -> asChild() ?: asParent() ?: seated()
                else -> asChild() ?: asParent()
            } ?: continue
        }
        planned[id] = (tube === here) to join
        val shape = stripShape(tube, join, host) ?: continue
        val tubeId = requireNotNull(tube.id)
        val strung = tube.withStrip(shape, join).let { it.alwaysHiding(trimmed(it, join)) }
        val under = underFoot(host, join, strung)
        shown[tubeId] = strung.showing(strung.surface, strung.turn, strips = strung.strips.dropLast(1) + join.hiding(under))
        shown[requireNotNull(host.id)] = host.alwaysHiding(under)
        changed = true
    }
    return if (changed) AssembledCharacter(parts.map { part -> part.id?.let(shown::get) ?: part }, sockets) else this
}

private const val HALF = 0.5f

/** The left part [part] mirrors, when it is the right side of a pair: on the same socket, of the same template and role. */
private fun AssembledCharacter.twinOf(part: PlacedPart): PartId? {
    if (part.side != Side.RIGHT) return null
    return parts.firstOrNull { it.side == Side.LEFT && it.covering == null && it.role == part.role && it.template.id == part.template.id && it.socket?.socket == part.socket?.socket }?.id
}

/**
 * This strip, planned on the left side of a pair, for its right side [joined] on [host]: the same rings and foot, read round the
 * other way on a host the pair shares, which mirrors itself across the body's plane, and as they are on the host's own right twin.
 */
private fun StripJoin.mirroredFor(joined: PartId, host: PlacedPart): StripJoin {
    val hostId = requireNotNull(host.id)
    val foot = if (hostId == this.host) foot.map { SurfaceUv(it.u, (1f - it.v).let { v -> v - floor(v) }) } else foot
    return StripJoin(patch, tube, ring, atStart, hostId, hostPatch, foot, joined, lift, capped = capped)
}

/**
 * This posed character with every strip a part of it holds built again where the pose has carried the strip's tube or its host, from
 * [rest], the same character at rest: its foot slid to where the posed tube leaves the host ([slidFoot]), the host's faces under the
 * old foot shown again and those under the new one left out, and a garment's strip over the same join standing on the same foot. A
 * strip neither moved keeps the shape its skin gave it. Itself when nothing moved.
 */
fun AssembledCharacter.withStripsPosed(rest: AssembledCharacter): AssembledCharacter {
    if (this === rest) return this
    val shown = LinkedHashMap<PartId, PlacedPart>()
    parts.forEach { part -> part.id?.let { shown[it] = part } }
    val feet = HashMap<PartId, List<SurfaceUv>>()
    val released = HashMap<PartId, MutableSet<Int>>()
    val covered = HashMap<PartId, BooleanArray>()
    var changed = false
    for (body in listOf(true, false)) for (part in parts) {
        if (part.strips.isEmpty() || (part.covering == null) != body) continue
        val id = part.id ?: continue
        val restPart = rest.part(id)
        var vertices: FloatArray? = null
        val moved = ArrayList<StripJoin>()
        for (join in part.strips) {
            val host = shown[join.host] ?: continue
            if (part === restPart && host === rest.part(join.host)) continue
            val foot = (if (body) slidFoot(part, join, host) else feet[join.joined]?.let { footOver(part, it) })?.takeUnless { it.sameAs(join.foot) }
            val placed = foot?.let(join::withFoot) ?: join
            val shape = stripShape(part, placed, host) ?: continue
            val (_, first) = part.patchWithFirst(join.patch) ?: continue
            val into = vertices ?: part.surface.mesh.vertices.copyOf().also { vertices = it }
            shape.mesh.vertices.copyInto(into, first * RenderMesh.FLOATS_PER_VERTEX)
            if (body && foot != null) {
                feet[join.joined] = foot
                moved += placed
            }
        }
        val after = vertices ?: continue
        changed = true
        val strung = part.showing(part.surface.posed(RenderMesh(after, part.surface.mesh.indices)), part.turn)
        shown[id] = strung
        for (join in moved) {
            val host = shown[join.host] ?: continue
            released.getOrPut(join.host) { HashSet() }.addAll(join.underFoot.asList())
            val under = underFoot(host, join, strung)
            covered[join.host] = covered[join.host]?.let { held -> BooleanArray(held.size) { held[it] || under[it] } } ?: under
        }
    }
    for ((hostId, freed) in released) {
        val host = shown[hostId] ?: continue
        shown[hostId] = host.refixed(freed, covered[hostId] ?: BooleanArray(host.surface.mesh.vertexCount))
    }
    return if (changed) AssembledCharacter(parts.map { part -> part.id?.let(shown::get) ?: part }, sockets) else this
}

/** How far a foot may lie from another, in u and v, and still be the same foot. */
private const val SAME_FOOT = 1e-4f

private fun List<SurfaceUv>.sameAs(other: List<SurfaceUv>) =
    size == other.size && indices.all { kotlin.math.abs(this[it].u - other[it].u) < SAME_FOOT && kotlin.math.abs(this[it].v - other[it].v) < SAME_FOOT }

/** The foot of a garment's strip over a body strip's [foot], column by column under the branch's own columns. */
private fun footOver(branch: PlacedPart, foot: List<SurfaceUv>): List<SurfaceUv>? {
    val covering = branch.covering ?: return null
    val shell = branch.surface.patches.firstOrNull() ?: return null
    return List(shell.nv) { j -> foot[covering.bodyColumns[j].coerceAtMost(foot.size - 1)] }
}

/** This part holding [shape], [join]'s strip, as a patch after its own and its vertices after its mesh's, hiding nothing of it. */
internal fun PlacedPart.withStrip(shape: StripShape, join: StripJoin): PlacedPart {
    val old = surface.mesh
    val count = old.vertexCount
    val added = IntArray(shape.mesh.indices.size) { shape.mesh.indices[it] + count }
    val mesh = RenderMesh(old.vertices + shape.mesh.vertices, old.indices + added)
    val more = shape.mesh.vertexCount
    val held = hiding?.let { Hiding(it.indices + added, it.fixed + BooleanArray(more), it.inside, it.whole, it.flags + BooleanArray(more)) }
    val strung = PartSurface(surface.patches + shape.patch, surface.mirrored, mesh, surface.silhouettes, PartSurface.boxOf(mesh), surface.extents)
    return showing(strung, turn, held, strips + join)
}

/** Which vertices of [tube]'s mesh [join]'s strip replaces: its patch's rings from the ring the strip meets on toward the host. */
internal fun trimmed(tube: PlacedPart, join: StripJoin): BooleanArray {
    val hidden = BooleanArray(tube.surface.mesh.vertexCount)
    val (patch, first) = tube.patchWithFirst(join.tube) ?: return hidden
    val rings = if (join.atStart) 0..join.ring else join.ring until patch.nu
    for (ring in rings) for (column in 0 until patch.nv) hidden[first + patch.index(ring, column)] = true
    return hidden
}

/**
 * Which vertices of [host]'s swept patches lie under [join]'s strip on [tube]: inside the strip, read as a shell of its rings from its
 * foot to the ring it meets.
 */
private fun underFoot(host: PlacedPart, join: StripJoin, tube: PlacedPart): BooleanArray {
    val hidden = BooleanArray(host.surface.mesh.vertexCount)
    val (strip, _) = tube.patchWithFirst(join.patch) ?: return hidden
    val shell = ShellInside(strip) { tube.origin + tube.surface.shown(it) }
    var first = 0
    for (patch in host.surface.patches) {
        if (patch.slices != null) {
            for (i in 0 until patch.nu * patch.nv) if (host.origin + host.surface.shown(patch.point(i)) in shell) hidden[first + i] = true
        }
        first += patch.nu * patch.nv
    }
    return hidden
}
