package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.ClumpInk
import dev.chieppa.squash.character.surface.firstRunIndex
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.document.PartId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** How many places each strand line runs through. */
private const val STRAND_PLACES = 14

/** Where along a clump its strand lines end, short of the tip's point. */
private const val STRAND_END = 0.96f

/** How far round a broad face, either way of its middle, its strand lines spread, as a share of a turn. */
private const val STRAND_SPREAD = 0.07f

/** How much later than the root fade the strand lines start, at most, each strand its own share of it. */
private const val STRAND_STAGGER = 0.3f

/** How far off the surface a strand line stands, as a share of the clump's thickness there, so it draws over the fill. */
private const val STRAND_LIFT = 0.04f

/** The broad face of a clump its strands lie on, round its section: its top, which a clump lying over the head turns out. */
private val FACES = listOf(0f)

/** Whether [placed]'s join with its parent is inked: its own join ink, else its template's. */
fun joinInked(placed: PlacedPart): Boolean = placed.placement[JoinParams.INK]?.let { it > INK_HALF } ?: placed.template.join.ink

private const val INK_HALF = 0.5f

/** How [placed] inks as a clump of hair, from its template at its defaults and the ink parameters placing carries; null for any other part. */
fun clumpInkOf(placed: PlacedPart): ClumpInk? {
    val values = placed.template.defaults + placed.placement
    return placed.template.blocks.firstNotNullOfOrNull { it.clumpInk(values) }
}

/** Each of [patch]'s grid points' share of its line where a clump's root fades its ink in over [soft] of its length. */
fun rootFade(patch: Patch, soft: Float): FloatArray {
    val us = patch.ringUs
    return FloatArray(patch.nu * patch.nv) { i ->
        if (soft <= 0f) 1f else (us[i / patch.nv] / soft).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    }
}

/**
 * The strand lines along [placed]'s clump, as [ink] says: on its top face, the one turned out where it lies over the head, lines from past the root's fade, each a little later
 * than the last, to near the tip, thin at both ends, standing just off the surface; once for each copy where it shows as copies, on
 * the copies' own surface.
 */
fun strandRuns(placed: PlacedPart, ink: ClumpInk): List<WeightedRun> {
    val id = placed.id ?: return emptyList()
    if (ink.strands <= 0) return emptyList()
    if (ink.flowing) return flowingStrands(placed, id, ink.strands)
    val copies = placed.copies
    val surface = copies?.rule?.surface ?: placed.surface
    val patches = surface.patches
    val run = firstRunIndex(patches).takeIf { it >= 0 } ?: return emptyList()
    val patch = patches[run]
    val local = strandPlaces(patch, ink)
    val shown = surface::shown
    val places: List<(Vec3) -> Vec3> = copies?.places?.map { place -> { p: Vec3 -> place.placed(shown(p)) } }
        ?: listOf { p: Vec3 -> placed.origin + shown(p) }
    val turns: List<(Vec3) -> Vec3> = copies?.places?.map { place -> { n: Vec3 -> (place.turn * shown(n)).normalized() } }
        ?: listOf { n: Vec3 -> shown(n) }
    return places.indices.flatMap { k ->
        local.map { strand ->
            WeightedRun(strand.map { (anchor, point, normal) -> ResolvedPoint(anchor.copy(part = id), places[k](point), turns[k](normal)) }, strand.weights)
        }
    }
}

/** The strand lines of [patch] in its own space, each place with its anchor, point and normal. */
private fun strandPlaces(patch: Patch, ink: ClumpInk): List<StrandLine> {
    val lines = ArrayList<StrandLine>()
    for ((f, face) in FACES.withIndex()) for (k in 0 until ink.strands) {
        val across = if (ink.strands == 1) 0f else (k.toFloat() / (ink.strands - 1) - 0.5f) * 2f * STRAND_SPREAD
        val column = ((face + across) * patch.segments).roundToInt().mod(patch.nv)
        val stagger = STRAND_STAGGER * (((k * 7 + f * 3) % 5) / 4f)
        val start = (ink.rootSoft + stagger).coerceAtMost(STRAND_END - 0.1f)
        val places = (0 until STRAND_PLACES).map { i -> start + (STRAND_END - start) * i / (STRAND_PLACES - 1) }
        val points = places.map { u -> placeAt(patch, u, column) }
        val weights = places.indices.map { i -> sqrt(sin(PI.toFloat() * i / (STRAND_PLACES - 1)).coerceAtLeast(0f)) }
        lines += StrandLine(points, weights)
    }
    return lines
}

/** How far down from the crown, in radians on the volume's sphere, flowing strands start: the first, and how much later the others may. */
private const val FLOW_START = 0.35f
private const val FLOW_STAGGER = 0.55f

/** How far down from the crown, in radians, flowing strands end at most, how much sooner the others may, and the least run they keep. */
private const val FLOW_END = 2.5f
private const val FLOW_SOONER = 0.6f
private const val FLOW_LEAST = 0.4f

/** How far a flowing strand sways from its way round as it falls, in radians, and how often. */
private const val FLOW_SWAY = 0.18f
private const val FLOW_WAVES = 1.7f

/** How many places a flowing strand is sampled at, before it stops at the hem. */
private const val FLOW_PLACES = 40

