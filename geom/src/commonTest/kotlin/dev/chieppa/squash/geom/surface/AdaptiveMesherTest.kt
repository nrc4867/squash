package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.SweptSolid
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cells of several sizes on one surface: closed and manifold across each change of size, small members kept apart, and an edit meshed again as meshed whole. */
class AdaptiveMesherTest {
    private val palm = capsuleSolid(PALM_LENGTH, PALM_RADIUS)
    private val digit = capsuleSolid(DIGIT_LENGTH, DIGIT_RADIUS)

    private fun hand(spread: Float = SPREAD, lift: Float = 0f) = JoinedField(
        listOf(JoinedMember(palm, Vec3.ZERO, false, 0f, -1, 0)) +
            (0 until DIGITS).map { k -> JoinedMember(digit, Vec3(PALM_LENGTH * 0.8f, (k - 1) * spread + if (k == 0) lift else 0f, 0f), false, BLEND, 0, 1) },
    )

    private val levels = IntArray(DIGITS + 1) { if (it == 0) 0 else 2 }

    @Test
    fun cellsOfThreeSizesMeshClosedManifoldOnTheFieldFacingOut() {
        val field = hand()
        val mesh = meshed(AdaptiveMesher(CELL), field, levels)
        assertEquals(3, CellLevels.of(field, CELL, levels).count, "the digits mesh two levels finer than the palm")
        assertClosedManifold(mesh)
        for (v in 0 until mesh.vertexCount) assertTrue(abs(field.value(mesh.position(v))) < CELL * 0.01f, "vertex $v lies on the field")
        var outward = 0
        for (t in 0 until mesh.triangleCount) {
            val a = mesh.position(mesh.triangles[t * 3])
            val b = mesh.position(mesh.triangles[t * 3 + 1])
            val c = mesh.position(mesh.triangles[t * 3 + 2])
            val n = mesh.normal(mesh.triangles[t * 3]) + mesh.normal(mesh.triangles[t * 3 + 1]) + mesh.normal(mesh.triangles[t * 3 + 2])
            if ((((b - a) cross (c - a)) dot n) > 0f) outward++
        }
        assertEquals(mesh.triangleCount, outward, "every triangle faces out")
        assertQuadsFirst(mesh)
        assertTrue(mesh.triangleCount > mesh.quadCount * 2, "where cells of two sizes meet some triangles stand alone")
    }

    /** Each of the mesh's first quads is two triangles sharing a diagonal, its corners at their indices 0, 1, 4 and 5, four apart. */
    private fun assertQuadsFirst(mesh: SurfaceMesh) {
        val t = mesh.triangles
        for (q in 0 until mesh.quadCount) {
            val corners = intArrayOf(t[q * 6], t[q * 6 + 1], t[q * 6 + 4], t[q * 6 + 5])
            assertEquals(4, corners.toSet().size, "quad $q has four corners")
            val first = setOf(t[q * 6], t[q * 6 + 1], t[q * 6 + 2])
            val second = setOf(t[q * 6 + 3], t[q * 6 + 4], t[q * 6 + 5])
            assertEquals(2, first.intersect(second).size, "quad $q's triangles share a diagonal")
        }
    }

    @Test
    fun digitsCloserThanTheBodysCellMeshAsSeparateSurfaces() {
        val field = hand()
        val coarse = meshed(AdaptiveMesher(CELL), field, IntArray(DIGITS + 1))
        val fine = meshed(AdaptiveMesher(CELL), field, levels)
        assertEquals(0, facingNeighbour(coarse), "at the body's cell the digits melt together")
        assertTrue(facingNeighbour(fine) > 20, "at a quarter of it each digit's side facing the next is meshed")
    }

    @Test
    fun anEditMeshedAgainMatchesTheSameFieldMeshedWhole() {
        val mesher = AdaptiveMesher(CELL)
        meshed(mesher, hand(), levels)
        val moved = hand(lift = -CELL * 1.7f)
        val again = meshed(mesher, moved, levels)
        val whole = meshed(AdaptiveMesher(CELL), moved, levels)
        assertContentEquals(whole.positions, again.positions)
        assertContentEquals(whole.triangles, again.triangles)
        assertContentEquals(whole.heldU, again.heldU)
        assertTrue(assertNotNullChange(again) > 0, "the edit keeps vertices it does not reach")
    }

