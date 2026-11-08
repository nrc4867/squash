package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BodyPoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** The longest a drag with stretch on makes a limb's lower bone, as a factor of its rest length. */
const val MAX_DRAG_STRETCH = 1.5f

/** The share of a look's turn the neck takes, the head turning the rest. See HLD: data-model, Poses are solved. */
const val NECK_SHARE = 0.3f

/** How many halvings find where a firm body stops. */
private const val FIRM_STEPS = 14

/** How far a curl's equal share turns either way at most, in degrees, before each joint's limits hold it, and how many thirds find it. */
private const val CURL_REACH = 120f
private const val CURL_STEPS = 40

/** How a drag solves: [firm] stops the body where planted paws reach, [stretch] lets a limb stretch past its reach. */
data class SolveOptions(val firm: Boolean = false, val stretch: Boolean = false)

/**
 * What a solve did: how far the dragged end stays from where it was asked to go, [missed], in body units or, for a look, degrees;
 * how far each held paw slid from where it stood, by paw, [slid]; each stretching joint the solve changed, with its factor,
 * [stretched]; and how far the body moved, [moved].
 */
class SolveReport(val missed: Float = 0f, val slid: Map<PartId, Float> = emptyMap(), val stretched: Map<BoneName, Float> = emptyMap(), val moved: Vec3 = Vec3.ZERO)

/** A solve's [pose] and its [report]. */
class Solved(val pose: Pose, val report: SolveReport)

/**
 * Makes poses on [rig] from handles: a paw reaching, the body moving over its planted paws, a tail curling, the head looking, a
 * knee swinging toward its pole. Each solve starts from the pose it is given, never from its own last answer, so a drag solves
 * from the pose it started on at every step. See HLD: data-model, Poses are solved.
 */
class PoseSolver(val rig: Rig) {
    val chains: PoseChains = poseChains(rig)

    /** Where [limb]'s end, the joint its paw hangs from, stands under [pose]. */
    fun endOf(pose: Pose, limb: Limb): Vec3 = PoseWork(rig, pose).joint(limb.end)

    /** [limb]'s end carried to [target], every other bone as it was, the paw keeping its turn in body space. */
    fun reach(start: Pose, limb: Limb, target: Vec3, options: SolveOptions = SolveOptions()): Solved {
        val work = PoseWork(rig, start)
        val foot = limb.foot?.let(work::turn)
        val missed = work.reachWith(limb, target, options.stretch, MAX_DRAG_STRETCH, foot)
        return Solved(work.pose, SolveReport(missed, stretched = stretchedBy(start, work.pose)))
    }

    /** [limb] swung about the line from its hip to its end so its knee goes toward [pole], the end and the paw staying put. */
    fun swing(start: Pose, limb: Limb, pole: Vec3): Solved {
        val work = PoseWork(rig, start)
        work.swingToward(limb, pole, limb.foot?.let(work::turn))
        return Solved(work.pose, SolveReport())
    }

    /**
     * The whole body moved [by], each planted paw, and each lifted one the pose keeps still, held where and as it stood by its leg. With
     * [SolveOptions.firm] the body stops where the first of them would leave its place; otherwise it goes all the way and a paw past
     * its leg's reach slides along the ground toward under the hip, by as little as lets the leg reach it.
     */
    fun moveBody(start: Pose, by: Vec3, options: SolveOptions = SolveOptions()): Solved {
        val begin = PoseWork(rig, start)
        val held = chains.limbs.filter { it.id in start.planted || it.id in start.still }
        val goals = held.associateWith { begin.joint(it.end) }
        val stances = held.associateWith { PawStance(begin, it) }
        fun attempt(share: Float): Pair<PoseWork, Map<Limb, Float>> {
            val work = PoseWork(rig, start.copy(offset = start.offset + by * share))
            val misses = held.associateWith { work.holdAt(it, goals.getValue(it), options.stretch, stances.getValue(it)) }
            return work to misses
        }
        val share = if (options.firm) firmShare(::attempt) else 1f
        val (work, misses) = attempt(share)
        val slid = HashMap<PartId, Float>()
        for (limb in held) {
            if (misses.getValue(limb) <= LIMB_TOLERANCE) continue
            val goal = goals.getValue(limb)
            work.holdAt(limb, slideTarget(work, limb, goal), options.stretch, stances.getValue(limb))
            slid[limb.id] = (work.joint(limb.end) - goal).length
        }
        return Solved(work.pose, SolveReport(slid = slid, stretched = stretchedBy(start, work.pose), moved = by * share))
    }

    /** The largest share of a move every held paw can follow, found by halving. */
    private fun firmShare(attempt: (Float) -> Pair<PoseWork, Map<Limb, Float>>): Float {
        if (attempt(1f).second.values.all { it <= LIMB_TOLERANCE }) return 1f
        var low = 0f
        var high = 1f
        repeat(FIRM_STEPS) {
            val middle = (low + high) / 2f
            if (attempt(middle).second.values.all { it <= LIMB_TOLERANCE }) low = middle else high = middle
        }
        return low
    }

