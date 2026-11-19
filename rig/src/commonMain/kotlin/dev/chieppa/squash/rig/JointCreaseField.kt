package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.RenderMesh
import kotlin.math.abs
import kotlin.math.exp

/** How far from a joint's pivot at rest, in its radii, a vertex can lie on its crease. */
private const val REACH_RADII = 2.6f

/** The least weight a vertex gives a joint's bones together to lie on its crease, and the least it gives the joint's own bone: what its bend moves. */
private const val REACH_INFLUENCE = 0.2f
private const val REACH_OWN = 0.04f

/** Where a crease's fold stands highest either side of its middle, in widths, and how wide each ridge is. */
private const val RIDGE_AT = 1.3f
private const val RIDGE_WIDTH = 0.55f

/**
 * Which vertices of one surface's drawn mesh each of [joints] can crease: those within [REACH_RADII] of its radius of its pivot at rest
 * that its bones weigh at least [REACH_INFLUENCE] together and its own bone at least [REACH_OWN], so its bend moves them, in [vertices],
 * one array a joint.
 */
class CreaseReach(val joints: List<CreaseJoint>, val vertices: List<IntArray>) {
    companion object {
        /** The reach of [joints] over the drawn mesh [rest], weighed by [skin]. */
        fun of(joints: List<CreaseJoint>, rest: RenderMesh, skin: SurfaceSkin): CreaseReach {
            val count = minOf(rest.vertexCount, skin.vertexCount)
            val stride = RenderMesh.FLOATS_PER_VERTEX
            return CreaseReach(joints, joints.map { joint ->
                val bones = intArrayOf(joint.bone, joint.parent, joint.carrier)
                val own = intArrayOf(joint.bone)
                val reach = joint.radius * REACH_RADII
                val within = ArrayList<Int>()
                for (v in 0 until count) {
                    if ((rest.vertices.vec(v * stride) - joint.pivot).length > reach) continue
                    if (influence(skin, v, bones) >= REACH_INFLUENCE && influence(skin, v, own) >= REACH_OWN) within += v
                }
                within.toIntArray()
            })
        }
    }
}

/**
 * One surface's joint creases in a pose, a value each vertex of its drawn mesh: the crease [shade], the strength its ink has round the
 * limb, [ink], and over its middle stretch alone, [middle], how far along the limb from that crease's middle in its widths, [along], the [owner], the index into [posed] of the crease
 * strongest there or -1, and [fold], how high cloth rises either side of the crease for a fold as high as the joint's radius.
 */
class JointCreaseField(
    val shade: FloatArray,
    val ink: FloatArray,
    val middle: FloatArray,
    val along: FloatArray,
    val owner: IntArray,
    val fold: FloatArray,
    val posed: List<PosedCreaseJoint>,
) {
    companion object {
        /**
         * The creases of [reach]'s joints as [posed] has them, null where one creases nowhere, on the drawn [vertices] of a mesh with
         * [count] of them.
         */
        fun of(reach: CreaseReach, posed: List<PosedCreaseJoint?>, vertices: FloatArray, count: Int): JointCreaseField {
            val shade = FloatArray(count)
            val ink = FloatArray(count)
            val middle = FloatArray(count)
            val along = FloatArray(count)
            val owner = IntArray(count) { -1 }
            val fold = FloatArray(count)
            val kept = ArrayList<PosedCreaseJoint>()
            val stride = RenderMesh.FLOATS_PER_VERTEX
            for ((j, joint) in posed.withIndex()) {
                if (joint == null) continue
                val index = kept.size
                kept += joint
                for (v in reach.vertices[j]) {
                    if (v >= count) continue
                    val p = vertices.vec(v * stride)
                    val facing = joint.facing(p)
                    val around = joint.around(facing)
                    if (around <= 0f) continue
                    val t = joint.along(p)
                    val s = around * exp(-t * t)
                    if (s > shade[v]) shade[v] = s
                    if (around * exp(-t * t * 0.25f) > ink[v] * exp(-along[v] * along[v] * 0.25f)) {
                        ink[v] = around
                        middle[v] = joint.middle(facing)
                        along[v] = t
                        owner[v] = index
                    }
                    val ridge = (abs(t) - RIDGE_AT) / RIDGE_WIDTH
                    fold[v] = maxOf(fold[v], around * exp(-ridge * ridge) * joint.joint.radius)
                }
            }
            return JointCreaseField(shade, ink, middle, along, owner, fold, kept)
        }
    }
}
