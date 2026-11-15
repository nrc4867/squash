package dev.chieppa.squash.fieldgpu

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.ClippedSolid
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldBatch
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.SweptSolid
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.geom.surface.SurfaceMesher
import org.junit.jupiter.api.Tag
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Field reads on the GPU against the CPU's on the same batches, and a mesh edited on the GPU's reads against one meshed whole. Needs a GPU. */
@Tag("slow")
class GpuFieldReadsTest {
    private val reads = GpuFieldReads()

    @AfterTest
    fun close() = reads.close()

    private val members = listOf(
        JoinedMember(tube(0.3f, 0.6f), Vec3.ZERO, false, 0f, -1, 0),
        JoinedMember(tube(0.15f, 0.4f), Vec3(0.2f, 0.1f, 0.05f), true, 0.06f, 0, 1),
        JoinedMember(ClippedSolid(tube(0.12f, 0.5f), Vec3(-0.3f, -0.3f, -0.3f), Vec3(0.2f, 0.3f, 0.3f)), Vec3(-0.1f, 0.2f, 0f), false, 0.04f, 0, 2, ease = 0.01f),
    )

    @Test
    fun theGpuReadsAsTheCpuWithinRounding() {
        val random = Random(7)
        val gpu = FieldBatch(members)
        repeat(QUERIES) {
            val p = Vec3(random.nextFloat() * 1.2f - 0.6f, random.nextFloat() * 1.2f - 0.6f, random.nextFloat() * 1.2f - 0.6f)
            gpu.add(it % members.size, p, it % 2 == 0)
        }
        val cpu = FieldBatch(members).also { it.addAll(gpu) }
        reads.read(gpu)
        CpuFieldReads.read(cpu)
        assertEquals(null, reads.failure, "the GPU answered")
        var worstField = 0f
        var worstV = 0f
        var flips = 0
        for (i in 0 until QUERIES) {
            worstField = maxOf(worstField, abs(gpu.field[i] - cpu.field[i]))
            if ((gpu.field[i] < 0f) != (cpu.field[i] < 0f)) flips++
            if (gpu.place[i]) worstV = maxOf(worstV, abs(gpu.v[i] - cpu.v[i]).let { minOf(it, 1f - it) })
        }
        println("GPU against CPU over $QUERIES reads: field within $worstField, v within $worstV, $flips signs differ")
        assertTrue(worstField < FIELD_TOLERANCE, "field within $FIELD_TOLERANCE: $worstField")
        assertTrue(worstV < V_TOLERANCE, "v within $V_TOLERANCE: $worstV")
    }

    @Test
    fun anEditMeshedOnTheGpuEqualsTheSameFieldMeshedWholeOnIt() {
        val mesher = SurfaceMesher(CELL, reads)
        meshed(mesher, JoinedField(members))
        val moved = JoinedField(members.mapIndexed { i, m -> if (i == 1) JoinedMember(m.solid, Vec3(0.24f, 0.08f, 0.05f), true, m.blend, m.parent, m.part) else m })
        val again = meshed(mesher, moved)
        val whole = meshed(SurfaceMesher(CELL, reads), moved)
        assertEquals(null, reads.failure, "the GPU answered")
        assertContentEquals(whole.positions, again.positions)
        assertContentEquals(whole.triangles, again.triangles)
        assertContentEquals(whole.heldU, again.heldU)
        val cpu = meshed(SurfaceMesher(CELL), moved)
        println("GPU mesh ${whole.vertexCount} vertices against the CPU's ${cpu.vertexCount}")
    }

    private fun meshed(mesher: SurfaceMesher, field: JoinedField): SurfaceMesh {
        val run = mesher.begin(field)
        while (true) (run.next() ?: break).forEach { it() }
        return run.mesh
    }

    private companion object {
        const val QUERIES = 20_000
        const val CELL = 0.03f
        const val FIELD_TOLERANCE = 1e-5f
        const val V_TOLERANCE = 0.02f
        const val RINGS = 17
        const val AROUND = 24

        /** A tube [radius] across and [length] long along x, swelling at its middle and closing to a point at either end. */
        fun tube(radius: Float, length: Float): SweptSolid {
            val points = List(RINGS) { i ->
                val theta = PI * i / (RINGS - 1)
                val x = (-length * 0.5f * cos(theta)).toFloat()
                val r = (radius * sin(theta) * (1f + 0.2f * sin(3 * theta))).toFloat()
                List(AROUND) { k ->
                    val a = 2 * PI * k / AROUND
                    Vec3(x, (r * cos(a)).toFloat(), (r * 0.8f * sin(a)).toFloat())
                }
            }
            return requireNotNull(
                SweptSolid.ofRings(points, FloatArray(AROUND) { it.toFloat() / AROUND }, List(RINGS) { Vec3.X }, List(RINGS) { Vec3.Y }, FloatArray(RINGS) { it / (RINGS - 1f) }),
            )
        }
    }
}
