package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (2.0 * PI).toFloat()

/** Where along a volume, from its back, its hang turns from the back's to the sides'. */
private const val SIDES_AT = 0.5f

/** The share of the volume's length, from its front, its parting runs back over. */
private const val PARTING_REACH = 0.6f

/** How wide the parting's groove is, in radians round the ring. */
private const val PARTING_WIDTH = 0.16f

/** Below this much closing a ring's hang closes with it, so the end rings close to a point. */
private const val END_CLOSE = 0.12f

/** The least a ring's top or hang reaches from its axis, as a share of how far its top rises from the socket's height. */
private const val MIN_SHARE = 0.15f

/** How far down round a ring, as the cosine below its axis, its locks start cutting into its hang, and where they cut in full. */
private const val LOCKS_FROM = 0.15f
private const val LOCKS_SPAN = 0.45f

/** How far behind the middle the back's locks are read from, a share of the length, and where along the volume they turn to the curtains'. */
private const val LOCKS_BACK = 0.3f
private const val LOCKS_TURN_FROM = 0.6f
private const val LOCKS_TURN_SPAN = 0.15f

/** Where along the volume a swept fringe starts, and how far its longer side hangs at most, as a share of the length. */
private const val SWEEP_FROM = 0.6f
private const val SWEEP_REACH = 0.25f

/** Where across a ring, as the sine of the angle from its top, its middle's hang starts turning to its sides', and where that ends. */
private const val CURTAIN_FROM = 0.55f
private const val CURTAIN_TO = 0.85f

/** How many rings and steps round each the hem is found over. */
private const val HEM_ROWS = 48
private const val HEM_STEPS = 64

/** How many places each edge of a shine band is drawn through. */
private const val SHINE_PLACES = 48

/**
 * A hair volume's shape with nothing drawn, its rings running from the back of the head (u 0) to the front (u 1) along a straight axis
 * [length] long, each ring about the axis, which rises [rise] of the length toward the front so the volume's front closes on the forehead
 * rather than before the face; heights are from the axis before it rises, the height of the socket. Each ring's top lies [crown] of the length above the axis and its half width is [width] of it, both
 * rounding off toward the two ends as a superellipse of exponent [fullness] (2 an ellipse, higher fuller). Its sides hang [back] of the
 * length below the axis at the back and [sides] from the middle on; its underside's middle hangs as far at the back and the middle and
 * rises to [fringe] at the front, so at the front the sides fall past the face as two curtains and the middle stops above the eyes, a
 * fringe. The lower half of each ring flares out by [flip] at its hem. A parting at [parting] round the top (0 the middle, toward 0.25
 * the side v 0.25 faces) dents the top by [partDepth] of the crown over the front of the volume. [sweep] lengthens the fringe toward
 * one side (positive toward v 0.25) and shortens it toward the other, a fringe swept aside. Its hem splits into [locks] locks round the
 * head, seen from above, each cut [lockDepth] of the length up from its neighbours' notches, the cut fading in down the ring's lower half:
 * so the mass is one surface and only its ends part into locks. [lockJag] shortens each lock by its own share of that, a ragged hem;
 * [lockSkew] leans each lock's point toward one neighbour; [lockRound] rounds the points; [lockSeed] picks which locks are shorter; [lockFlare] spreads each lock's point out
 * sideways by that share of the half width, so the locks splay. Ring
 * v runs from the top (0) round by the side v 0.25 faces to underneath (0.5).
 */
