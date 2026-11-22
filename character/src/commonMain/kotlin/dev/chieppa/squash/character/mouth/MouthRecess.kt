package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.RecessSolid
import dev.chieppa.squash.geom.field.RecessStation
import dev.chieppa.squash.geom.field.RecessWallPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How many places along the mouth line its recess is laid at, corner to corner. */
private const val STATIONS = 25

/** How far a recess's edge is rounded into the lips round it, as a share of how far they part at their widest. */
private const val LIP_ROUNDING = 0.35f

/** How deep the recess may go behind a place, as a multiple of how far the lips part there, so a narrow opening stays shallow. */
private const val DEEP_PER_HALF = 2.5f

/** How far the recess's slot runs out in front of the surface, as a multiple of the opening's tallest half, so it opens through the lips' curve. */
private const val REACH_PER_HALF = 0.75f

/** How far a place found on the patch may miss its place in the mouth's flat frame, a share of the mouth's width; a station missing by more is left out. */
private const val WALK_MISS = 0.02f

/**
 * How deep a jaw's pocket goes behind its slit at the slit's tallest, as a share of the straight distance between the slit's corners: the
 * lips' width, or across a muzzle between the ends of its line.
 */
const val JAW_POCKET = 0.32f

/** The fewest stations a recess is laid along. */
private const val MIN_STATIONS = 3

private const val TINY = 1e-6f

/**
 * A mouth's recess, cut into [part]'s run [patch] where its lips part: the groove behind the opening of the mouth group [group], swept
 * along its line from one corner to the other, in the part's own space as its rest surface holds it, shown or not; a [jaw]'s, whose lips part
 * on the surface as the jaw drops where [lipsPart]. See HLD: look, Mouth.
 */
data class MouthRecess(
    val part: PartId,
    val group: MarkGroupId,
    val patch: String,
    val stations: List<RecessStation>,
    val reach: Float,
    val jaw: Boolean = false,
    val lipsPart: Boolean = true,
) {
    /** The recess as a solid, in the part's own space. */
    val solid: RecessSolid by lazy { RecessSolid(stations, reach) }

    /** How far the recess's edge is rounded into the lips round it, in body units. */
    val rounding: Float get() = stations.maxOf { it.half } * LIP_ROUNDING

    /**
     * The point on the recess's wall [along] the mouth line, evenly across the frame from where the lips start to part (0, the side the
     * frame's x starts on) to where they meet again (1), and [depth] of the way in (0 at the lip, 1 at the back), on the upper wall, the
     * upper jaw's, when [upper], else the lower jaw's, with the wall's normal pointing into the open mouth, in the part's own space.
     */
    fun wallAt(along: Float, depth: Float, upper: Boolean): RecessWallPoint = solid.wallAt(along, depth, upper)
}

/**
 * The recess behind [mouth]'s opening on [holder]'s [surface], [carve] of its width deep at most and never deeper than [DEEP_PER_HALF]
 * times how far its lips part at a place; null where its lips do not part or [carve] is 0, a painted mouth. A [jaw]'s pocket, behind the
 * slit a posed jaw drops away from, is [JAW_POCKET] of the distance between its corners deep at the opening's tallest, shallower toward
 * the corners; its lips part on the surface where [lipsPart], else its opening is painted and the jaw takes only the face below its lower lip.
 */
fun mouthRecessOf(
    holder: PartId,
    group: MarkGroupId,
    mouth: ShapedMouth,
    surface: PartSurface,
    carve: Float,
    jaw: Boolean = false,
    lipsPart: Boolean = true,
): MouthRecess? {
    if (carve <= 0f && !jaw) return null
    val loop = mouth.flat.opening?.takeIf { it.size >= MIN_LOOP } ?: return null
    val left = loop.minOf { it.x }
    val right = loop.maxOf { it.x }
    val width = right - left
    if (width < TINY) return null
    val within = width * WALK_MISS
    fun at(uv: SurfaceUv) = surface.pointAt(Anchor(holder, mouth.patch, uv.u, uv.v))
    val raw = (0 until STATIONS).mapNotNull { i ->
        val x = left + width * i / (STATIONS - 1)
        val (top, bottom) = crossings(loop, x) ?: return@mapNotNull null
        val middleUv = mouth.uvWithin(Vec2(x, (top + bottom) / 2f), within) ?: return@mapNotNull null
        val upperUv = mouth.uvWithin(Vec2(x, top), within) ?: return@mapNotNull null
        val lowerUv = if (bottom - top < TINY) upperUv else mouth.uvWithin(Vec2(x, bottom), within) ?: return@mapNotNull null
        val upper = at(upperUv) ?: return@mapNotNull null
        val lower = at(lowerUv) ?: return@mapNotNull null
        val middle = at(middleUv) ?: return@mapNotNull null
        Raw(surface.shown(upper.position), surface.shown(lower.position), surface.shown(middle.position), surface.shown(middle.normal), middleUv, upperUv, lowerUv)
    }
    if (raw.size < MIN_STATIONS) return null
    val normals = raw.indices.map { i -> outward(raw, i) }
    val downs = raw.mapIndexed { i, r -> r.down(normals[i]) }
    val fallback = downs.firstOrNull { it != null } ?: return null
    val halves = raw.mapIndexed { i, r -> downs[i]?.let { d -> abs((r.bottom - r.top) dot d) / 2f } ?: 0f }
    val tallest = halves.max()
    if (tallest < TINY) return null
    val first = (halves.indexOfFirst { it > TINY } - 1).coerceAtLeast(0)
    val last = (halves.indexOfLast { it > TINY } + 1).coerceAtMost(raw.lastIndex)
    val chord = (raw[last].middle - raw[first].middle).length
    val stations = (first..last).map { i ->
        val r = raw[i]
        val down = downs[i] ?: nearestDown(downs, i) ?: fallback
        val depth = if (jaw) min(JAW_POCKET * chord * sqrt(halves[i] / tallest), facingRoom(raw, normals, i)) else min(carve * width, DEEP_PER_HALF * halves[i])
        RecessStation(r.middle, down, normals[i] * -1f, halves[i], depth, r.place, r.upperPlace, r.lowerPlace)
    }
    if (stations.size < 2) return null
    return MouthRecess(holder, group, mouth.patch, stations, tallest * REACH_PER_HALF, jaw, lipsPart)
}

