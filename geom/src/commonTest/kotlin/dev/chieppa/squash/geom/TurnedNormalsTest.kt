package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Normals turned with a surface: a sheet tipped up at one edge turns the normals there and keeps those where nothing moved. */
class TurnedNormalsTest {
    /** A sheet in the x-z plane facing +y, [SIDE] points a side. */
    private val flat = FloatArray(SIDE * SIDE * 3).also { out ->
        for (j in 0 until SIDE) for (i in 0 until SIDE) {
            out[(j * SIDE + i) * 3] = i.toFloat()
            out[(j * SIDE + i) * 3 + 2] = j.toFloat()
        }
    }
    private val triangles = IntArray((SIDE - 1) * (SIDE - 1) * 6).also { out ->
        var at = 0
        for (j in 0 until SIDE - 1) for (i in 0 until SIDE - 1) {
            val a = j * SIDE + i
            for (v in intArrayOf(a, a + SIDE, a + 1, a + 1, a + SIDE, a + SIDE + 1)) out[at++] = v
        }
    }

    @Test
    fun aSheetTippedAtOneEdgeTurnsItsNormalsThereAlone() {
        val count = SIDE * SIDE
        val smooth = FloatArray(count * 3) { if (it % 3 == 1) 1f else 0f }
        assertTrue((0 until count).all { vertexNormals(flat, triangles, count)[it * 3 + 1] > 0.99f }, "the sheet faces +y")
        val tipped = flat.copyOf()
        for (j in 0 until SIDE) tipped[(j * SIDE + SIDE - 1) * 3 + 1] = 1f
        val turned = turnedNormals(smooth, flat, tipped, triangles)
        val edge = SIDE - 1
        assertTrue(turned[edge * 3] < -0.3f, "a normal on the tipped edge turns away from it: ${turned[edge * 3]}")
        assertContentEquals(smooth.copyOfRange(0, 3), turned.copyOfRange(0, 3), "a normal where nothing moved stays")
        for (v in 0 until count) {
            val length = turned[v * 3] * turned[v * 3] + turned[v * 3 + 1] * turned[v * 3 + 1] + turned[v * 3 + 2] * turned[v * 3 + 2]
            assertEquals(1f, length, 1e-4f)
        }
    }

    private companion object {
        const val SIDE = 5
    }
}
