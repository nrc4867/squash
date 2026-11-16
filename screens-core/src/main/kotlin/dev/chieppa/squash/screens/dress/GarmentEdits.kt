package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.shape.ringRound
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentEdge
import dev.chieppa.squash.document.SurfaceCurve

/** Where along a part, in u, the edge round its root lies, and the cuff band's second edge. */
const val ROOT_U = 0.01f
const val BAND_U = 0.12f

/** Where along a part a place beyond its root's edges lies, the part's side of them. */
private const val PART_MIDDLE_U = 0.5f

/** The command that adds [curve] to [garment]'s edges as a hem, its fill as it was. */
fun Character.edgeCommand(body: AssembledCharacter, garment: Garment, curve: SurfaceCurve): DocumentCommand =
    shapeCommands(this, body, garment, garment.edges + GarmentEdge(curve), garment.fill)

/** The command a press at [at] sends for [garment]: the side pressed fills, or flips (see [GarmentShape.pressed]). */
fun Character.fillCommand(body: AssembledCharacter, garment: Garment, at: Anchor): DocumentCommand =
    shapeCommands(this, body, garment, garment.edges, GarmentShape.of(this, body, garment.edges, garment.fill).pressed(at))

/** The command that takes [garment]'s edge [index] away, the cloth kept as it was on each side but where the edge's sides merge (see [GarmentShape.without]). */
fun Character.removeEdgeCommand(body: AssembledCharacter, garment: Garment, index: Int): DocumentCommand {
    val edges = garment.edges.filterIndexed { i, _ -> i != index }
    if (edges.isEmpty()) return shapeCommands(this, body, garment, edges, emptyList())
    return shapeCommands(this, body, garment, edges, GarmentShape.of(this, body, garment.edges, garment.fill).without(index))
}

/**
 * The command that adds [hole] to [garment], or null where it cuts nothing off: the cloth stays as it was but for the hole's inside,
 * the side of it holding [inside] or without one its smaller side, which is left bare; [band], places beside it, is cloth, so a cuff
 * band between two edges is cloth.
 */
fun Character.holeCommand(body: AssembledCharacter, garment: Garment, hole: GarmentEdge, band: List<Anchor> = emptyList(), inside: Anchor? = null): DocumentCommand? {
    val before = GarmentShape.of(this, body, garment.edges, garment.fill)
    val edges = garment.edges + hole
    val after = GarmentShape.of(this, body, edges, garment.fill)
    val sides = after.regions.sides.last()
    if (sides.size < 2) return null
    val banded = band.mapNotNullTo(HashSet()) { after.regions.regionAt(it) }
    val out = inside?.let(after.regions::regionAt)?.takeIf { it in sides } ?: after.regions.smallest(sides - banded) ?: return null
    val kept = before.carriedTo(after, setOf(out)) + band
    return shapeCommands(this, body, garment, edges, kept)
}

/**
 * The command a Hole press on [at]'s part sends where the part meets [garment], the garment covering it or the part it sits on: a closed
 * edge round its root, then a cuff band beside it; null where it does not meet the garment or has both.
 */
fun Character.rootHoleCommand(body: AssembledCharacter, garment: Garment, at: Anchor): DocumentCommand? {
    val part = part(at.part)?.let(::leaderOf) ?: return null
    val parent = part.socket?.parent?.let(::part) ?: return null
    if (covers(garment, parent) == null && covers(garment, part) == null) return null
    val patch = bodyChart(this, body).part(part.id)?.patch ?: return null
    val root = Anchor(part.id, patch, ROOT_U, 0f)
    val inside = root.copy(u = PART_MIDDLE_U)
    val rings = garment.edges.count { edge -> edge.hole && edge.curve.closed && edge.curve.points.all { it.part == part.id } && edge.curve.points.first().u in setOf(ROOT_U, BAND_U) }
    return when (rings) {
        0 -> holeCommand(body, garment, GarmentEdge(ringRound(root), hole = true), inside = inside)
        1 -> holeCommand(body, garment, GarmentEdge(ringRound(root.copy(u = BAND_U)), hole = true), listOf(root.copy(u = (ROOT_U + BAND_U) / 2f)), inside)
        else -> null
    }
}
