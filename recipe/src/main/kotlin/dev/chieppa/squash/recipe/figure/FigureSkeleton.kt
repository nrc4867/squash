package dev.chieppa.squash.recipe.figure

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.recipe.model.RecipeRest
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.tan

/** Whether a part runs up the body's middle, its right mirroring its left, or is the left side of a pair. */
enum class FigureKind { TRUNK, LIMB }

/** The body directions a part's rest names. */
private val DIRECTION = mapOf(
    "forward" to P3.FORWARD, "back" to P3.FORWARD * -1.0, "up" to P3.UP, "down" to P3.UP * -1.0, "left" to P3.RIGHT * -1.0, "right" to P3.RIGHT,
)

/** Where v 0 and v 0.25 face on a part resting each way, and its Side's +y (frames.md). */
private val REST_FRAME = mapOf(
    RecipeRest.FORWARD to Triple("up", "left", "up"),
    RecipeRest.BACK to Triple("up", "right", "up"),
    RecipeRest.UP to Triple("forward", "right", "forward"),
    RecipeRest.DOWN to Triple("forward", "left", "forward"),
    RecipeRest.OUT to Triple("up", "back", "up"),
)

private val REST_ALONG = mapOf(
    RecipeRest.FORWARD to "forward", RecipeRest.BACK to "back", RecipeRest.UP to "up", RecipeRest.DOWN to "down", RecipeRest.OUT to "left",
)

/**
 * A part's frame at rest as its left side builds it: the way it runs, and where v 0 and v 0.25 face round it, its rest turned by its
 * outward and lean as the Move tool turns it: each adds its tangent onto the rest axis.
 */
class PartFrame(val along: P3, val v0: P3, val v25: P3) {
    /** The direction round the part at [v]. */
    fun round(v: Double): P3 = (v0 * Math.cos(2 * Math.PI * v) + v25 * Math.sin(2 * Math.PI * v)).unit()

    /** The v facing [direction], which lies across the part. */
    fun vToward(direction: P3): Double {
        val v = Math.atan2(direction dot v25, direction dot v0) / (2 * Math.PI)
        return v - Math.floor(v)
    }

    companion object {
        fun of(rest: RecipeRest, outward: Double, lean: Double): PartFrame {
            val (v0Name, v25Name, upName) = REST_FRAME.getValue(rest)
            val restAlong = DIRECTION.getValue(REST_ALONG.getValue(rest))
            val up = DIRECTION.getValue(upName)
            val out = P3.RIGHT * -1.0
            var along = restAlong
            if (outward != 0.0 && abs(restAlong dot out) < 0.99) along += out * tan(Math.toRadians(outward))
            if (lean != 0.0) along += up * tan(Math.toRadians(lean))
            along = along.unit()
            val axis = restAlong cross along
            val v0 = DIRECTION.getValue(v0Name)
            val v25 = DIRECTION.getValue(v25Name)
            if (axis.length < 1e-9) return PartFrame(along, v0, v25)
            val k = axis.unit()
            val degrees = Math.toDegrees(acos((restAlong dot along).coerceIn(-1.0, 1.0)))
            return PartFrame(along, v0.turned(k, degrees), v25.turned(k, degrees))
        }
    }
}

/** A joint on a part's chain: its name, its u, and where it sits in body space. */
data class ChainJoint(val name: String, val u: Double, val at: P3)

/** One fitted part's chain of joints in body space, in order of u; a pair is its left side, [pose] naming it [poseId]. */
data class FigureChain(
    val part: String,
    val poseId: String,
    val kind: FigureKind,
    val length: Double,
    val joints: List<ChainJoint>,
    val parent: String?,
    /** How far behind its root, as a share of its first bone, the chain still takes outline nearer to it than to its parent. */
    val rootReach: Double = 0.0,
) {
    val root: P3 get() = joints.first().at
    val tip: P3 get() = joints.last().at

    /** The chain posed by [pose]: each joint's lean swings what lies past it toward its outer side, its degrees back about its hinge. */
    fun posedBy(pose: List<BonePose>): FigureChain {
        val points = joints.map { it.at }.toMutableList()
        joints.forEachIndexed { i, joint ->
            if (i == points.lastIndex) return@forEachIndexed
            for (bone in pose) {
                if (bone.part.value != poseId || bone.joint != joint.name) continue
                val origin = points[i]
                val segment = (points[i + 1] - origin).unit()
                val swings = buildList {
                    if (bone.lean != 0f) add((segment cross (P3.FORWARD cross segment)).unit() to bone.lean.toDouble())
                    if (bone.degrees != 0f) add((segment cross (P3.FORWARD * -1.0)).unit() to bone.degrees.toDouble())
                }
                for ((axis, degrees) in swings) {
                    for (k in i + 1 until points.size) points[k] = origin + (points[k] - origin).turned(axis, degrees)
                }
            }
        }
        return copy(joints = joints.mapIndexed { i, j -> j.copy(at = points[i]) })
    }

    /** The point on the chain at [u], between the joints either side of it. */
    fun at(u: Double): P3 {
        val next = joints.indexOfFirst { it.u >= u }.let { if (it <= 0) 1 else it }
        val a = joints[next - 1]
        val b = joints[next]
        val w = if (b.u > a.u) (u - a.u) / (b.u - a.u) else 0.0
        return a.at + (b.at - a.at) * w
    }
}

/** A bone as one view shows it: a stretch of a part's chain, its ends in the view, and the outline x it spans on the part. */
data class ViewBone(
    val part: String,
    val parent: String?,
    val from: P2,
    val to: P2,
    val xFrom: Double,
    val xTo: Double,
    /** How far past its start and its end, as a share of its length, it takes outline. */
    val before: Double,
    val after: Double,
)

/** A bone takes outline this share of its length past either end. */
private const val END_REACH = 0.02

/** A chain with a parent takes outline this share of its last bone past its tip: up to a hand or foot the outline leaves out. */
private const val TIP_REACH = 0.2

/** A chain's bones as [view] shows them. */
fun FigureChain.viewBones(view: FigureView, rootHeight: Double): List<ViewBone> {
    val last = joints.size - 2
    return (0..last).map { i ->
        val a = joints[i]
        val b = joints[i + 1]
        ViewBone(
            part, parent, project(view, a.at, rootHeight), project(view, b.at, rootHeight), a.u * length, b.u * length,
            before = if (i == 0) maxOf(END_REACH, rootReach) else END_REACH,
            after = if (i == last && parent != null) TIP_REACH else END_REACH,
        )
    }
}