/** How far off the surface a flowing strand stands, as a share of its ring's reach from its middle. */
private const val FLOW_LIFT = 0.012f

/**
 * [count] strand lines flowing down over a hair volume, read on the sphere through its rings (u 0 the back to 1 the front, v round
 * each ring from the top): each falls from near the crown along its own way round, the ways spread evenly and staggered, and stops at
 * the hem, where the surface turns back up, so it runs down into the locks the hem parts into. See HLD: parts, Hair.
 */
private fun flowingStrands(placed: PlacedPart, id: PartId, count: Int): List<WeightedRun> {
    val patch = placed.surface.patches.firstOrNull { it.nu > 1 && it.nv > 2 } ?: return emptyList()
    val shownUp = placed.surface::shown
    return (0 until count).mapNotNull { k ->
        val wayAt = 2f * PI.toFloat() * (k + 0.5f + 0.3f * (((k * 7) % 5) / 4f - 0.5f)) / count
        val start = FLOW_START + FLOW_STAGGER * (((k * 5) % 7) / 6f)
        val end = FLOW_END - FLOW_SOONER * (((k * 3) % 5) / 4f)
        val places = ArrayList<Triple<Anchor, Vec3, Vec3>>()
        var lowest = Float.MAX_VALUE
        for (i in 0 until FLOW_PLACES) {
            val down = start + (end - start) * i / (FLOW_PLACES - 1)
            val way = wayAt + FLOW_SWAY * sin(FLOW_WAVES * down + k)
            val x = sin(down) * cos(way)
            val z = sin(down) * sin(way)
            val u = ((x + 1f) / 2f).coerceIn(0f, 1f)
            val v = (atan2(z, cos(down)) / (2f * PI.toFloat())).let { it - floor(it) }
            val (anchor, point, normal) = sampleAt(patch, u, v, id)
            val height = shownUp(point).y
            if (height > lowest + 1e-4f && i > 2) break
            lowest = minOf(lowest, height)
            places += Triple(anchor, placed.origin + shownUp(point), shownUp(normal).normalized())
        }
        if (places.size < 3 || (end - start) * places.size / FLOW_PLACES < FLOW_LEAST) return@mapNotNull null
        val n = places.size
        WeightedRun(places.map { (a, p, nrm) -> ResolvedPoint(a, p, nrm) }, List(n) { i -> sqrt(sin(PI.toFloat() * i / (n - 1)).coerceAtLeast(0f)) })
    }
}

/** The point of [patch] at ([u], [v]), between its grid points, lifted off the surface along its normal, with its anchor and normal. */
private fun sampleAt(patch: Patch, u: Float, v: Float, part: PartId): Triple<Anchor, Vec3, Vec3> {
    val us = patch.ringUs
    val ring = us.indexOfFirst { it >= u }.let { if (it <= 0) 1 else it }.coerceAtMost(patch.nu - 1)
    val t = ((u - us[ring - 1]) / (us[ring] - us[ring - 1]).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
    val column = v * patch.segments
    val c0 = floor(column).toInt()
    val s = column - c0
    fun at(r: Int, c: Int) = patch.point(patch.index(r, c.mod(patch.nv)))
    fun across(r: Int) = at(r, c0) + (at(r, c0 + 1) - at(r, c0)) * s
    val point = across(ring - 1) + (across(ring) - across(ring - 1)) * t
    val centre = ringCentre(patch, ring - 1) + (ringCentre(patch, ring) - ringCentre(patch, ring - 1)) * t
    val out = point - centre
    val normal = if (out.length > 1e-7f) out.normalized() else Vec3(0f, 1f, 0f)
    return Triple(Anchor(part, patch.name, u, v), point + normal * (out.length * FLOW_LIFT), normal)
}

private class StrandLine(val places: List<Triple<Anchor, Vec3, Vec3>>, val weights: List<Float>) : List<Triple<Anchor, Vec3, Vec3>> by places

/** The point of [patch] at [u] on [column], lifted off the surface along its normal, with its anchor and normal. */
private fun placeAt(patch: Patch, u: Float, column: Int): Triple<Anchor, Vec3, Vec3> {
    val us = patch.ringUs
    val ring = us.indexOfFirst { it >= u }.let { if (it <= 0) 1 else it }.coerceAtMost(patch.nu - 1)
    val t = ((u - us[ring - 1]) / (us[ring] - us[ring - 1]).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
    fun at(r: Int) = patch.point(patch.index(r, column))
    val point = at(ring - 1) + (at(ring) - at(ring - 1)) * t
    val centre = ringCentre(patch, ring - 1) + (ringCentre(patch, ring) - ringCentre(patch, ring - 1)) * t
    val out = point - centre
    val normal = if (out.length > 1e-7f) out.normalized() else Vec3(0f, 1f, 0f)
    val lifted = point + normal * (out.length * STRAND_LIFT)
    return Triple(Anchor(dev.chieppa.squash.document.PartId(""), patch.name, u, column.toFloat() / patch.segments), lifted, normal)
}

private fun ringCentre(patch: Patch, ring: Int): Vec3 {
    var sum = Vec3.ZERO
    for (c in 0 until patch.segments) sum += patch.point(patch.index(ring, c))
    return sum * (1f / patch.segments)
}
