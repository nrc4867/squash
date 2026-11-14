package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh
import kotlin.math.abs
import kotlin.math.round

/**
 * How far inside [worn]'s coverage a vertex of a garment's or a body's mesh meshed from [field] lies, in body units, held within
 * [reach]: for each run it holds a share of, the distance into its branch's u range, and its v range for a branch part of the way round,
 * measured along the run; a run of a part the garment does not cover, or a part's later run, which its branch does not read, counts as
 * [reach] outside. The share-weighted sum of those is one
 * smooth function, zero along the hem, the neck opening and the cuffs. Where a part comes out through a hole of the garment, [holes],
 * its members as placed, the coverage is at most how far the vertex lies outside them, less [clear]. Safe to read from many threads at once.
 */
class Coverage(
    private val field: CharacterField,
    private val worn: WornCover,
    private val reach: Float,
    private val holes: List<JoinedMember> = emptyList(),
    private val clear: Float = 0f,
) {
    private val branches = Array(field.runs.size) { field.runs[it].takeIf { run -> run.first }?.let { run -> worn.branchOf(run.partId) } }
    private val scales = Array(field.runs.size) { if (branches[it] != null) RunScales(field.runs[it].patch) else null }

    /** The coverage at vertex [v] of [mesh]. */
    fun at(mesh: SurfaceMesh, v: Int): Float {
        var sum = 0f
        var total = 0f
        for (k in 0 until HELD) {
            val member = mesh.held[v * HELD + k]
            if (member < 0) continue
            val share = mesh.heldShares[v * HELD + k]
            val branch = branches[member]
            val inside = if (branch == null) {
                -reach
            } else {
                val u = mesh.heldU[v * HELD + k]
                val scale = checkNotNull(scales[member])
                val along = minOf(u - branch.from, branch.to - u) * scale.along(u)
                if (branch.allRound) along else minOf(along, aroundInside(mesh.heldV[v * HELD + k], branch) * scale.around(u))
            }
            sum += share * inside.coerceIn(-reach, reach)
            total += share
        }
        val covered = if (total > 0f) sum / total else -reach
        if (holes.isEmpty() || covered <= -reach) return covered
        val at = Vec3(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2])
        var outside = reach
        for (hole in holes) if (hole.boxDistance(at) < reach + clear) outside = minOf(outside, hole.own(at) - clear)
        return minOf(covered, outside.coerceIn(-reach, reach))
    }

    /** Fills [out] at vertices [from] until [to] of [mesh]. */
    fun fill(mesh: SurfaceMesh, out: FloatArray, from: Int, to: Int) {
        for (v in from until to) out[v] = at(mesh, v)
    }
}

/** Whether [anchor], on [surface], its part's, lies in this cover's range on its part's first run, whose (u, v) a branch reads. */
fun GarmentCover.coversAnchor(anchor: Anchor, surface: PartSurface): Boolean {
    val branch = branches[anchor.part] ?: return false
    if (surface.patches.firstOrNull(::isRun)?.name != anchor.patch) return false
    if (anchor.u < branch.from || anchor.u > branch.to) return false
    return branch.allRound || aroundInside(anchor.v, branch) >= 0f
}

/** How far [v] lies inside [branch]'s range round, wrapping once. */
private fun aroundInside(v: Float, branch: GarmentBranch): Float {
    val middle = (branch.fromV + branch.toV) / 2f
    val half = (branch.toV - branch.fromV) / 2f
    var off = v - middle
    off -= round(off)
    return half - abs(off)
}