    /** Where a paw that cannot stay at [goal] lands: slid along the ground toward under the hip until the leg's full reach meets it. */
    private fun slideTarget(work: PoseWork, limb: Limb, goal: Vec3): Vec3 {
        val hip = work.joint(limb.upper)
        val reach = (work.joint(limb.lower) - hip).length + (work.joint(limb.end) - work.joint(limb.lower)).length - LIMB_TOLERANCE
        val drop = hip.y - goal.y
        val under = Vec3(hip.x, goal.y, hip.z)
        if (reach <= abs(drop)) return under
        val radius = sqrt(reach * reach - drop * drop)
        val away = goal - under
        return if (away.length <= radius) goal else under + away.normalized() * radius
    }

    /** [tail] curled toward [target]: one turn about each joint's hinge, the same share for every joint, added to each joint's own. */
    fun curl(start: Pose, tail: Tail, target: Vec3): Solved {
        val tip = tail.bones.last()
        fun curled(share: Float): PoseWork = PoseWork(rig, start).also { work ->
            tail.bones.forEach { i -> work.setAngles(i, work.angles(i).let { it.copy(tip = it.tip + share) }) }
        }
        var best = 0f
        var bestMiss = (curled(0f).joint(tip) - target).length
        var low = -CURL_REACH
        var high = CURL_REACH
        repeat(CURL_STEPS) {
            val a = low + (high - low) / 3f
            val b = high - (high - low) / 3f
            val missA = (curled(a).joint(tip) - target).length
            val missB = (curled(b).joint(tip) - target).length
            if (missA < missB) high = b else low = a
            if (missA < bestMiss) {
                best = a
                bestMiss = missA
            }
            if (missB < bestMiss) {
                best = b
                bestMiss = missB
            }
        }
        return Solved(curled(best).pose, SolveReport(bestMiss))
    }

    /**
     * The head turned to face [target], the neck taking [NECK_SHARE] of the turn and the head the rest, each within its limits;
     * planted paws keep their place. The pose keeps [target] as its look-at point. Its report's miss is in degrees.
     */
    fun look(start: Pose, target: Vec3, options: SolveOptions = SolveOptions()): Solved {
        val chain = chains.look ?: return Solved(start, SolveReport())
        val begin = PoseWork(rig, start)
        val work = PoseWork(rig, start)
        val head = rig.bones[chain.head]
        fun facing() = work.turn(chain.head) * head.direction
        val whole = target - work.joint(chain.head)
        chain.neck.forEach { bone ->
            work.setWorldTurn(bone, shareOfTurn(facing(), whole, NECK_SHARE / chain.neck.size) * work.turn(bone))
        }
        work.setWorldTurn(chain.head, shareOfTurn(facing(), target - work.joint(chain.head)) * work.turn(chain.head))
        holdPlanted(begin, work, options)
        val missed = turnBetween(facing(), target - work.joint(chain.head))?.second?.times(RADIANS_TO_DEGREES) ?: 0f
        return Solved(work.pose.copy(lookAt = BodyPoint(target.x, target.y, target.z)), SolveReport(missed))
    }

    /** The degrees bone [bone] turns in body space between [from] and [to]. */
    fun turnedBetween(from: Pose, to: Pose, bone: Int): Float {
        val a = PoseWork(rig, from).turn(bone)
        val b = PoseWork(rig, to).turn(bone)
        return angleOf(b * a.transposed)
    }

    /**
     * The pin solve: [pose] with each paw in [targets], by paw, carried back to its target by its leg, the paw and its leg's end bone
     * keeping the turns in body space they had under [stood], so the paw stands as it stood. A paw no leg can bring back stays as near
     * as it reaches, and the report says how far each stays off, in [SolveReport.slid]. Run it after any solve that moves bones above
     * planted paws, such as a body curve's, with the paws' places and the pose before it.
     */
    fun holdPaws(pose: Pose, targets: Map<PartId, Vec3>, stood: Pose, options: SolveOptions = SolveOptions()): Solved {
        val work = PoseWork(rig, pose)
        val before = PoseWork(rig, stood)
        val off = HashMap<PartId, Float>()
        chains.limbs.filter { it.id in targets }.forEach { limb ->
            val miss = work.holdAt(limb, targets.getValue(limb.id), options.stretch, PawStance(before, limb))
            if (miss > LIMB_TOLERANCE) off[limb.id] = miss
        }
        return Solved(work.pose, SolveReport(slid = off, stretched = stretchedBy(pose, work.pose)))
    }

    /** Puts each planted paw back where it stood in [begin], after [work] has moved bones above it, each paw standing as it did in [begin]. */
    private fun holdPlanted(begin: PoseWork, work: PoseWork, options: SolveOptions) {
        chains.limbs.filter { it.id in work.pose.planted }.forEach { limb ->
            work.holdAt(limb, begin.joint(limb.end), options.stretch, PawStance(begin, limb))
        }
    }

    private fun stretchedBy(from: Pose, to: Pose): Map<BoneName, Float> = rig.bones.mapNotNull { bone ->
        val after = to.of(bone.name.part, bone.name.joint)?.stretch ?: 1f
        val before = from.of(bone.name.part, bone.name.joint)?.stretch ?: 1f
        if (after != before) bone.name to after else null
    }.toMap()
}

private operator fun BodyPoint.plus(v: Vec3) = BodyPoint(x + v.x, y + v.y, z + v.z)
