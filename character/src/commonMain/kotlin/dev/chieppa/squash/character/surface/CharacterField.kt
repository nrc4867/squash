package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.field.JoinAngles
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.RootedRings
import dev.chieppa.squash.geom.field.rootReach
import dev.chieppa.squash.geom.field.MemberKey
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.field.SweptSolid

/** A part's socket blend, when the document holds none, as a share of the least extent of its first run's box. */
private const val SOCKET_BLEND_SHARE = 0.25f

/**
 * A part's socket blend, when the document and template hold none and its first run starts open (a limb, a neck, a tail), as a
 * multiple of that run's root radius, so the join rounds over as wide as the part is thick and a neck flows into the shoulders.
 */
const val ROOT_BLEND_SHARE = 1.4f

/** How many times the narrowest gap between a run and its parent, past its root, a default rounded blend may reach. */
private const val GAP_BLENDS = 1.5f

/** Every how many rings along a run the gap to the parent is read. */
private const val GAP_RING_STEP = 2

/** Every how many points round a ring the gap to the parent is read. */
private const val GAP_SEGMENT_STEP = 4

/** How large a run's first ring must be, as a share of its widest, for the run to start open rather than closed at a pole. */
private const val OPEN_ROOT_SHARE = 0.5f

/** The blend one run of a part joins its first run by, when the template names no merge, as a share of the least extent of its box. */
private const val RUN_BLEND_SHARE = 0.125f

/** The template parameter a part's runs join its first run by, as a share of the least extent of the joining run's box. */
private const val MERGE_PARAM = "merge"

/**
 * The body's cell, as a share of its root's first run's least half-extent: the part on no socket, which no outfit swaps, so the cell
 * stays the same whatever the character wears. At this share the fennec's body meshes at 0.024, its head, hands and feet at half that
 * and its digits at a quarter (see [digitLevels]).
 */
const val CELL_SHARE = 0.1f

/**
 * One member of a character's field: run [patchIndex], [patch], of [part], and whether it is the part's first run: its body run, whose
 * (u, v) are the part's own, or its first run where it has none.
 */
class FieldRun(val part: PlacedPart, val patchIndex: Int, val patch: Patch, val first: Boolean) {
    val partId: PartId get() = requireNotNull(part.id)
}

/** A character's field: its runs' solids joined in [field], [runs] saying which part and run each member is, and the [cell] it meshes at. */
class CharacterField(val field: JoinedField, val runs: List<FieldRun>, val cell: Float) {
    /** The parts in the field. */
    val parts: Set<PartId> = runs.mapTo(LinkedHashSet()) { it.partId }
}

/** Whether [part] is in a character's field: a part of the body, not thin or hard, shown once and not as copies, with a swept run. */
fun isFieldPart(part: PlacedPart): Boolean =
    part.id != null && part.covering == null && part.hanging == null && part.spanning == null && part.owner == null && part.copies == null &&
        part.template.surface == SurfaceMethod.FIELD && part.surface.patches.any { isFieldRun(part, it) }

/** Whether [patch] of [part] is a run in the body's field: a swept run that no block of its template builds as its own mesh. */
fun isFieldRun(part: PlacedPart, patch: Patch): Boolean =
    isRun(patch) && part.template.blocks.none { block ->
        block.surfaceMethod() == SurfaceMethod.SEPARATE &&
            block.patchNames().any { it.name == patch.name || (it.repeats && patch.name.startsWith(it.name + ":")) }
    }

/** Whether [patch] is a swept run that a member's solid is read from. */
fun isRun(patch: Patch): Boolean = patch.slices != null && patch.nu >= 2

/** Which of [patches] is a part's first run: its body run, whose (u, v) are the part's own, or its first run where it has none; -1 with no run. */
fun firstRunIndex(patches: List<Patch>): Int =
    patches.indexOfFirst { isRun(it) && it.name == BODY_PATCH }.takeIf { it >= 0 } ?: patches.indexOfFirst(::isRun)

/**
 * The field of [character]'s field parts: each run of each part a member, its solid swept by its rings and kept in [solids] while the
 * run is the same; a part's first run joined to the run of its socket parent nearest its socket by the part's socket blend, its other
 * runs joined to its first by its merge, and every other pair of runs meeting by a plain union, meshed at [cellShare] of its root's
 * least half-extent. Null with no field part.
 */
fun fieldOf(character: AssembledCharacter, solids: RunSolids, cellShare: Float = CELL_SHARE): CharacterField? {
    val parts = character.parts.filter(::isFieldPart)
    if (parts.isEmpty()) return null
    val runs = parts.flatMap { part ->
        val first = firstRunIndex(part.surface.patches)
        part.surface.patches.withIndex().filter { isFieldRun(part, it.value) }.map { (k, patch) -> FieldRun(part, k, patch, k == first) }
    }
    val plain = runs.map { solids.of(it.patch) }
    fun bare(k: Int) = JoinedMember(plain[k], runs[k].part.origin, runs[k].part.mirrored, 0f)
    val firstOf = HashMap<PartId, Int>()
    runs.forEachIndexed { i, run -> if (run.first) firstOf[run.partId] = i }
    val partIndex = parts.withIndex().associate { (i, p) -> requireNotNull(p.id) to i }
    val parentOf = IntArray(runs.size) { i ->
        val run = runs[i]
        if (run.first) {
            val parentId = run.part.socket?.parent
            runs.indices.filter { runs[it].partId == parentId }.minByOrNull { k -> bare(k).own(run.part.origin) } ?: -1
        } else {
            firstOf.getValue(run.partId)
        }
    }
    val made = runs.mapIndexed { i, run ->
        val parent = parentOf[i]
        val reach = if (run.first && parent >= 0) onClosedEndReach(run, runs[parent].part, bare(parent)) else 0f
        if (reach > 0f) solids.of(run.patch, reach) else plain[i]
    }
    val members = runs.mapIndexed { i, run ->
        val part = run.part
        val parent = parentOf[i]
        val blend = when {
            parent < 0 -> 0f
            run.first -> roomyBlend(part, plain[i], run.patch, bare(parent), solids)
            else -> leastExtent(plain[i]) * (part.template.defaults[MERGE_PARAM]?.let { it * SOCKET_BLEND_SHARE } ?: RUN_BLEND_SHARE)
        }
        val member = JoinedMember(made[i], part.origin, part.mirrored, blend, parent, partIndex.getValue(run.partId))
        if (!run.first || parent < 0 || blend <= 0f || part.template.join.blend != null) {
            member
        } else {
            val parentMember = JoinedMember(made[parent], runs[parent].part.origin, runs[parent].part.mirrored, 0f)
            JoinedMember(made[i], part.origin, part.mirrored, blend, parent, member.part, angles = solids.angles(member, parentMember))
        }
    }.let { withPockets(runs, it, solids) }.let { withSoles(runs, it, solids) }
    val firsts = runs.indices.filter { runs[it].first }
    val root = firsts.filter { runs[it].part.socket == null }.ifEmpty { firsts }
    return CharacterField(JoinedField(members), runs, root.maxOf { leastExtent(made[it]) / 2f } * cellShare)
}