    @Test
    fun withEveryMemberAtTheBodysCellTheSurfaceIsTheSingleMeshersOwn() {
        val field = hand(spread = SPREAD * 2f)
        val adaptive = meshed(AdaptiveMesher(CELL), field, IntArray(DIGITS + 1))
        val single = SurfaceMesher(CELL).begin(field).let { run ->
            while (true) (run.next() ?: break).forEach { it() }
            run.mesh
        }
        assertContentEquals(single.positions, adaptive.positions)
        assertContentEquals(single.triangles, adaptive.triangles)
    }

    private fun assertNotNullChange(mesh: SurfaceMesh): Int = checkNotNull(mesh.change).kept

    /** Vertices past the palm lying between neighbouring digits on a digit's side that faces the next one. */
    private fun facingNeighbour(mesh: SurfaceMesh): Int {
        var count = 0
        for (v in 0 until mesh.vertexCount) {
            val p = mesh.position(v)
            if (p.x < PALM_LENGTH * 1.2f || abs(p.z) > DIGIT_RADIUS * 0.5f) continue
            for (k in 0 until DIGITS - 1) {
                val low = (k - 1) * SPREAD
                val high = low + SPREAD
                if (p.y <= low || p.y >= high) continue
                val toward = if (p.y < (low + high) / 2f) 1f else -1f
                if (mesh.normal(v).y * toward > FACING) count++
            }
        }
        return count
    }

    private fun assertClosedManifold(mesh: SurfaceMesh) {
        val edges = HashMap<Pair<Int, Int>, Int>()
        val directed = HashSet<Pair<Int, Int>>()
        var repeated = 0
        for (t in 0 until mesh.triangleCount) for (c in 0 until 3) {
            val a = mesh.triangles[t * 3 + c]
            val b = mesh.triangles[t * 3 + (c + 1) % 3]
            assertTrue(a != b, "triangle $t has two equal corners")
            edges.merge(if (a < b) a to b else b to a, 1, Int::plus)
            if (!directed.add(a to b)) repeated++
        }
        assertTrue(edges.values.all { it == 2 }, "every edge has two triangles: ${edges.values.count { it != 2 }} have not")
        assertEquals(0, repeated, "neighbouring triangles run their shared edge opposite ways")
    }

    private fun meshed(mesher: AdaptiveMesher, field: JoinedField, levels: IntArray): SurfaceMesh {
        val run = mesher.begin(field, levels)
        while (true) (run.next() ?: break).forEach { it() }
        return run.mesh
    }

    private companion object {
        const val CELL = 0.04f
        const val PALM_LENGTH = 0.4f
        const val PALM_RADIUS = 0.2f
        const val DIGIT_LENGTH = 0.35f
        const val DIGIT_RADIUS = 0.05f
        const val DIGITS = 3

        /** Digits' axes this far apart, leaving a gap under the body's cell between neighbours. */
        const val SPREAD = 0.12f
        const val BLEND = 0.03f
        const val FACING = 0.7f
        const val RINGS = 21
        const val AROUND = 24

        /** A capsule [length] long along x from its origin, [radius] round, closing to a point at either end. */
        fun capsuleSolid(length: Float, radius: Float): SweptSolid {
            val points = List(RINGS) { i ->
                val t = i / (RINGS - 1f)
                val cap = radius / length
                val (x, r) = when {
                    t < cap -> {
                        val a = PI / 2 * (1 - t / cap)
                        (radius - radius * sin(a)).toFloat() to (radius * cos(a)).toFloat()
                    }
                    t > 1 - cap -> {
                        val a = PI / 2 * ((t - (1 - cap)) / cap)
                        (length - radius + radius * sin(a)).toFloat() to (radius * cos(a)).toFloat()
                    }
                    else -> t * length to radius
                }
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
