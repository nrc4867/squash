package dev.chieppa.squash.geom

import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val TWO_PI = (2.0 * PI).toFloat()

/** How many rings and places round each the flow is sampled on to find its hem and a first guess at a place. */
private const val ROWS = 72
private const val STEPS = 128

/** How many ways round the head the hem's height is kept for. */
private const val BINS = 256

/** How far a sampled place's neighbours lie, in t and in radians, to find which way it faces. */
private const val NEAR_T = 1e-3f
private const val NEAR_ANGLE = 1e-3f

/** How many steps a place is searched for at most, and how near it must come, in radians round and as a share of the length up. */
private const val SOLVE_STEPS = 10
private const val SOLVE_WAY = 2e-3f
private const val SOLVE_HEIGHT = 1e-3f

/** The most a solve step moves in t and in radians round. */
private const val MOST_T = 0.06f
private const val MOST_ANGLE = 0.25f

/** How far, as a share of the length, two places of one line may lie apart before it breaks there. */
private const val BREAK = 0.08f

/** How far from the middle of the head, at least, a place facing down lies to show beside it rather than under it, a share of the half width. */
private const val BESIDE = 0.75f

/** How long a step a fall takes, as a share of the length, and how many it takes at most. */
private const val FALL_STEP = 0.012f
private const val FALL_STEPS = 160

/** How far a fall may be steered round at each step, as a share of the step. */
private const val STEER = 1.5f

/** How many hair flows are kept, by their shape, so a drag that keeps the volume's shape finds none again. */
private const val KEPT = 4

/**
 * How hair flows over a [VolumeShape]: each place (t, angle) has a way round the head, [VolumeShape.azimuthAt], and a height,
 * [VolumeShape.heightAt], and hair falls down each way to the hem, the lowest place that way. From these come the hem's height each way
 * round, the hem area as a level ([hemLevel]), and the lines a volume inks: strands that fall along their way and gather into their
 * lock's point ([strands]), the ragged edges of the layers of locks over the hem ([layerEdges]) and the lines up from the notches where
 * one lock lies over the next ([lockLines]), each a run of places (t, angle). See HLD: parts, Hair.
 */
class HairFlow private constructor(val shape: VolumeShape) {
    private val ts = FloatArray(ROWS * STEPS)
    private val angles = FloatArray(ROWS * STEPS)
    private val ways = FloatArray(ROWS * STEPS)
    private val heights = FloatArray(ROWS * STEPS)
    private val outer = BooleanArray(ROWS * STEPS)
    private val hem = FloatArray(BINS) { Float.MAX_VALUE }
    private val hemPlaces = IntArray(BINS) { -1 }
    private val tops = FloatArray(BINS) { -Float.MAX_VALUE }
    private val mean: FloatArray

    init {
        for (r in 0 until ROWS) for (s in 0 until STEPS) {
            val i = r * STEPS + s
            val t = (r + 0.5f) / ROWS
            val a = (s + 0.5f) * TWO_PI / STEPS
            val p = shape.pointAt(t, a)
            ts[i] = t
            angles[i] = a
            ways[i] = shape.azimuthAt(t, a)
            heights[i] = p.y
            outer[i] = facesOut(t, a)
            val bin = binOf(ways[i])
            if (p.y < hem[bin]) {
                hem[bin] = p.y
                hemPlaces[bin] = i
            }
            tops[bin] = max(tops[bin], p.y)
        }
        fill(hem)
        for (i in tops.indices) if (tops[i] == -Float.MAX_VALUE) tops[i] = Float.MAX_VALUE
        fill(tops)
        val reach = BINS / (2 * max(shape.locks, 1))
        mean = FloatArray(BINS) { i -> (-reach..reach).sumOf { hem[(i + it).mod(BINS)].toDouble() }.toFloat() / (2 * reach + 1) }
    }

    /** The hem's height at [way] round the head, its lowest place that way, in body units. */
    fun hemAt(way: Float): Float = read(hem, way)

    /** The hem's height at [way] evened out over a lock either way, midway between its points and notches, in body units. */
    fun meanHemAt(way: Float): Float = read(mean, way)

    /** The lowest sampled place at [way] round, in the volume's own space, or null where none was sampled that way. */
    fun hemPointAt(way: Float): Vec3? = hemPlaces[binOf(way)].takeIf { it >= 0 }?.let { shape.pointAt(ts[it], angles[it]) }

    /** The height of the highest place at [way], in body units. */
    fun topAt(way: Float): Float = read(tops, way)

    /** How far inside the hem area the place ([t], [angle]) lies, in body units: positive within [depth] above the hem that way. */
    fun hemLevel(t: Float, angle: Float, depth: Float): Float = hemAt(shape.azimuthAt(t, angle)) + depth - shape.heightAt(t, angle)