/**
 * How far [run], a part's first run that starts open, carries on past its root into [parent], the part it sits on, when it sits on
 * [parent]'s closed end: the longest of [ROOT_REACHES] of its root radius at which its root ring, moved back that far along the run, lies
 * wholly inside [parentRun]. A neck wider than the closed top it stands on so comes up out of the torso and blends into its shoulders,
 * with no flat root standing out over the top's edge. 0 elsewhere.
 */
internal fun onClosedEndReach(run: FieldRun, parent: PlacedPart, parentRun: JoinedMember): Float {
    val part = run.part
    val spec = part.socketSpec ?: return 0f
    val patch = run.patch
    val radius = openRootRadius(patch)
    if (radius <= 0f || parent.poleAt(spec.u, spec.patch) == null) return 0f
    val back = part.surface.shown(requireNotNull(patch.slices)[0].frame.tangent.normalized()) * -1f
    val ring = List(patch.nv) { k -> part.origin + part.surface.shown(patch.point(patch.index(0, k))) }
    return rootReach(ring, back, radius) { parentRun.own(it) }
}

/**
 * The blend [part]'s first run, [patch] swept as [solid], joins its socket parent by: the part's own, else its template's share of the
 * run's length, else the larger of a share of the least extent of the run's box and a multiple of the radius it starts open at.
 */
internal fun socketBlend(part: PlacedPart, solid: Solid, patch: Patch): Float =
    part.blend ?: part.template.join.blend?.let { it * runLength(patch) }
        ?: maxOf(leastExtent(solid) * SOCKET_BLEND_SHARE, openRootRadius(patch) * ROOT_BLEND_SHARE)

/**
 * The mean radius of [patch]'s first ring where the run starts open there, at least [OPEN_ROOT_SHARE] of its widest ring; 0 where it starts
 * closed, as a head or a foot does round a pole.
 */
internal fun openRootRadius(patch: Patch): Float {
    val slices = patch.slices ?: return 0f
    val radii = slices.map { (it.width + it.height) / 4f }
    val root = radii.firstOrNull() ?: return 0f
    return if (root >= radii.max() * OPEN_ROOT_SHARE) root else 0f
}

/**
 * [part]'s socket blend as [socketBlend] gives it, but a default that rounds by the root radius held to [GAP_BLENDS] times the narrowest
 * gap between the run, past its root and the blend's own reach, and [parent], the run it joins: a smooth union fuses two surfaces less
 * than half its blend apart, so an arm hanging close beside the hips is not webbed to them.
 */
internal fun roomyBlend(part: PlacedPart, solid: Solid, patch: Patch, parent: JoinedMember, solids: RunSolids = RunSolids()): Float {
    val blend = socketBlend(part, solid, patch)
    if (part.blend != null || part.template.join.blend != null) return blend
    val least = leastExtent(solid) * SOCKET_BLEND_SHARE
    if (blend <= least) return blend
    val gap = solids.gap(patch, part.origin, parent.key) { gapPastRoot(part, patch, parent, blend + openRootRadius(patch)) } ?: return blend
    return blend.coerceAtMost(maxOf(least, gap * GAP_BLENDS))
}

/** The nearest [parent]'s field comes to [patch]'s rings further than [past] along the run from its root, or null with none so far. */
private fun gapPastRoot(part: PlacedPart, patch: Patch, parent: JoinedMember, past: Float): Float? {
    val slices = patch.slices ?: return null
    var along = 0f
    var gap: Float? = null
    for (ring in 0 until patch.nu) {
        if (ring > 0) along += (slices[ring].frame.center - slices[ring - 1].frame.center).length
        if (along < past || ring % GAP_RING_STEP != 0) continue
        for (k in 0 until patch.nv step GAP_SEGMENT_STEP) {
            val d = parent.own(part.origin + part.surface.shown(patch.point(patch.index(ring, k))))
            gap = minOf(gap ?: d, d)
        }
    }
    return gap?.coerceAtLeast(0f)
}

/** How long [patch]'s run is along the middles of its rings. */
private fun runLength(patch: Patch): Float {
    val slices = patch.slices ?: return 0f
    return slices.zipWithNext { a, b -> (b.frame.center - a.frame.center).length }.sum()
}

private fun leastExtent(solid: Solid): Float {
    val size = solid.high - solid.low
    return minOf(size.x, size.y, size.z)
}

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
