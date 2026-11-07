package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class CutOnMeshTest {
    private fun tube(u: Float, v: Float, bend: Float): Vec3 {
        val angle = 2.0 * PI * v
        val x = 2f * u
        return Vec3(x, 0.5f * cos(angle).toFloat() + bend * x * x, -0.5f * sin(angle).toFloat())
    }

    @Test
    fun aCutLaidOnItsOwnGridIsItself() {
        val grid = tubeGrid(rings = 17, segments = 24)
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.3f, 0.2f), SurfaceUv(0.7f, 0.3f)), 0.1f), grid, 1e-4f)
        val laid = cut.laidOn(grid)
        for (i in 0 until cut.mesh.vertexCount) assertEquals(0f, (laid.position(i) - cut.mesh.position(i)).length, 1e-4f)
    }

    @Test
    fun aCutFollowsItsMeshWhenTheMeshMoves() {
        val rest = gridOf(17, 24, true) { u, v -> tube(u, v, 0f) }
        val bent = gridOf(17, 24, true) { u, v -> tube(u, v, 0.4f) }
        val cut = cutShape(ColorShape.Brush(listOf(SurfaceUv(0.5f, 0.25f)), 0.1f), rest, 1e-4f)
        val laid = cut.laidOn(bent)
        for (i in 0 until cut.mesh.vertexCount) {
            val at = cut.mesh.position(i)
            assertEquals(at.x, laid.position(i).x, 1e-4f)
            assertEquals(at.y + 0.4f * at.x * at.x, laid.position(i).y, 0.02f)
        }
    }
}
