package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.ClothNet
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.NetPattern
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.geom.surface.HELD
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** How far a net's second set of threads turns from its first in a square net, in degrees. */
private const val SQUARE_TURN = 90f

/** A set of threads slanting less than this from round its part, as a cosine, crosses the part's v seam at least once. */
private const val LEAST_ROUND_SHARE = 0.05f

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
 * where plain, read on the run of the body that holds it most; and the net's threads laid on the body under it by [NetLayout], one place blended across the runs holding each vertex so a leg's net runs on over the ankle onto
 * the foot with no step, [part] the run whose girth its counts round come from: [along] is its distance along the body in the net's
 * hole spacing, [roundCos] and [roundSin] where round it lies, as the angle 2 pi times its turn round it.
 * Each set of threads crosses the run at a fixed slant, so the net's value along a set is the turn times its count round ([countA],
 * [countB]), a whole number so the threads meet themselves all the way round, less [along] times the sine of its slant
 * ([slopeA], [slopeB]): the threads lie at its whole numbers, [thread] wide as a share of the spacing. [trust], 0 to 1, is how well the
 * run's girth holds the net there: low where it narrows to a pole and squeezes its threads together; the renderer shows the net's mean
 * tone where it is low. [hole] is the net's spacing.
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
            middles()
            return out
        }

        private val layout = if (firstNet != null) NetLayout(surface) else null

        private fun corner(v: Int) {
            val member = heldMost(v) ?: return
            val slot = (0 until HELD).firstOrNull { mesh.held[v * HELD + it] == member } ?: return
            val u = mesh.heldU[v * HELD + slot]
            val around = mesh.heldV[v * HELD + slot]
            val run = runs[member]
            val cloth = garment.cover.clothOf(run.partId)
            val area = cloth?.let { held -> if (held.areaCount == 0) 0 else (0 until held.areaCount).maxByOrNull { held.areaAt(it, u, around) } } ?: -1
            val look = looks.getOrNull(area)
            out.area[v] = area
            out.opacity[v] = look?.opacity ?: 1f
            out.net[v] = if (look?.net != null) 1f else 0f
            val net = look?.net ?: firstNet
            val layout = layout
            if (net == null || layout == null) {
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
            val counts = layout.countRun[v].takeIf { it >= 0 } ?: member
            val girth = surface.chart.scales(counts).meanGirth
            val countA = countRound(girth, slantA, net.hole)
            val countB = countRound(girth, slantB, net.hole)
            val ring = surface.chart.scales(member).around(u)
            out.part[v] = counts.toFloat()
            out.countA[v] = countA
            out.countB[v] = countB
            out.along[v] = layout.along[v] / net.hole
            out.roundCos[v] = cos(2f * PI.toFloat() * layout.turn[v])
            out.roundSin[v] = sin(2f * PI.toFloat() * layout.turn[v])
            out.trust[v] = smoothstep(SQUEEZED_UNTRUSTED, SQUEEZED_TRUSTED, minOf(spacingShare(ring, slantA, countA, net.hole), spacingShare(ring, slantB, countB, net.hole)))
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

/** How far apart a set slanting [slant] degrees, [count] round a ring [girth] round, lies there, as a share of [hole]; 1 for a set that runs round. */
private fun spacingShare(girth: Float, slant: Float, count: Float, hole: Float): Float =
    if (count == 0f) 1f else girth * abs(cos(slant * DEGREES)) / (abs(count) * hole)
