package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.mouth.MouthRecess
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.BodySolid
import dev.chieppa.squash.geom.field.CarvedSolid
import dev.chieppa.squash.geom.field.Hollow
import dev.chieppa.squash.geom.field.JoinedMember

/**
 * [members] with each of [recesses] cut out of its part ([Hollow], [CarvedSolid]): out of the run its mouth lies on with its edge
 * rounded into the lips, a point the recess shapes placed at the middle of the opening nearest it, so the walls take the opening's paint;
 * and out of every other run of the part it reaches by the blends they join by more, so the walls are the mouth's run's own. Each
 * carving is kept in [solids] while what it is made from is the same. See HLD: look, Mouth.
 */
internal fun withRecesses(runs: List<FieldRun>, members: List<JoinedMember>, solids: RunSolids, recesses: List<MouthRecess>): List<JoinedMember> {
    if (recesses.isEmpty()) return members
    val out = members.toMutableList()
    for (j in runs.indices) {
        val run = runs[j]
        val part = run.part
        val member = out[j]
        val reached = recesses.filter { recess ->
            recess.part == run.partId && BodySolid(recess.solid, part.origin, part.mirrored).let { overlaps(member, it.low, it.high) }
        }
        if (reached.isEmpty()) continue
        val walls = reached.map { recess -> if (recess.patch == run.patch.name) 0f else maxOf(member.blend, mouthRunBlend(runs, members, recess)) }
        val rounding = reached.minOf { it.rounding }
        val carved = solids.kept(listOf(Same(member.solid), member.origin, member.mirrored, rounding, walls) + reached) {
            val hollows = reached.mapIndexed { k, recess -> Hollow(BodySolid(recess.solid, part.origin, part.mirrored), walls[k]) }
            val mouths = reached.filter { it.patch == run.patch.name }
            val placeCut = if (mouths.isEmpty()) null else { q: Vec3, place: FloatArray ->
                mouths.minBy { recess -> recess.solid.distance(q) }.solid.placeInOpening(q, place)
            }
            CarvedSolid(member.solid, hollows, member.origin, member.mirrored, rounding, placeCut = placeCut)
        }
        out[j] = JoinedMember(carved, member.origin, member.mirrored, member.blend, member.parent, member.part, member.ease, member.angles)
    }
    return out
}

/** The blend the run [recess]'s mouth lies on joins its parent by; 0 where it is no run of the field. */
private fun mouthRunBlend(runs: List<FieldRun>, members: List<JoinedMember>, recess: MouthRecess): Float =
    runs.indexOfFirst { it.partId == recess.part && it.patch.name == recess.patch }.takeIf { it >= 0 }?.let { members[it].blend } ?: 0f

/** Whether [member]'s box meets the box from [low] to [high], both in body space. */
private fun overlaps(member: JoinedMember, low: Vec3, high: Vec3): Boolean =
    member.low.x <= high.x && member.high.x >= low.x && member.low.y <= high.y && member.high.y >= low.y && member.low.z <= high.z && member.high.z >= low.z
