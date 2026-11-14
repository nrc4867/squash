package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.field.SweptSolid
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The one-surface mesher: closed and manifold, on the field, facing out, sharing joins by the union's weight, and the same built again after an edit as built whole. */
class SurfaceMesherTest {
    private val ball = ballSolid(RADIUS)
    private val small = ballSolid(RADIUS * 0.6f)

    private fun body(childAt: Vec3) = JoinedField(
        listOf(
            JoinedMember(ball, Vec3.ZERO, false, 0f, -1, 0),
            JoinedMember(small, childAt, false, BLEND, 0, 1),
        ),
    )

    @Test
    fun aJoinedPairMeshesClosedManifoldOnTheFieldFacingOut() {
        val field = body(Vec3(RADIUS * 1.2f, 0f, 0f))
        val mesh = meshed(SurfaceMesher(CELL), field)
        assertTrue(mesh.vertexCount > 100)
        assertClosed(mesh)
        for (v in 0 until mesh.vertexCount) assertTrue(abs(field.value(mesh.position(v))) < CELL * 0.01f, "vertex $v lies on the field")
        var outward = 0
        for (t in 0 until mesh.triangleCount) {
            val a = mesh.position(mesh.triangles[t * 3])
            val b = mesh.position(mesh.triangles[t * 3 + 1])
            val c = mesh.position(mesh.triangles[t * 3 + 2])
            if ((((b - a) cross (c - a)) dot mesh.normal(mesh.triangles[t * 3])) > 0f) outward++
        }
        assertEquals(mesh.triangleCount, outward, "every triangle faces out")
        val shared = (0 until mesh.vertexCount).filter { mesh.parents[it] == 0 }
        assertTrue(shared.isNotEmpty() && shared.all { mesh.winners[it] == 1 && mesh.shares[it] in 0f..1f }, "the small ball shares its join with the large")
        assertTrue(shared.any { mesh.shares[it] < 0.5f } && shared.any { mesh.shares[it] > 0.5f }, "the shares cross a half round the join")
    }

    @Test
    fun aFieldChangingFasterThanADistanceStillMeshesClosed() {
        val steep = object : Solid {
            override fun distance(p: Vec3): Float = STEEPNESS * (p.length - RADIUS * 0.5f)
            override val low = Vec3(-RADIUS, -RADIUS, -RADIUS)
            override val high = Vec3(RADIUS, RADIUS, RADIUS)
        }
        val field = JoinedField(
            listOf(
                JoinedMember(ball, Vec3.ZERO, false, 0f, -1, 0),
                JoinedMember(steep, Vec3(RADIUS * 1.1f, RADIUS * 0.3f, 0.01f), false, 0f, -1, 1),
            ),
        )
        assertClosed(meshed(SurfaceMesher(CELL * 0.5f), field))
    }

    private fun assertClosed(mesh: SurfaceMesh) {
        val edges = HashMap<Pair<Int, Int>, Int>()
        for (t in 0 until mesh.triangleCount) for (c in 0 until 3) {
            val a = mesh.triangles[t * 3 + c]
            val b = mesh.triangles[t * 3 + (c + 1) % 3]
            edges.merge(if (a < b) a to b else b to a, 1, Int::plus)
        }
        assertTrue(edges.values.all { it == 2 }, "every edge has two triangles: ${edges.values.count { it != 2 }} have not")
    }

    @Test
    fun anEditMeshedAgainMatchesTheSameFieldMeshedWhole() {
        val mesher = SurfaceMesher(CELL)
        meshed(mesher, body(Vec3(RADIUS * 1.2f, 0f, 0f)))
        val moved = body(Vec3(RADIUS * 1.3f, 0.02f, 0f))
        val again = meshed(mesher, moved)
        val whole = meshed(SurfaceMesher(CELL), moved)
        assertContentEquals(whole.positions, again.positions)
        assertContentEquals(whole.triangles, again.triangles)
        assertContentEquals(whole.shares, again.shares)
    }

    private fun meshed(mesher: SurfaceMesher, field: JoinedField): SurfaceMesh {
        val run = mesher.begin(field)
        while (true) (run.next() ?: break).forEach { it() }
        return run.mesh
    }

    private companion object {
        const val RADIUS = 0.3f
        const val CELL = 0.04f
        const val BLEND = 0.08f
        const val STEEPNESS = 8f
        const val RINGS = 17
        const val AROUND = 24

        /** A ball [radius] across swept by rings along x, closing to a point at either end. */
        fun ballSolid(radius: Float): SweptSolid {
            val points = List(RINGS) { i ->
                val theta = PI * i / (RINGS - 1)
                val x = (-radius * cos(theta)).toFloat()
                val r = (radius * sin(theta)).toFloat()
                List(AROUND) { k ->
                    val a = 2 * PI * k / AROUND
                    Vec3(x, (r * cos(a)).toFloat(), (r * sin(a)).toFloat())
                }
            }
            return requireNotNull(
                SweptSolid.ofRings(points, FloatArray(AROUND) { it.toFloat() / AROUND }, List(RINGS) { Vec3.X }, List(RINGS) { Vec3.Y }, FloatArray(RINGS) { it / (RINGS - 1f) }),
            )
        }
    }
}
