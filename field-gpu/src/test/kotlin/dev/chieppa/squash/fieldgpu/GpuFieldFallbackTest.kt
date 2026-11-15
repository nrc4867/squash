package dev.chieppa.squash.fieldgpu

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldBatch
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.Solid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Field reads whose GPU cannot be opened: read on the CPU, and no longer gathered, with a new generation. Opens no GPU. */
class GpuFieldFallbackTest {
    private val ball = object : Solid {
        override fun distance(p: Vec3): Float = p.length - 0.3f
        override val low = Vec3(-0.3f, -0.3f, -0.3f)
        override val high = Vec3(0.3f, 0.3f, 0.3f)
    }

    @Test
    fun readsWhoseGpuFailsAreReadOnTheCpu() {
        GpuFieldReads(open = { error("no GPU here") }).use { reads ->
            assertTrue(reads.gathers)
            val before = reads.generation
            val members = listOf(JoinedMember(ball, Vec3(0.1f, 0f, 0f), false, 0f))
            val batch = FieldBatch(members)
            for (i in 0 until 50) batch.add(0, Vec3(i * 0.01f, 0.05f, -0.02f), i % 2 == 0)
            val cpu = FieldBatch(members).also { it.addAll(batch) }
            reads.read(batch)
            CpuFieldReads.read(cpu)
            assertContentEquals(cpu.field.copyOf(cpu.size), batch.field.copyOf(batch.size))
            assertFalse(reads.gathers, "reads are no longer gathered")
            assertNotEquals(before, reads.generation)
            assertEquals("no GPU here", reads.failure?.message)
        }
    }
}
