package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

private const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()

/** How many rounds a mass fit measures and resizes the mass in. */
private const val ROUNDS = 4

/** How close, as a share of the outline's height, the mass's top and sides come to their targets before the fit stops. */
private const val CLOSE_ENOUGH = 0.01f

/** Which share of the outline's reach in a direction its envelope takes, so the spikes and flicks standing out of it count for little. */
private const val ENVELOPE_SHARE = 0.35f

/** How far inside the outline's envelope the mass stops, in thicknesses of its locks, so the locks over it reach the outline. */
private const val INSET_THICKNESSES = 1.3f

/** The degrees either side of straight up a mass's top is read over, and either side of level its sides are read over. */
private const val TOP_SPREAD = 15f
private const val SIDE_SPREAD = 35f

/** How far below the scalp's middle, as a share of the outline's height, its envelope is read, and the mass's sides measured. */
private const val ENVELOPE_BELOW = 0.15f
private const val SIDE_BELOW = 0.1f

/** The ranges the hair-volume template gives its crown and width. */
private val CROWN_RANGE = 0.1f..1.2f
private val WIDTH_RANGE = 0.2f..1.2f

/**
 * Which outline points a hem is read from: in the front view those out past [OUTER_SHARE] of the mass's half width, below the scalp's
 * middle and off the body; in the back those within [MIDDLE_SHARE] of it of the middle. At least [LEAST_HEM_READS] of them, the hem their
 * value [HEM_SHARE] (front) or [NAPE_SHARE] (back) of the way up, so a stray tip below it counts for little.
 */
private const val OUTER_SHARE = 0.5f
private const val MIDDLE_SHARE = 0.4f
private const val LEAST_HEM_READS = 3
private const val HEM_SHARE = 0.15f
private const val NAPE_SHARE = 0.3f

/** The most the fit lets the mass's sides and back hang, and the halvings that find their hang. */
private const val SIDES_MOST = 0.8f
private const val BACK_MOST = 1.1f
private const val HANG_STEPS = 7

/** A step mass parameters are rounded to, so the same outline fits the same mass. */
private const val PARAM_STEP = 0.005f

/** A hair mass refitted: the [params] that change, and its [points] in body space as they build. */
class RefittedMass(val params: Map<String, Float>, val points: List<Vec3>)

/**
 * A hair [mass] as built from [data], built again with other parameters and placed as it is: each build's points carried into body space by
 * the turn and shift that carry its build as it stands onto where it lies.
 */
internal class MassBuilds(private val mass: PlacedPart, private val data: PartData) {
    private val own: List<Vec3> = pointsOf(data.params)
    private val placed: List<Vec3> = mass.surface.patches.flatMap { patch -> patchPoints(patch.points) }.map { mass.origin + mass.surface.shown(it) }
    private val carry: (Vec3) -> Vec3 = carrying(own, placed)

    /** Where the mass sits, its socket on the head. */
    val origin: Vec3 get() = mass.origin

    /** The mass's points in body space with [params] over its own. */
    fun points(params: Map<String, Float>): List<Vec3> = if (params.isEmpty()) placed else pointsOf(data.params + params).map(carry)

    private fun pointsOf(params: Map<String, Float>): List<Vec3> =
        PartBuilder.build(mass.template, data.copy(params = params)).flatMap { patchPoints(it.points) }

    private fun patchPoints(points: FloatArray): List<Vec3> = List(points.size / 3) { Vec3(points[3 * it], points[3 * it + 1], points[3 * it + 2]) }