/** A station's top, bottom and middle on the surface and the surface's outward normal at the middle, in the part's own space, and their places on the patch. */
private class Raw(
    val top: Vec3,
    val bottom: Vec3,
    val middle: Vec3,
    val normal: Vec3,
    val place: SurfaceUv,
    val upperPlace: SurfaceUv,
    val lowerPlace: SurfaceUv,
) {
    /** The unit direction from top to bottom square to [out], or null where they meet. */
    fun down(out: Vec3): Vec3? {
        val across = bottom - top
        val flat = across - out * (across dot out)
        return if (flat.length < TINY) null else flat.normalized()
    }
}

/**
 * The outward direction out of the opening at station [i] of [raw]: square to the mouth line, along its neighbours' middles, and to the
 * opening across it, on the side the surface faces; the surface's own normal where the opening is shut there. Read from the stations
 * rather than the surface, so a mouth round a muzzle's tip, where the surface's normal turns fast, opens the way the mouth faces.
 */
private fun outward(raw: List<Raw>, i: Int): Vec3 {
    val r = raw[i]
    val along = raw[minOf(i + 1, raw.lastIndex)].middle - raw[maxOf(i - 1, 0)].middle
    val across = r.bottom - r.top
    val square = along cross across
    if (square.length < TINY * (along.length + TINY) || across.length < TINY) return r.normal
    val n = square.normalized()
    return if ((n dot r.normal) < 0f) n * -1f else n
}

/**
 * How deep station [i] of [raw] may go before it meets the pocket of a station across the mouth from it, as on the other side of a narrow
 * muzzle: [FACING_ROOM] of the way to the nearest station whose outward way, [normals], faces back against its own.
 */
private fun facingRoom(raw: List<Raw>, normals: List<Vec3>, i: Int): Float =
    raw.indices.filter { (normals[it] dot normals[i]) < FACING }.minOfOrNull { (raw[it].middle - raw[i].middle).length * FACING_ROOM } ?: Float.MAX_VALUE

/** How far two stations' outward ways must turn against each other to face across the mouth, as the cosine between them. */
private const val FACING = -0.3f

/** The share of the way to a station across the mouth a jaw's pocket may reach, so the two sides' pockets never meet. */
private const val FACING_ROOM = 0.4f

private fun nearestDown(downs: List<Vec3?>, i: Int): Vec3? =
    downs.indices.filter { downs[it] != null }.minByOrNull { abs(it - i) }?.let { downs[it] }

/** Where a line down the frame at [x] first and last crosses [loop]; at the loop's ends, where it only touches, the end place itself. */
private fun crossings(loop: List<Vec2>, x: Float): Pair<Float, Float>? {
    var top = Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (i in loop.indices) {
        val a = loop[i]
        val b = loop[(i + 1) % loop.size]
        if (x < min(a.x, b.x) || x > max(a.x, b.x)) continue
        val y = if (abs(b.x - a.x) < TINY) min(a.y, b.y) else a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
        val yb = if (abs(b.x - a.x) < TINY) max(a.y, b.y) else y
        top = min(top, y)
        bottom = max(bottom, yb)
    }
    if (top > bottom) {
        val end = loop.minByOrNull { abs(it.x - x) } ?: return null
        return end.y to end.y
    }
    return top to bottom
}

private const val MIN_LOOP = 3
