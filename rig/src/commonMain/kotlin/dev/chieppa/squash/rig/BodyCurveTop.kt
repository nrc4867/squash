package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.ArcPath
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.atan2

/** How far a lean is sought either way, in degrees, for a joint that no limits hold. */
private const val FREE_LEAN = 90f

/**
 * How many times the walk runs, the stroke scaled each time to the steps as they now look from above and carried to where the root
 * part's first step now starts: a lean changes both.
 */
private const val CARRY_ROUNDS = 5

/** How many times a step is aimed again at where its leant length reaches. */
private const val AIM_ROUNDS = 3

/** How many halvings find a bone's lean. */
private const val LEAN_HALVINGS = 16

/** Below this a step seen from above has no direction to aim, in body units. */
private const val FLAT_STEP = 1e-5f

/**
 * [pose] with the chain's bones leant across their hinges so the chain, seen from above, lies along the Top [stroke], and where the
 * stroke was placed to meet it, in body x and z. The stroke is scaled as the Side solve's is, from the chain's steps as seen from
 * above, and carried to start where the root part's first step does; then from the root part outward each bone leans, within the reach its limits give a lean, until its
 * step points at the stroke one step's chord on, the part ahead along the stroke's end. Each bone's tip, its turn and every stretch
 * stay as [pose] has them, and a locked bone keeps its own lean.
 */
internal fun leanAlongTop(rig: Rig, chain: BodyChain, pose: Pose, stroke: BodyStroke): Pair<Pose, StrokePlacement> {
    val work = PoseWork(rig, pose)
    for (link in chain.links) work.setAngles(link.owner, work.angles(link.owner).copy(lean = 0f))
    val drawn = stroke.path().runningAlong(chain.along { Vec2(it.x, it.z) })
    var placement = StrokePlacement.NONE
    repeat(CARRY_ROUNDS) {
        val steps = chain.links.indices.map { work.step(chain, it).size }
        val total = steps.sum()
        if (total <= FLAT_STEP) return work.pose to StrokePlacement.NONE
        val scale = fittingScale(drawn, steps, total)
        val before = steps.take(chain.root).sum()
        val sized = drawn.placed(scale)
        placement = StrokePlacement(scale, work.base(chain, chain.root) - sized.pointAt(before))
        work.walk(chain, sized.placed(1f, placement.offset), before)
    }
    return work.pose to placement
}

/**
 * Leans each bone of the chain from the root part outward so its step ends on [path] past the last, the part ahead along its end. A
 * lean changes how long a step looks from above, so each step is aimed again at the point its new length reaches.
 */
private fun PoseWork.walk(chain: BodyChain, path: ArcPath, before: Float) {
    var s = before
    for (i in chain.root until chain.links.size) {
        if (chain.links[i].ahead) aim(chain, i, path.endTangent) else s = reachAlong(chain, i, path, s)
    }
    val back = path.reversed()
    var r = path.length - before
    for (i in chain.root - 1 downTo 0) r = reachAlong(chain, i, back, r)
}

/** Aims chain step [i] at the point of [path] past arc length [after] its length reaches, and gives that point's arc length. */
private fun PoseWork.reachAlong(chain: BodyChain, i: Int, path: ArcPath, after: Float): Float {
    val base = base(chain, i)
    var s = after
    repeat(AIM_ROUNDS) {
        s = path.reach(base, after, step(chain, i).size)
        aim(chain, i, path.pointAt(s) - base)
    }
    return s
}

/** The posed point of chain step [i] nearer the root part, seen from above. */
private fun PoseWork.base(chain: BodyChain, i: Int): Vec2 = ends(chain, i).first

/** Chain step [i] as posed, from its end nearer the root part to its far end, seen from above. */
private fun PoseWork.step(chain: BodyChain, i: Int): Vec2 = ends(chain, i).let { (base, tip) -> tip - base }

private fun PoseWork.ends(chain: BodyChain, i: Int): Pair<Vec2, Vec2> {
    val link = chain.links[i]
    val carry = frames.transforms[link.owner]
    val from = carry(link.from).top
    val to = carry(link.to).top
    return if (i >= chain.root) Pair(from, to) else Pair(to, from)
}

/** Leans chain step [i]'s bone, within the reach its limits give a lean, so the step points along [toward] seen from above. */
private fun PoseWork.aim(chain: BodyChain, i: Int, toward: Vec2) {
    val owner = chain.links[i].owner
    if (locked(owner) || toward.size <= FLAT_STEP) return
    val bone = rig.bones[owner]
    val reach = if (set(owner)?.mode == JointMode.FREE) FREE_LEAN else bone.limits?.turnReach() ?: FREE_LEAN
    if (reach <= 0f) return
    val angles = angles(owner)
    val aim = atan2(toward.y, toward.x)
    fun off(lean: Float): Float {
        setAngles(owner, angles.copy(lean = lean))
        val step = step(chain, i)
        return if (step.size <= FLAT_STEP) 0f else wrapped(atan2(step.y, step.x) - aim)
    }
    var low = -reach
    var high = reach
    val atLow = off(low)
    val atHigh = off(high)
    if (atLow * atHigh > 0f) {
        off(if (abs(atLow) < abs(atHigh)) low else high)
        return
    }
    repeat(LEAN_HALVINGS) {
        val middle = (low + high) / 2f
        if (off(middle) * atLow > 0f) low = middle else high = middle
    }
    off((low + high) / 2f)
}

private val Vec3.top: Vec2 get() = Vec2(x, z)