    /**
     * The places that reach each of [targets], a way round and a height, one after another, each found from the last: runs of places
     * (t, angle), broken where a target cannot be reached on the outside of the volume, where [keep] says no, or where two places lie
     * far apart.
     */
    internal fun trace(targets: List<Pair<Float, Float>>, keep: (Vec2, Float) -> Boolean = { _, _ -> true }): List<List<Vec2>> {
        val runs = ArrayList<List<Vec2>>()
        var run = ArrayList<Vec2>()
        var last: Vec2? = null
        fun close() {
            if (run.size >= 3) runs += run
            run = ArrayList()
        }
        for ((way, height) in targets) {
            val found = last?.let { solve(way, height, it) } ?: solve(way, height, guess(way, height))
            val place = found?.takeIf { facesOut(it.x, it.y) && keep(it, height) }
            if (place == null) {
                close()
                last = null
                continue
            }
            val previous = run.lastOrNull()
            if (previous != null && (shape.pointAt(previous.x, previous.y) - shape.pointAt(place.x, place.y)).length > BREAK * shape.length) close()
            run += place
            last = place
        }
        close()
        return runs
    }

    /**
     * The way hair falls from [start], straight down the surface as seen from above the head's middle, as a run of places (t, angle) a
     * step apart, turned round its ring at each step to the way [steer] asks for at its place and height, if any, until it reaches the
     * bottom of the lock it falls into, turns in under the head, or [stop] says so at a place and its height.
     */
    internal fun fall(start: Vec2, steer: (Vec2, Float) -> Float?, stop: (Vec2, Float) -> Boolean): List<Vec2> {
        val run = arrayListOf(start)
        var t = start.x
        var a = start.y
        var height = shape.heightAt(t, a)
        val step = FALL_STEP * shape.length
        repeat(FALL_STEPS) {
            val pt = (shape.pointAt(t + NEAR_T, a) - shape.pointAt(t - NEAR_T, a)) * (0.5f / NEAR_T)
            val pa = (shape.pointAt(t, a + NEAR_ANGLE) - shape.pointAt(t, a - NEAR_ANGLE)) * (0.5f / NEAR_ANGLE)
            val gtt = pt dot pt
            val gta = pt dot pa
            val gaa = pa dot pa
            val det = gtt * gaa - gta * gta
            if (det < 1e-12f) return run
            val wt = wrapped(roundOf(t + NEAR_T, a) - roundOf(t - NEAR_T, a))
            val wa = wrapped(roundOf(t, a + NEAR_ANGLE) - roundOf(t, a - NEAR_ANGLE))
            val down = if (wa * pt.y - wt * pa.y > 0f) -1f else 1f
            val dt = down * wa
            val da = -down * wt
            val size = sqrt(max(0f, dt * dt * gtt + 2f * dt * da * gta + da * da * gaa))
            if (size < 1e-6f) return run
            val nt = (t + (dt * step / size).coerceIn(-MOST_T, MOST_T)).coerceIn(0.005f, 0.995f)
            var na = a + (da * step / size).coerceIn(-MOST_ANGLE, MOST_ANGLE)
            var st = nt
            steer(Vec2(nt, na), shape.heightAt(nt, na))?.let { way ->
                val off = wrapped(way - shape.azimuthAt(nt, na))
                val ft = wrapped(shape.azimuthAt(nt + NEAR_T, na) - shape.azimuthAt(nt - NEAR_T, na)) / (2f * NEAR_T)
                val fa = wrapped(shape.azimuthAt(nt, na + NEAR_ANGLE) - shape.azimuthAt(nt, na - NEAR_ANGLE)) / (2f * NEAR_ANGLE)
                val gt = (gaa * ft - gta * fa) / det
                val ga = (gtt * fa - gta * ft) / det
                val across = ft * gt + fa * ga
                if (across > 1e-9f) {
                    val most = STEER * step
                    val scale = (off / across).let { k -> k.coerceIn(-most / sqrt(across), most / sqrt(across)) }
                    st = (nt + (gt * scale).coerceIn(-MOST_T, MOST_T)).coerceIn(0.005f, 0.995f)
                    na += (ga * scale).coerceIn(-MOST_ANGLE, MOST_ANGLE)
                }
            }
            val nt2 = st
            val next = shape.heightAt(nt2, na)
            if (next >= height || !facesOut(nt2, na)) return run
            val place = Vec2(nt2, na)
            if (stop(place, next)) return run
            t = nt2
            a = na
            height = next
            run += place
        }
        return run
    }

    /** The place (t, angle) on the outside of the volume whose way is [way] and whose height is [height], or null where none is. */
    internal fun place(way: Float, height: Float): Vec2? = solve(way, height, guess(way, height))?.takeIf { facesOut(it.x, it.y) }

    /** Which way round the head's upright middle, seen from above, the place at [t] and [a] lies, in radians. */
    private fun roundOf(t: Float, a: Float): Float {
        val p = shape.pointAt(t, a)
        return atan2(p.z, p.x - 0.5f * shape.length)
    }

    /** The sampled outer place nearest [way] and [height], as a first guess. */
    private fun guess(way: Float, height: Float): Vec2 {
        var best = 0
        var bestScore = Float.MAX_VALUE
        val scale = shape.length
        for (i in ts.indices) {
            if (!outer[i]) continue
            val dw = wrapped(ways[i] - way)
            val dh = (heights[i] - height) / scale
            val score = dw * dw + 4f * dh * dh
            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }
        return Vec2(ts[best], angles[best])
    }

