package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3

/** How far ahead of the head the look-at point rests while a pose sets none, as a multiple of the head bone's length. */
private const val LOOK_AHEAD = 1.4f

/** How far out from a bent knee its pole target stands, as a share of the limb's length. */
private const val POLE_OUT = 0.35f

/** How far off the line from hip to end a knee must stand for its limb to count as bent, as a share of the limb's length. */
private const val BENT = 0.06f

/** A tail's curl handle: an arc round its [tip], bending toward [toward], a unit direction. */
class CurlHandle(val tail: Tail, val tip: Vec3, val toward: Vec3)

/** A stretched joint's tag: the [factor] the span [from] the joint before it [to] it is stretched by. */
class StretchTag(val joint: Int, val from: Vec3, val to: Vec3, val factor: Float)

/** A bent limb's pole target: the point [at], out from its [knee]. */
class PoleHandle(val knee: Vec3, val at: Vec3)

/**
 * Where a pose's handles stand in body space: the [body] ring at the middle of the root part, each limb's [ends], each bent limb's
 * [poles] out from its knee, each tail's [curls], the [look] point ahead of the head seen from the head's joint [lookFrom], a [pins]
 * point on the ground under each planted paw, and a tag on each stretched span, [stretches].
 */
class PoseHandles(
    val body: Vec3?,
    val ends: Map<PartId, Vec3>,
    val poles: Map<PartId, PoleHandle>,
    val curls: List<CurlHandle>,
    val look: Vec3?,
    val lookFrom: Vec3?,
    val pins: Map<PartId, Vec3>,
    val stretches: List<StretchTag>,
)

/** The lowest point of [rig]'s parts at rest, where the ground is. */
val Rig.floor: Float get() = parts.minOfOrNull { part -> (1 until part.rest.size step 3).minOfOrNull { part.rest[it] } ?: Float.MAX_VALUE } ?: 0f

/** [pose]'s handles on this solver's rig, with the ground at [floor]. */
fun PoseSolver.handles(pose: Pose, floor: Float = rig.floor): PoseHandles {
    val work = PoseWork(rig, pose)
    val root = chains.root.takeIf { it >= 0 }
    val body = root?.let { r ->
        val part = rig.bones[r].name.part
        val own = rig.bones.indices.filter { rig.bones[it].name.part == part }
        (work.joint(own.first()) + work.joint(own.last())) * 0.5f
    }
    val ends = chains.limbs.associate { it.id to work.joint(it.end) }
    val poles = chains.limbs.mapNotNull { limb ->
        val hip = work.joint(limb.upper)
        val knee = work.joint(limb.lower)
        val end = work.joint(limb.end)
        val length = (knee - hip).length + (end - knee).length
        val out = knee - (hip + end) * 0.5f
        if (length <= 0f || out.length < BENT * length) null else limb.id to PoleHandle(knee, knee + out.normalized() * (POLE_OUT * length))
    }.toMap()
    val curls = chains.tails.map { tail ->
        val last = tail.bones.last()
        val before = tail.bones.dropLast(1).lastOrNull() ?: last
        val along = (work.joint(last) - work.joint(before)).let { if (it.length > 0f) it.normalized() else Vec3.Y }
        val toward = (work.turn(last) * rig.bones[last].hinge cross along).let { if (it.length > 0f) it.normalized() else Vec3.Y }
        CurlHandle(tail, work.joint(last), toward)
    }
    val look = pose.lookAt?.let { Vec3(it.x, it.y, it.z) } ?: chains.look?.let { chain ->
        val head = rig.bones[chain.head]
        work.joint(chain.head) + work.turn(chain.head) * head.direction * (head.length * LOOK_AHEAD)
    }
    val pins = chains.limbs.filter { it.id in pose.planted }.associate { limb -> limb.id to ends.getValue(limb.id).let { Vec3(it.x, floor, it.z) } }
    val stretches = rig.bones.indices.mapNotNull { i ->
        val factor = work.stretch(i).takeIf { it != 1f } ?: return@mapNotNull null
        val before = rig.parents[i].takeIf { it >= 0 } ?: return@mapNotNull null
        StretchTag(i, work.joint(before), work.joint(i), factor)
    }
    val lookFrom = chains.look?.let { work.joint(it.head) }
    return PoseHandles(body, ends, poles, curls, look, lookFrom, pins, stretches)
}
