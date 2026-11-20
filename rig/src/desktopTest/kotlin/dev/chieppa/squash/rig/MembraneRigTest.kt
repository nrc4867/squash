package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Membrane
import dev.chieppa.squash.document.MembraneEdge
import dev.chieppa.squash.document.MembraneId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetMembrane
import dev.chieppa.squash.document.membranePartId
import dev.chieppa.squash.geom.Vec3
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val FRONT = PartId("front-leg-l")
private val HIND = PartId("back-leg-l")
private val PATAGIUM = MembraneId("patagium")

/** A membrane between the fennec's left legs rides their bones: each end follows its own leg, and posing a leg moves the sheet with it. */
@Tag("integration")
class MembraneRigTest {
    private val rig: Rig by lazy {
        val membrane = Membrane(PATAGIUM, listOf(MembraneEdge(FRONT, 0.1f, 0.25f, 0.9f, 0.25f)), listOf(MembraneEdge(HIND, 0.1f, 0.25f, 0.9f, 0.25f)))
        settledRig(SetMembrane(PATAGIUM, membrane).apply(savedFennec()).document, "fennec with a membrane")
    }

    private fun point(positions: FloatArray, vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])

    @Test
    fun eachEndRidesTheBonesOfItsOwnLegAndAddsNoBone() {
        val sheet = assertNotNull(rig.part(membranePartId(PATAGIUM)))
        val bonesOf = { vertex: Int -> sheet.weights.of(vertex).map { (bone, _) -> rig.bones[bone].name.part }.toSet() }
        val near = (0 until sheet.vertexCount).filter { sheet.vertices[it].u < 0.15f }
        assertTrue(near.isNotEmpty())
        assertTrue(rig.bones.none { it.name.part == membranePartId(PATAGIUM) }, "a membrane has no bone of its own")
        val parts = (0 until sheet.vertexCount).flatMap { bonesOf(it) }.toSet()
        assertTrue(FRONT in parts && HIND in parts, "the sheet follows both legs: $parts")
    }

    @Test
    fun posingTheFrontLegMovesTheSheetBesideItMostAndTheHindEndLeast() {
        val sheet = assertNotNull(rig.part(membranePartId(PATAGIUM)))
        val posed = rig.posed(Pose(listOf(BonePose(FRONT, "hip", degrees = -40f)))).parts.getValue(membranePartId(PATAGIUM))
        fun moved(vertex: Int) = (point(posed, vertex) - point(sheet.rest, vertex)).length
        fun share(vertex: Int, part: PartId) = sheet.weights.of(vertex).filter { (bone, _) -> rig.bones[bone].name.part == part }.sumOf { it.second.toDouble() }
        val onFront = (0 until sheet.vertexCount).filter { share(it, FRONT) > 0.95 }
        val onHind = (0 until sheet.vertexCount).filter { share(it, HIND) > 0.95 }
        val front = onFront.maxOf(::moved)
        val hind = onHind.maxOf(::moved)
        assertTrue(front > 0.01f, "the side on the front leg moves with it: $front")
        assertTrue(hind < front * 0.05f, "the side on the hind leg stays: $hind against $front")
    }
}
