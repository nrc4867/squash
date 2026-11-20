package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.ClothNet
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.NetPattern
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** How far a net's second set of threads turns from its first in a square net, in degrees. */
private const val SQUARE_TURN = 90f

/** A set of threads slanting less than this from round its part, as a cosine, crosses the part's v seam at least once. */
private const val LEAST_ROUND_SHARE = 0.05f

/** Over what rate, threads crossed for each spacing walked across the surface, a net spread or squeezed by its run goes untrusted. */
private const val STRETCHED_FROM = 0.45f
private const val STRETCHED_TO = 0.65f
private const val SQUEEZED_FROM = 1.6f
private const val SQUEEZED_TO = 2.3f

/** How far a run must hold the net at a vertex to be laid on it before a run that holds it better but lies further from the root. */
private const val SURE = 0.8f

/** The most sockets a part's depth is counted through. */
private const val MOST_DEPTH = 32

/** Over what share of the net's spacing the threads squeezed together round a narrowing run go from untrusted to trusted, as near a pole. */
private const val SQUEEZED_UNTRUSTED = 0.35f
private const val SQUEEZED_TRUSTED = 0.65f

/** How one area of a garment's cloth shows what lies under it: its [opacity] where it faces the viewer, and its [net], null for plain cloth. */
data class AreaSeeThrough(val opacity: Float, val net: ClothNet?)

/**
 * How each area of [garment], in order, shows what lies under it, null for an area that hides it whole: its opacity the area's own times
 * its slot's in [palette], the garment's slot where it has none. Empty where every area is whole.
 */
fun seeThroughOf(garment: Garment, palette: Palette): List<AreaSeeThrough?> {
    val areas = garment.areas.map { area ->
        val slot = area.color ?: garment.color
        val opacity = (area.opacity ?: 1f) * (slot?.let(palette::opacity) ?: 1f)
        if (opacity >= 1f && area.net == null) null else AreaSeeThrough(opacity.coerceIn(0f, 1f), area.net)
    }
    return if (areas.all { it == null }) emptyList() else areas
}

/**
 * A see-through garment's cloth at each vertex of its surface's drawn mesh, as the renderer reads it (HLD: look, See-through cloth): the
 * [area] holding it most, -1 where none does; its [opacity] facing the viewer, 1 where whole; [net], 1 where it is knitted as a net and 0
 * where plain, read on the run of the body that holds it most; and the net's threads laid on the body under it, by the axis of a run
 * holding the vertex or of its part's parent ([RunAxis]), the one that holds the net best there, a parent's first so a leg's net runs on
 * over the ankle, [part] that run's index: [along] is its distance along the run's axis in the net's hole spacing, [roundCos] and
 * [roundSin] where round the axis it lies, as the angle 2 pi times its turn round it, a share of a whole turn as v is.
 * Each set of threads crosses the run at a fixed slant, so the net's value along a set is the turn times its count round ([countA],
 * [countB]), a whole number so the threads meet themselves all the way round, less [along] times the sine of its slant
 * ([slopeA], [slopeB]): the threads lie at its whole numbers, [thread] wide as a share of the spacing. [trust], 0 to 1, is how well the
 * run's coordinates hold the net there: low where the run holds little of the surface, as in a join's blend, and where it narrows to a
 * pole and squeezes its threads together; the renderer shows the net's mean tone where it is low. [hole] is the net's spacing.
 */
