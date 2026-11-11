package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HemPushTest {
    private val rings = 16
    private val columns = 24
    private val drop = 0.5f
    private val radius = 0.2f

    /** A skirt held at y = 0, ring 0, hanging straight down to the hem at y = -[drop]. */
    private fun skirt(): PointGrid {
        val points = FloatArray(rings * columns * 3)
        for (i in 0 until rings) for (j in 0 until columns) {
            val a = 2f * PI.toFloat() * j / columns
            val at = (i * columns + j) * 3
            points[at] = radius * cos(a)
            points[at + 1] = -drop * i / (rings - 1)
            points[at + 2] = radius * sin(a)
        }
        return PointGrid(points, rings, columns, closedV = true)
    }

    /** A leg of [thickness] from [root] toward [tip], closed round. */
    private fun leg(root: Vec3, tip: Vec3, thickness: Float = 0.06f): PointGrid {
        val nu = 30
        val nv = 12
        val along = (tip - root).normalized()
        val side = (along cross Vec3.Z).normalized()
        val up = along cross side
        val points = FloatArray(nu * nv * 3)
        for (i in 0 until nu) for (j in 0 until nv) {
            val a = 2f * PI.toFloat() * j / nv
            val p = root + (tip - root) * (i.toFloat() / (nu - 1)) + side * (thickness * cos(a)) + up * (thickness * sin(a))
            val at = (i * nv + j) * 3
            points[at] = p.x
            points[at + 1] = p.y
            points[at + 2] = p.z
        }
        return PointGrid(points, nu, nv, closedV = true)
    }

    private fun moved(shell: PointGrid, shift: Array<Vec3>, i: Int, j: Int) = shell.point(i, j) + shift[i * columns + j]

    @Test
    fun aLegInsideMovesNothing() {
        assertNull(HemPush.push(skirt(), 0, listOf(leg(Vec3(0.05f, 0f, 0f), Vec3(0.05f, -0.9f, 0f))), 0.01f))
    }

    @Test
    fun aStepClearsTheLegHoldsTheWaistAndSpreadsRoundAndDown() {
        val shell = skirt()
        val stepping = leg(Vec3(0.05f, 0f, 0f), Vec3(0.55f, -0.75f, 0f))
        val shift = requireNotNull(HemPush.push(shell, 0, listOf(stepping), 0.01f))
        for (j in 0 until columns) assertEquals(Vec3.ZERO, shift[j], "the waist is held")
        val front = 0
        val back = columns / 2
        val hem = rings - 1
        assertTrue(shift[hem * columns + front].length > 0.1f, "the hem is pushed out in front of the leg")
        assertEquals(0f, shift[hem * columns + back].length, "the back hangs as it was")
        assertTrue(shift[hem * columns + 3].length > 0f && shift[hem * columns + columns - 3].length > 0f, "the push spreads round")
        for (i in 1 until rings) assertTrue(shift[i * columns + front].length >= shift[(i - 1) * columns + front].length - 1e-5f, "the push grows down")
        for (i in 0 until stepping.nu) for (k in 0 until stepping.nv) {
            val p = stepping.point(i, k)
            val ring = (-p.y / drop * (rings - 1)).roundToInt()
            if (ring !in 1 until rings) continue
            val angle = atan2(p.z, p.x)
            val column = ((angle / (2f * PI.toFloat()) * columns).roundToInt() + columns) % columns
            val wall = moved(shell, shift, ring, column).let { sqrt(it.x * it.x + it.z * it.z) }
            assertTrue(sqrt(p.x * p.x + p.z * p.z) <= wall + 0.02f, "leg point $p lies outside the skirt at ring $ring")
        }
    }
}
