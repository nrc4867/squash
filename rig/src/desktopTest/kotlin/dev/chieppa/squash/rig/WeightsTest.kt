package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeightsTest {
    private val rig get() = fennec.rig

    @Test
    fun everyVertexsWeightsSumToOne() {
        for (part in rig.parts) for (v in 0 until part.vertexCount) {
            val weights = part.weights.of(v)
            assertTrue(weights.isNotEmpty(), "${part.id} vertex $v follows no bone")
            assertEquals(1f, weights.sumOf { it.second.toDouble() }.toFloat(), 1e-5f, "${part.id} vertex $v")
        }
    }

    @Test
    fun onlyVerticesInABandFollowMoreThanOneBone() {
        val parts = fennec.assembled.parts.filter { it.id != null }.map(::RiggedPart)
        val byId = parts.associateBy { it.id }
        val index = rig.bones.withIndex().associate { (i, bone) -> bone.name to i }
        var blended = 0
        for (part in parts) {
            val parent = part.placed.socket?.parent?.let(byId::get)
            val childBand = if (carrierOf(part, byId) != null) childBandOf(part, parent) else 0f
            val own = OwnWeights(part, index)
            val joints = own.halfWidths
            val rims = parts.filter { it.placed.socket?.parent == part.id }.mapNotNull { child ->
                child.placed.anchor?.let { it to seamRadius(child, part) * (1f + SEAM_REACH) }
            }
            val weights = checkNotNull(rig.part(part.id)).weights
            var vertex = 0
            for (patch in part.placed.surface.patches) for (local in 0 until patch.nu * patch.nv) {
                val u = patch.boneU ?: patch.ringUs[local / patch.nv]
                val position = part.placed.origin + part.placed.surface.mesh.position(vertex)
                val digit = part.joints.digitOf(patch.name)
                val inBand = u < childBand ||
                    digit != null && part.joints.digits[digit].joints.withIndex().any { (k, joint) -> abs(u - joint.u) < own.digitHalfWidths[digit][k] } ||
                    digit == null && joints.indices.any { k -> k > 0 && abs(u - part.joints.specs[k].u) < joints[k] } ||
                    rims.any { (anchor, reach) -> (position - anchor).length < reach }
                val bones = weights.of(vertex).size
                if (!inBand) assertEquals(1, bones, "${part.id} vertex $vertex at u $u lies in no band")
                if (bones > 1) blended++
                vertex++
            }
        }
        assertTrue(blended > 0)
    }

    @Test
    fun mirroredPairsHaveMirroredWeights() {
        val pairs = fennec.assembled.parts.filter { it.side == Side.LEFT }.mapNotNull { it.id }.map { it to twinOf(it) }
        assertEquals(5, pairs.size, "ears, front and back legs, front and back paws")
        for ((left, right) in pairs) {
            val l = checkNotNull(rig.part(left)).weights
            val r = checkNotNull(rig.part(right)).weights
            assertEquals(l.vertexCount, r.vertexCount)
            for (v in 0 until l.vertexCount) {
                val expected = l.of(v).associate { (bone, w) -> twinName(rig.bones[bone].name) to w }
                val actual = r.of(v).associate { (bone, w) -> rig.bones[bone].name to w }
                assertEquals(expected.keys, actual.keys, "$right vertex $v")
                for ((bone, w) in expected) assertEquals(w, actual.getValue(bone), 1e-4f, "$right vertex $v on $bone")
            }
        }
    }

    private fun twinOf(part: PartId) = PartId(part.value.removeSuffix("-l") + "-r")

    private fun twinName(name: BoneName) = if (name.part.value.endsWith("-l")) BoneName(twinOf(name.part), name.joint) else name
}
