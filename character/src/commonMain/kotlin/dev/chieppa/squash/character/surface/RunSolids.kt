package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinAngles
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.MemberKey
import dev.chieppa.squash.geom.field.RootedRings
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.field.SweptSolid

/** The solid each run sweeps out, kept while the run is the same object, so an unchanged part keeps its members. */
class RunSolids {
    private var kept: List<Pair<Patch, Solid>> = emptyList()
    private var gaps: List<Pair<GapKey, Float?>> = emptyList()
    private var angles: List<Pair<AnglesKey, JoinAngles?>> = emptyList()
    private var rooted: List<Pair<Pair<Patch, Float>, Solid>> = emptyList()

    /** The gap [read] gives between [patch], placed at [origin], and the parent [parent] reads as, kept while all three are the same. */
    internal fun gap(patch: Patch, origin: Vec3, parent: MemberKey, read: () -> Float?): Float? {
        val key = GapKey(patch, origin, parent)
        gaps.firstOrNull { it.first == key }?.let { return it.second }
        val gap = read()
        gaps = (gaps + (key to gap)).takeLast(KEPT)
        return gap
    }

    private class GapKey(val patch: Patch, val origin: Vec3, val parent: MemberKey) {
        override fun equals(other: Any?): Boolean = other is GapKey && patch === other.patch && origin == other.origin && parent == other.parent
        override fun hashCode(): Int = origin.hashCode() * 31 + parent.hashCode()
    }

    /**
     * The shares of its blend [child]'s smooth union with [parent] takes by the angle they meet at ([JoinAngles]), kept while both read
     * the same and the blend is the same.
     */
    internal fun angles(child: JoinedMember, parent: JoinedMember): JoinAngles? {
        val key = AnglesKey(child.key, parent.key, child.blend)
        angles.firstOrNull { it.first == key }?.let { return it.second }
        val made = JoinAngles.of(child, parent, child.blend)
        angles = (angles + (key to made)).takeLast(KEPT)
        return made
    }

    private data class AnglesKey(val child: MemberKey, val parent: MemberKey, val blend: Float)

    private var hollows: List<Pair<Patch, SweptSolid>> = emptyList()
    private var made: List<Pair<List<Any>, Solid>> = emptyList()

    /** The hollow [patch]'s rings sweep with its mouth run on [reach] out past its first ring, kept while the run is the same object. */
    fun hollowOf(patch: Patch, reach: Float): SweptSolid {
        hollows.firstOrNull { it.first === patch }?.let { return it.second }
        val solid = mouthed(patch, reach)
        hollows = (hollows + (patch to solid)).takeLast(KEPT)
        return solid
    }

    /** What [make] makes, kept while [key], compared item by item, is the same, so a member made from unchanged solids reads as before. */
    internal fun <T : Solid> kept(key: List<Any>, make: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        made.firstOrNull { it.first == key }?.let { return it.second as T }
        val solid = make()
        made = (made + (key to solid)).takeLast(KEPT)
        return solid
    }

    fun of(patch: Patch): Solid {
        kept.firstOrNull { it.first === patch }?.let { return it.second }
        val solid = sweptOf(patch)
        kept = (kept + (patch to solid)).takeLast(KEPT)
        return solid
    }

    /** The solid [patch]'s rings sweep with its root carried on [reach] back along the run (see [sweptOf]), kept while both are the same. */
    fun of(patch: Patch, reach: Float): Solid {
        rooted.firstOrNull { it.first.first === patch && it.first.second == reach }?.let { return it.second }
        val solid = sweptOf(patch, reach)
        rooted = (rooted + ((patch to reach) to solid)).takeLast(KEPT)
        return solid
    }

    private companion object {
        const val KEPT = 256
    }
}

/**
 * The solid [patch]'s rings sweep, read from its points in its slices' planes at its rings' u and its columns' v, each ring read round
 * from its section's centre where the section names one. With [reach], its first ring is laid again that far back along the run, at the
 * u the run carries on to there, so the run's root goes on into its parent unchanged instead of ending flat.
 */
private fun sweptOf(patch: Patch, reach: Float = 0f): Solid {
    val slices = requireNotNull(patch.slices)
    val middles = slices.map { slice ->
        slice.section.centre?.let { c -> slice.frame.center + slice.frame.side * (c.x * slice.width / 2f) + slice.frame.up * (c.y * slice.height / 2f) }
    }
    val vs = FloatArray(patch.nv) { it.toFloat() / patch.segments }
    val rings = RootedRings(
        List(patch.nu) { ring -> List(patch.nv) { patch.point(patch.index(ring, it)) } },
        slices.map { it.frame.tangent },
        slices.map { it.frame.up },
        patch.ringUs,
        middles,
        reach,
    )
    return requireNotNull(SweptSolid.ofRings(rings.points, vs, rings.tangents, rings.ups, rings.us, rings.middles?.takeIf { all -> all.any { it != null } }))
}

/** [patch]'s solid with a ring before its first, its first moved [reach] back along the run, so the run opens out past where it starts. */
private fun mouthed(patch: Patch, reach: Float): SweptSolid {
    val slices = requireNotNull(patch.slices)
    val back = slices.first().frame.tangent * -reach
    val rings = List(patch.nu) { ring -> List(patch.nv) { patch.point(patch.index(ring, it)) } }
    val us = patch.ringUs
    val perU = (slices.zipWithNext { a, b -> (b.frame.center - a.frame.center).length }.sum() / (us.last() - us.first())).takeIf { it > 0f } ?: 1f
    val vs = FloatArray(patch.nv) { it.toFloat() / patch.segments }
    return requireNotNull(
        SweptSolid.ofRings(
            listOf(rings.first().map { it + back }) + rings,
            vs,
            listOf(slices.first().frame.tangent) + slices.map { it.frame.tangent },
            listOf(slices.first().frame.up) + slices.map { it.frame.up },
            floatArrayOf(us.first() - reach / perU) + us,
        ),
    )
}
