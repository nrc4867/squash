package dev.chieppa.squash.character.shape

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** How the front of an opening drops below its ring: a round curve, as a crew or scoop neck, or two straight sides to a point, as a V-neck. */
enum class OpeningCut { ROUND, V }

/** How far past a part's root ring an opening reaches where nothing says, in body units. */
const val DEFAULT_OPENING_REACH = 0.01f

/** How many rows past its opening ring a part's ring is read, with it, to give the way the part leaves the body. */
private const val AXIS_ROWS = 6

/** How far from its root a part's narrowest ring is looked for, in rows: its first half. */
private const val NARROW_ROWS = CHART_ROWS / 2

/** How much wider than its narrowest ring a part's ring is at most to be where it leaves its flare into the body. */
private const val BASE_WIDTH = 1.15f

/** How little of the body's forward lies across a part's axis for it to give the front of an opening round it. */
private const val LEAST_ACROSS = 0.2f

/**
 * An opening round where a part comes out of the cloth, in body units: every cell within [reach] past the part's base ring, read across
 * the part's axis, and on the front, toward the body's forward, dropping [depth] below the ring at its middle in its [cut]. The base ring
 * is where the part leaves its flare into the body: the first of its rings from its root no wider than [BASE_WIDTH] of its narrowest in
 * its first half. A neck drawn flaring into rounded shoulders keeps its flare as cloth past the reach, so the neckline follows the flare and
 * the shoulder slope; a straight neck opens from its root. A
 * crew neck is a round cut a few hundredths deep, a V-neck a V several times deeper.
 */
data class Opening(val cut: OpeningCut = OpeningCut.ROUND, val reach: Float = DEFAULT_OPENING_REACH, val depth: Float = 0f) {
    init {
        require(reach >= 0f) { "An opening reaches a negative way past its ring" }
        require(depth >= 0f) { "An opening drops a negative depth" }
    }

    /** How far below the ring the front drops at [across], a share of the opening's half width from its middle (0) to its side (1). */
    fun dropAt(across: Float): Float {
        val t = abs(across)
        if (t >= 1f) return 0f
        return depth * when (cut) {
            OpeningCut.ROUND -> sqrt(1f - t * t)
            OpeningCut.V -> 1f - t
        }
    }
}

/**
 * The cells [opening] holds round where [part] comes out, on every part but those beyond [part], on both sides of a pair: what a neckline,
 * an armhole or an ear hole cuts from the cloth, or paints round it as a band. [forward] is the body's front.
 */
fun ChartPlaces.opening(part: PartId, opening: Opening, forward: Vec3 = Vec3.X): SurfaceMask {
    val frames = baseFrames(part, forward)
    if (frames.isEmpty()) return SurfaceMask.empty(chart)
    val own = chart.part(part)?.id
    val beyond = beyond(part)
    return where { on, p -> on !in beyond && frames.any { it.holds(p, opening, on == own) } }
}

/** Each side's base ring of [part], where it leaves its flare into the body, with its axis and [forward] across it. */
private fun ChartPlaces.baseFrames(part: PartId, forward: Vec3): List<RootFrame> {
    val rows = (0..NARROW_ROWS).map { rings(part, it) }
    val sides = rows.first().size
    if (sides == 0 || rows.any { it.size != sides }) return emptyList()
    return (0 until sides).map { side ->
        val narrowest = rows.minOf { it[side].second }
        val base = rows.indexOfFirst { it[side].second <= narrowest * BASE_WIDTH }
        val next = rings(part, minOf(base + AXIS_ROWS, CHART_ROWS - 1)).getOrNull(side)?.first ?: rows[base][side].first
        RootFrame.of(rows[base][side].first, rows[base][side].second, next, forward)
    }
}

/** How far past its ring a new opening reaches, in its part's base radii. */
private const val NEW_REACH_RADII = 0.3f

/** How deep a new round opening's front drops, in its part's base radii. */
private const val NEW_ROUND_DEPTH_RADII = 0.8f

/** How deep a new V opening's front drops, in its part's base radii. */
private const val NEW_V_DEPTH_RADII = 3f

/** A new opening in [cut] round [part], sized by its base ring: a crew neck's scoop or a V-neck's point for a neck of its width. */
fun ChartPlaces.newOpening(part: PartId, cut: OpeningCut): Opening {
    val radius = baseFrames(part, Vec3.X).maxOfOrNull { it.radius } ?: return Opening(cut)
    val depth = radius * if (cut == OpeningCut.V) NEW_V_DEPTH_RADII else NEW_ROUND_DEPTH_RADII
    return Opening(cut, radius * NEW_REACH_RADII, depth)
}

/** The parts beyond [part] in the socket tree, [part] not among them. */
private fun ChartPlaces.beyond(part: PartId): Set<PartId> {
    val root = chart.part(part)?.id ?: return emptySet()
    val out = HashSet<PartId>()
    var grew = true
    while (grew) {
        grew = false
        for (each in chart.parts) {
            if (each.id in out || each.id == root) continue
            val parent = each.parent?.let { chart.part(it)?.id } ?: continue
            if (parent == root || parent in out) {
                out += each.id
                grew = true
            }
        }
    }
    return out
}

/** A part's base ring: its [center] and [radius], the [axis] the part leaves along, and [front] and [side] across it. */
private class RootFrame(val center: Vec3, val radius: Float, val axis: Vec3, val front: Vec3, val side: Vec3) {
    /** Whether [p] lies in [opening]; [own] where it lies on the part itself, which opens all the way up it. */
    fun holds(p: Vec3, opening: Opening, own: Boolean): Boolean {
        val d = p - center
        val along = d dot axis
        val width = radius + opening.reach
        val across = d - axis * along
        val x = across dot front
        val y = across dot side
        if (along > width && !own) return false
        if (across.length <= width && along >= -width) return true
        return x > 0f && abs(y) < width && -along <= opening.dropAt(y / width)
    }

    companion object {
        fun of(center: Vec3, radius: Float, next: Vec3, forward: Vec3): RootFrame {
            val toward = next - center
            val axis = if (toward.length > 0f) toward.normalized() else Vec3.Y
            var front = forward - axis * (forward dot axis)
            if (front.length < LEAST_ACROSS) front = Vec3.Y - axis * (Vec3.Y dot axis)
            front = front.normalized()
            return RootFrame(center, radius, axis, front, axis cross front)
        }
    }
}