    private companion object {
        /** The rigid move carrying [from] onto [to], read off three far-apart points of each; a plain shift where they do not match. */
        fun carrying(from: List<Vec3>, to: List<Vec3>): (Vec3) -> Vec3 {
            if (from.size != to.size || from.size < 3) {
                val shift = (to.firstOrNull() ?: Vec3.ZERO) - (from.firstOrNull() ?: Vec3.ZERO)
                return { it + shift }
            }
            val a = 0
            val b = from.indices.maxBy { (from[it] - from[a]).length }
            val ab = (from[b] - from[a]).normalized()
            val c = from.indices.maxBy { ((from[it] - from[a]) - ab * ((from[it] - from[a]) dot ab)).length }
            val local = frame(from[a], from[b], from[c]) ?: return { it + (to[a] - from[a]) }
            val body = frame(to[a], to[b], to[c]) ?: return { it + (to[a] - from[a]) }
            return { p ->
                val d = p - from[a]
                to[a] + body.x * (d dot local.x) + body.y * (d dot local.y) + body.z * (d dot local.z)
            }
        }

        fun frame(a: Vec3, b: Vec3, c: Vec3): Mat3? {
            val x = b - a
            if (x.length < 1e-6f) return null
            val xn = x.normalized()
            val y = (c - a) - xn * ((c - a) dot xn)
            if (y.length < 1e-6f) return null
            val yn = y.normalized()
            return Mat3(xn, yn, xn cross yn)
        }
    }
}

/**
 * The mass's crown and width that bring its top and sides, seen in [front], to the outline's envelope there less [thickness] (its locks'
 * thickness in body units, times [INSET_THICKNESSES]), never below [least], a close cap's, nor below what the mass holds in [params], so
 * no scalp shows: the outline's reach from [middle], the scalp's middle, over the head and out to its sides, taken low among nearby
 * directions so spikes and flicks count for little, measured and resized a few rounds. Then its sides hang down to the front outline's
 * hem at its outer edges, and with a [behind] view drawn its back to that outline's hem over the nape, each the least hang that reaches
 * there, less the inset, from the close cap's or the mass's own where that is more, so a bob's mass is round and full to its hem. A
 * fit only grows the mass, so a refit of the same outline starts from what it set and changes nothing. Its [RefittedMass.params] hold only what differs from [params]. Null where the outline gives no envelope.
 */
