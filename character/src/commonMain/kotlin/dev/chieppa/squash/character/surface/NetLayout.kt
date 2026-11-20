package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

/** The fewest vertices a run and its parent must both hold for the run's place to be turned and moved to meet its parent's. */
private const val LEAST_SHARED = 3

/** How many rings of mesh sides past a join's blend the net's place is relaxed over, and how many times. */
private const val RELAXED_RINGS = 6
private const val RELAXING = 60

/** A vertex held this much by one run, as a share of all that hold it, lies in no join's blend. */
private const val ALONE = 0.999f

/** How far a run's girth may stand from the girth its net's counts come from, as a ratio either way, before it counts its own. */
private const val MOST_GIRTH_RATIO = 2f

/**
 * One place on a garment's surface for a net to be laid in, at each mesh vertex: [along] its distance down the body's runs from the root,
 * in body units, [turn] its turn round them, 0 to 1, and [countRun] the run whose girth the net's counts round are read from.
 *
 * Each run of the body under the garment is read by its own axis ([RunAxis]). A run is turned and moved to meet the run of its part's
 * parent (or, for a run past a part's first, that part's first run) over the vertices the two both hold: its turn by the mean of how far
 * it stands from its parent's, round the same way or the other, whichever fits closer, and its distance along by the mean difference,
 * running on away from the join as its parent's runs toward it. A vertex several runs hold takes their places blended by their shares of it, the turns
 * each read from the one held most, so the place runs on smoothly across a join with no step: a leg's net runs on over the ankle onto the
 * foot, and the place is relaxed over a band round each join so the net's cells change shape evenly across it. A run whose girth
 * stands far from its parent's counts its own threads round.
 */
internal class NetLayout(surface: CharacterSurface) {
    private val mesh: SurfaceMesh = surface.mesh
    private val runs = surface.field.runs
    private val corners = mesh.vertexCount
    private val firstRuns: Map<PartId, Int> = runs.indices.filter { runs[it].first }.associateBy { runs[it].partId }
    private val chart = surface.chart

    val along = FloatArray(corners)
    val turn = FloatArray(corners)
    val countRun = IntArray(corners) { -1 }

    private val rawAlong = HashMap<Int, FloatArray>()
    private val rawTurn = HashMap<Int, FloatArray>()
    private val fits = HashMap<Int, Fit>()
    private val axes = HashMap<Int, RunAxis>()

    /** How a run's own place is turned and moved: turn [turnSign] times its own plus [phase], along [alongSign] times its own plus [offset]. */
    private class Fit(val turnSign: Float, val phase: Float, val alongSign: Float, val offset: Float, val countRun: Int)

