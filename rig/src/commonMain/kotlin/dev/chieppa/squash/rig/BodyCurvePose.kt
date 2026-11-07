package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.Pose

/**
 * [start] with its body chain bent onto [curve], then each planted paw carried back by its leg to where it stood before the bend: the
 * body curve's solve followed by [PoseSolver.holdPaws]. A paw that is not planted rides along with the body. See HLD: data-model,
 * The body curve bends the spine and tail.
 */
fun PoseSolver.bendTo(start: Pose, curve: BodyCurve, options: SolveOptions = SolveOptions()): Solved {
    val fit = solveBodyCurve(rig, start, curve)
    return holdPaws(fit.pose, fit.paws.associate { it.part to it.before }, options)
}

/**
 * [start] with the body curve gone and the chain it bends back at rest, planted paws held where they stood by their legs reaching
 * again from rest.
 */
fun PoseSolver.clearCurve(start: Pose, options: SolveOptions = SolveOptions()): Solved {
    val bones = curveBones(rig) ?: return Solved(start.copy(curve = null), SolveReport())
    val legs = chains.limbs.filter { it.id in start.planted }.flatMap { listOf(it.upper, it.lower) }.map { rig.bones[it].name }
    var rested = start.copy(curve = null)
    for (name in bones.behind + bones.spine + bones.ahead + bones.stretched + legs) rested = rested.without(name.part, name.joint)
    return holdPaws(rested, pawShifts(rig, start, rested).associate { it.part to it.before }, options)
}
