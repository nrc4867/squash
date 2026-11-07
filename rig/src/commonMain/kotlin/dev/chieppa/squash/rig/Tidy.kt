package dev.chieppa.squash.rig

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SeamBrush
import dev.chieppa.squash.document.SeamSide
import dev.chieppa.squash.document.SeamStroke
import dev.chieppa.squash.document.SeamStrokeId
import dev.chieppa.squash.geom.Vec3

/** A side claims a vertex when its bone holds more than half of it, by a margin past rounding. */
private const val CLAIM = 0.5f + 1e-3f

/** How far along u from a part's lowest ring its socket end reaches, which a parent's piece must touch to be attached. */
private const val SOCKET_END = 1e-3f

/** How far a Tidy dab reaches, as a share of the seam's part's axis length: no further than the vertex it lands on. */
const val TIDY_RADIUS = 1e-3f

/**
 * A piece of one side's claim at a seam cut off from that side's main piece: [anchors] landing on vertices of [part], which
 * Tidy hands to the bone on the [to] side, the one on its border.
 */
class StrayPatch(val child: PartId, val part: PartId, val anchors: List<Anchor>, val to: SeamSide) {
    /** The stroke handing this patch over: a dab of full strength on each of its vertices. */
    fun stroke(id: SeamStrokeId): SeamStroke = SeamStroke(id, anchors, SeamBrush.PAINT, to, TIDY_RADIUS, 1f, dabs = true)
}

/**
 * The stray patches at the seam where [child] meets its parent. On the child, a piece of the parent bone's claim that does not
 * reach the socket end, and every piece of the child's claim but its largest that the parent's claim surrounds; on the parent, a
 * piece of the child bone's claim that does not come within the seam's reach of the socket, and every piece of the rest but its
 * largest that the child's claim surrounds. A patch of the mesh apart from the rest, touching neither claim, is no stray. A side claims a vertex
 * when its bone holds more than half of it. See HLD: data-model, The rig.
 */
fun Rig.strayPatches(child: PartId): List<StrayPatch> {
    val seam = seam(child) ?: return emptyList()
    val onChild = part(child)?.let { childPatches(seam, it) }.orEmpty()
    val onParent = part(seam.parent)?.let { parentPatches(seam, it) }.orEmpty()
    return onChild + onParent
}

private fun childPatches(seam: RigSeam, part: RigPart): List<StrayPatch> {
    val pieces = MeshPieces(part)
    val parentClaim = BooleanArray(part.vertexCount) { part.weights.weightOf(it, seam.carrier) > CLAIM }
    val lowest = part.vertices.minOfOrNull { it.u } ?: return emptyList()
    val cutOff = pieces.pieces(parentClaim).filter { piece -> piece.none { part.vertices[it].u <= lowest + SOCKET_END } }
    val own = pieces.pieces(BooleanArray(part.vertexCount) { !parentClaim[it] })
    val largest = own.maxByOrNull { it.size }
    val strays = own.filter { it !== largest && pieces.borders(it, parentClaim) }
    return cutOff.map { patch(seam, part, it, SeamSide.CHILD) } + strays.map { patch(seam, part, it, SeamSide.PARENT) }
}

private fun parentPatches(seam: RigSeam, part: RigPart): List<StrayPatch> {
    val pieces = MeshPieces(part)
    val childClaim = BooleanArray(part.vertexCount) { part.weights.weightOf(it, seam.first) > CLAIM }
    val reach = seam.radius * (1f + SEAM_REACH)
    val cutOff = pieces.pieces(childClaim).filter { piece -> piece.none { (restOf(part, it) - seam.anchor).length <= reach } }
    val rest = pieces.pieces(BooleanArray(part.vertexCount) { !childClaim[it] })
    val largest = rest.maxByOrNull { it.size }
    val strays = rest.filter { it !== largest && pieces.borders(it, childClaim) }
    return cutOff.map { patch(seam, part, it, SeamSide.PARENT) } + strays.map { patch(seam, part, it, SeamSide.CHILD) }
}

private fun restOf(part: RigPart, vertex: Int) = Vec3(part.rest[vertex * 3], part.rest[vertex * 3 + 1], part.rest[vertex * 3 + 2])

private fun patch(seam: RigSeam, part: RigPart, vertices: List<Int>, to: SeamSide) =
    StrayPatch(seam.child, part.id, vertices.sorted().map { part.anchors[it] }, to)
