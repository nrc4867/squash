package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.block.Field
import dev.chieppa.squash.character.block.FieldFloor
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.BodySolid
import dev.chieppa.squash.geom.field.CarvedSolid
import dev.chieppa.squash.geom.field.Hollow
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.TurnedEllipsoid

/** How near the floor, as a share of a run's reach along the part's axis, a run's furthest point lies for the run to stand on it. */
private const val ON_FLOOR = 0.02f

/** A ring no wider than this share of the run's reach is collapsed, a pole or a ring past the run's end. */
private const val COLLAPSED = 1e-3f

/** The floor of [part]'s field where it dishes the soles standing on it, or null. */
private fun dishedFloor(part: PlacedPart): FieldFloor? =
    part.template.blocks.firstNotNullOfOrNull { (it as? Field)?.floor }?.takeIf { it.across && it.hollow > 0f }

/**
 * [members] with each run that stands on a dished floor of its part ([FieldFloor.hollow]) dished into its sole: an ellipsoid as wide as
 * the run's last full ring laid flat on the floor, less the rim, and the hollow's share of its half width deep, cut out of every run of
 * the part it reaches ([Hollow], [CarvedSolid]), so each toe of a cloven hoof has its own hollow inside its wall. Each carving is kept in
 * [solids] while what it is made from is the same.
 */
internal fun withSoles(runs: List<FieldRun>, members: List<JoinedMember>, solids: RunSolids): List<JoinedMember> {
    val byPart = runs.indices.filter { dishedFloor(runs[it].part) != null }.groupBy { runs[it].partId }
    if (byPart.isEmpty()) return members
    val out = members.toMutableList()
    for ((_, indices) in byPart) {
        val part = runs[indices.first()].part
        val floor = checkNotNull(dishedFloor(part))
        val along = PartBuilder.axes(part.template).along
        val reaches = indices.associateWith { reachOf(runs[it].patch, along) }
        val level = reaches.values.mapNotNull { it?.first }.maxOrNull() ?: continue
        val span = reaches.values.mapNotNull { it?.second }.maxOrNull() ?: continue
        val dishes = indices.filter { i -> reaches[i]?.let { level - it.first <= span * ON_FLOOR } == true }
            .mapNotNull { i -> dishOf(runs[i].patch, along, level, span, floor) }
            .map { BodySolid(it, part.origin, part.mirrored) }
        if (dishes.isEmpty()) continue
        for (j in indices) {
            val member = out[j]
            val reached = dishes.filter { overlaps(member, it.low, it.high) }
            if (reached.isEmpty()) continue
            val carved = solids.kept(listOf(Same(member.solid), member.origin, member.mirrored) + reached.map { Same(it.solid) }) {
                CarvedSolid(member.solid, reached.map { Hollow(it, 0f) }, member.origin, member.mirrored)
            }
            out[j] = JoinedMember(carved, member.origin, member.mirrored, member.blend, member.parent, member.part, member.ease, member.angles)
        }
    }
    return out
}

/** The full rings of [patch]: those wider than a pole. */
private fun fullRings(patch: Patch, along: Vec3): List<List<Vec3>> {
    val rings = List(patch.nu) { ring -> List(patch.nv) { patch.point(patch.index(ring, it)) } }
    val all = rings.flatten()
    val reach = all.maxOf { it dot along } - all.minOf { it dot along }
    return rings.filter { ring -> ring.maxOf { (it - ring.first()).length } > reach * COLLAPSED }
}

/** How far along [along] [patch]'s full rings reach, and over how long a stretch; null with none. */
private fun reachOf(patch: Patch, along: Vec3): Pair<Float, Float>? {
    val points = fullRings(patch, along).flatten().ifEmpty { return null }
    val most = points.maxOf { it dot along }
    return most to most - points.minOf { it dot along }
}

/** The dish in [patch]'s sole on the floor at [level] along [along]: its last full ring laid on the floor, less [floor]'s rim. */
private fun dishOf(patch: Patch, along: Vec3, level: Float, span: Float, floor: FieldFloor): TurnedEllipsoid? {
    val last = fullRings(patch, along).lastOrNull() ?: return null
    val flat = last.map { it - along * ((it dot along) - level) }
    val mean = flat.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / flat.size)
    val across = (flat.maxBy { (it - mean).length } - mean).let { it - along * (it dot along) }
    if (across.length <= span * COLLAPSED) return null
    val x = across.normalized()
    val y = along cross x
    val xs = flat.map { (it - mean) dot x }
    val ys = flat.map { (it - mean) dot y }
    val halfX = (xs.max() - xs.min()) / 2f
    val halfY = (ys.max() - ys.min()) / 2f
    val narrow = minOf(halfX, halfY)
    if (narrow <= span * COLLAPSED) return null
    val centre = mean + x * ((xs.max() + xs.min()) / 2f) + y * ((ys.max() + ys.min()) / 2f)
    val inner = 1f - floor.rim
    return TurnedEllipsoid(centre, along, x, y, Vec3(narrow * floor.hollow, halfX * inner, halfY * inner))
}

/** Whether [member]'s box comes near the box from [low] to [high], both in body space. */
private fun overlaps(member: JoinedMember, low: Vec3, high: Vec3): Boolean =
    member.low.x <= high.x && member.high.x >= low.x && member.low.y <= high.y && member.high.y >= low.y &&
        member.low.z <= high.z && member.high.z >= low.z