data class VolumeShape(
    val length: Float,
    val crown: Float,
    val width: Float,
    val back: Float,
    val sides: Float,
    val fringe: Float,
    val flip: Float = 0f,
    val fullness: Float = 2.5f,
    val parting: Float = 0f,
    val partDepth: Float = 0f,
    val rise: Float = 0f,
    val locks: Int = 0,
    val lockDepth: Float = 0f,
    val lockJag: Float = 0f,
    val lockSkew: Float = 0f,
    val lockRound: Float = 0f,
    val lockSeed: Int = 0,
    val lockFlare: Float = 0f,
    val sweep: Float = 0f,
) {
    /** How far the ring at [t] is closed toward the volume's ends, 1 in its middle and 0 at either end. */
    fun closing(t: Float): Float {
        val x = abs(2f * t.coerceIn(0f, 1f) - 1f)
        return (1f - x.pow(fullness)).coerceAtLeast(0f).pow(1f / fullness)
    }

    /** How far below the axis the ring at [t] hangs at its sides, in body units. */
    fun sideHangAt(t: Float): Float {
        val share = if (t < SIDES_AT) back + (sides - back) * smooth(t / SIDES_AT) else sides
        return share * length * sqrt(closing(t))
    }

    /** How far below the axis the ring at [t] hangs under its middle, in body units. */
    fun middleHangAt(t: Float): Float {
        val share = if (t < SIDES_AT) back + (sides - back) * smooth(t / SIDES_AT) else sides + (fringe - sides) * smooth((t - SIDES_AT) / (1f - SIDES_AT))
        return share * length * sqrt(closing(t))
    }

    /** How far above the axis the ring at [t] rises, in body units. */
    fun topAt(t: Float): Float = crown * length * closing(t)

    /** The ring at [t]'s half width, in body units. */
    fun halfWidthAt(t: Float): Float = width * length * closing(t)

    /** How far the ring at [t]'s top rises above the risen axis there. */
    private fun topOver(t: Float): Float = (topAt(t) - riseAt(t)).coerceAtLeast(MIN_SHARE * topAt(t)) * endClose(t)

    /** How far the ring at [t] is let reach at all: 1 but at the very ends, where it closes to a point with the volume. */
    private fun endClose(t: Float): Float = (closing(t) / END_CLOSE).coerceAtMost(1f)

    /** How far the ring at [t]'s sides hang below the risen axis there. */
    private fun sideHangUnder(t: Float): Float = (sideHangAt(t) + riseAt(t)).coerceAtLeast(MIN_SHARE * topAt(t))

    /** How far the axis has risen at [t], in body units. */
    fun riseAt(t: Float): Float = rise * length * smooth(t)

    /**
     * The section of the ring at [t], with [segments] control points, in body units about the axis: an ellipse over the top, and under it
     * the sides' hang falling to the middle's, so a short middle leaves two curtains either side.
     */
    fun sectionAt(t: Float, segments: Int): ClosedSpline {
        val partAngle = parting * TWO_PI
        val reach = ((t - (1f - PARTING_REACH)) / PARTING_REACH).coerceIn(0f, 1f)
        val top = topOver(t)
        val half = halfWidthAt(t)
        return ClosedSpline(
            List(segments) { k ->
                val angle = k * TWO_PI / segments
                val up = cos(angle)
                if (up >= 0f) {
                    val off = wrappedAngle(angle - partAngle)
                    val dent = partDepth * crown * length * reach * closing(t) * exp(-(off / PARTING_WIDTH) * (off / PARTING_WIDTH))
                    Vec2(sin(angle) * half, up * top - dent)
                } else {
                    val under = up * up
                    val splay = 1f + flip * under + lockFlare * lockPoint(t, angle)
                    Vec2(sin(angle) * half * splay, up * hangAt(t, angle))
                }
            },
            Vec2(0f, 0f),
        )
    }

    /** How far the ring at [t] reaches from the risen axis at [angle] round it, on its lower half: its hang, cut into locks. */
    private fun hangAt(t: Float, angle: Float): Float {
        val top = topOver(t)
        val end = endClose(t)
        val across = sin(angle)
        val swept = sweep * across * smooth((t - SWEEP_FROM) / (1f - SWEEP_FROM)) * SWEEP_REACH * length
        val middle = (middleHangAt(t) + riseAt(t) + swept).coerceAtLeast(MIN_SHARE * top)
        val hang = hangOver(across, sideHangUnder(t), middle) * end
        val floor = MIN_SHARE * top * end
        return (hang - lockCut(t, angle)).coerceAtLeast(floor)
    }

    /**
     * How far the locks cut up into the hang at [t] and [angle]: nothing at a lock's point, [lockDepth] of the length at the notch between
     * two, less for a shorter lock, faded in down the ring's lower half.
     */
    private fun lockCut(t: Float, angle: Float): Float {
        if (locks <= 0 || lockDepth <= 0f) return 0f
        val below = smooth((-cos(angle) - LOCKS_FROM) / LOCKS_SPAN)
        if (below <= 0f) return 0f
        val (shaped, lock) = lockShape(t, angle)
        val short = lockJag * shortness(lock.mod(locks))
        return lockDepth * length * below * (shaped + short * (1f - shaped))
    }

    /** How near a lock's point the hem at [t] and [angle] lies, 1 at its point and 0 at a notch, faded in down the ring's lower half. */
    private fun lockPoint(t: Float, angle: Float): Float {
        if (locks <= 0 || lockFlare == 0f) return 0f
        val below = smooth((-cos(angle) - LOCKS_FROM) / LOCKS_SPAN)
        if (below <= 0f) return 0f
        return below * below * (1f - lockShape(t, angle).first)
    }

    /** How far from its lock's point, 0 at the point to 1 at a notch, the hem at [t] and [angle] lies, shaped by [lockRound], and which lock. */
    private fun lockShape(t: Float, angle: Float): Pair<Float, Int> {
        val turn = (lockTurn(t, sin(angle)) / TWO_PI + 0.5f) * locks
        val lock = floor(turn)
        val within = turn - lock
        val point = 0.5f + 0.4f * lockSkew.coerceIn(-1f, 1f)
        val off = if (within < point) (point - within) / point else (within - point) / (1f - point)
        return off.pow(1f + lockRound) to lock.toInt()
    }

    /**
     * Which way round the head, seen from above, the hem at [t] and [across] (the sine of the angle round the ring) lies, in radians: at
     * the back and under the front's middle by how far across it lies, so locks part the nape and the fringe side by side, and along the
     * curtains by how far forward, so they part the curtains one behind another, each lock straight down its ring's lower half.
     */
    private fun lockTurn(t: Float, across: Float): Float {
        val wide = width * length
        val lateral = across * halfWidthAt(t)
        val back = Vec2(-LOCKS_BACK * length, lateral)
        val front = Vec2((t - 0.5f) * length, (lateral / (CURTAIN_FROM * wide)).coerceIn(-1f, 1f) * wide)
        val f = smooth((t - LOCKS_TURN_FROM) / LOCKS_TURN_SPAN)
        val x = back.x + (front.x - back.x) * f
        val y = back.y + (front.y - back.y) * f
        return atan2(y, x)
    }

    /** How much shorter lock [k] is, 0 to 1, the same for the same seed. */
    private fun shortness(k: Int): Float {
        var h = (k + 1) * 374761393 + lockSeed * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    /** How far a ring hangs at [across], the sine of the angle round it from the top, between its [middle] hang and its [side] hang. */
    private fun hangOver(across: Float, side: Float, middle: Float): Float {
        val a = ((abs(across) - CURTAIN_FROM) / (CURTAIN_TO - CURTAIN_FROM)).coerceIn(0f, 1f)
        return middle + (side - middle) * a * a * (3f - 2f * a)
    }

    /** How far below the risen axis the ring at [t] reaches at [angle] round it, in body units; negative above it. */
    private fun depthAt(t: Float, angle: Float): Float {
        val up = cos(angle)
        if (up >= 0f) return -up * topOver(t)
        return -up * hangAt(t, angle)
    }

    /**
     * The hem: on each ring, the stretch round it within [depth] body units of its lowest point, as two loops of places (u, v) on its
     * halves toward v 0.25 and v 0.75, so the hem follows the curtains and the back wherever they end.
     */
    fun hemLoops(depth: Float): List<List<Vec2>> {
        val rows = (1 until HEM_ROWS).mapNotNull { i ->
            val t = i.toFloat() / HEM_ROWS
            val angles = (0..HEM_STEPS).map { PI.toFloat() * (0.5f + 0.5f * it / HEM_STEPS) }
            val depths = angles.map { depthAt(t, it) }
            val deepest = depths.max()
            if (deepest <= 0f) return@mapNotNull null
            val inside = depths.indices.filter { depths[it] >= deepest - depth }
            Triple(t, angles[inside.first()] / TWO_PI, angles[inside.last()] / TWO_PI)
        }
        if (rows.size < 2) return emptyList()
        val half = rows.map { Vec2(it.first, it.second) } + rows.reversed().map { Vec2(it.first, it.third) }
        return listOf(half, half.map { Vec2(it.x, 1f - it.y) }.reversed())
    }

    /** The volume's rings over [us] along [axis], their up from [up], each ring's section with [segments] control points. */
    fun slices(axis: AxisSpline, us: List<Float>, up: Vec3?, segments: Int): List<Slice> {
        val frames = SliceBuilder.frames(axis, us, up)
        return us.indices.map { ring ->
            val t = us[ring]
            val f = frames[ring]
            val frame = Frame(f.center + f.up * riseAt(t), f.tangent, f.up)
            Slice(t, frame, 2f, 2f, sectionAt(t, segments), frame.rotation)
        }
    }

    /**
     * Where [local], a point in the volume's own space with [along], [up] and [side] its axes there, lies against the volume: x from -1
     * at its back to 1 at its front, and y and z on the sphere through the volume's rings, y up and z toward the side v 0.25 faces.
     */
    fun sphereOf(local: Vec3, along: Vec3, up: Vec3, side: Vec3): Vec3 {
        val t = ((local dot along) / length).coerceIn(0f, 1f)
        val x = 2f * t - 1f
        val rising = (local dot up) - riseAt(t)
        val reach = max(if (rising >= 0f) topOver(t) else sideHangUnder(t), 1e-5f)
        val angle = atan2((local dot side) / max(halfWidthAt(t), 1e-5f), rising / reach)
        val round = sqrt(max(0f, 1f - x * x))
        return Vec3(x, cos(angle) * round, sin(angle) * round)
    }

    private fun smooth(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun wrappedAngle(a: Float): Float = a - TWO_PI * floor((a + PI.toFloat()) / TWO_PI)
}

/**
 * A band of shine round a hair volume, as an anime head's ring of light: on the sphere through the volume's rings (see
 * [VolumeShape.sphereOf]), the points whose height y lies between [at] less half of [width] and [at] plus half of it, its lower edge cut
 * into [teeth] points [jag] deep as it runs round the crown.
 */
data class ShineBand(val at: Float, val width: Float, val jag: Float = 0f, val teeth: Int = 0) {
    private val top: Float get() = at + width / 2f

    /** The lower edge's height at [azimuth] round the crown, 0 at the front and pi at the back. */
    private fun bottom(azimuth: Float): Float {
        if (jag <= 0f || teeth <= 0) return at - width / 2f
        val phase = teeth * azimuth / PI.toFloat()
        val f = phase - floor(phase)
        return at - width / 2f - jag * (1f - abs(2f * f - 1f))
    }

    /** How far inside the band [sphere], a point [VolumeShape.sphereOf] gives, lies: at least zero inside. */
    fun inside(sphere: Vec3): Float {
        val azimuth = atan2(abs(sphere.z), sphere.x)
        return minOf(sphere.y - bottom(azimuth), top - sphere.y)
    }

    /**
     * The band on the volume's own rings as places (u, v), u the ring's share from the back and v round it: two loops, the half toward v
     * 0.25 and its mirror toward v 0.75, each meeting the other along the seam at v 0 over the crown. Empty where the band misses the volume.
     */
    fun loops(): List<List<Vec2>> {
        if (top <= -1f || bottom(0f) >= 1f) return emptyList()
        val outer = List(SHINE_PLACES + 1) { i -> edge(bottom(PI.toFloat() * i / SHINE_PLACES), PI.toFloat() * i / SHINE_PLACES) }
        val inner = List(SHINE_PLACES + 1) { i -> edge(top, PI.toFloat() * (SHINE_PLACES - i) / SHINE_PLACES) }
        val half = (outer + inner).distinctAdjacent()
        return listOf(half, half.map { Vec2(it.x, 1f - it.y) }.reversed())
    }

    /** The place (u, v) on the volume at height [height] on its sphere and [azimuth] round the crown, on the half toward v 0.25. */
    private fun edge(height: Float, azimuth: Float): Vec2 {
        val level = height.coerceIn(-1f, 1f)
        val round = sqrt(max(0f, 1f - level * level))
        val x = round * cos(azimuth)
        val z = round * sin(azimuth)
        val angle = atan2(z, level).coerceIn(0f, PI.toFloat())
        return Vec2((x + 1f) / 2f, angle / TWO_PI)
    }

    private fun List<Vec2>.distinctAdjacent(): List<Vec2> = filterIndexed { i, p -> i == 0 || abs(p.x - this[i - 1].x) + abs(p.y - this[i - 1].y) > 1e-5f }
}
