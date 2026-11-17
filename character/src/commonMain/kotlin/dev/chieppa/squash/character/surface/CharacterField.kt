package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.field.SweptSolid

/** A part's socket blend, when the document holds none, as a share of the least extent of its first run's box. */
private const val SOCKET_BLEND_SHARE = 0.25f

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

/** Whether [part] is in a character's field: a part of the body, not thin or hard, with a swept run. */
fun isFieldPart(part: PlacedPart): Boolean =
    part.id != null && part.covering == null && part.hanging == null && part.owner == null &&
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
    val made = runs.map { solids.of(it.patch) }
    val firstOf = HashMap<PartId, Int>()
    runs.forEachIndexed { i, run -> if (run.first) firstOf[run.partId] = i }
    val partIndex = parts.withIndex().associate { (i, p) -> requireNotNull(p.id) to i }
    val members = runs.mapIndexed { i, run ->
        val part = run.part
        val solid = made[i]
        val (parent, blend) = if (run.first) {
            val parentId = part.socket?.parent
            val parentRuns = runs.indices.filter { runs[it].partId == parentId }
            val anchor = part.origin
            val parentRun = parentRuns.minByOrNull { k -> JoinedMember(made[k], runs[k].part.origin, runs[k].part.mirrored, 0f).own(anchor) } ?: -1
            parentRun to if (parentRun < 0) 0f else socketBlend(part, solid)
        } else {
            val share = part.template.defaults[MERGE_PARAM]?.let { it * SOCKET_BLEND_SHARE } ?: RUN_BLEND_SHARE
            firstOf.getValue(run.partId) to leastExtent(solid) * share
        }
        JoinedMember(solid, part.origin, part.mirrored, blend, parent, partIndex.getValue(run.partId))
    }
    val firsts = runs.indices.filter { runs[it].first }
    val root = firsts.filter { runs[it].part.socket == null }.ifEmpty { firsts }
    return CharacterField(JoinedField(members), runs, root.maxOf { leastExtent(made[it]) / 2f } * cellShare)
}

/** The blend [part]'s first run, swept as [solid], joins its socket parent by. */
internal fun socketBlend(part: PlacedPart, solid: Solid): Float = part.blend ?: leastExtent(solid) * SOCKET_BLEND_SHARE

private fun leastExtent(solid: Solid): Float {
    val size = solid.high - solid.low
    return minOf(size.x, size.y, size.z)
}

/** The solid each run sweeps out, kept while the run is the same object, so an unchanged part keeps its members. */
class RunSolids {
    private var kept: List<Pair<Patch, Solid>> = emptyList()

    fun of(patch: Patch): Solid {
        kept.firstOrNull { it.first === patch }?.let { return it.second }
        val solid = sweptOf(patch)
        kept = (kept + (patch to solid)).takeLast(KEPT)
        return solid
    }

    private companion object {
        const val KEPT = 256
    }
}

/** The solid [patch]'s rings sweep, read from its points in its slices' planes at its rings' u and its columns' v. */
private fun sweptOf(patch: Patch): Solid {
    val slices = requireNotNull(patch.slices)
    val vs = FloatArray(patch.nv) { it.toFloat() / patch.segments }
    return requireNotNull(
        SweptSolid.ofRings(
            List(patch.nu) { ring -> List(patch.nv) { patch.point(patch.index(ring, it)) } },
            vs,
            slices.map { it.frame.tangent },
            slices.map { it.frame.up },
            patch.ringUs,
        ),
    )
}
