package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SeamBrush
import dev.chieppa.squash.document.SeamSide
import dev.chieppa.squash.document.SeamStroke
import dev.chieppa.squash.document.SeamStrokeId
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeamPaintTest {
    private val leg = PartId("front-leg-l")
    private val assembled get() = fennec.assembled
    private val guessed get() = fennec.rig

    /** The leg's vertex nearest u = [u], as its anchor and place. */
    private fun legVertex(u: Float): Int {
        val part = checkNotNull(guessed.part(leg))
        return part.vertices.indices.minBy { abs(part.vertices[it].u - u) }
    }

    private fun stroke(id: String, vertex: Int, toward: SeamSide = SeamSide.PARENT, radius: Float = 0.08f, strength: Float = 1f, brush: SeamBrush = SeamBrush.PAINT) =
        SeamStroke(SeamStrokeId(id), listOf(checkNotNull(guessed.part(leg)).anchors[vertex]), brush, toward.takeIf { brush == SeamBrush.PAINT }, radius, strength)

    private fun painted(vararg strokes: SeamStroke, reach: JointReach = JointReach.GUESSED) =
        rigOf(assembled, StrokePaint(assembled, mapOf(leg to strokes.toList())), reach = reach)

    private fun RigPart.at(v: Int) = Vec3(rest[v * 3], rest[v * 3 + 1], rest[v * 3 + 2])

    @Test
    fun aStrokeChangesTheWeightsWhereItLiesAndNowhereElse() {
        val vertex = legVertex(0.05f)
        val laid = stroke("s", vertex)
        val rig = painted(laid)
        val center = checkNotNull(guessed.part(leg)).at(vertex)
        val reach = laid.radius * seamStrokeScale(assembled, leg)
        var changed = 0
        for (part in rig.parts) {
            val before = checkNotNull(guessed.part(part.id))
            for (v in 0 until part.vertexCount) {
                val same = part.weights.of(v) == before.weights.of(v)
                val inside = (part.at(v) - center).length < reach
                if (!same) {
                    changed++
                    assertTrue(inside, "${part.id} vertex $v changed outside the brush")
                }
            }
        }
        assertTrue(changed > 0, "the stroke moved no weight")
        val carrier = checkNotNull(rig.seam(leg)).carrier
        val legPart = checkNotNull(rig.part(leg))
        assertTrue(legPart.weights.weightOf(vertex, carrier) > checkNotNull(guessed.part(leg)).weights.weightOf(vertex, carrier) + 0.1f)
    }

    @Test
    fun replayingTheStrokesOnARebuildGivesTheSameWeights() {
        val strokes = arrayOf(stroke("a", legVertex(0.05f)), stroke("b", legVertex(0.1f), SeamSide.CHILD, strength = 0.5f), stroke("c", legVertex(0.07f), brush = SeamBrush.SMOOTH))
        val first = painted(*strokes)
        val again = painted(*strokes)
        for (part in first.parts) {
            val other = checkNotNull(again.part(part.id))
            assertContentEquals(part.weights.bones, other.weights.bones)
            assertContentEquals(part.weights.weights, other.weights.weights)
        }
    }

    @Test
    fun anEraseHandsTheWeightBackToTheGuess() {
        val vertex = legVertex(0.05f)
        val rig = painted(stroke("p", vertex), stroke("e", vertex, radius = 0.2f, brush = SeamBrush.ERASE))
        val part = checkNotNull(rig.part(leg))
        val before = checkNotNull(guessed.part(leg))
        val carrier = checkNotNull(rig.seam(leg)).carrier
        assertEquals(before.weights.weightOf(vertex, carrier), part.weights.weightOf(vertex, carrier), 1e-4f)
    }

    @Test
    fun moreReachWidensTheSeamsBand() {
        val first = checkNotNull(guessed.part(leg)).let { guessed.bones[checkNotNull(guessed.seam(leg)).first].name.joint }
        val wide = painted(reach = JointReach { part, joint -> if (part == leg && joint == first) 2f else 1f })
        val carrier = checkNotNull(guessed.seam(leg)).carrier
        fun blended(rig: Rig) = checkNotNull(rig.part(leg)).let { p -> (0 until p.vertexCount).count { p.weights.weightOf(it, carrier) > 0f } }
        assertTrue(blended(wide) > blended(guessed), "${blended(wide)} against ${blended(guessed)}")
    }

    @Test
    fun tidyFindsAPatchCutOffFromItsSideAndItsStrokeHandsItBack() {
        val island = stroke("island", legVertex(0.6f), radius = 0.05f)
        val rig = painted(island)
        val patches = rig.strayPatches(leg)
        val stray = patches.single { it.part == leg && it.to == SeamSide.CHILD }
        assertTrue(stray.anchors.isNotEmpty())
        val tidied = painted(island, *patches.mapIndexed { i, it -> it.stroke(SeamStrokeId("t$i")) }.toTypedArray())
        assertTrue(tidied.strayPatches(leg).none { it.part == leg && it.to == SeamSide.CHILD })
    }

    @Test
    fun anUnpaintedSeamHasNoStrayPatches() {
        for (seam in guessed.seams) assertEquals(emptyList(), guessed.strayPatches(seam.child).map { it.part }, "${seam.child}")
    }
}
