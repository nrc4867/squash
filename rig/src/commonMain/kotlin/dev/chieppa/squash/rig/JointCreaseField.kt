package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.field.smoothstep
import kotlin.math.abs
import kotlin.math.exp

/** How far from a joint's pivot at rest, in its radii, a vertex can lie on its crease. */
private const val REACH_RADII = 2.6f

/** The least weight a vertex gives a joint's bones together to lie on its crease, and the least it gives the joint's own bone: what its bend moves. */
private const val REACH_INFLUENCE = 0.2f
private const val REACH_OWN = 0.04f

/**
 * The least a shoulder's own bone weighs a vertex on its crease. Its pivot lies deep inside the trunk, so a raised arm's plane meets
 * the surface well inside the arm's root, and a vertex the arm barely moves (the base of a collar) is not its.
 */
private const val REACH_OWN_SHOULDER = 0.4f

/** Where a crease's fold stands highest either side of its middle, in widths, and how wide each ridge is. */
private const val RIDGE_AT = 1.3f
private const val RIDGE_WIDTH = 0.55f

/** How much narrower a crease's shade falls off along the limb than its ink's reach, so its band is a slim wedge about the ink. */
private const val BAND_NARROWING = 1.8f

/** Where loose cloth stops taking a joint's shade band, as shares of the joint's radius from its axis: it folds there instead. */
private const val CLOTH_SHADE_NEAR = 1.8f
private const val CLOTH_SHADE_FAR = 2.6f

/**
 * Which vertices of one surface's drawn mesh each of [joints] can crease: those within [REACH_RADII] of its radius of its pivot at rest
 * that its bones weigh at least [REACH_INFLUENCE] together and its own bone at least [REACH_OWN] ([REACH_OWN_SHOULDER] at a shoulder), so
 * its bend moves them, in [vertices], one array a joint.
 */
class CreaseReach(val joints: List<CreaseJoint>, val vertices: List<IntArray>) {
    companion object {
        /** The reach of [joints] over the drawn mesh [rest], weighed by [skin], of its vertices only those [creasable] keeps, or all with none. */
        fun of(joints: List<CreaseJoint>, rest: RenderMesh, skin: SurfaceSkin, creasable: BooleanArray? = null): CreaseReach {
            val count = minOf(rest.vertexCount, skin.vertexCount)
            val stride = RenderMesh.FLOATS_PER_VERTEX
            return CreaseReach(joints, joints.map { joint ->
                val bones = intArrayOf(joint.bone, joint.parent, joint.carrier)
                val own = intArrayOf(joint.bone)
                val reach = joint.radius * REACH_RADII
                val least = if (joint.shoulder) REACH_OWN_SHOULDER else REACH_OWN
                val within = ArrayList<Int>()
                for (v in 0 until count) {
                    if (creasable != null && !creasable.getOrElse(v) { true }) continue
                    if ((rest.vertices.vec(v * stride) - joint.pivot).length > reach) continue
                    if (influence(skin, v, bones) >= REACH_INFLUENCE && influence(skin, v, own) >= least) within += v
                }
                within.toIntArray()
            })
        }
    }
}

/**
 * One surface's joint creases in a pose, a value each vertex of its drawn mesh: the crease [shade], the strength its ink has round the
 * limb, [ink], and over its middle stretch alone, [middle], how far along the limb from that crease's middle in its widths, [along], the [owner], the index into [posed] of the crease
 * strongest there or -1, [fold], how high cloth rises either side of the crease for a fold as high as the joint's radius, and [ridge], that fold's height as a
 * share of its most, 0 to the crease's amount.
 */
class JointCreaseField(
    val shade: FloatArray,
    val ink: FloatArray,
    val middle: FloatArray,
    val along: FloatArray,
    val owner: IntArray,
    val fold: FloatArray,
    val ridge: FloatArray,
    val posed: List<PosedCreaseJoint>,
) {
    companion object {
        /**
         * The creases of [reach]'s joints as [posed] has them, null where one creases nowhere, on the drawn [vertices] of a mesh with
         * [count] of them, each shade times [shadeShare]. On [cloth] the shade fades out from [CLOTH_SHADE_NEAR] to [CLOTH_SHADE_FAR] of
         * a joint's radius from its axis, so cloth standing well off a joint folds there instead of taking a band.
         */
        fun of(reach: CreaseReach, posed: List<PosedCreaseJoint?>, vertices: FloatArray, count: Int, shadeShare: Float = 1f, cloth: Boolean = false): JointCreaseField {
            val shade = FloatArray(count)
            val ink = FloatArray(count)
            val middle = FloatArray(count)
            val along = FloatArray(count)
            val owner = IntArray(count) { -1 }
            val fold = FloatArray(count)
            val ridges = FloatArray(count)
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
                    val near = if (cloth) 1f - smoothstep(CLOTH_SHADE_NEAR, CLOTH_SHADE_FAR, joint.axisShare(p)) else 1f
                    val s = around * exp(-BAND_NARROWING * t * t) * shadeShare * near
                    if (s > shade[v]) shade[v] = s
                    if (around * exp(-t * t * 0.25f) > ink[v] * exp(-along[v] * along[v] * 0.25f)) {
                        ink[v] = around
                        middle[v] = joint.middle(facing)
                        along[v] = t
                        owner[v] = index
                    }
                    val ridge = (abs(t) - RIDGE_AT) / RIDGE_WIDTH
                    val crest = around * exp(-ridge * ridge)
                    fold[v] = maxOf(fold[v], crest * joint.joint.radius)
                    ridges[v] = maxOf(ridges[v], crest)
                }
            }
            return JointCreaseField(shade, ink, middle, along, owner, fold, ridges, kept)
        }
    }
}
