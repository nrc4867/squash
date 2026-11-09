package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.geom.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A ray from inside a closed surface never leaves it, and one from inside an open surface can. */
class RayGridTest {
    private val ways = List(96) { i ->
        val y = 1f - 2f * (i + 0.5f) / 96
        val r = sqrt(1f - y * y)
        Vec3(r * cos(i * 2.3999631f), y, r * sin(i * 2.3999631f))
    }

    /** A sphere of [rings] by [columns] around the origin, with a gap of [open] columns. */
    private fun sphere(rings: Int, columns: Int, open: Int): FloatArray {
        fun at(i: Int, j: Int): Vec3 {
            val a = Math.PI * i / rings
            val b = 2 * Math.PI * j / columns
            return Vec3((sin(a) * cos(b)).toFloat(), cos(a).toFloat(), (sin(a) * sin(b)).toFloat())
        }
        val out = ArrayList<Float>()
        for (i in 0 until rings) for (j in open until columns) {
            for (p in listOf(at(i, j), at(i + 1, j), at(i + 1, j + 1), at(i, j), at(i + 1, j + 1), at(i, j + 1))) { out += p.x; out += p.y; out += p.z }
        }
        return out.toFloatArray()
    }

    @Test
    fun aClosedSphereHidesItsMiddle() {
        val grid = RayGrid(sphere(16, 24, 0))
        assertEquals(0, ways.count { grid.escapes(Vec3(0.1f, 0.2f, 0.05f), it) })
    }

    @Test
    fun anOpenSphereShowsItsMiddle() {
        val grid = RayGrid(sphere(16, 24, 3))
        assertTrue(ways.count { grid.escapes(Vec3(0.1f, 0.2f, 0.05f), it) } > 0)
    }
}