    init {
        for (v in 0 until corners) for (k in 0 until HELD) {
            val run = mesh.held[v * HELD + k]
            if (run < 0 || mesh.heldShares[v * HELD + k] <= 0f) continue
            val axis = axes.getOrPut(run) { RunAxis(runs[run]) }
            val place = axis.place(Vec3(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2]))
            rawAlong.getOrPut(run) { FloatArray(corners) { Float.NaN } }[v] = place.along
            rawTurn.getOrPut(run) { FloatArray(corners) { Float.NaN } }[v] = place.turn
        }
        for (run in rawAlong.keys.sortedBy(::depth)) fits[run] = fitOf(run)
        val joined = BooleanArray(corners)
        for (v in 0 until corners) joined[v] = blend(v)
        relax(joined)
    }

    /**
     * Each vertex within [RELAXED_RINGS] of a join's blend takes the mean of its neighbours' places, [RELAXING] times, the rest held, so
     * the net's cells change shape evenly over the join rather than in a narrow band.
     */
    private fun relax(joined: BooleanArray) {
        val links = chart.links
        val starts = chart.linkStart
        val free = joined.copyOf()
        repeat(RELAXED_RINGS) {
            val ring = free.copyOf()
            for (v in 0 until corners) if (free[v]) for (j in starts[v] until starts[v + 1]) ring[links[j]] = true
            ring.copyInto(free)
        }
        val moving = (0 until corners).filter { free[it] && countRun[it] >= 0 }
        repeat(RELAXING) {
            for (v in moving) {
                var turned = 0f
                var along = 0f
                var n = 0
                for (j in starts[v] until starts[v + 1]) {
                    val other = links[j]
                    if (countRun[other] < 0) continue
                    turned += wrapped(turn[other] - turn[v])
                    along += this.along[other]
                    n++
                }
                if (n == 0) continue
                val at = turn[v] + turned / n
                turn[v] = at - floor(at)
                this.along[v] = along / n
            }
        }
    }

    /** The run [run] meets: its part's first run for a later run, else its parent part's first run; null for a root or a parent not under the garment. */
    private fun parentOf(run: Int): Int? {
        val held = runs[run]
        val parent = if (!held.first) firstRuns[held.partId] else held.part.socket?.parent?.let(firstRuns::get)
        return parent?.takeIf { it != run && it in rawAlong }
    }

    private fun depth(run: Int): Int {
        var steps = 0
        var at: Int? = parentOf(run)
        while (at != null && steps < runs.size) {
            steps++
            at = parentOf(at)
        }
        return steps
    }

    private fun alignedTurn(run: Int, v: Int): Float {
        val fit = fits.getValue(run)
        return fit.turnSign * rawTurn.getValue(run)[v] + fit.phase
    }

    private fun alignedAlong(run: Int, v: Int): Float {
        val fit = fits.getValue(run)
        return fit.alongSign * rawAlong.getValue(run)[v] + fit.offset
    }

    private fun fitOf(run: Int): Fit {
        val parent = parentOf(run) ?: return Fit(1f, 0f, 1f, 0f, run)
        val ownTurn = rawTurn.getValue(run)
        val ownAlong = rawAlong.getValue(run)
        val parentTurn = rawTurn.getValue(parent)
        val shared = (0 until corners).filter { !ownTurn[it].isNaN() && !parentTurn[it].isNaN() }
        if (shared.size < LEAST_SHARED) return Fit(1f, 0f, 1f, 0f, run)
        val (turnSign, phase) = listOf(1f, -1f).map { sign ->
            val angles = shared.map { alignedTurn(parent, it) - sign * ownTurn[it] }
            val mean = circularMean(angles)
            Triple(sign, mean, angles.sumOf { abs(wrapped(it - mean)).toDouble() })
        }.minBy { it.third }.let { it.first to it.second }
        val theirs = shared.map { alignedAlong(parent, it) }.average().toFloat()
        val mine = shared.map { ownAlong[it] }.average().toFloat()
        val towardChild = theirs - meanOf(parent) { alignedAlong(parent, it) }
        val awayFromParent = meanOf(run) { ownAlong[it] } - mine
        val alongSign = if ((towardChild >= 0f) == (awayFromParent >= 0f)) 1f else -1f
        val offset = theirs - alongSign * mine
        val parentCounts = fits.getValue(parent).countRun
        val ratio = chart.scales(run).meanGirth / chart.scales(parentCounts).meanGirth
        val counts = if (ratio in 1f / MOST_GIRTH_RATIO..MOST_GIRTH_RATIO) parentCounts else run
        return Fit(turnSign, phase, alongSign, offset, counts)
    }

    /** The mean of [value] over the vertices [run] holds. */
    private fun meanOf(run: Int, value: (Int) -> Float): Float {
        val own = rawAlong.getValue(run)
        var sum = 0.0
        var n = 0
        for (v in 0 until corners) if (!own[v].isNaN()) {
            sum += value(v)
            n++
        }
        return if (n == 0) 0f else (sum / n).toFloat()
    }

    /** Vertex [v]'s place: the places of the runs holding it, blended by their shares, each turn read from the one held most; true where more than one holds it. */
    private fun blend(v: Int): Boolean {
        var most = -1
        var mostShare = 0f
        var total = 0f
        for (k in 0 until HELD) {
            val run = mesh.held[v * HELD + k]
            val share = mesh.heldShares[v * HELD + k]
            if (run < 0 || share <= 0f) continue
            total += share
            if (share > mostShare) {
                most = run
                mostShare = share
            }
        }
        if (most < 0) return false
        val base = alignedTurn(most, v)
        var turned = 0f
        var along = 0f
        for (k in 0 until HELD) {
            val run = mesh.held[v * HELD + k]
            val share = mesh.heldShares[v * HELD + k]
            if (run < 0 || share <= 0f) continue
            val weight = share / total
            turned += weight * wrapped(alignedTurn(run, v) - base)
            along += weight * alignedAlong(run, v)
        }
        val at = base + turned
        this.turn[v] = at - floor(at)
        this.along[v] = along
        countRun[v] = fits.getValue(most).countRun
        return mostShare < ALONE * total
    }
}

/** [turn] taken to the nearest whole turn: -1/2 to 1/2. */
private fun wrapped(turn: Float): Float = turn - round(turn)

/** The mean of [turns], each a share of a whole turn, read round the circle. */
private fun circularMean(turns: List<Float>): Float {
    var x = 0.0
    var y = 0.0
    for (t in turns) {
        x += cos(2.0 * PI * t)
        y += sin(2.0 * PI * t)
    }
    return (atan2(y, x) / (2.0 * PI)).toFloat()
}