    /** The place (t, angle) near [from] whose way is [way] and whose height is [height], or null where none is near. */
    private fun solve(way: Float, height: Float, from: Vec2): Vec2? {
        var t = from.x
        var a = from.y
        val scale = shape.length
        repeat(SOLVE_STEPS) {
            val fw = wrapped(shape.azimuthAt(t, a) - way)
            val fh = (shape.heightAt(t, a) - height) / scale
            if (abs(fw) < SOLVE_WAY && abs(fh) < SOLVE_HEIGHT) return Vec2(t, a)
            val wt = wrapped(shape.azimuthAt(t + NEAR_T, a) - shape.azimuthAt(t - NEAR_T, a)) / (2f * NEAR_T)
            val wa = wrapped(shape.azimuthAt(t, a + NEAR_ANGLE) - shape.azimuthAt(t, a - NEAR_ANGLE)) / (2f * NEAR_ANGLE)
            val ht = (shape.heightAt(t + NEAR_T, a) - shape.heightAt(t - NEAR_T, a)) / (2f * NEAR_T * scale)
            val ha = (shape.heightAt(t, a + NEAR_ANGLE) - shape.heightAt(t, a - NEAR_ANGLE)) / (2f * NEAR_ANGLE * scale)
            val det = wt * ha - wa * ht
            if (abs(det) < 1e-9f) return null
            val dt = (-fw * ha + fh * wa) / det
            val da = (-fh * wt + fw * ht) / det
            t = (t + dt.coerceIn(-MOST_T, MOST_T)).coerceIn(0.005f, 0.995f)
            a += da.coerceIn(-MOST_ANGLE, MOST_ANGLE)
        }
        val fw = wrapped(shape.azimuthAt(t, a) - way)
        val fh = (shape.heightAt(t, a) - height) / scale
        return if (abs(fw) < 4f * SOLVE_WAY && abs(fh) < 4f * SOLVE_HEIGHT) Vec2(t, a) else null
    }

    /**
     * Whether the place faces out from the middle of the head, rather than in toward it or straight down under it, where the head
     * covers it.
     */
    internal fun facesOut(t: Float, a: Float): Boolean {
        val out = shape.pointAt(t, a) - Vec3(0.5f * shape.length, 0f, 0f)
        val alongT = shape.pointAt(t + NEAR_T, a) - shape.pointAt(t - NEAR_T, a)
        val round = shape.pointAt(t, a + NEAR_ANGLE) - shape.pointAt(t, a - NEAR_ANGLE)
        val normal = (round cross alongT).normalized()
        if (normal dot out <= 0f) return false
        if (normal.y >= 0f) return true
        val beside = sqrt(out.x * out.x + out.z * out.z)
        return beside > BESIDE * shape.width * shape.length
    }

    private fun binOf(way: Float): Int = (((way / TWO_PI + 0.5f) * BINS).toInt()).mod(BINS)

    private fun read(table: FloatArray, way: Float): Float {
        val at = (way / TWO_PI + 0.5f) * BINS - 0.5f
        val i = floor(at)
        val f = at - i
        val a = table[i.toInt().mod(BINS)]
        val b = table[(i.toInt() + 1).mod(BINS)]
        return a + (b - a) * f
    }

    /** [table]'s empty bins filled from their nearest filled neighbours round it. */
    private fun fill(table: FloatArray) {
        val filled = table.indices.filter { table[it] < Float.MAX_VALUE }
        if (filled.isEmpty()) {
            table.fill(0f)
            return
        }
        for (i in table.indices) {
            if (table[i] < Float.MAX_VALUE) continue
            val before = filled.lastOrNull { it < i } ?: (filled.last() - BINS)
            val after = filled.firstOrNull { it > i } ?: (filled.first() + BINS)
            val f = (i - before).toFloat() / (after - before)
            table[i] = table[before.mod(BINS)] + (table[after.mod(BINS)] - table[before.mod(BINS)]) * f
        }
    }

    private fun wrapped(a: Float): Float = a - TWO_PI * floor((a + PI.toFloat()) / TWO_PI)

    companion object {
        @Volatile
        private var kept: List<HairFlow> = emptyList()

        /** The flow over [shape], made once for each of the last few shapes asked for. */
        fun of(shape: VolumeShape): HairFlow {
            kept.firstOrNull { it.shape == shape }?.let { return it }
            val made = HairFlow(shape)
            kept = (listOf(made) + kept).take(KEPT)
            return made
        }
    }
}

/** The hem area of a hair volume [shape] as a level on its (u, v): within [depth] body units above the hem, each way round. */
data class HemLevel(val shape: VolumeShape, val depth: Float) : UvLevel {
    override fun at(u: Float, v: Float, uScale: Float, vScale: Float): Float = HairFlow.of(shape).hemLevel(u, v * TWO_PI, depth)
}

