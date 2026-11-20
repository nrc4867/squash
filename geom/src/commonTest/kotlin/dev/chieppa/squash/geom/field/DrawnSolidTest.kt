package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.Polyline2
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A drawn solid's silhouettes in Side and Top are the drawing's, its section Front's, and it closes smoothly at its ends. */
class DrawnSolidTest {
    /** A loop through [points], closed. */
    private fun loop(vararg points: Pair<Float, Float>): OutlineProfile =
        assertNotNull(OutlineProfile.of(Polyline2(FloatArray(points.size + 1) { points[it % points.size].first }, FloatArray(points.size + 1) { points[it % points.size].second })))

    /** A foot-like Side: a flat sole at -0.04, a round heel behind, a high instep near the ankle and a low toe end. */
    private val side = loop(-0.05f to -0.01f, -0.03f to 0.02f, 0.03f to 0.01f, 0.12f to -0.01f, 0.16f to -0.03f, 0.12f to -0.04f, -0.03f to -0.04f)

    /** A Top wider at the toes than at the heel, and further out on one side. */
    private val top = loop(-0.05f to 0f, -0.02f to 0.04f, 0.1f to 0.07f, 0.16f to 0f, 0.1f to -0.05f, -0.02f to -0.04f)

    private val circle = SectionProfile.CIRCLE.spline(32).sample(64)

    /** The furthest [solid] reaches from [from] toward [direction]. */
    private fun edge(solid: DrawnSolid, from: Vec3, direction: Vec3): Float {
        var inside = 0f
        var outside = 0.5f
        repeat(40) {
            val mid = (inside + outside) / 2f
            if (solid.distance(from + direction * mid) < 0f) inside = mid else outside = mid
        }
        return inside
    }

    @Test
    fun itsSideAndTopAreTheDrawing() {
        val solid = DrawnSolid(side, top, 1f, circle)
        for (x in listOf(-0.03f, 0f, 0.05f, 0.1f, 0.13f)) {
            val middle = Vec3(x, side.midAt(x), -top.midAt(x))
            assertEquals(side.highAt(x), middle.y + edge(solid, middle, Vec3.Y), 1e-3f, "the top at $x")
            assertEquals(side.lowAt(x), middle.y - edge(solid, middle, Vec3.Y * -1f), 1e-3f, "the sole at $x")
            assertEquals(top.extentAt(x), edge(solid, middle, Vec3.Z) + edge(solid, middle, Vec3.Z * -1f), 1e-3f, "the width at $x")
            assertEquals(-top.lowAt(x), middle.z + edge(solid, middle, Vec3.Z), 1e-3f, "the side the drawing reaches further at $x")
        }
    }

    @Test
    fun frontSetsTheSectionAndTheDrawingItsSize() {
        val square = listOf(Vec2(-1f, -1f), Vec2(1f, -1f), Vec2(1f, 1f), Vec2(-1f, 1f))
        val solid = DrawnSolid(side, top, 1f, square)
        val x = 0.05f
        val middle = Vec3(x, side.midAt(x), -top.midAt(x))
        val halfUp = side.extentAt(x) / 2f
        val halfAcross = top.extentAt(x) / 2f
        val corner = middle + Vec3(0f, halfUp * 0.9f, halfAcross * 0.9f)
        assertTrue(solid.distance(corner) < 0f, "a square section fills its corners")
        val round = DrawnSolid(side, top, 1f, circle)
        assertTrue(round.distance(corner) > 0f, "a round one does not")
    }

    @Test
    fun aViewNotDrawnTakesTheSectionsProportion() {
        val flat = SectionProfile.CIRCLE.spline(32).sample(64).map { Vec2(it.x * 2f, it.y) }
        val solid = DrawnSolid(side, null, 1f, flat)
        val x = 0.05f
        val middle = Vec3(x, side.midAt(x), 0f)
        assertEquals(side.extentAt(x) * 2f, edge(solid, middle, Vec3.Z) * 2f, 2e-3f)
    }

    @Test
    fun itsEndsCloseAndItsReachIsTheDrawings() {
        val solid = DrawnSolid(side, top, 1f, circle)
        val field = BlendedField(listOf(Blended(solid)))
        val (start, end) = FieldLoft.reach(field)
        assertEquals(-0.05f, start, 2e-3f)
        assertEquals(0.16f, end, 2e-3f)
        assertTrue(solid.distance(Vec3(0.2f, side.midAt(0.16f), 0f)) > 0.03f, "it stops at the drawn toe")
    }

    @Test
    fun aScaleMultipliesTheDrawing() {
        val solid = DrawnSolid(side, top, 2f, circle)
        val x = 0.05f
        val middle = Vec3(x * 2f, side.midAt(x) * 2f, -top.midAt(x) * 2f)
        assertEquals(side.extentAt(x) * 2f, edge(solid, middle, Vec3.Y) + edge(solid, middle, Vec3.Y * -1f), 2e-3f)
    }

    @Test
    fun sectionsAlongItKeepTheirShapesAtTheDrawingsSizes() {
        val square = listOf(Vec2(-1f, -1f), Vec2(1f, -1f), Vec2(1f, 1f), Vec2(-1f, 1f))
        val solid = DrawnSolid(side, top, 1f, circle) { x -> if (x < 0.05f) square else circle }
        fun corner(x: Float): Vec3 {
            val middle = Vec3(x, side.midAt(x), -top.midAt(x))
            return middle + Vec3(0f, side.extentAt(x) / 2f * 0.9f, top.extentAt(x) / 2f * 0.9f)
        }
        assertTrue(solid.distance(corner(-0.01f)) < 0f, "a square section behind fills its corners")
        assertTrue(solid.distance(corner(0.12f)) > 0f, "a round one in front leaves them")
        val x = 0.12f
        val middle = Vec3(x, side.midAt(x), -top.midAt(x))
        assertEquals(side.highAt(x), middle.y + edge(solid, middle, Vec3.Y), 1e-3f, "still as tall as drawn")
    }
}
