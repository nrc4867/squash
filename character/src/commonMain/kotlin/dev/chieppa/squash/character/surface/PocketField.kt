package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.block.Pocket
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.BodySolid
import dev.chieppa.squash.geom.field.CarvedSolid
import dev.chieppa.squash.geom.field.Hollow
import dev.chieppa.squash.geom.field.HollowLining
import dev.chieppa.squash.geom.field.JoinedMember

/** How far a pocket's hollow runs on out of its mouth, past its first ring, as a multiple of the mouth's radius, so it opens through the surface. */
internal const val MOUTH_REACH = 2.5f

/** The share of a pocket's wall its lining joins the part it is cut into by, where it has no rim. */
private const val PLAIN_EDGE_BLEND = 0.25f

/** The pocket block of [part]'s template, where the part is a recess into what it sits on. */
internal fun pocketOf(part: PlacedPart): Pocket? = part.template.blocks.firstNotNullOfOrNull { it as? Pocket }

/**
 * [members] with each pocket's first run made a recess into the part its socket is on: that part's runs reaching the hollow cut by it and
 * its wall ([Hollow], [CarvedSolid]), and the pocket's member the lining that fills the wall again, with its rim ([HollowLining]), joined
 * to the run it sits on by the rim's radius. Each lining and carving is kept in [solids] while what it is made from is the same, so an
 * unchanged pocket keeps its members. See HLD: surface, Pockets.
 */
internal fun withPockets(runs: List<FieldRun>, members: List<JoinedMember>, solids: RunSolids): List<JoinedMember> {
    val pockets = runs.indices.filter { runs[it].first && pocketOf(runs[it].part) != null }
    if (pockets.isEmpty()) return members
    val out = members.toMutableList()
    val cuts = HashMap<Int, MutableList<Hollow>>()
    for (i in pockets) {
        val run = runs[i]
        val part = run.part
        val pocket = checkNotNull(pocketOf(part))
        val slices = run.patch.slices ?: continue
        val mouth = slices.first().let { (it.width + it.height) / 4f }
        val hollow = solids.hollowOf(run.patch, mouth * MOUTH_REACH)
        val wall = mouth * maxOf(pocket.wall, pocket.rim * 2f)
        val rim = mouth * pocket.rim
        val hosts = runs.indices.filter { j -> runs[j].partId == part.socket?.parent && !(runs[j].first && pocketOf(runs[j].part) != null) }
        val inBody = BodySolid(hollow, part.origin, part.mirrored)
        val reached = hosts.filter { j -> overlaps(members[j], inBody.low, inBody.high, wall) }
        if (reached.isEmpty()) continue
        val hostSolids = reached.map { j -> members[j].solid to members[j] }
        val lining = solids.kept(listOf(Same(hollow), wall, rim, part.origin, part.mirrored) + hostSolids.map { Same(it.first) }) {
            HollowLining(hollow, hostSolids.map { (solid, member) -> BodySolid(solid, member.origin, member.mirrored) }, wall, rim, part.origin, part.mirrored)
        }
        val member = members[i]
        out[i] = JoinedMember(lining, member.origin, member.mirrored, if (rim > 0f) rim else wall * PLAIN_EDGE_BLEND, member.parent, member.part, member.ease, member.angles)
        reached.forEach { j -> cuts.getOrPut(j) { ArrayList() } += Hollow(inBody, wall) }
    }
    for ((j, hollows) in cuts) {
        val member = members[j]
        val carved = solids.kept(listOf(Same(member.solid), member.origin, member.mirrored) + hollows.map { Same((it.hollow as BodySolid).solid) to it.wall }) {
            CarvedSolid(member.solid, hollows, member.origin, member.mirrored)
        }
        out[j] = JoinedMember(carved, member.origin, member.mirrored, member.blend, member.parent, member.part, member.ease, member.angles)
    }
    return out
}

/** Whether [member]'s box comes within [margin] of the box from [low] to [high], both in body space. */
private fun overlaps(member: JoinedMember, low: Vec3, high: Vec3, margin: Float): Boolean =
    member.low.x <= high.x + margin && member.high.x >= low.x - margin &&
        member.low.y <= high.y + margin && member.high.y >= low.y - margin &&
        member.low.z <= high.z + margin && member.high.z >= low.z - margin
