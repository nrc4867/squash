package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How hair flows over a volume: its hem each way round, the hem area, strands gathering into locks, layers' edges and lock lines. */
class HairFlowTest {
    private val bob = VolumeShape(
        length = 1f, crown = 0.55f, width = 0.5f, back = 0.55f, sides = 0.45f, fringe = -0.05f, rise = 0.3f,
        locks = 12, lockDepth = 0.2f, lockJag = 0f,
    )
    private val flow = HairFlow.of(bob)

    @Test
    fun `the hem rises at each notch and falls at each lock's point`() {
        val back = PI.toFloat() * 0.9f
        val turn = floor(bob.lockTurnOf(back))
        val notch = flow.hemAt(bob.azimuthOfTurn(turn))
        val point = flow.hemAt(bob.azimuthOfTurn(turn + bob.lockCentre))
        assertTrue(notch - point > 0.1f, "the notch stands ${notch - point} over the point")
        assertTrue(flow.meanHemAt(bob.azimuthOfTurn(turn)) in point..notch)
    }

    @Test
    fun `the hem area runs along the cut edge evenly, at points and notches alike`() {
        val depth = 0.05f
        var inPoint = 0
        var inNotch = 0
        for (r in 1 until 60) for (s in 0 until 120) {
            val t = r / 60f
            val angle = 2f * PI.toFloat() * s / 120
            if (flow.hemLevel(t, angle, depth) <= 0f) continue
            val within = bob.lockTurnOf(bob.azimuthAt(t, angle)).let { it - floor(it) }
            if (abs(within - bob.lockCentre) < 0.15f) inPoint++
            if (within < 0.15f || within > 0.85f) inNotch++
            assertTrue(bob.heightAt(t, angle) < flow.hemAt(bob.azimuthAt(t, angle)) + depth + 1e-4f)
        }
        assertTrue(inPoint > 0 && inNotch > 0, "the hem holds points ($inPoint) and notches ($inNotch)")
    }

    @Test
    fun `strands gather toward their lock's point as they reach the hem`() {
        val strands = flow.strands(24)
        assertTrue(strands.size >= 12, "${strands.size} strands")
        fun off(place: Vec2): Float = bob.lockTurnOf(bob.azimuthAt(place.x, place.y)).let { abs(it - floor(it) - bob.lockCentre) }
        for (run in strands) assertTrue(bob.heightAt(run.first().x, run.first().y) > bob.heightAt(run.last().x, run.last().y))
        val starts = strands.map { off(it.first()) }.average()
        val ends = strands.map { off(it.last()) }.average()
        assertTrue(ends < starts * 0.75, "ends lie ${ends} from their lock's point, starts ${starts}")
        assertTrue(strands.count { off(it.last()) < 0.25f } >= strands.size * 2 / 3)
    }

    @Test
    fun `each layer's edge lies between the hem and the crown, and lock lines rise from the notches`() {
        val edges = flow.layerEdges(2, 0.1f)
        assertTrue(edges.isNotEmpty())
        for (place in edges.flatten()) {
            val way = bob.azimuthAt(place.x, place.y)
            assertTrue(bob.heightAt(place.x, place.y) in flow.hemAt(way)..flow.topAt(way))
        }
        val lines = flow.lockLines(0.15f)
        assertTrue(lines.size >= bob.locks / 2, "${lines.size} lock lines")
        lines.forEach { assertTrue(bob.heightAt(it.first().x, it.first().y) > bob.heightAt(it.last().x, it.last().y)) }
        assertEquals(emptyList(), flow.layerEdges(0, 0.1f))
    }
}