internal fun fitMass(
    front: FitView, behind: FitView?, middle: Vec3, builds: MassBuilds, params: Map<String, Float>, thickness: Float, least: Map<String, Float>,
): RefittedMass? {
    val view = front.head
    val m = view.flat(middle)
    val h = front.height
    val inset = view.drawn(thickness) * INSET_THICKNESSES
    val envelope = front.outline.resampled(h / 150f).filter { it.y > m.y - h * ENVELOPE_BELOW && !front.onBody(it) }
    if (envelope.isEmpty()) return null
    fun angle(p: Vec2) = atan2(p.y - m.y, p.x - m.x) * RADIANS_TO_DEGREES
    val tops = envelope.filter { abs(angle(it) - 90f) <= TOP_SPREAD }.map { it.y - m.y }
    val rights = envelope.filter { abs(angle(it)) <= SIDE_SPREAD }.map { it.x - m.x }
    val lefts = envelope.filter { abs(abs(angle(it)) - 180f) <= SIDE_SPREAD }.map { m.x - it.x }
    val topTarget = tops.takeIf { it.isNotEmpty() }?.let { share(it, ENVELOPE_SHARE) - inset }
    val sideReads = listOfNotNull(rights.takeIf { it.isNotEmpty() }?.let { share(it, ENVELOPE_SHARE) }, lefts.takeIf { it.isNotEmpty() }?.let { share(it, ENVELOPE_SHARE) })
    val widthTarget = sideReads.takeIf { it.isNotEmpty() }?.let { it.average().toFloat() - inset }
    if (topTarget == null && widthTarget == null) return null
    val socket = view.flat(builds.origin).y
    val lowCrown = rounded(maxOf(least["crown"] ?: CROWN_RANGE.start, params["crown"] ?: return null))
    val lowWidth = rounded(maxOf(least["width"] ?: WIDTH_RANGE.start, params["width"] ?: return null))
    var crown = lowCrown
    var width = lowWidth
    val lowSides = rounded(maxOf(least["sides"] ?: Float.NEGATIVE_INFINITY, params["sides"] ?: return null))
    val lowBack = rounded(maxOf(least["back"] ?: Float.NEGATIVE_INFINITY, params["back"] ?: return null))
    var sides = lowSides
    var back = lowBack
    fun built() = builds.points(mapOf("crown" to crown, "width" to width, "sides" to sides, "back" to back))
    var points = built()
    var rounds = 0
    while (rounds++ < ROUNDS) {
        val flats = points.map(view::flat)
        val top = flats.maxOf { it.y } - m.y
        val side = flats.filter { it.y > m.y - h * SIDE_BELOW }.maxOfOrNull { abs(it.x - m.x) } ?: break
        val over = top + m.y - socket
        val nextCrown = if (topTarget != null && abs(topTarget - top) > h * CLOSE_ENOUGH && over > 1e-4f) {
            rounded((crown * (topTarget + m.y - socket) / over).coerceIn(CROWN_RANGE).coerceAtLeast(lowCrown))
        } else {
            crown
        }
        val nextWidth = if (widthTarget != null && abs(widthTarget - side) > h * CLOSE_ENOUGH && side > 1e-4f) {
            rounded((width * widthTarget / side).coerceIn(WIDTH_RANGE).coerceAtLeast(lowWidth))
        } else {
            width
        }
        if (nextCrown == crown && nextWidth == width) break
        crown = nextCrown
        width = nextWidth
        points = built()
    }
    val massSide = points.map(view::flat).filter { it.y > m.y - h * SIDE_BELOW }.maxOfOrNull { abs(it.x - m.x) }
    if (massSide != null) {
        val outer = front.outline.resampled(h / 150f).filter { abs(it.x - m.x) >= massSide * OUTER_SHARE && it.y < m.y && !front.onBody(it) }.map { it.y }
        if (outer.size >= LEAST_HEM_READS) {
            val target = share(outer, HEM_SHARE) + inset
            sides = deepest(lowSides, SIDES_MOST) { s ->
                sides = s
                built().map(view::flat).filter { abs(it.x - m.x) >= massSide * OUTER_SHARE }.minOfOrNull { it.y }?.let { it <= target } ?: false
            }
        }
    }
    if (behind != null) {
        val bv = behind.head
        val mb = bv.flat(middle)
        val hb = behind.height
        val bInset = bv.drawn(thickness) * INSET_THICKNESSES
        val massHalf = points.map(bv::flat).filter { it.y > mb.y - hb * SIDE_BELOW }.maxOfOrNull { abs(it.x - mb.x) }
        if (massHalf != null) {
            val within = massHalf * MIDDLE_SHARE
            val hem = behind.outline.resampled(hb / 150f).filter { abs(it.x - mb.x) <= within && it.y < mb.y }.map { it.y }
            if (hem.size >= LEAST_HEM_READS) {
                val target = share(hem, NAPE_SHARE) + bInset
                back = deepest(lowBack, BACK_MOST) { b ->
                    back = b
                    built().map(bv::flat).filter { abs(it.x - mb.x) <= within }.minOfOrNull { it.y }?.let { it <= target } ?: false
                }
            }
        }
    }
    points = built()
    val now = mapOf("crown" to crown, "width" to width, "sides" to sides, "back" to back).filter { (k, v) -> params[k] != v }
    return RefittedMass(now, points)
}

private fun rounded(value: Float): Float = (value / PARAM_STEP).roundToInt() * PARAM_STEP

/**
 * The least value from [low] to [most], rounded, that [reaches] (true from there on as the value grows, a hang reaching down to its hem),
 * found by halving; [low] where it reaches already, [most] where nothing does.
 */
private fun deepest(low: Float, most: Float, reaches: (Float) -> Boolean): Float {
    if (reaches(low)) return low
    if (!reaches(most)) return most
    var short = low
    var deep = most
    repeat(HANG_STEPS) {
        val middle = (short + deep) / 2f
        if (reaches(middle)) deep = middle else short = middle
    }
    return rounded(deep)
}

/** The value [share] of the way up [values] sorted. */
private fun share(values: List<Float>, share: Float): Float {
    val sorted = values.sorted()
    return sorted[((sorted.size - 1) * share).roundToInt()]
}
