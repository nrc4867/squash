package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.isCuffPart
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.surfaceAt

/** A ring of a shell no wider than this across is a pole, with no edge to ink. */
private const val POLE_REACH = 1e-4f

/**
 * The lines a garment's branch or cuff [placed] inks, as its covering's ink says: its hem, the open edges of its grid where its faces
 * are shown (a cuff's far end only, its near end lying inside the part it rises from), and where it crosses each part it inks a
 * crossing with. Null when it inks nothing. See HLD: data-model, Garments.
 */
internal fun garmentEdges(character: AssembledCharacter, placed: PlacedPart, parents: CreaseParents): SocketCrease? {
    val id = placed.id ?: return null
    val ink = placed.covering?.ink ?: return null
    val patch = placed.surface.patches.singleOrNull() ?: return null
    val runs = buildList {
        if (ink.hem) addAll(hemRuns(placed, patch, cuff = isCuffPart(id)))
        ink.crossings.forEach { other -> character.part(other)?.let { addAll(crossingRuns(placed, id, parents.of(it))) } }
    }
    return if (runs.isEmpty()) null else SocketCrease(id, runs)
}

/** The open edges of [patch], [placed]'s only one, broken where its faces are hidden; a [cuff] leaves out its first ring. */
private fun hemRuns(placed: PlacedPart, patch: Patch, cuff: Boolean): List<List<ResolvedPoint>> {
    val id = requireNotNull(placed.id)
    val shown = shownVertices(placed, patch)
    val edges = mutableListOf<List<Int>>()
    val rings = if (cuff) listOf(patch.nu - 1) else listOf(0, patch.nu - 1)
    for (ring in rings) {
        val columns = (0 until patch.nv).map { patch.index(ring, it) }
        if (isPole(patch, columns)) continue
        edges += if (patch.closedV) columns + columns.first() else columns
    }
    if (!patch.meetsAround) for (column in listOf(0, patch.nv - 1)) edges += (0 until patch.nu).map { patch.index(it, column) }
    fun pointAt(index: Int): ResolvedPoint {
        val u = patch.ringUs[index / patch.nv]
        val v = (index % patch.nv).toFloat() / patch.segments
        val normal = placed.surface.shown(patch.surfaceAt(u, v).normal)
        return ResolvedPoint(Anchor(id, patch.name, u, v), placed.origin + placed.surface.shown(patch.point(index)), normal)
    }
    return edges.flatMap { edge -> shownStretches(edge, shown) }.filter { it.size >= 2 }.map { run -> run.map(::pointAt) }
}

/** Which of [patch]'s grid points a kept face of [placed]'s mesh has as a corner. */
private fun shownVertices(placed: PlacedPart, patch: Patch): BooleanArray {
    val shown = BooleanArray(patch.nu * patch.nv)
    for (index in placed.surface.mesh.indices) if (index < shown.size) shown[index] = true
    return shown
}

/** [edge] cut into the runs of points [shown] holds. */
private fun shownStretches(edge: List<Int>, shown: BooleanArray): List<List<Int>> {
    val runs = mutableListOf<List<Int>>()
    var run = mutableListOf<Int>()
    for (index in edge) {
        if (shown[index]) {
            run += index
        } else {
            if (run.isNotEmpty()) runs += run
            run = mutableListOf()
        }
    }
    if (run.isNotEmpty()) runs += run
    return runs
}

private fun isPole(patch: Patch, ring: List<Int>): Boolean {
    val first = patch.point(ring.first())
    return ring.all { (patch.point(it) - first).length < POLE_REACH }
}