class SeeThroughCloth(
    val area: IntArray,
    val opacity: FloatArray,
    val net: FloatArray,
    val thread: FloatArray,
    val part: FloatArray,
    val along: FloatArray,
    val roundCos: FloatArray,
    val roundSin: FloatArray,
    val countA: FloatArray,
    val countB: FloatArray,
    val slopeA: FloatArray,
    val slopeB: FloatArray,
    val trust: FloatArray,
    val hole: FloatArray,
) {
    companion object {
        /** [garment]'s see-through cloth over its surface's drawn mesh, or null where its cloth hides what is under it whole. */
        fun of(garment: SurfaceGarment): SeeThroughCloth? {
            val looks = garment.cover.cover.seeThrough
            if (looks.none { it != null }) return null
            return Reader(garment, looks).read()
        }
    }

    private class Reader(private val garment: SurfaceGarment, private val looks: List<AreaSeeThrough?>) {
        private val surface = garment.surface
        private val mesh = surface.mesh
        private val runs = surface.field.runs
        private val count = surface.render.vertexCount
        private val corners = mesh.vertexCount
        private val firstNet = looks.firstNotNullOfOrNull { it?.net }
        private val out = SeeThroughCloth(
            IntArray(count) { -1 }, FloatArray(count) { 1f }, FloatArray(count), FloatArray(count), FloatArray(count), FloatArray(count),
            FloatArray(count), FloatArray(count), FloatArray(count), FloatArray(count), FloatArray(count), FloatArray(count), FloatArray(count), FloatArray(count),
        )

        fun read(): SeeThroughCloth {
            for (v in 0 until corners) corner(v)
            if (firstNet != null) choose()
            middles()
            return out
        }

        private fun corner(v: Int) {
            val member = heldMost(v) ?: return
            val slot = (0 until HELD).firstOrNull { mesh.held[v * HELD + it] == member } ?: return
            val u = mesh.heldU[v * HELD + slot]
            val around = mesh.heldV[v * HELD + slot]
            val run = runs[member]
            val cloth = garment.cover.clothOf(run.partId)
            val area = cloth?.let { held -> (0 until held.areaCount).maxByOrNull { held.areaAt(it, u, around) } } ?: -1
            val look = looks.getOrNull(area)
            out.area[v] = area
            out.opacity[v] = look?.opacity ?: 1f
            out.net[v] = if (look?.net != null) 1f else 0f
            val net = look?.net ?: firstNet
            if (net == null) {
                out.part[v] = member.toFloat()
                return
            }
            out.thread[v] = net.thread / net.hole
            out.hole[v] = net.hole
            val (slantA, slantB) = when (net.pattern) {
                NetPattern.DIAMOND -> net.angle to -net.angle
                NetPattern.SQUARE -> net.angle to net.angle + SQUARE_TURN
            }
            out.slopeA[v] = sin(slantA * DEGREES)
            out.slopeB[v] = sin(slantB * DEGREES)
            val chosen = candidates(v).map { run -> Laid(run, axisOf(run).place(position(v)), slantA, slantB, net) }
            laid[v] = chosen
        }

        /** A vertex's net laid on [run]: where it lies by the run's axis, the sets' counts round the run, and how far the run's girth holds them. */
        private inner class Laid(val run: Int, val place: AxisPlace, slantA: Float, slantB: Float, val net: ClothNet) {
            private val scales = surface.chart.scales(run)
            val slopeA = sin(slantA * DEGREES)
            val slopeB = sin(slantB * DEGREES)
            val countA = countRound(scales.meanGirth, slantA, net.hole)
            val countB = countRound(scales.meanGirth, slantB, net.hole)
            val squeezed = smoothstep(SQUEEZED_UNTRUSTED, SQUEEZED_TRUSTED, minOf(spacingShare(scales.around(place.u), slantA, countA, net.hole), spacingShare(scales.around(place.u), slantB, countB, net.hole)))
            var trust = 0f
        }

        private val laid = arrayOfNulls<List<Laid>>(corners)
        private val axes = HashMap<Int, RunAxis>()
        private val firstRuns: Map<PartId, Int> = runs.indices.filter { runs[it].first }.associateBy { runs[it].partId }

        private fun axisOf(run: Int): RunAxis = axes.getOrPut(run) { RunAxis(runs[run]) }

        private fun position(v: Int) = Vec3(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2])

        /** The runs a vertex's net may be laid on: those holding it, then the run of each one's parent part, each once. */
        private fun candidates(v: Int): List<Int> {
            val held = (0 until HELD).map { mesh.held[v * HELD + it] }.filter { it >= 0 }
            val parents = held.mapNotNull { runs[it].part.socket?.parent?.let(firstRuns::get) }
            return (held + parents).distinct()
        }

        /**
         * For each vertex, how far each run it may be laid on holds the net there: the run's girth not squeezing the threads, and the
         * threads lying near their spacing apart on the surface at rest, read along the mesh's sides to the neighbours laid on the same run;
         * then the net laid on the run trusted most, a parent's before a run of its own that holds it as well, and the trust eased once over
         * the vertex's neighbours so the fade has no speckle.
         */
        private fun choose() {
            val links = surface.chart.links
            val starts = surface.chart.linkStart
            val positions = mesh.positions
            for (v in 0 until corners) {
                val here = laid[v] ?: continue
                for (each in here) {
                    var mostA = 0f
                    var mostB = 0f
                    for (j in starts[v] until starts[v + 1]) {
                        val n = links[j]
                        val there = laid[n]?.firstOrNull { it.run == each.run } ?: continue
                        val dx = positions[n * 3] - positions[v * 3]
                        val dy = positions[n * 3 + 1] - positions[v * 3 + 1]
                        val dz = positions[n * 3 + 2] - positions[v * 3 + 2]
                        val length = sqrt(dx * dx + dy * dy + dz * dz)
                        if (length <= 0f) continue
                        var turn = there.place.turn - each.place.turn
                        turn -= round(turn)
                        val along = (there.place.along - each.place.along) / each.net.hole
                        mostA = maxOf(mostA, abs(turn * each.countA - along * each.slopeA) / length)
                        mostB = maxOf(mostB, abs(turn * each.countB - along * each.slopeB) / length)
                    }
                    each.trust = each.squeezed * minOf(spacingHeld(mostA * each.net.hole), spacingHeld(mostB * each.net.hole))
                }
                val parentFirst = here.sortedWith(compareByDescending<Laid> { it.trust >= SURE }.thenByDescending { if (it.trust >= SURE) depth(it.run) else 0 }.thenByDescending { it.trust })
                val pick = parentFirst.first()
                out.part[v] = pick.run.toFloat()
                out.roundCos[v] = cos(2f * PI.toFloat() * pick.place.turn)
                out.roundSin[v] = sin(2f * PI.toFloat() * pick.place.turn)
                out.along[v] = pick.place.along / pick.net.hole
                out.countA[v] = pick.countA
                out.countB[v] = pick.countB
                out.trust[v] = pick.trust
            }
            val held = out.trust.copyOf(corners)
            for (v in 0 until corners) {
                var sum = held[v]
                var n = 1
                for (j in starts[v] until starts[v + 1]) {
                    sum += held[links[j]]
                    n++
                }
                out.trust[v] = sum / n
            }
        }

        /** How near the root a run's part lies: higher for a part fewer sockets from it, so a parent's net runs on before its child's. */
        private fun depth(run: Int): Int {
            var steps = 0
            var part = runs[run].part.socket?.parent
            while (part != null && steps < MOST_DEPTH) {
                steps++
                part = firstRuns[part]?.let { runs[it].part.socket?.parent }
            }
            return -steps
        }

        /** The run holding vertex [v] most, or null where none does. */
        private fun heldMost(v: Int): Int? {
            val winner = mesh.winners[v]
            if (winner >= 0) return winner
            var best = -1
            var share = 0f
            for (k in 0 until HELD) {
                val member = mesh.held[v * HELD + k]
                if (member >= 0 && mesh.heldShares[v * HELD + k] > share) {
                    best = member
                    share = mesh.heldShares[v * HELD + k]
                }
            }
            return best.takeIf { it >= 0 }
        }

        /** Each quad's middle as the mean of its corners, its area, part and counts its first corner's. */
        private fun middles() {
            val indices = surface.render.indices
            val smooth = listOf(out.opacity, out.net, out.thread, out.part, out.along, out.roundCos, out.roundSin, out.slopeA, out.slopeB, out.trust, out.hole)
            for (tri in 0 until indices.size / 3) {
                val middle = indices[tri * 3 + 2]
                if (middle < corners) continue
                val quad = tri / 4
                val first = indices[quad * 4 * 3]
                for (values in smooth) {
                    var sum = 0f
                    for (k in 0 until 4) sum += values[indices[(quad * 4 + k) * 3]]
                    values[middle] = sum / 4f
                }
                out.area[middle] = out.area[first]
                out.countA[middle] = out.countA[first]
                out.countB[middle] = out.countB[first]
            }
        }
    }
}

