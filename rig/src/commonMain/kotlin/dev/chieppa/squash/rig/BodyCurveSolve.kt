package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.ArcPath
import dev.chieppa.squash.geom.BezierPath
import dev.chieppa.squash.geom.CubicBezier
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

private const val RADIANS_PER_DEGREE = (PI / 180.0).toFloat()
private const val FULL_TURN = (2.0 * PI).toFloat()
private const val HALF_TURN = PI.toFloat()
private const val SCALE_GROWTH = 1.25f
private const val SCALE_HALVINGS = 30

/** Where a planted paw stood, by its first bone's pivot in body space, under the pose before a solve and under the solved one. */
data class PawShift(val part: PartId, val before: Vec3, val after: Vec3)

/**
 * A body curve solved onto a rig. [pose] is the pose given with the chain's bones turned and stretched and the curve kept.
 * The Side stroke lies where the chain was fitted to it at its drawn points times [scale] plus [offset], in body x and y.
 * [paws] says where each planted paw ended against where it stood, for the limbs' pin solve to bring back.
 */
class BodyCurveFit(val pose: Pose, val scale: Float, val offset: Vec2, val paws: List<PawShift>) {
    /** A point of the Side stroke as drawn, where the fit put it in body x and y. */
    fun placed(drawn: Vec2): Vec2 = Vec2(drawn.x * scale + offset.x, drawn.y * scale + offset.y)
}

/**
 * [pose] with [rig]'s body chain bent onto [curve]'s Side stroke by arc length; see HLD: data-model, Body curve. The stroke is
 * scaled so the chain's spans, laid along it as chords from its start, end at its end, at the curve's length factor: every
 * span of the tail and the root part stretches by that factor and narrows by its square root, the head keeping its size. From the root part's first step outward each joint lands
 * on the stroke one span's chord from the last, each bone turning about its hinge, within its limits, to lie along its chord;
 * the part ahead lies along the stroke's end tangent. The root bone keeps its place, so the stroke is carried to meet it. Legs,
 * the jaw and the ears keep their pose. A Top stroke is kept and bends nothing.
 */
fun solveBodyCurve(rig: Rig, pose: Pose, curve: BodyCurve): BodyCurveFit {
    val kept = pose.copy(curve = curve)
    val chain = bodyChain(rig)
    val stroke = curve.stroke(View.SIDE)
    if (chain == null || stroke == null) return BodyCurveFit(kept, 1f, Vec2(0f, 0f), pawShifts(rig, pose, kept))
    val factor = curve.length.coerceAtLeast(MIN_STRETCH)
    val vectors = chain.links.map { (stretchOf(rig, chain, it.owner, factor) * (it.to - it.from)).flat }
    val total = vectors.sumOf { it.size.toDouble() }.toFloat()
    val drawn = stroke.path().runningAlong(chain)
    val scale = fittingScale(drawn, vectors.map { it.size }, total)
    val path = drawn.placed(scale)
    val walk = ChainWalk(rig, pose)

    val before = vectors.take(chain.root).sumOf { it.size.toDouble() }.toFloat()
    var s = before
    val start = path.pointAt(s)
    var point = start
    for (i in chain.root until chain.links.size) {
        val chord = if (chain.links[i].ahead) {
            path.endTangent
        } else {
            s = path.reach(point, s, vectors[i].size)
            path.pointAt(s) - point
        }
        point += walk.place(chain.links[i].owner, chord, vectors[i])
    }
    val back = path.reversed()
    var r = path.length - before
    point = start
    for (i in chain.root - 1 downTo 0) {
        r = back.reach(point, r, vectors[i].size)
        point -= walk.place(chain.links[i].owner, point - back.pointAt(r), vectors[i])
    }

    val rootLink = chain.links[chain.root]
    val owner = rig.bones[rootLink.owner]
    val parent = rig.parents[rootLink.owner]
    val pivot = if (parent >= 0) boneTransforms(rig, pose)[parent](owner.pivot) else owner.pivot
    val reach = (stretchOf(rig, chain, rootLink.owner, factor) * (rootLink.from - owner.pivot)).flat
    val carried = pivot.flat + rotate(reach, walk.turnOf(rootLink.owner))
    val solved = walk.posed(kept, chain, factor)
    return BodyCurveFit(solved, scale, carried - start, pawShifts(rig, pose, solved))
}

/** The turns a walk along the chain has set: each bone's turn in the Side plane as the body sees it, and its own degrees. */
private class ChainWalk(private val rig: Rig, private val pose: Pose) {
    private val turns = HashMap<Int, Float>()
    private val degrees = LinkedHashMap<Int, Float>()

    fun turnOf(bone: Int): Float = turns[bone] ?: run {
        val b = rig.bones[bone]
        val own = (pose.of(b.name.part, b.name.joint)?.degrees ?: 0f).heldBy(b)
        val parent = rig.parents[bone]
        (if (parent >= 0) turnOf(parent) else 0f) + b.hinge.z.sign * own * RADIANS_PER_DEGREE
    }

