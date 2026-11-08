package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BodyPoint
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3

/**
 * [start] with its body chain bent onto [curve], then each planted paw carried back by its leg to where and as it stood before the bend: the
 * body curve's solve followed by [PoseSolver.holdPaws]. An upright body turns on its planted hips, which stay where they stood (see
 * [onPlantedHips]). A paw that is not planted rides along with the body. See HLD: data-model, The body curve bends the spine and tail.
 */
fun PoseSolver.bendTo(start: Pose, curve: BodyCurve, options: SolveOptions = SolveOptions()): Solved {
    val fit = solveBodyCurve(rig, start, curve)
    return holdPaws(onPlantedHips(start, fit.pose), fit.paws.associate { it.part to it.before }, start, options)
}

/**
 * [start] with the body curve gone and the chain it bends back at rest, planted paws held where they stood by their legs reaching
 * again from rest, an upright body on its planted hips.
 */
fun PoseSolver.clearCurve(start: Pose, options: SolveOptions = SolveOptions()): Solved {
    val bones = curveBones(rig) ?: return Solved(start.copy(curve = null), SolveReport())
    val legs = chains.limbs.filter { it.id in start.planted }.flatMap { listOf(it.upper, it.lower) }.map { rig.bones[it].name }
    var rested = start.copy(curve = null)
    for (name in bones.behind + bones.spine + bones.ahead + bones.stretched + legs) rested = rested.without(name.part, name.joint)
    return holdPaws(onPlantedHips(start, rested), pawShifts(rig, start, rested).associate { it.part to it.before }, start, options)
}

/**
 * [bent] carried so the middle of the planted legs' hips on the root bone stands where it stood under [start], when the body is
 * upright: its pelvis then turns on its legs, as a standing body's does, rather than about the root bone's pivot below the hips,
 * which would lift the hips past the reach of legs standing straight. A body on all fours, whose legs bend under it, turns about the
 * root bone's pivot, its paws held by its legs.
 */
internal fun PoseSolver.onPlantedHips(start: Pose, bent: Pose): Pose {
    if (!rig.isUpright) return bent
    val hips = chains.limbs.filter { it.id in start.planted && rig.parents[it.upper] == chains.root }.map { it.upper }
    if (hips.isEmpty()) return bent
    fun middle(pose: Pose): Vec3 = PoseWork(rig, pose).let { work -> hips.fold(Vec3.ZERO) { sum, hip -> sum + work.joint(hip) } * (1f / hips.size) }
    val by = middle(start) - middle(bent)
    return bent.copy(offset = BodyPoint(bent.offset.x + by.x, bent.offset.y + by.y, bent.offset.z + by.z))
}
