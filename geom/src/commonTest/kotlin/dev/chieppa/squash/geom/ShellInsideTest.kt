package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RINGS = 11
private const val COLUMNS = 16
private const val LENGTH = 4f

/** The inside test reads a shell's rings from its grid as it stands, and counts as covered only what its open ends do not show. */
class ShellInsideTest {
    /** An open tube of radius 1 along x from 0 to [LENGTH], each point of it through [place]. */
    private fun tube(place: (Vec3) -> Vec3 = { it }): Patch {
        val points = FloatArray(RINGS * COLUMNS * Patch.COMPONENTS)
        for (ring in 0 until RINGS) for (column in 0 until COLUMNS) {
            val angle = 2.0 * PI * column / COLUMNS
            val p = place(Vec3(LENGTH * ring / (RINGS - 1), cos(angle).toFloat(), sin(angle).toFloat()))
            val at = (ring * COLUMNS + column) * Patch.COMPONENTS
            points[at] = p.x
            points[at + 1] = p.y
            points[at + 2] = p.z
        }
        return Patch("tube", RINGS, COLUMNS, closedV = true, slices = null, points = points)
    }

    @Test
    fun aPointInTheMiddleIsInsideAndCovered() {
        val inside = ShellInside(tube())
        assertTrue(Vec3(2f, 0.2f, 0f) in inside)
        assertTrue(inside.covers(Vec3(2f, 0.2f, 0f)))
        assertFalse(Vec3(2f, 1.2f, 0f) in inside)
        assertFalse(Vec3(-0.1f, 0f, 0f) in inside)
    }

    @Test
    fun aPointNearAnOpenEndAndAwayFromTheWallIsInsideButNotCovered() {
        val inside = ShellInside(tube())
        assertTrue(Vec3(0.2f, 0f, 0f) in inside)
        assertFalse(inside.covers(Vec3(0.2f, 0f, 0f)), "seen through the open end")
        assertTrue(inside.covers(Vec3(0.2f, 0.9f, 0f)), "close by the wall")
    }

    @Test
    fun theTestFollowsTheGridWhereverItsPointsAre() {
        val turned = ShellInside(tube { p -> Vec3(-p.y, p.x, p.z) + Vec3(5f, 0f, 0f) })
        assertTrue(turned.covers(Vec3(5f, 2f, 0f)))
        assertFalse(turned.covers(Vec3(2f, 0f, 0f)))
    }
}
