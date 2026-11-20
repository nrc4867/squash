package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TWO_PI = (2.0 * PI).toFloat()

/** How near the hem a line stops or starts, as a share of the length. */
private const val HEM_GAP = 1e-3f

/** How many places a layer's edge runs through for each lock. */
private const val EDGE_PLACES = 12

/** How far from the crown strands start, in radians on the sphere through the volume, the first and how much further the others may. */
private const val STRAND_FROM = 0.3f
private const val STRAND_STAGGER = 0.5f

/** How far above the hem strands stop, the most, as a share of the way from the hem to the crown, so their ends are ragged. */
private const val STRAND_SHORT = 0.15f

/** How far, as a share of the gap between two strands, a strand's place round wanders from even. */
private const val STRAND_WANDER = 0.35f

/** How far above the hem a strand starts gathering into its lock's point, as a share of the way from the hem to the crown, and how far in it gathers. */
private const val GATHER_FROM = 0.35f
private const val GATHER = 0.85f

/** The fewest places a strand keeps. */
private const val STRAND_LEAST = 4

/** How deep a layer's edge is cut between its locks, as a share of the hem's cut, so the layers over it read softer than the hem. */
private const val LAYER_CUT = 0.6f

/** Where along the volume a layer's edge or a lock line may run, so they keep off the back's pole and the face. */
private const val POLE = 0.06f
private const val FRONT = 0.85f

/** How narrow a lock may be, as a share of the hem's cut, before the lines between it and its neighbours are left out. */
private const val NARROW = 0.5f

/** How high a layer's edge may run, as a share of the way from the hem to the crown, so it stays off the crown. */
private const val EDGE_TOP = 0.6f

/** How far a line between two locks leans round as it rises, as a share of a lock. */
private const val LOCK_LINE_LEAN = 0.12f

/**
 * [count] strand lines, each starting below the crown at its own place round it, staggered, and falling straight down the surface as
 * seen from above, so the strands spread evenly from the crown, then gathering into their lock's point toward the hem, where they stop,
 * some sooner than others: runs of places (t, angle).
 */
fun HairFlow.strands(count: Int): List<List<Vec2>> {
    if (count <= 0) return emptyList()
    return (0 until count).mapNotNull { k ->
        val wander = STRAND_WANDER * (((k * 7) % 5) / 4f - 0.5f)
        val round = TWO_PI * (k + 0.5f + wander) / count
        val down = STRAND_FROM + STRAND_STAGGER * (((k * 5) % 7) / 6f)
        val x = sin(down) * cos(round)
        val z = sin(down) * sin(round)
        val start = Vec2(((x + 1f) / 2f).coerceIn(0.01f, 0.99f), atan2(z, cos(down)))
        if (!facesOut(start.x, start.y)) return@mapNotNull null
        val short = STRAND_SHORT * (((k * 3) % 5) / 4f)
        var entered: Pair<Float, Float>? = null
        val run = fall(
            start,
            steer = { place, height ->
                val way = shape.azimuthAt(place.x, place.y)
                val from = hemAt(way) + GATHER_FROM * (topAt(way) - hemAt(way))
                if (height < from && entered == null) entered = way to height
                entered?.let { (first, top) ->
                    val end = hemAt(gathered(first, 1f))
                    gathered(first, ((top - height) / (top - end).coerceAtLeast(1e-5f)).coerceIn(0f, 1f))
                }
            },
            stop = { place, height ->
                val way = shape.azimuthAt(place.x, place.y)
                height < hemAt(way) + short * (topAt(way) - hemAt(way)) + HEM_GAP * shape.length
            },
        )
        run.takeIf { it.size >= STRAND_LEAST }
    }
}

/** The way a strand that starts gathering at [way] takes when it has come [share] of the way from there to the hem. */
private fun HairFlow.gathered(way: Float, share: Float): Float {
    if (shape.locks <= 0) return way
    val turn = shape.lockTurnOf(way)
    val lock = floor(turn)
    val point = shape.lockCentre
    val pull = GATHER * share * (2f - share)
    return shape.azimuthOfTurn(lock + point + (turn - lock - point) * (1f - pull))
}

