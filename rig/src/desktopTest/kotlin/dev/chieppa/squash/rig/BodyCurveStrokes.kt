package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec2

internal val TORSO = PartId("torso")
internal val TAIL = PartId("tail")
internal val HEAD = PartId("head")
internal val PAWS = listOf("front-paw-l", "front-paw-r", "back-paw-l", "back-paw-r").map(::PartId).toSet()

/** A play bow: the torso sloping down to the chest, the head raised against it, the tail lifted high. */
internal val BOW = Pose(
    listOf(
        BonePose(TORSO, "hips", 8f), BonePose(TORSO, "loin", 6f), BonePose(TORSO, "waist", 6f), BonePose(TORSO, "ribs", 4f),
        BonePose(TORSO, "chest", -6f), BonePose(HEAD, "neck", -28f),
        BonePose(TAIL, "root", -45f), BonePose(TAIL, "tail1", -12f), BonePose(TAIL, "tail2", -10f), BonePose(TAIL, "tail3", 8f),
        BonePose(TAIL, "tail4", 12f), BonePose(TAIL, "tail5", 10f),
    ),
)

/**
 * The play bow as a stroke drawn in Side over the bowed fennec: a smooth curve from the tail's tip through every joint of the
 * chain to the head's end, its tangents those of a Catmull-Rom spline. Body x and y, scaled and moved off the body so the solve
 * has to fit it.
 */
internal val PLAY_BOW: BodyStroke by lazy { strokeOver(BOW, View.SIDE) }

/** The bow with the tail swung to one side and the spine following it a little, as leans. */
internal val SWAYED = BOW.bones.map { bone ->
    when (bone.part) {
        TAIL -> bone.copy(lean = 14f)
        TORSO -> bone.copy(lean = 4f)
        else -> bone
    }
}.let(::Pose)

/** The swayed bow as a stroke drawn in Top over it: body x and z, scaled and moved off the body as [PLAY_BOW] is. */
internal val SWAY: BodyStroke by lazy { strokeOver(SWAYED, View.TOP) }

/** A smooth stroke in [view] through every joint of the chain under [pose] to the head's end, scaled and moved off the body. */
internal fun strokeOver(pose: Pose, view: View): BodyStroke {
    val chain = checkNotNull(bodyChain(fennec.rig))
    val transforms = boneTransforms(fennec.rig, pose)
    val points = chain.links.map { transforms[it.owner](it.from) } + chain.links.last().let { transforms[it.owner](it.to) }
    val flat = points.map { Vec2(it.x * DRAWN_SCALE + DRAWN_SHIFT, (if (view == View.TOP) it.z else it.y) * DRAWN_SCALE - DRAWN_SHIFT) }
    return BodyStroke(view, catmullRom(flat).map { CurvePoint(it.x, it.y) })
}

private const val DRAWN_SCALE = 1.3f
private const val DRAWN_SHIFT = 0.2f

/** Joined cubics through [points] with Catmull-Rom tangents, each end's tangent along its last chord. */
private fun catmullRom(points: List<Vec2>): List<Vec2> = buildList {
    add(points.first())
    for (i in 0 until points.lastIndex) {
        val before = points[(i - 1).coerceAtLeast(0)]
        val after = points[(i + 2).coerceAtMost(points.lastIndex)]
        val a = points[i]
        val b = points[i + 1]
        val outOf = if (i == 0) (b - a) * (1f / 3f) else (b - before) * (1f / 6f)
        val into = if (i + 1 == points.lastIndex) (b - a) * (1f / 3f) else (after - a) * (1f / 6f)
        add(a + outOf)
        add(b - into)
        add(b)
    }
}
