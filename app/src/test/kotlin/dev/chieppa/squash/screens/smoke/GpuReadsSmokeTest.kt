package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.GarmentBuilder
import dev.chieppa.squash.character.surface.GarmentCover
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.character.surface.surfaceCovers
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.fieldgpu.GpuFieldReads
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldBatch
import dev.chieppa.squash.geom.field.FieldReads
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.look.exampleDocument
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.math.abs

/**
 * The winter fennec's surface and garments built on the GPU's field reads: a leg edit built again by kept builders where it reaches gives
 * the same meshes as builders that have built nothing before, at the full cell and the coarse one a drag meshes at; and every batch
 * those builds read, read again on the CPU, is within rounding of it.
 */
@Tag("slow")
class GpuReadsSmokeTest {
    @Test
    fun anEditBuiltOnTheGpuMatchesAFreshBuildOnIt() {
        val (rest, edited, covers) = placedAndEdited()
        GpuFieldReads().use { reads ->
            for (dragging in listOf(true, false)) {
                val kept = Stack(reads)
                kept.build(rest, covers, dragging)
                val again = kept.build(edited, covers, dragging)
                val fresh = Stack(reads).build(edited, covers, dragging)
                assertSameMesh(fresh.first.mesh, again.first.mesh, "body, dragging $dragging")
                assertEquals(fresh.second.size, again.second.size)
                fresh.second.zip(again.second).forEachIndexed { g, (a, b) -> assertSameMesh(a, b, "garment $g, dragging $dragging") }
            }
            assertEquals(null, reads.failure, "the GPU answered")
        }
    }

    @Test
    fun theGpuReadsTheWinterFennecsBatchesAsTheCpuWithinRounding() {
        val (rest, _, covers) = placedAndEdited()
        GpuFieldReads().use { gpu ->
            val comparing = ComparingReads(gpu)
            for (dragging in listOf(true, false)) Stack(comparing).build(rest, covers, dragging)
            assertEquals(null, gpu.failure, "the GPU answered")
            println(
                "GPU against CPU over ${comparing.queries} reads in ${comparing.batches} batches: field within ${comparing.worstField}, " +
                    "${comparing.flips} signs differ (the farthest from zero ${comparing.farthestFlip}), ${comparing.placed} placed of which " +
                    "${comparing.vApart} differ in v by over $V_APART and ${comparing.uApart} in u by over $U_APART " +
                    "(${comparing.uApartNear} of them within $NEAR of the surface; median ${comparing.uDiffs.sorted().getOrNull(comparing.uDiffs.size / 2)}, " +
                    "largest ${comparing.uDiffs.maxOrNull()})",
            )
            assertTrue(comparing.worstField < FIELD_TOLERANCE, "field within $FIELD_TOLERANCE: ${comparing.worstField}")
            assertTrue(comparing.farthestFlip < FLIP_TOLERANCE, "signs differ only at rounding from zero: ${comparing.farthestFlip}")
            assertTrue(comparing.vApart <= comparing.placed / V_APART_SHARE, "few placed reads differ in v")
            assertTrue(comparing.uApart <= comparing.placed / U_APART_SHARE, "few placed reads differ in u")
            assertTrue(comparing.uApartNear <= comparing.placed / U_NEAR_SHARE, "fewer still near the surface")
        }
    }

    /** Reads on [gpu], and each batch again on the CPU to compare. */
    private class ComparingReads(private val gpu: FieldReads) : FieldReads {
        var batches = 0
        var queries = 0L
        var placed = 0L
        var worstField = 0f
        var flips = 0
        var farthestFlip = 0f
        var vApart = 0
        var uApart = 0
        var uApartNear = 0
        val uDiffs = ArrayList<Float>()

        @Synchronized
        override fun read(batch: FieldBatch) {
            val cpu = FieldBatch(batch.members).also { it.addAll(batch) }
            gpu.read(batch)
            CpuFieldReads.read(cpu)
            batches++
            queries += batch.size
            for (i in 0 until batch.size) {
                worstField = maxOf(worstField, abs(batch.field[i] - cpu.field[i]))
                if ((batch.field[i] < 0f) != (cpu.field[i] < 0f)) {
                    flips++
                    farthestFlip = maxOf(farthestFlip, minOf(abs(batch.field[i]), abs(cpu.field[i])))
                }
                if (!batch.place[i]) continue
                placed++
                if (abs(batch.v[i] - cpu.v[i]).let { minOf(it, 1f - it) } > V_APART) vApart++
                if (abs(batch.u[i] - cpu.u[i]) > U_APART) {
                    uApart++
                    if (abs(cpu.field[i]) < NEAR) uApartNear++
                    uDiffs += abs(batch.u[i] - cpu.u[i])
                }
            }
        }

        override val gathers: Boolean get() = gpu.gathers
        override val generation: Int get() = gpu.generation
    }

        /** The winter fennec placed at rest and with its front leg grown, and what it wears. */
    private fun placedAndEdited(): Triple<AssembledCharacter, AssembledCharacter, List<GarmentCover>> = TestWork().use { work ->
        val session = CharacterSession(exampleDocument(EXAMPLE, false, File(".")), CloseUps.shippedLibrary(), work.scope, work.dispatcher)
        session.send(ShowOutfit(OutfitId(OUTFIT)))
        work.settle(session)
        val rest = assertNotNull(session.placed.output.value)
        val leg = PartId(PART)
        val size = session.document.value.character.let { c -> c.resolved(assertNotNull(c.part(leg))).params["size"] ?: 1f }
        session.send(SetParameter(leg, "size", size * GROWTH))
        work.settle(session)
        Triple(rest, assertNotNull(session.placed.output.value), surfaceCovers(session.document.value.character))
    }

    private fun assertSameMesh(a: SurfaceMesh, b: SurfaceMesh, what: String) {
        assertContentEquals(a.positions, b.positions, "$what positions")
        assertContentEquals(a.normals, b.normals, "$what normals")
        assertContentEquals(a.triangles, b.triangles, "$what triangles")
        assertContentEquals(a.heldU, b.heldU, "$what u")
        assertContentEquals(a.heldV, b.heldV, "$what v")
        assertContentEquals(a.shares, b.shares, "$what shares")
    }

    /** The builders a session keeps, reading through [reads]. */
    private class Stack(reads: FieldReads) {
        private val surfaces = SurfaceBuilder(reads = reads)
        private val garments = GarmentBuilder(reads)

        fun build(placed: AssembledCharacter, covers: List<GarmentCover>, dragging: Boolean): Pair<CharacterSurface, List<SurfaceMesh>> {
            val surface = assertNotNull(surfaces.build(placed, dragging))
            val dress = garments.start(surface.field, surface.mesh, placed, covers)
            while (true) (dress.next() ?: break).forEach { it() }
            return surface to dress.garments.map { it.surface.mesh }
        }
    }

    private companion object {
        const val EXAMPLE = "fennec-dressed"
        const val OUTFIT = "winter"
        const val PART = "front-leg-l"
        const val GROWTH = 1.03f
        const val FIELD_TOLERANCE = 1e-5f
        const val FLIP_TOLERANCE = 1e-6f
        const val V_APART = 1e-3f
        const val U_APART = 1e-3f

        /**
         * At most one placed read in this many may lie apart in v, in u, and in u within [NEAR] of the surface: where two pairs of rings
         * give the same field, the GPU's scan over every pair and the CPU's pair grid can take either, and u and v follow the pair.
         */
        const val V_APART_SHARE = 1000
        const val U_APART_SHARE = 100
        const val U_NEAR_SHARE = 500
        const val NEAR = 0.01f
    }
}