/**
 * The edges of [layers] layers of locks over the hem, each [gap] of the length above the last with its locks half a lock round from
 * the last's, cut a little less deep than the hem and less again where a lock is narrower than the cut is deep, so each layer's points
 * lie over the notches under it: runs of places (t, angle) on the lower part of the volume, off the back's pole and the face.
 */
fun HairFlow.layerEdges(layers: Int, gap: Float): List<List<Vec2>> {
    if (layers <= 0 || gap <= 0f) return emptyList()
    val locks = max(shape.locks, 1)
    val places = locks * EDGE_PLACES
    val widths = lockWidths()
    return (1..layers).flatMap { layer ->
        val targets = List(places + 1) { j ->
            val turn = j.toFloat() / EDGE_PLACES
            val way = shape.azimuthOfTurn(turn).let { if (shape.locks == 0) (turn / locks - 0.5f) * TWO_PI else it }
            way to (meanHemAt(way) + layer * gap * shape.length + layerCut(turn, layer, widths) - 0.5f * LAYER_CUT * shape.lockDepth * shape.length)
        }
        trace(targets) { place, height ->
            val way = shape.azimuthAt(place.x, place.y)
            place.x in POLE..FRONT && height > hemAt(way) + HEM_GAP * shape.length && height < hemAt(way) + EDGE_TOP * (topAt(way) - hemAt(way))
        }
    }
}

/** Lines [reach] of the length long running up from each notch of the hem, where one lock lies over the next: runs of places (t, angle). */
fun HairFlow.lockLines(reach: Float): List<List<Vec2>> {
    if (shape.locks <= 0 || reach <= 0f) return emptyList()
    val widths = lockWidths()
    return (0 until shape.locks).flatMap { n ->
        if (min(widths[n], widths[(n - 1).mod(shape.locks)]) < NARROW * shape.lockDepth * shape.length) return@flatMap emptyList()
        val notch = shape.azimuthOfTurn(n.toFloat())
        val from = hemAt(notch) + HEM_GAP * shape.length
        val short = 0.5f + 0.5f * shape.lockShortness(n, shape.lockSeed + 1)
        val targets = List(EDGE_PLACES) { i ->
            val s = i.toFloat() / (EDGE_PLACES - 1)
            val way = shape.azimuthOfTurn(n + LOCK_LINE_LEAN * s * shape.lockSkew.let { if (it == 0f) 1f else it })
            way to (from + reach * shape.length * short * s)
        }
        trace(targets.reversed()) { place, _ -> place.x >= POLE }
    }
}

/**
 * How wide each lock is across at the hem, in body units: from its notch to the next, or 0 where either was not sampled.
 */
private fun HairFlow.lockWidths(): FloatArray {
    val locks = max(shape.locks, 1)
    val notches = List(locks) { n -> hemPointAt(shape.azimuthOfTurn(n.toFloat())) }
    return FloatArray(locks) { n ->
        val a = notches[n]
        val b = notches[(n + 1) % locks]
        if (a == null || b == null) 0f else (a - b).length
    }
}

/** How far up a layer's edge is cut at [turn] locks round, from its lock's point, for [layer]. */
private fun HairFlow.layerCut(turn: Float, layer: Int, widths: FloatArray): Float {
    val shifted = turn + 0.5f * layer
    val lock = floor(shifted)
    val within = shifted - lock
    val point = shape.lockCentre
    val off = if (within < point) (point - within) / point else (within - point) / (1f - point)
    val shaped = off * off * (3f - 2f * off)
    val short = shape.lockJag * shape.lockShortness(lock.toInt(), shape.lockSeed + layer)
    val depth = shape.lockDepth * shape.length
    val narrow = (widths[floor(turn).toInt().mod(widths.size)] / depth).coerceIn(0f, 1f)
    return LAYER_CUT * depth * narrow * (shaped + short * (1f - shaped))
}