    /** Turns [bone] so its rest step [vector] lies along [chord], within its limits, and gives the step as turned. */
    fun place(bone: Int, chord: Vec2, vector: Vec2): Vec2 {
        val b = rig.bones[bone]
        val parent = rig.parents[bone].let { if (it >= 0) turnOf(it) else 0f }
        val wanted = wrapped(atan2(chord.y, chord.x) - atan2(vector.y, vector.x) - parent)
        val own = (b.hinge.z.sign * wanted / RADIANS_PER_DEGREE).heldBy(b)
        degrees[bone] = own
        val turn = parent + b.hinge.z.sign * own * RADIANS_PER_DEGREE
        turns[bone] = turn
        return rotate(vector, turn)
    }

    /** [pose] with the walked bones' degrees and every stretched span's joint at [factor]. */
    fun posed(pose: Pose, chain: BodyChain, factor: Float): Pose {
        var out = pose
        for ((bone, own) in degrees) out = out.set(rig.bones[bone].name) { it.copy(degrees = own) }
        for (bone in chain.stretched) {
            val joint = rig.stretchedBy[bone]
            if (joint >= 0) out = out.set(rig.bones[joint].name) { it.copy(stretch = factor) }
        }
        return out
    }

    private fun Pose.set(name: BoneName, change: (BonePose) -> BonePose): Pose {
        val bone = change(of(name.part, name.joint) ?: BonePose(name.part, name.joint))
        return if (bone.isRest) without(name.part, name.joint) else with(bone)
    }
}

/** The map that stretches [bone]'s span by [factor] when the chain stretches it, keeping volume, or null when it keeps its length. */
private fun stretchOf(rig: Rig, chain: BodyChain, bone: Int, factor: Float): Mat3? =
    if (bone in chain.stretched && rig.stretchedBy[bone] >= 0 && factor != 1f) stretchAlong(rig.bones[bone].direction, factor) else null

private operator fun Mat3?.times(v: Vec3): Vec3 = if (this == null) v else this * v

/**
 * The scale at which [steps] of chord laid one after another along [drawn] from its start end at its end. Chords cut across
 * the curve's bends, so this is at least [total] over the drawn length.
 */
private fun fittingScale(drawn: ArcPath, steps: List<Float>, total: Float): Float {
    fun overshoot(scale: Float): Float {
        var s = 0f
        for (step in steps) s = drawn.reach(drawn.pointAt(s), s, step / scale)
        return s - drawn.length
    }
    var low = total / drawn.length
    var high = low
    while (overshoot(high) > 0f) high *= SCALE_GROWTH
    repeat(SCALE_HALVINGS) {
        val middle = (low + high) / 2f
        if (overshoot(middle) > 0f) low = middle else high = middle
    }
    return (low + high) / 2f
}

private fun pawShifts(rig: Rig, before: Pose, after: Pose): List<PawShift> {
    if (after.planted.isEmpty()) return emptyList()
    val from = boneTransforms(rig, before)
    val to = boneTransforms(rig, after)
    return after.planted.mapNotNull { part ->
        val first = rig.bones.indexOfFirst { it.name.part == part }.takeIf { it >= 0 } ?: return@mapNotNull null
        val pivot = rig.bones[first].pivot
        PawShift(part, from[first](pivot), to[first](pivot))
    }
}

/** This stroke's joined cubics as a path measured by arc length. */
internal fun BodyStroke.path(): ArcPath {
    val p = points.map { Vec2(it.x, it.y) }
    return ArcPath.of(BezierPath((0 until (p.size - 1) / 3).map { CubicBezier(p[it * 3], p[it * 3 + 1], p[it * 3 + 2], p[it * 3 + 3]) }))
}

/** This path running the way the chain does, from the part behind to the part ahead, whichever way it was drawn. */
private fun ArcPath.runningAlong(chain: BodyChain): ArcPath {
    val along = (chain.links.last().to - chain.links.first().from).flat
    val drawn = points.last() - points.first()
    return if (drawn.x * along.x + drawn.y * along.y < 0f) reversed() else this
}

private val Vec3.flat: Vec2 get() = Vec2(x, y)

private val Vec2.size: Float get() = hypot(x, y)

private fun rotate(v: Vec2, radians: Float): Vec2 {
    val c = cos(radians)
    val s = sin(radians)
    return Vec2(v.x * c - v.y * s, v.x * s + v.y * c)
}

private fun wrapped(radians: Float): Float {
    var a = radians % FULL_TURN
    if (a > HALF_TURN) a -= FULL_TURN
    if (a < -HALF_TURN) a += FULL_TURN
    return a
}

private fun Float.heldBy(bone: Bone): Float = bone.limits?.let { coerceIn(it.min, it.max) } ?: this