private const val DEGREES = PI.toFloat() / 180f

/**
 * How many threads of a set slanting [slant] degrees from a part's length cross round it, [girth] round, [hole] apart: a whole number,
 * so the set meets itself across the part's v seam, at least one where the set runs any way round.
 */
internal fun countRound(girth: Float, slant: Float, hole: Float): Float {
    val share = cos(slant * DEGREES)
    if (abs(share) < LEAST_ROUND_SHARE) return 0f
    val count = (girth * share / hole).roundToInt()
    return if (count == 0) share.sign else count.toFloat()
}

/**
 * How well threads changing [rate] times their spacing for each body unit hold the net: 1 near once, falling to 0 as they spread far
 * further apart or squeeze far closer.
 */
private fun spacingHeld(rate: Float): Float =
    smoothstep(STRETCHED_FROM, STRETCHED_TO, rate) * (1f - smoothstep(SQUEEZED_FROM, SQUEEZED_TO, rate))

/** How far apart a set slanting [slant] degrees, [count] round a ring [girth] round, lies there, as a share of [hole]; 1 for a set that runs round. */
private fun spacingShare(girth: Float, slant: Float, count: Float, hole: Float): Float =
    if (count == 0f) 1f else girth * abs(cos(slant * DEGREES)) / (abs(count) * hole)
